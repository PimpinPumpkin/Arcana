package com.arcana.service.ai.local

/**
 * One language model the app can run: where to get it, what it must hash to, and how to sample
 * from it. A file is pinned by SHA-256, so a new build of a model is a new entry, never an edit.
 */
data class ModelSpec(
    val id: String,
    /** What the picker calls it. Sizes and speeds mean more to people than model names. */
    val title: String,
    val summary: String,
    /** Who made it and under what terms, shown in small print. */
    val credit: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
    /** Tried in order. */
    val urls: List<String>,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.08f,
    /** No longer offered for download. Still listed, and still works, on a phone that has it. */
    val retired: Boolean = false,
)

/**
 * The models on offer. They were chosen by running the same readings through each candidate with
 * `tools/reading-cli`, reading what came out, and timing it on a Pixel 4a 5G (2020, mid-range).
 * A three-card reading there, model load included, took 23 s with Quick, 33 s with Balanced and
 * 41 s with Thorough, against 23 s and 37 s for the two retired models.
 *
 * Download links name a revision, not a branch, so the file behind one cannot change.
 */
object ModelCatalog {
    private const val HF = "https://huggingface.co"
    private const val MIRROR = "https://github.com/PimpinPumpkin/Arcana/releases/download/models-v1"

    val QUICK = ModelSpec(
        id = "lfm2.5-1.2b-instruct-q4_k_m",
        title = "Quick",
        summary = "The fastest: three cards in about 25 seconds. Short, clear readings.",
        credit = "LFM2.5 1.2B by Liquid AI, LFM Open License",
        fileName = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        bytes = 730_895_168L,
        sha256 = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5",
        urls = listOf("$HF/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/8ed288026e23958ad9dfa92d53ed773a8eee7125/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"),
        // Its makers suggest a low temperature; at the usual 0.7 it wanders from the card.
        temperature = 0.4f,
        repeatPenalty = 1.05f,
    )

    val BALANCED = ModelSpec(
        id = "gemma-3-1b-it-q4_k_m",
        title = "Balanced",
        summary = "Warmer readings that stay closer to your question, in about 35 seconds. The one to start with.",
        credit = "Gemma 3 1B by Google, Gemma Terms of Use",
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        bytes = 806_058_240L,
        sha256 = "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
        urls = listOf("$HF/ggml-org/gemma-3-1b-it-GGUF/resolve/f9c28bcd85737ffc5aef028638d3341d49869c27/gemma-3-1b-it-Q4_K_M.gguf"),
    )

    val THOROUGH = ModelSpec(
        id = "qwen3.5-2b-q4_k_m",
        title = "Thorough",
        summary = "The most thoughtful readings, in about 45 seconds. Best on a recent phone.",
        credit = "Qwen3.5 2B by Alibaba, Apache 2.0",
        fileName = "Qwen3.5-2B-Q4_K_M.gguf",
        bytes = 1_280_835_840L,
        sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
        urls = listOf("$HF/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf"),
    )

    // The two models of earlier versions. The three above write better readings, Quick in the
    // time the old Small took and Balanced in less than the old Medium, so these are kept only
    // for whoever already has them.
    val OLD_SMALL = ModelSpec(
        id = "qwen2.5-0.5b-instruct-q4_k_m",
        title = "Small (older)",
        summary = "From an earlier version of Arcana. Quick writes better readings in the same time.",
        credit = "Qwen 2.5 0.5B by Alibaba, Apache 2.0",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        bytes = 491_400_032L,
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        urls = listOf(
            "$MIRROR/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "$HF/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        ),
        retired = true,
    )

    val OLD_MEDIUM = ModelSpec(
        id = "qwen2.5-1.5b-instruct-q4_k_m",
        title = "Medium (older)",
        summary = "From an earlier version of Arcana. Balanced is quicker, smaller and writes better readings.",
        credit = "Qwen 2.5 1.5B by Alibaba, Apache 2.0",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        bytes = 1_117_320_736L,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        urls = listOf(
            "$MIRROR/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "$HF/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        ),
        retired = true,
    )

    val all: List<ModelSpec> = listOf(QUICK, BALANCED, THOROUGH, OLD_SMALL, OLD_MEDIUM)

    /** What a new user is offered. */
    val offered: List<ModelSpec> = all.filterNot { it.retired }

    /** File name of a model the user brought themselves. */
    const val IMPORTED_FILE = "imported.gguf"
    const val IMPORTED_ID = "imported"

    fun imported(name: String, bytes: Long) = ModelSpec(
        id = IMPORTED_ID,
        title = name,
        summary = "A GGUF chat model you supplied.",
        credit = "Imported file",
        fileName = IMPORTED_FILE,
        bytes = bytes,
        sha256 = "",
        urls = emptyList(),
    )
}
