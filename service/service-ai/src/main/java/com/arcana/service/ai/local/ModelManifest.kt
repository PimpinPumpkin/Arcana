package com.arcana.service.ai.local

/**
 * Pointer to a single on-device LLM artifact: where to fetch it, how big it
 * should be, and what its SHA-256 must equal once we have it on disk.
 *
 * For now Arcana ships exactly one option (Qwen 2.5 0.5B Instruct, Q4_K_M
 * GGUF). If we add more later, [ModelManifest.ALL] can grow into a list and
 * Settings can let the user pick.
 */
data class ModelManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val expectedBytes: Long,
    /** Lowercase hex SHA-256 of the GGUF file. Empty string disables checksum verification. */
    val sha256: String,
) {
    companion object {
        /**
         * Qwen 2.5 0.5B Instruct, Q4_K_M GGUF.
         * Apache 2.0 license. Mirrored to a `models-v1` release on the
         * Arcana repo so we don't depend on Hugging Face auth at install
         * time. Original source:
         *   https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
         *
         * Bytes + SHA-256 captured from the actual mirrored file:
         *   shasum -a 256 qwen2.5-0.5b-instruct-q4_k_m.gguf
         */
        val QWEN_2_5_0_5B_INSTRUCT = ModelManifest(
            id = "qwen2.5-0.5b-instruct-q4_k_m",
            displayName = "Qwen 2.5 0.5B Instruct",
            description = "0.5B parameter instruction-tuned model, Q4_K_M quantization. Apache 2.0.",
            downloadUrl = "https://github.com/PimpinPumpkin/Arcana/releases/download/" +
                "models-v1/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            expectedBytes = 491_400_032L, // 469 MB
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        )

        val DEFAULT: ModelManifest = QWEN_2_5_0_5B_INSTRUCT
    }
}
