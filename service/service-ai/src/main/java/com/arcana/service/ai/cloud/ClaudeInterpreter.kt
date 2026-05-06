package com.arcana.service.ai.cloud

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.PromptBuilder
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
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
        emit(InterpretationChunk.Status("Asking Claude…"))

        val body = ClaudeMessagesRequest(
            model = ai.claudeModelId.ifBlank { DEFAULT_MODEL },
            max_tokens = 1500,
            system = PromptBuilder.SYSTEM_PROMPT,
            messages = listOf(
                ClaudeMessage(role = "user", content = PromptBuilder.userPrompt(request)),
            ),
            stream = true,
        )

        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", ai.claudeApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("accept", "text/event-stream")
            .post(json.encodeToString(ClaudeMessagesRequest.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()

        emitAll(streamSse(httpRequest))
    }.flowOn(Dispatchers.IO)

    private fun streamSse(httpRequest: Request): Flow<InterpretationChunk> = callbackFlow {
        val factory = EventSources.createFactory(client)
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(InterpretationChunk.Complete)
                    close()
                    return
                }
                runCatching {
                    val event = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    when (event.type) {
                        "content_block_delta" -> {
                            event.delta?.text?.let { trySend(InterpretationChunk.Text(it)) }
                        }
                        "message_stop" -> {
                            trySend(InterpretationChunk.Complete)
                            close()
                        }
                        "error" -> {
                            val msg = event.error?.message ?: "Stream error"
                            trySend(InterpretationChunk.Error(msg))
                            close()
                        }
                        else -> Unit // message_start, content_block_start, ping, etc — ignore
                    }
                }.onFailure { /* swallow malformed events */ }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val responseBody = runCatching { response?.body?.string() }.getOrNull()
                val msg = when {
                    response?.code == 401 -> "Anthropic rejected the API key. Check it in Settings."
                    response?.code == 429 -> "Anthropic rate limit hit. Try again in a moment."
                    response != null -> "Claude error ${response.code}: ${responseBody ?: response.message}"
                    t != null -> "Network error: ${t.message ?: t::class.java.simpleName}"
                    else -> "Unknown stream failure"
                }
                trySend(InterpretationChunk.Error(msg, t))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(InterpretationChunk.Complete)
                close()
            }
        }
        val source = factory.newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val DEFAULT_MODEL = "claude-sonnet-4-5"
    }
}

private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) {
    flow.collect { emit(it) }
}
