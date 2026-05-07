// JNI bridge for the on-device LLM.
//
// Surface (mirrored 1:1 by LlamaBridge.kt):
//   nativeInit(nativeLibDir)            — once per process; loads ggml CPU
//                                         backends from the APK's lib dir.
//   nativeLoadModel(path, n_ctx)        — returns a session handle (jlong)
//                                         or 0 on failure.
//   nativeFreeModel(handle)             — frees model+context+sampler.
//   nativeStartGeneration(handle, prompt, max_tokens)
//                                       — tokenize + decode prompt; arms the
//                                         loop. Returns 0 on success, neg
//                                         error code otherwise.
//   nativeNextToken(handle)             — sample + decode the next token.
//                                         Returns the token's text, or null
//                                         on EOS / cap / error.
//   nativeStopGeneration(handle)        — sets a flag that nativeNextToken
//                                         observes; can be called from any
//                                         thread.
//   nativeGreeting()                    — Phase 1 smoke test, kept around.
//
// Threading: each session is pinned to whichever thread calls
// nativeNextToken (llama_context isn't safe across threads). The Kotlin side
// runs the whole session on Dispatchers.IO sequentially.

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>
#include "llama.h"

#define ARCANA_TAG "ArcanaLlama"
#define ARCANA_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  ARCANA_TAG, __VA_ARGS__)
#define ARCANA_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  ARCANA_TAG, __VA_ARGS__)
#define ARCANA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ARCANA_TAG, __VA_ARGS__)

// llama.cpp logs into our logcat tag for easier debugging.
static void llama_log_to_android(ggml_log_level level, const char* text, void*) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: __android_log_print(ANDROID_LOG_ERROR, "llama.cpp", "%s", text); break;
        case GGML_LOG_LEVEL_WARN:  __android_log_print(ANDROID_LOG_WARN,  "llama.cpp", "%s", text); break;
        case GGML_LOG_LEVEL_INFO:  __android_log_print(ANDROID_LOG_INFO,  "llama.cpp", "%s", text); break;
        default:                   __android_log_print(ANDROID_LOG_DEBUG, "llama.cpp", "%s", text); break;
    }
}

