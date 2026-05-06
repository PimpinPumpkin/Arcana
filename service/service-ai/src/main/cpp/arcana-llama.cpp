// Phase 1 stub for the on-device llama.cpp bridge.
//
// The goal of this file in Phase 1 is to verify the toolchain end-to-end:
// llama.cpp compiles, our shared lib links against it, and JNI symbols are
// resolvable from Kotlin. The actual inference path (model load, sampling,
// streaming) lands in Phase 3 once the downloader (Phase 2) provides a
// model file.

#include <jni.h>
#include <android/log.h>
#include <string>
#include "llama.h"

#define ARCANA_TAG "ArcanaLlama"
#define ARCANA_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ARCANA_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arcana_service_ai_local_LlamaBridge_nativeGreeting(JNIEnv* env, jclass) {
    ARCANA_LOGI("LlamaBridge.nativeGreeting() called");
    // Pull a real symbol from llama.h so the linker proves it can resolve
    // upstream functions, not just our wrapper TU.
    const char* sysinfo = llama_print_system_info();
    std::string out = "arcana-llama linked OK; ";
    out += (sysinfo ? sysinfo : "(no system info)");
    return env->NewStringUTF(out.c_str());
}
