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
    auto sparams = llama_sampler_chain_default_params();
    session->sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(session->sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(session->sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(0.7f));
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
    std::vector<llama_token> tokens(n_needed);
    if (llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), n_needed, true, true) < 0) {
        ARCANA_LOGE("llama_tokenize: tokenization failed");
        return -3;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(session->ctx, batch) != 0) {
        ARCANA_LOGE("llama_decode: prompt prefill failed");
        return -4;
    }

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