struct LlamaSession {
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* sampler = nullptr;
    int32_t n_decoded   = 0;
    int32_t n_max       = 0;
    std::atomic<bool> stop{false};
    bool generating     = false;
};

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeGreeting(JNIEnv* env, jclass) {
    const char* sysinfo = llama_print_system_info();
    std::string out = "arcana-llama linked OK; ";
    out += (sysinfo ? sysinfo : "(no system info)");
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeInit(JNIEnv* env, jclass, jstring jlib_dir) {
    llama_log_set(llama_log_to_android, nullptr);

    const char* lib_dir = env->GetStringUTFChars(jlib_dir, nullptr);
    ARCANA_LOGI("nativeInit: loading ggml backends from %s", lib_dir);
    ggml_backend_load_all_from_path(lib_dir);
    env->ReleaseStringUTFChars(jlib_dir, lib_dir);

    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeLoadModel(
    JNIEnv* env, jclass, jstring jpath, jint n_ctx
) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    ARCANA_LOGI("nativeLoadModel: %s (n_ctx=%d)", path, n_ctx);

    auto* session = new LlamaSession();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only on Android for now.
    session->model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!session->model) {
        ARCANA_LOGE("llama_model_load_from_file returned null");
        delete session;
        return 0L;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx         = static_cast<uint32_t>(n_ctx);
    cparams.n_batch       = 512;
    cparams.n_threads     = 4;
    cparams.n_threads_batch = 4;

    session->ctx = llama_init_from_model(session->model, cparams);
    if (!session->ctx) {
        ARCANA_LOGE("llama_init_from_model returned null");
        llama_model_free(session->model);
        delete session;
        return 0L;
    }

    // Sampler chain: top-k → top-p → temperature → distribution.
    // Temperature of 0.5 (was 0.7) trades a bit of variety for noticeably
    // tighter format compliance on the 0.5B model — 0.7 was producing
    // "###2###"-style heading drift and occasional invented cards. 0.5 is
    // still warm enough that re-running gives meaningfully different prose.
    auto sparams = llama_sampler_chain_default_params();
    session->sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(session->sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(session->sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(0.5f));
    llama_sampler_chain_add(session->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeFreeModel(JNIEnv*, jclass, jlong handle) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (!session) return;
    if (session->sampler) llama_sampler_free(session->sampler);
    if (session->ctx)     llama_free(session->ctx);
    if (session->model)   llama_model_free(session->model);
    delete session;
}

JNIEXPORT jint JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeStartGeneration(
    JNIEnv* env, jclass, jlong handle, jstring jprompt, jint max_tokens
) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (!session) return -1;

    const llama_vocab* vocab = llama_model_get_vocab(session->model);

    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c);
    env->ReleaseStringUTFChars(jprompt, prompt_c);

    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(session->ctx));
    ARCANA_LOGI("nativeStartGeneration: prompt %zu chars, n_ctx=%d, max_tokens=%d",
                prompt.size(), n_ctx, max_tokens);

    // Wipe per-generation state. Without this, the KV cache from prior
    // generations stays in place and new prompt tokens append on top —
    // running a 3-card reading then a 10-card reading would push the
    // accumulated sequence past n_ctx and crash inside llama_decode on
    // some Android builds. Reset the sampler too so its randomness
    // history doesn't bleed across what should be independent readings.
    llama_memory_clear(llama_get_memory(session->ctx), /*data=*/true);
    llama_sampler_reset(session->sampler);

    // First call with a 0-sized buffer returns the negated token count we
    // need to allocate. (Standard llama_tokenize idiom.)
    const int32_t n_needed = -llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        nullptr, 0, /*add_special*/ true, /*parse_special*/ true
    );
    if (n_needed <= 0) {
        ARCANA_LOGE("llama_tokenize: bad prompt (n_needed=%d)", n_needed);
        return -2;
    }
    ARCANA_LOGI("Tokenized prompt: %d tokens", n_needed);

    // Bail before decoding if the prompt + intended generation can't fit
    // in the context window. Without this, llama_decode may behave
    // unpredictably (or crash on some Android builds) when the prompt
    // alone is already close to n_ctx.
    if (n_needed + max_tokens > n_ctx) {
        ARCANA_LOGE("Prompt (%d) + max_tokens (%d) exceeds n_ctx (%d)",
                    n_needed, max_tokens, n_ctx);
        return -5;
    }

    std::vector<llama_token> tokens(n_needed);
    if (llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), n_needed, true, true) < 0) {
        ARCANA_LOGE("llama_tokenize: tokenization failed");
        return -3;
    }

    // Feed the prompt through llama_decode in n_batch-sized chunks rather
    // than one giant batch. llama.cpp is supposed to handle the split
    // internally when batch.size > n_batch, but on Android we've seen
    // hard native crashes mid-decode for prompts well under n_ctx —
    // splitting ourselves dodges that path entirely. Each chunk gets its
    // own llama_batch_get_one with positions auto-tracked.
    constexpr int32_t PROMPT_CHUNK = 256;
    int32_t fed = 0;
    while (fed < n_needed) {
        const int32_t chunk = std::min<int32_t>(PROMPT_CHUNK, n_needed - fed);
        ARCANA_LOGI("llama_decode: chunk [%d, %d) of %d", fed, fed + chunk, n_needed);
        llama_batch batch = llama_batch_get_one(tokens.data() + fed, chunk);
        const int32_t rc = llama_decode(session->ctx, batch);
        if (rc != 0) {
            ARCANA_LOGE("llama_decode: prompt chunk at %d failed (rc=%d)", fed, rc);
            return -4;
        }
        fed += chunk;
    }
    ARCANA_LOGI("Prompt prefill done.");

    session->n_decoded   = 0;
    session->n_max       = max_tokens > 0 ? max_tokens : 512;
    session->stop.store(false);
    session->generating  = true;
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeNextToken(JNIEnv* env, jclass, jlong handle) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (!session || !session->generating) return nullptr;
    if (session->stop.load() || session->n_decoded >= session->n_max) {
        session->generating = false;
        return nullptr;
    }

    const llama_vocab* vocab = llama_model_get_vocab(session->model);

    llama_token id = llama_sampler_sample(session->sampler, session->ctx, -1);
    if (llama_vocab_is_eog(vocab, id)) {
        session->generating = false;
        return nullptr;
    }
    llama_sampler_accept(session->sampler, id);

    // Convert the chosen token into a UTF-8 piece. Most pieces are tiny;
    // a 256-byte buffer is comfortably more than enough.
    char piece[256];
    int32_t n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, /*special*/ true);
    if (n < 0) {
        ARCANA_LOGW("llama_token_to_piece returned %d for token %d", n, id);
        session->generating = false;
        return nullptr;
    }
    std::string out(piece, n);

    // Feed the freshly sampled token back so the next sample sees it.
    llama_batch next = llama_batch_get_one(&id, 1);
    if (llama_decode(session->ctx, next) != 0) {
        ARCANA_LOGE("llama_decode: failed to advance with sampled token");
        session->generating = false;
        return nullptr;
    }

    session->n_decoded++;
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeStopGeneration(JNIEnv*, jclass, jlong handle) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (session) session->stop.store(true);
}

} // extern "C"
