package ru.aolenev.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context

data class OllamaCompletionRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("prompt") val prompt: String,
    @JsonProperty("max_tokens") val maxTokens: Int = 1000,
    @JsonProperty("temperature") val temperature: Double = 0.7
)

data class OllamaCompletionChoice(
    @JsonProperty("text") val text: String,
    @JsonProperty("index") val index: Int,
    @JsonProperty("logprobs") val logprobs: Any? = null,
    @JsonProperty("finish_reason") val finishReason: String
)

data class OllamaCompletionUsage(
    @JsonProperty("prompt_tokens") val promptTokens: Int,
    @JsonProperty("completion_tokens") val completionTokens: Int,
    @JsonProperty("total_tokens") val totalTokens: Int
)

data class OllamaCompletionResponse(
    @JsonProperty("id") val id: String,
    @JsonProperty("object") val objectType: String,
    @JsonProperty("created") val created: Long,
    @JsonProperty("model") val model: String,
    @JsonProperty("choices") val choices: List<OllamaCompletionChoice>,
    @JsonProperty("usage") val usage: OllamaCompletionUsage
)

class OllamaService {
    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()

    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val ollamaUrl: String = config.getString("ai-challenge.ollama.baseUrl")

    suspend fun callModel(prompt: String, model: String = "qwen2.5:3b"): String? {
        return try {
            log.info("Calling Ollama v1/completions with model: $model")

            val request = OllamaCompletionRequest(
                model = model,
                prompt = prompt,
                maxTokens = 1000,
                temperature = 0.7
            )

            val response = httpClient.post("$ollamaUrl/v1/completions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<OllamaCompletionResponse>()

            log.info("Ollama response received: ${response.choices.size} choices, usage: ${response.usage.totalTokens} tokens")

            response.choices.firstOrNull()?.text
        } catch (e: Exception) {
            log.error("Error calling Ollama v1/completions", e)
            null
        }
    }
}
