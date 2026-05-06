package com.arcana.service.ai

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the active interpreter based on the user's settings, with fallback to
 * RuleBasedInterpreter when the chosen backend is unavailable.
 */
@Singleton
class InterpreterRegistry @Inject constructor(
    private val interpreters: Map<AiBackendType, @JvmSuppressWildcards TarotInterpreter>,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun activeInterpreter(): TarotInterpreter {
        val ai = settingsRepository.ai.first()
        val chosen = interpreters[ai.backendType]
        return when {
            chosen != null && chosen.isAvailable -> chosen
            else -> interpreters[AiBackendType.RULE_BASED]
                ?: error("No fallback interpreter available")
        }
    }

    fun availableBackends(): List<TarotInterpreter> = interpreters.values.toList()
}
