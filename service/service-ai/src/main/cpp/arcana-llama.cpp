// The app's side of the on-device model: JNI functions for LlamaBridge.kt, each a thin wrapper
// over a Session (arcana-session.h), which is where the work is done.
//
// Text crosses the boundary as UTF-8 bytes, never as Java strings. JNI's string functions use a
// modified UTF-8 that mangles emoji, and a character can be split across two tokens.

#include <jni.h>
#include <android/log.h>
#include <string>

#include "arcana-session.h"
#include "ggml-backend.h"

#define TAG "ArcanaLlama"

namespace {

void log_errors(enum ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
}

std::string bytes(JNIEnv *env, jbyteArray array) {
    if (!array) return {};
    jsize n = env->GetArrayLength(array);
    std::string out(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(array, 0, n, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

arcana::Session *session(jlong handle) { return reinterpret_cast<arcana::Session *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeInit(JNIEnv *env, jclass, jstring lib_dir) {
    llama_log_set(log_errors, nullptr);
    // The CPU math comes in one library per instruction set; this picks the best the phone has.
    const char *dir = env->GetStringUTFChars(lib_dir, nullptr);
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(lib_dir, dir);
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeLoad(JNIEnv *env, jclass, jstring path, jint n_ctx, jint threads, jint batch_threads) {
    const char *file = env->GetStringUTFChars(path, nullptr);
    arcana::Session *s = arcana::Session::load(file, n_ctx, threads, batch_threads);
    env->ReleaseStringUTFChars(path, file);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeFree(JNIEnv *, jclass, jlong handle) {
    delete session(handle);
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeBegin(JNIEnv *env, jclass, jlong handle, jbyteArray system,
                                                         jfloat temperature, jint top_k, jfloat top_p, jfloat repeat_penalty) {
    session(handle)->begin(bytes(env, system), temperature, top_k, top_p, repeat_penalty);
}

JNIEXPORT jint JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeUser(JNIEnv *env, jclass, jlong handle, jbyteArray text, jint reserve) {
    return session(handle)->user(bytes(env, text), reserve);
}

JNIEXPORT jint JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeFeed(JNIEnv *, jclass, jlong handle, jint max_tokens) {
    return session(handle)->feed(max_tokens);
}

JNIEXPORT jboolean JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeGrammar(JNIEnv *env, jclass, jlong handle, jbyteArray gbnf) {
    const bool ok = session(handle)->grammar(bytes(env, gbnf));
    if (!ok) __android_log_print(ANDROID_LOG_ERROR, TAG, "grammar did not parse");
    return ok ? JNI_TRUE : JNI_FALSE;
}

// The next token's bytes, or null when the reply is finished.
JNIEXPORT jbyteArray JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeNext(JNIEnv *env, jclass, jlong handle) {
    std::string piece;
    if (!session(handle)->next(piece)) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(piece.size()));
    if (out && !piece.empty()) env->SetByteArrayRegion(out, 0, static_cast<jsize>(piece.size()), reinterpret_cast<const jbyte *>(piece.data()));
    return out;
}

JNIEXPORT void JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeReply(JNIEnv *env, jclass, jlong handle, jbyteArray text) {
    session(handle)->reply(bytes(env, text));
}

JNIEXPORT jint JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeContextLeft(JNIEnv *, jclass, jlong handle) {
    return session(handle)->context_left();
}

}  // extern "C"
