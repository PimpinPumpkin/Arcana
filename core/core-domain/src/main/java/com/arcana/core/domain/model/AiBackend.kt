package com.arcana.core.domain.model

enum class AiBackendType(val displayName: String, val requiresNetwork: Boolean) {
    RULE_BASED("Rule-based (offline, no AI)", requiresNetwork = false),
    LOCAL_LLM("Local LLM (on-device)", requiresNetwork = false),
    CLAUDE_API("Claude API (cloud)", requiresNetwork = true),
}

data class AiSettings(
    val backendType: AiBackendType,
    val claudeApiKey: String,
    val localModelInstalled: Boolean,
    val localModelId: String,
    val claudeModelId: String,
    /**
     * Whether we've already shown the user the first-tap "install offline AI?"
     * prompt at least once. Flipped to true the first time the user makes a
     * choice (either "install" or "not now") so the prompt doesn't keep
     * appearing.
     */
    val interpretPromptShown: Boolean,
)
