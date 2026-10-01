package com.arcana.service.ai.cloud

import kotlinx.serialization.Serializable

@Serializable
internal data class ClaudeMessagesRequest(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val stream: Boolean = true,
)

@Serializable
internal data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ClaudeStreamEvent(
    val type: String,
    val delta: ClaudeStreamDelta? = null,
    val error: ClaudeStreamError? = null,
)

@Serializable
internal data class ClaudeStreamDelta(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
internal data class ClaudeStreamError(
    val type: String,
    val message: String,
)
