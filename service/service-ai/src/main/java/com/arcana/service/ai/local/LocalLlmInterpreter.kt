package com.arcana.service.ai.local

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM interpreter — currently a placeholder.
 *
 * To wire this up:
 *   1. Add the MLC-LLM Android AAR or equivalent (e.g. llama.cpp JNI bridge) to this module.
 *   2. Implement model download in the Settings screen (sets [SettingsRepository.setLocalModelInstalled]).
 *   3. Replace the body of [interpret] with a real streaming inference call using the system prompt
 *      from [com.arcana.service.ai.PromptBuilder].
 *
 * Until those steps are done, this reports Unavailable and the UI directs the user
 * either to install a model, switch to Claude, or use the rule-based fallback.
 */
@Singleton
class LocalLlmInterpreter @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : TarotInterpreter {

    override val type = AiBackendType.LOCAL_LLM
    override val isAvailable: Boolean = false

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = flow {
        val ai = settingsRepository.ai.first()
        if (!ai.localModelInstalled) {
            emit(InterpretationChunk.Error("Local model not installed yet. Install one in Settings, or switch to a different backend."))
            return@flow
        }
        emit(InterpretationChunk.Error("Local LLM runtime is not yet wired into this build. Use Claude or the rule-based reader for now."))
    }
}
