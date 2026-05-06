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
    GROUNDED("Grounded & honest", "Respond with grounded, honest, plainspoken interpretations. Avoid mysticism for its own sake."),
    POETIC("Poetic & evocative", "Respond with poetic, evocative language that lingers on imagery and metaphor."),
    PRACTICAL("Practical & actionable", "Focus on practical, actionable advice. Translate symbolism into clear next steps."),
    THERAPEUTIC("Reflective & gentle", "Respond as a gentle, reflective companion. Invite the querent to consider feelings and patterns."),
}

sealed interface InterpretationChunk {
    data class Text(val delta: String) : InterpretationChunk
    data class Status(val message: String) : InterpretationChunk
    data class Error(val message: String, val cause: Throwable? = null) : InterpretationChunk
    data object Complete : InterpretationChunk
}

interface TarotInterpreter {
    val type: AiBackendType
    val isAvailable: Boolean
    fun interpret(request: InterpretationRequest): Flow<InterpretationChunk>
}
