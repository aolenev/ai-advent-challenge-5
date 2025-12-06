package ru.aolenev

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

class ClaudeService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()
    private val mapper: ObjectMapper by context.instance()

    private val sdkClient = AnthropicOkHttpClient.fromEnv()
    private val sonnet45 = "claude-sonnet-4-5-20250929"
    private val singlePromptStructResponseTool = this::class.java
        .getResource("/tool-templates/single-prompt-struct-response.json")!!
        .readText()
    private val multiQuestionsTool = this::class.java
        .getResource("/tool-templates/multi-questions.json")!!
        .readText()

    fun singlePrompt(prompt: String): String? {
        val params = MessageCreateParams.builder()
            .model(sonnet45)
            .maxTokens(1000)
            .addUserMessage(prompt)
            .build()

        return sdkClient.messages().create(params).content().first().text().map { it.text() }.getOrNull()
    }

    suspend fun singlePromptWithStructuredResponse(prompt: String): StructuredResponse? {
        try {
            return requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    tools = listOf(mapper.readValue(singlePromptStructResponseTool, HashMap::class.java)),
                    messages = listOf(
                        ClaudeMessage(role = "user", content = prompt)
                    ),
                    system = null
                )
            ).body<ClaudeSinglePromptStructuredResponse>().content[0].input
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    suspend fun unstructuredChat(chatId: String, aiRole: String?, userPrompt: String): String? {
        if (aiRole.isNullOrEmpty()) throw BadRequestException(message = "You didn't provide an AI role")
        try {
            val existingChat = chatCache.getIfPresent(chatId)
            val currentChat = if (existingChat == null) { // это первое сообщение в чате
                val chat = Chat(
                    id = chatId,
                    aiRole = aiRole,
                    messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
                    isFinished = false
                )
                chatCache.put(chatId, chat)
                chat
            } else { // Сообщения в чате уже были, просто добавляем ещё одно сообщение в хвост
                existingChat.copy(
                    aiRole = aiRole,
                    messages = existingChat.messages + ClaudeMessage(
                        role = "user",
                        content = userPrompt
                    )
                )
            }

            val response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    messages = currentChat.messages,
                    system = currentChat.aiRole
                )
            ).body<ClaudeSimpleResponse>()

            val responseMessage = response.content.first().content

            chatCache.put(
                chatId,
                currentChat.copy(
                    messages = currentChat.messages + ClaudeMessage(
                        role = "assistant",
                        content = responseMessage
                    )
                )
            )

            return responseMessage
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    suspend fun chatStructuredByTool(chatId: String, aiRole: String?, userPrompt: String): FiniteChatResponse? {
        try {
            val existingChat = chatCache.getIfPresent(chatId)
            val currentChat = if (existingChat == null) { // это первое сообщение в чате
                // Для первого сообщения надо указать system prompt
                if (aiRole.isNullOrEmpty()) throw BadRequestException(message = "You didn't provide an AI role")

                val chat = Chat(
                    id = chatId,
                    aiRole = aiRole,
                    messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
                    isFinished = false
                )
                chatCache.put(chatId, chat)
                chat
            } else if (existingChat.isFinished) {
                return FiniteChatResponse(
                    response = "Your conversation is finished, last response is: ${(existingChat.messages.last().content as List<ClaudeMultiQuestionsContent>).first().input.response}",
                    isChatFinished = true
                )
            } else { // Сообщения в чате уже были, значит были и ответы

                // Последний ответ в цепочке должен соответствовать той схеме, которую мы отправили в tool
                val tooledResponse = existingChat.messages.last()
                val toolId = (tooledResponse.content as List<ClaudeMultiQuestionsContent>)[0].id
                existingChat.copy(
                    messages = existingChat.messages + ClaudeMessage(
                        role = "user",
                        content = listOf(TooledContent(
                            toolId = toolId,
                            content = userPrompt
                        ))
                    )
                )
            }

            val response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    tools = listOf(mapper.readValue(multiQuestionsTool, HashMap::class.java)),
                    messages = currentChat.messages,
                    system = currentChat.aiRole
                )
            ).body<ClaudeMultiQuestionsResponse>()

            val tooledResponse = response.content
                .filter { it.type == "tool_use" }
                .map { ClaudeMultiQuestionsContent(id = it.id!!, name = it.name!!, input = it.input!!) }
                .last()

            chatCache.put(
                chatId,
                currentChat.copy(
                    messages = currentChat.messages + ClaudeMessage(
                        role = "assistant",
                        content = listOf(tooledResponse)
                    ), isFinished = tooledResponse.input.isFinished
                )
            )

            return FiniteChatResponse(response = tooledResponse.input.response, isChatFinished = tooledResponse.input.isFinished)
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    private val chatCache: Cache<String, Chat> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build()

    private suspend fun requestClaude(req: ClaudeRawRequest): HttpResponse {
        return httpClient.post("https://api.anthropic.com/v1/messages") {
            headers {
                contentType(ContentType.Application.Json)
                header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
                header("anthropic-version", "2023-06-01")
            }
            setBody(req)
        }
    }
}

data class Chat(val id: String, val aiRole: String, val messages: List<ClaudeMessage>, val isFinished: Boolean)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClaudeRawRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("max_tokens") val maxTokens: Int? = 2048,
    @JsonProperty("system") val system: String?,
    @JsonProperty("tools") val tools: List<Any>? = null,
    @JsonProperty("messages") val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("content") val content: Any
)

data class TooledContent(
    @JsonProperty("type") val type: String = "tool_result",
    @JsonProperty("tool_use_id") val toolId: String,
    @JsonProperty("content") val content: String
)

private data class ClaudeSimpleResponse(
    @JsonProperty("content") val content: List<ClaudeTextContent>,
)

private data class ClaudeTextContent(
    @JsonProperty("type") val type: String,
    @JsonProperty("text") val content: String
)

private data class ClaudeSinglePromptStructuredResponse(
    @JsonProperty("content") val content: List<ClaudeSinglePromptStructuredContent>,
)

private data class ClaudeSinglePromptStructuredContent(
    @JsonProperty("input") val input: StructuredResponse
)

private data class ClaudeMultiQuestionsResponse(
    @JsonProperty("content") val content: List<ClaudeMultiQuestionsRawContent>,
)

private data class ClaudeMultiQuestionsRawContent(
    @JsonProperty("type") val type: String,
    @JsonProperty("text") val text: String?,
    @JsonProperty("id") val id: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("input") val input: ResponseWithFinishFlag?
)

private data class ClaudeMultiQuestionsContent(
    @JsonProperty("type") val type: String = "tool_use",
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("input") val input: ResponseWithFinishFlag
)