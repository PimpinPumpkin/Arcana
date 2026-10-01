package com.arcana.service.ai

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Spread
import kotlinx.coroutines.flow.Flow

data class InterpretationRequest(
    val spread: Spread,
    val drawnCards: List<DrawnCard>,
    val question: String?,
    val tone: InterpretationTone = InterpretationTone.GROUNDED,
)

enum class InterpretationTone(val displayName: String, val systemHint: String) {
    GROUNDED("Grounded", "Be grounded, honest and plainspoken. No mysticism for its own sake."),
    POETIC("Poetic", "Be poetic: reach for images and metaphor."),
    PRACTICAL("Practical", "Be practical: turn each symbol into a clear next step."),
    THERAPEUTIC("Gentle", "Be gentle and reflective: invite them to notice feelings and patterns."),
}

sealed interface InterpretationChunk {
    data class Text(val delta: String) : InterpretationChunk

    /**
     * What is happening while there is nothing new to read yet.
     *
     * @param progress 0 to 1 when it can be measured, else null
     */
    data class Status(val message: String, val progress: Float? = null) : InterpretationChunk
    data class Error(val message: String, val cause: Throwable? = null) : InterpretationChunk
    data object Complete : InterpretationChunk
}

interface TarotInterpreter {
    val type: AiBackendType
    val isAvailable: Boolean
    fun interpret(request: InterpretationRequest): Flow<InterpretationChunk>
}
