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
         * Apache 2.0. Smallest, fastest, weakest output. ~469 MB download.
         * Source: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
         */
        val QWEN_2_5_0_5B_INSTRUCT = ModelManifest(
            id = "qwen2.5-0.5b-instruct-q4_k_m",
            displayName = "Qwen 2.5 0.5B (small)",
            description = "Fastest. Weaker prose, occasional repetition. Best for older / budget phones.",
            downloadUrl = "https://github.com/PimpinPumpkin/Arcana/releases/download/" +
                "models-v1/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            expectedBytes = 491_400_032L, // 469 MB
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        )

        /**
         * Qwen 2.5 1.5B Instruct, Q4_K_M GGUF.
         * Apache 2.0. ~1.0 GB download. Noticeably better prose at the cost
         * of ~3× model load time and ~2× per-token latency.
         * Source: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
         */
        val QWEN_2_5_1_5B_INSTRUCT = ModelManifest(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen 2.5 1.5B (medium)",
            description = "Better prose, more coherent multi-paragraph readings. Recommended for flagships from the last few years.",
            downloadUrl = "https://github.com/PimpinPumpkin/Arcana/releases/download/" +
                "models-v1/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            expectedBytes = 1_117_320_736L, // 1.04 GB
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        )

        val ALL: List<ModelManifest> = listOf(
            QWEN_2_5_0_5B_INSTRUCT,
            QWEN_2_5_1_5B_INSTRUCT,
        )

        /** Pick the smaller model as default — easier to install on first run. */
        val DEFAULT: ModelManifest = QWEN_2_5_0_5B_INSTRUCT

        fun byId(id: String): ModelManifest = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
