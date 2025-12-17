package ru.aolenev.services

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.model.ResponseWithUsageDetails
import ru.aolenev.model.SinglePrompt
import ru.aolenev.context

class YandexGptService : GptService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()

    override suspend fun singlePrompt(req: SinglePrompt): ResponseWithUsageDetails? {
        return try {
            val apiKey = System.getenv("YANDEX_API_KEY")
                ?: throw IllegalStateException("YANDEX_API_KEY environment variable is not set")

            val catalogId = System.getenv("YANDEX_CATALOG_ID")
                ?: throw IllegalStateException("YANDEX_CATALOG_ID environment variable is not set")

            val messages = mutableListOf<YandexMessage>()

            if (!req.systemPrompt.isNullOrEmpty()) {
                messages.add(YandexMessage(role = "system", text = req.systemPrompt))
            }

            messages.add(YandexMessage(role = "user", text = req.prompt))

            val yandexRequest = YandexGptRequest(
                modelUri = "gpt://$catalogId/yandexgpt-lite/latest",
                completionOptions = YandexCompletionOptions(
                    stream = false,
                    temperature = req.temperature?.toDouble(),
                    maxTokens = 1000
                ),
                messages = messages
            )

            val response = httpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                headers {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Api-Key $apiKey")
                }
                setBody(yandexRequest)
            }.body<YandexGptResponse>()

            return ResponseWithUsageDetails(
                response = response.result.alternatives.first().message.text,
                inputTokens = response.result.usage.inputTextTokens,
                outputTokens = response.result.usage.completionTokens,
                stopReason = "unknown"
            )

        } catch (e: Exception) {
            log.error("Error calling YandexGPT API", e)
            null
        }
    }
}

data class YandexGptRequest(
    @JsonProperty("modelUri") val modelUri: String,
    @JsonProperty("completionOptions") val completionOptions: YandexCompletionOptions,
    @JsonProperty("messages") val messages: List<YandexMessage>
)

data class YandexCompletionOptions(
    @JsonProperty("stream") val stream: Boolean = false,
    @JsonProperty("temperature") val temperature: Double? = null,
    @JsonProperty("maxTokens") val maxTokens: Int? = null
)

data class YandexMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("text") val text: String
)

data class YandexGptResponse(
    @JsonProperty("result") val result: YandexResult
)

data class YandexResult(
    @JsonProperty("alternatives") val alternatives: List<YandexAlternative>,
    @JsonProperty("usage") val usage: YandexUsage,
    @JsonProperty("modelVersion") val modelVersion: String
)

data class YandexAlternative(
    @JsonProperty("message") val message: YandexMessage,
    @JsonProperty("status") val status: String
)

data class YandexUsage(
    @JsonProperty("inputTextTokens") val inputTextTokens: Int,
    @JsonProperty("completionTokens") val completionTokens: Int,
    @JsonProperty("totalTokens") val totalTokens: Int
)