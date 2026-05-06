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
)
