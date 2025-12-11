package ru.aolenev.services

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
import ru.aolenev.*
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class ClaudeService : GptService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()
    private val mapper: ObjectMapper by context.instance()

    private val sonnet45 = "claude-sonnet-4-5-20250929"
    private val singlePromptStructResponseTool = this::class.java
        .getResource("/tool-templates/single-prompt-struct-response.json")!!
        .readText()
    private val multiQuestionsTool = this::class.java
        .getResource("/tool-templates/multi-questions.json")!!
        .readText()

    private val autoSummaryThreshold = 8
    private val summaryPrompt = "Please make a summary of our dialog split by keywords 'user' and 'assistant', so I could use this summary to continue dialog"

    override suspend fun singlePrompt(req: SinglePrompt): ResponseWithUsageDetails {
        val response = requestClaude(
            req = ClaudeRawRequest(
                model = sonnet45,
                messages = listOf(ClaudeMessage(role = "user", content = req.prompt)),
                system = req.systemPrompt,
                temperature = req.temperature,
                maxTokens = req.maxTokens
            )
        ).body<ClaudeSimpleResponse>()

        return ResponseWithUsageDetails(
            response = response.content.first().content,
            inputTokens = response.usage.inputTokens,
            outputTokens = response.usage.outputTokens,
            stopReason = response.stopReason
        )
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

    private suspend fun performAutoSummaryIfNeeded(chat: Chat): List<ClaudeMessage> {
        return if (chat.messages.size >= autoSummaryThreshold) {
            val response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    messages = chat.messages + ClaudeMessage(
                        role = "user",
                        content = summaryPrompt
                    ),
                    system = chat.aiRole
                )
            ).body<ClaudeSimpleResponse>()

            val responseMessage = response.content.first().content
            listOf(ClaudeMessage(role = "user", content = responseMessage))
        } else chat.messages
    }

    suspend fun unstructuredChatWithAutoSummary(chatId: String, aiRole: String?, userPrompt: String): ResponseWithHistory? {
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
            } else { // Сообщения в чате уже были, делаем автосуммирование истории, если требуется, а потом добавляем ещё одно сообщение в хвост

                val compressedMessages = performAutoSummaryIfNeeded(existingChat)

                existingChat.copy(
                    aiRole = aiRole,
                    messages = compressedMessages + ClaudeMessage(
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

            return ResponseWithHistory(
                response = responseMessage,
                messageHistory = chatCache.getIfPresent(chatId)!!.messages.map { mapOf(it.role to it.content.toString()) },
                usage = response.usage.toTokenUsage()
            )
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
                        content = listOf(
                            TooledContent(
                            toolId = toolId,
                            content = userPrompt
                        )
                        )
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
    @JsonProperty("temperature") val temperature: BigDecimal? = null,
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
    @JsonProperty("usage") val usage: ClaudeUsage,
    @JsonProperty("stop_reason") val stopReason: String
)

private fun ClaudeUsage.toTokenUsage(): TokenUsage = TokenUsage(inputTokens = this.inputTokens, outputTokens = this.outputTokens)

private data class ClaudeUsage(
    @JsonProperty("input_tokens") val inputTokens: Int,
    @JsonProperty("output_tokens") val outputTokens: Int
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