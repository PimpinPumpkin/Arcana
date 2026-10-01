package com.arcana.service.ai.local

import com.arcana.core.domain.model.AiBackendType
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.ReadingScript
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Readings written by a language model on the phone. The reading is a conversation, one card per
 * turn (see [ReadingScript]); each answer is streamed out as it is written.
 */
@Singleton
class LocalLlmInterpreter @Inject constructor(
    private val store: ModelStore,
    private val engine: LlamaEngine,
) : TarotInterpreter {

    override val type = AiBackendType.LOCAL_LLM

    override val isAvailable: Boolean
        get() = engine.supported && store.specs.any { store.file(it) != null }

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = channelFlow {
        val (spec, file) = store.active() ?: run {
            send(InterpretationChunk.Error("No reading model is installed. Get one in Settings."))
            return@channelFlow
        }
        send(InterpretationChunk.Status("Waking the model"))
        try {
            engine.converse(file) {
                val script = ReadingScript.of(request)
                fun begin() = begin(script.system, spec.temperature, spec.topK, spec.topP, spec.repeatPenalty)
                begin()
                script.turns.forEachIndexed { i, turn ->
                    val waiting = user(if (i == 0) script.promptFrom(0) else turn.prompt, reserve = turn.maxTokens) ?: run {
                        // Out of room. Carry on in a new conversation that is told which cards
                        // have been read, in a line each, instead of everything said about them.
                        begin()
                        user(script.promptFrom(i), reserve = turn.maxTokens)
                    } ?: throw IOException("This spread is too large for the on-device model.")
                    var left = waiting
                    while (left > 0) {
                        currentCoroutineContext().ensureActive()
                        left = feed(FEED_TOKENS)
                        // Only the first wait is long enough to be worth a bar: later turns are short.
                        if (i == 0) send(InterpretationChunk.Status("Reading the cards", 1f - left.toFloat() / waiting))
                    }
                    send(InterpretationChunk.Text((if (i == 0) "" else "\n\n") + turn.heading + "\n"))
                    grammar(turn.grammar)
                    val reply = Utf8Stream()
                    var tokens = 0
                    while (tokens < turn.maxTokens) {
                        currentCoroutineContext().ensureActive()
                        val piece = next() ?: break
                        val delta = reply.push(piece)
                        if (delta.isNotEmpty()) send(InterpretationChunk.Text(delta))
                        tokens++
                    }
                    reply(reply.text.trim())
                }
            }
            send(InterpretationChunk.Complete)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            send(InterpretationChunk.Error(e.message ?: "The on-device model stopped unexpectedly.", e))
        }
    }

    private companion object {
        // Small enough that a cancel is noticed within about a second on a slow phone.
        const val FEED_TOKENS = 48
    }
}
