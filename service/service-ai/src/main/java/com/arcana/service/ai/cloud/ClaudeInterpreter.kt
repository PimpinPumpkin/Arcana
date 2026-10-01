package com.arcana.service.ai.cloud

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.PromptBuilder
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Readings from Anthropic's Messages API, streamed. Used only when the user picks it and has
 * entered a key of their own; the key goes to api.anthropic.com and nowhere else.
 */
@Singleton
class ClaudeInterpreter @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : TarotInterpreter {

    override val type = AiBackendType.CLAUDE_API
    override val isAvailable: Boolean = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = flow {
        val ai = settingsRepository.ai.first()
        if (ai.claudeApiKey.isBlank()) {
            emit(InterpretationChunk.Error("Add your Anthropic API key in Settings to use Claude."))
            return@flow
        }
        emit(InterpretationChunk.Status("Asking Claude"))

        val body = ClaudeMessagesRequest(
            model = ai.claudeModelId.ifBlank { ClaudeModels.DEFAULT },
            max_tokens = 1500,
            system = PromptBuilder.system(request.tone),
            messages = listOf(ClaudeMessage(role = "user", content = PromptBuilder.user(request))),
        )
        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", ai.claudeApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("accept", "text/event-stream")
            .post(json.encodeToString(ClaudeMessagesRequest.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()

        emitAll(stream(httpRequest))
    }.flowOn(Dispatchers.IO)

    private fun stream(httpRequest: Request): Flow<InterpretationChunk> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = runCatching { json.decodeFromString(ClaudeStreamEvent.serializer(), data) }.getOrNull() ?: return
                when (event.type) {
                    "content_block_delta" -> event.delta?.text?.let { trySend(InterpretationChunk.Text(it)) }
                    "message_stop" -> {
                        trySend(InterpretationChunk.Complete)
                        close()
                    }
                    "error" -> {
                        trySend(InterpretationChunk.Error(event.error?.message ?: "Claude reported an error."))
                        close()
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val detail = runCatching { response?.body?.string() }.getOrNull()
                val message = when {
                    response?.code == 401 -> "Anthropic did not accept the API key. Check it in Settings."
                    response?.code == 404 -> "Anthropic does not know that model. Pick another in Settings."
                    response?.code == 429 -> "Anthropic's rate limit was hit. Try again in a moment."
                    response != null -> "Claude answered ${response.code}: ${detail ?: response.message}"
                    t != null -> "No connection to Claude: ${t.message ?: t::class.java.simpleName}"
                    else -> "The connection to Claude closed unexpectedly."
                }
                trySend(InterpretationChunk.Error(message, t))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(InterpretationChunk.Complete)
                close()
            }
        }
        val source = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

/** The models offered in Settings. Any other model id can be typed in. */
object ClaudeModels {
    const val DEFAULT = "claude-sonnet-5-5"

    /** Model id to the name shown for it. */
    val offered: List<Pair<String, String>> = listOf(
        "claude-sonnet-5-5" to "Sonnet 5.5",
        "claude-opus-5-5" to "Opus 5.5",
        "claude-haiku-4-5-20251001" to "Haiku 4.5",
    )
}
