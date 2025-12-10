package ru.aolenev.services

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.ResponseWithUsageDetails
import ru.aolenev.SinglePrompt
import ru.aolenev.context

class OpenAIService : GptService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()

    override suspend fun singlePrompt(req: SinglePrompt): ResponseWithUsageDetails? {
        return try {
            val apiKey = System.getenv("OPEN_ROUTER_API_KEY")
                ?: throw IllegalStateException("OPEN_ROUTER_API_KEY environment variable is not set")

            val messages = mutableListOf<OpenAIMessage>()

            if (!req.systemPrompt.isNullOrEmpty()) {
                messages.add(OpenAIMessage(role = "system", content = req.systemPrompt))
            }

            messages.add(OpenAIMessage(role = "user", content = req.prompt))

            val openAIRequest = OpenAIRequest(
                model = req.model ?: "amazon/nova-2-lite-v1:free",
                messages = messages,
                temperature = req.temperature?.toDouble(),
                maxTokens = req.maxTokens
            )

            val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                headers {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                }
                setBody(openAIRequest)
            }.body<OpenAIResponse>()

            return ResponseWithUsageDetails(
                response = response.choices.first().message.content,
                inputTokens = response.usage.promptTokens,
                outputTokens = response.usage.completionTokens,
                stopReason = response.choices.first().finishReason
            )
        } catch (e: Exception) {
            log.error("Error calling OpenAI API", e)
            null
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAIRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("messages") val messages: List<OpenAIMessage>,
    @JsonProperty("temperature") val temperature: Double? = null,
    @JsonProperty("max_tokens") val maxTokens: Int? = null
)

data class OpenAIMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("content") val content: String
)

data class OpenAIResponse(
    @JsonProperty("id") val id: String,
    @JsonProperty("object") val objectType: String,
    @JsonProperty("created") val created: Long,
    @JsonProperty("model") val model: String,
    @JsonProperty("choices") val choices: List<OpenAIChoice>,
    @JsonProperty("usage") val usage: OpenAIUsage
)

data class OpenAIChoice(
    @JsonProperty("index") val index: Int,
    @JsonProperty("message") val message: OpenAIMessage,
    @JsonProperty("finish_reason") val finishReason: String
)

data class OpenAIUsage(
    @JsonProperty("prompt_tokens") val promptTokens: Int,
    @JsonProperty("completion_tokens") val completionTokens: Int,
    @JsonProperty("total_tokens") val totalTokens: Int
)