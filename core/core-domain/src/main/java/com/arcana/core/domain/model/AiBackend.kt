package com.arcana.core.domain.model

enum class AiBackendType(val requiresNetwork: Boolean) {
    RULE_BASED(requiresNetwork = false),
    LOCAL_LLM(requiresNetwork = false),
    CLAUDE_API(requiresNetwork = true),
}

data class AiSettings(
    val backendType: AiBackendType,
    val claudeApiKey: String,
    /** The on-device model readings should use, if it is installed. */
    val localModelId: String,
    /** Blank means the app's current default. */
    val claudeModelId: String,
    /** Whether the offer to install an on-device model has been answered, so it is made only once. */
    val interpretPromptShown: Boolean,
)
