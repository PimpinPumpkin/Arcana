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
         * Apache 2.0 license. Hosted as an asset on the Arcana repo's
         * `models-v1` release so we don't depend on Hugging Face auth.
         *
         * To finalize this manifest:
         *   1. Download Qwen 2.5 0.5B Instruct Q4_K_M GGUF from
         *      https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
         *      (or convert from the safetensors weights with llama.cpp's
         *      convert_hf_to_gguf.py + llama-quantize).
         *   2. Upload it as `qwen2.5-0.5b-instruct-q4_k_m.gguf` to a
         *      `models-v1` GitHub Release on PimpinPumpkin/Arcana.
         *   3. Run `shasum -a 256 qwen2.5-0.5b-instruct-q4_k_m.gguf` and
         *      paste the hex digest into [sha256] below.
         *   4. Verify [expectedBytes] matches the actual file size; the
         *      number below is approximate.
         */
        val QWEN_2_5_0_5B_INSTRUCT = ModelManifest(
            id = "qwen2.5-0.5b-instruct-q4_k_m",
            displayName = "Qwen 2.5 0.5B Instruct",
            description = "0.5B parameter instruction-tuned model, Q4_K_M quantization. Apache 2.0.",
            downloadUrl = "https://github.com/PimpinPumpkin/Arcana/releases/download/" +
                "models-v1/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            expectedBytes = 397_280_768L, // ≈379 MB; refine after upload
            sha256 = "", // FILL IN after upload
        )

        val DEFAULT: ModelManifest = QWEN_2_5_0_5B_INSTRUCT
    }
}
