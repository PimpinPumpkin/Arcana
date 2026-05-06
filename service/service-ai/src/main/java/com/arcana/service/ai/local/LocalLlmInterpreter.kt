package com.arcana.service.ai.local

import com.arcana.core.domain.model.AiBackendType
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.PromptBuilder
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM interpreter using llama.cpp + a downloaded GGUF.
 *
 * Flow:
 *   1. [ModelInstaller] reports whether the file is on disk via
 *      [ModelInstaller.modelFile]. If null → emit a friendly error pointing
 *      the user at Settings.
 *   2. Otherwise, ensure [LlamaEngine] has the file loaded (cheap if it
 *      already does, since the engine caches the session).
 *   3. Format the prompt in Qwen's ChatML template, hand it to the engine,
 *      and re-emit each token piece as an [InterpretationChunk.Text].
 */
@Singleton
class LocalLlmInterpreter @Inject constructor(
    private val modelInstaller: ModelInstaller,
    private val engine: LlamaEngine,
) : TarotInterpreter {

    override val type = AiBackendType.LOCAL_LLM

    // Reflect installation state — InterpreterRegistry uses this to fall
    // through to RuleBasedInterpreter when the user hasn't installed yet.
    override val isAvailable: Boolean
        get() = modelInstaller.modelFile != null

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = flow {
        val file = modelInstaller.modelFile
        if (file == null) {
            emit(
                InterpretationChunk.Error(
                    "Local model not installed. Open Settings → AI Interpreter and tap Install.",
                ),
            )
            return@flow
        }

        emit(InterpretationChunk.Status("Loading model…"))
        if (!engine.loadModel(file)) {
            emit(
                InterpretationChunk.Error(
                    "Couldn't load the local model. The file may be incomplete or corrupted — try reinstalling from Settings.",
                ),
            )
            return@flow
        }

        emit(InterpretationChunk.Status("Interpreting…"))

        val prompt = buildChatMlPrompt(
            system = PromptBuilder.SYSTEM_PROMPT,
            user = PromptBuilder.userPrompt(request),
        )

        try {
            engine.generate(prompt).collect { piece ->
                // Some chat models emit role/end tokens as visible text on
                // small/quantized weights. Strip the obvious ones so we
                // don't leak template literals into the reading.
                if (piece.contains("<|im_end|>") || piece.contains("<|im_start|>")) {
                    return@collect
                }
                emit(InterpretationChunk.Text(piece))
            }
            emit(InterpretationChunk.Complete)
        } catch (e: Throwable) {
            emit(
                InterpretationChunk.Error(
                    "Local generation failed: ${e.message ?: e::class.java.simpleName}",
                    e,
                ),
            )
        }
    }

    // Qwen 2.5 (and most modern instruct models) are trained on this
    // template. llama.cpp ships a `llama_chat_apply_template` that could do
    // this for us, but hardcoding here keeps the bridge surface tiny and
    // makes prompt tuning a Kotlin edit away.
    private fun buildChatMlPrompt(system: String, user: String): String = buildString {
        append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }
}
