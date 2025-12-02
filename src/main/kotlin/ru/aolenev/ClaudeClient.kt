package ru.aolenev

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrNull

class ClaudeClient {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()
    private val mapper: ObjectMapper by context.instance()

    private val sdkClient = AnthropicOkHttpClient.fromEnv()
    private val sonnet45 = "claude-sonnet-4-5-20250929"

    fun prompt(prompt: String): String? {
        val params = MessageCreateParams.builder()
            .model(sonnet45)
            .maxTokens(1000)
            .addUserMessage(prompt)
            .build()

        return sdkClient.messages().create(params).content().first().text().map { it.text() }.getOrNull()
    }

    suspend fun promptWithStructuredResponse(prompt: String): StructuredResponse? {
        try {

            val shortResponseTool = this::class.java
                .getResource("/short-prompt-response.json")!!
                .readText()
            log.info(shortResponseTool)
            val response = httpClient.post("https://api.anthropic.com/v1/messages") {
                headers {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
                    header("anthropic-version", "2023-06-01")
                }
                setBody(
                    ClaudeRawRequest(
                        model = sonnet45,
                        tools = listOf(mapper.readValue(shortResponseTool, HashMap::class.java)),
                        messages = listOf(
                            ClaudeMessage(role = "user", content = prompt)
                        )
                    )
                )
            }.body<ClaudeToolResponse>()


            return response.content[0].input
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }
}

data class ClaudeRawRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("max_tokens") val maxTokens: Int? = 1024,
    @JsonProperty("tools") val tools: List<Any>,
    @JsonProperty("messages") val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("content") val content: String
)

private data class ClaudeToolResponse(
    @JsonProperty("content") val content: List<ClaudeContent>,
)

private data class ClaudeContent(
    @JsonProperty("input") val input: StructuredResponse
)