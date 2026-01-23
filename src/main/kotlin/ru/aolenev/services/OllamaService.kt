package ru.aolenev.services

import asMap
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import ru.aolenev.model.MessageType
import ru.aolenev.model.ResponseWithHistory
import ru.aolenev.model.TokenUsage
import ru.aolenev.repo.ChatTable
import ru.aolenev.repo.MessageTable
import ru.aolenev.repo.RagEmbeddingsTable
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class OllamaService {
    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()
    private val mapper: ObjectMapper by context.instance()
    private val localTurboMcpServer: TurboMcpServer by context.instance()
    private val localDatabaseMcpServer: DatabaseMcpServer by context.instance()
    private val localShellMcpServer: ShellMcpServer by context.instance()
    private val ollamaRagService: OllamaRagService by context.instance()
    private val gitlabMcpService: GitlabMcpService by context.instance()
    private val gitHubMcpService: GitHubMcpService by context.instance()
    private val cronJobService: CronJobService by context.instance()

    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val ollamaUrl: String = config.getString("ai-challenge.ollama.baseUrl")

    private val autoSummaryThreshold = 8
    private val summaryPrompt = "Please make a summary of our dialog split by keywords 'user' and 'assistant', so I could use this summary to continue dialog"

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

    suspend fun tooledChat(
        chatId: String,
        userPrompt: String,
        aiRoleOpt: String?,
        withRag: Boolean,
        minSimilarity: BigDecimal,
        model: String = "qwen2.5:3b",
        temperature: Double? = null
    ): ResponseWithHistory? {
        val aiRole = aiRoleOpt ?: "Use tools if needed"
        try {
            val richPrompt = if (withRag) enrichPromptWithRagContext(userPrompt, minSimilarity) else userPrompt
            val existingChat = chatCache.get(chatId)
            val currentChat = if (existingChat == null) {
                val chat = OllamaChat(
                    id = chatId,
                    aiRole = aiRole,
                    messages = listOf(OllamaChatMessage(role = "system", content = aiRole)) +
                            OllamaChatMessage(role = "user", content = richPrompt),
                    isFinished = false
                )
                chatCache.put(chatId, chat)
                ChatTable.addChat(chatId = chatId, aiRole = aiRole)
                MessageTable.saveMessage(messageContent = richPrompt, chatId = chatId, messageType = MessageType.USER)
                chat
            } else {
                val compressedMessages = performAutoSummaryIfNeeded(existingChat, model)
                MessageTable.saveMessage(chatId = chatId, messageContent = richPrompt, messageType = MessageType.USER)

                existingChat.copy(
                    aiRole = aiRole,
                    messages = compressedMessages + OllamaChatMessage(role = "user", content = richPrompt)
                )
            }

            // Combine tools from all MCP servers
            val turboTools = localTurboMcpServer.listTools().result.tools ?: emptyList()
            val databaseTools = localDatabaseMcpServer.listTools().result.tools ?: emptyList()
            val shellTools = localShellMcpServer.listTools().result.tools ?: emptyList()
            val gitlabTools = gitlabMcpService.getTools()
            val githubTools = gitHubMcpService.getTools()
            val cronJobTools = cronJobService.getTools()
            val allTools = (turboTools + databaseTools + shellTools + gitlabTools + githubTools + cronJobTools).map { it.toOllama() }

            var response = requestOllamaChat(
                request = OllamaChatRequest(
                    model = model,
                    messages = currentChat.messages,
                    tools = allTools,
                    temperature = temperature ?: 0.7
                )
            )

            var updatedChat = currentChat
            while (response.choices.firstOrNull()?.finishReason == "tool_calls") {
                val result = handleToolUse(chatId, response, allTools, model, updatedChat, temperature)
                response = result.response
                updatedChat = result.chat
            }

            val textContent = response.choices.firstOrNull()?.message?.content

            if (textContent != null) {
                log.info("Text response: $textContent")
                val assistantMessage = OllamaChatMessage(role = "assistant", content = textContent)
                memoizeMessage(updatedChat.id, assistantMessage, MessageType.ASSISTANT)

                val finalChat = chatCache.getIfPresent(chatId)!!
                return ResponseWithHistory(
                    response = textContent,
                    usage = TokenUsage(
                        inputTokens = response.usage.promptTokens,
                        outputTokens = response.usage.completionTokens
                    ),
                    messageHistory = finalChat.messages.map { mapOf(it.role to (it.content ?: "")) }
                )
            }

            return null
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    private suspend fun performAutoSummaryIfNeeded(chat: OllamaChat, model: String): List<OllamaChatMessage> {
        return if (chat.messages.size >= autoSummaryThreshold) {
            val response = requestOllamaChat(
                request = OllamaChatRequest(
                    model = model,
                    messages = chat.messages + OllamaChatMessage(role = "user", content = summaryPrompt)
                )
            )

            val responseMessage = response.choices.firstOrNull()?.message?.content ?: ""
            MessageTable.saveMessage(chatId = chat.id, messageContent = responseMessage, messageType = MessageType.SUMMARY)
            chatCache.invalidate(chat.id)

            listOf(
                OllamaChatMessage(role = "system", content = chat.aiRole),
                OllamaChatMessage(role = "user", content = responseMessage)
            )
        } else chat.messages
    }

    private suspend fun enrichPromptWithRagContext(userPrompt: String, minSimilarity: BigDecimal): String {
        log.info("Enriching prompt with RAG context")

        val chunks = ollamaRagService.splitByChunks(userPrompt, chunkSize = 50, overlap = 10)
        log.info("Split prompt into ${chunks.size} chunks")

        val embeddingsMap = ollamaRagService.getEmbeddings(chunks)

        val allSimilarChunks = mutableSetOf<String>()
        embeddingsMap.forEach { (chunk, embedding) ->
            val similarChunks = RagEmbeddingsTable.findSimilarChunks(embedding, minSimilarity)
            allSimilarChunks.addAll(similarChunks)
            log.info("Found ${similarChunks.size} similar chunks for chunk: ${chunk.take(50)}...")
        }

        return if (allSimilarChunks.isNotEmpty()) {
            val context = allSimilarChunks.joinToString("\n\n")
            log.info("Adding ${allSimilarChunks.size} unique chunks as context")
            """
            |Additional context from knowledge base:
            |
            |$context
            |
            |User question: $userPrompt
            """.trimMargin()
        } else {
            log.info("No similar chunks found, using original prompt")
            userPrompt
        }
    }

    private data class ToolUseResult(
        val response: OllamaChatResponse,
        val chat: OllamaChat
    )

    private suspend fun handleToolUse(
        chatId: String,
        response: OllamaChatResponse,
        allTools: List<OllamaTool>,
        model: String,
        currentChat: OllamaChat,
        temperature: Double? = null
    ): ToolUseResult {
        val assistantMessage = response.choices.firstOrNull()?.message
        val toolCalls = assistantMessage?.toolCalls

        if (toolCalls.isNullOrEmpty()) {
            return ToolUseResult(response, currentChat)
        }

        val toolResultMessages = mutableListOf<OllamaChatMessage>()

        for (toolCall in toolCalls) {
            val toolName = toolCall.function.name
            @Suppress("UNCHECKED_CAST")
            val arguments = mapper.readValue(toolCall.function.arguments, Map::class.java) as Map<String, Any>

            log.info("Calling tool: $toolName with arguments: $arguments")

            val toolResult = when (toolName) {
                "get_completed_fuelings" -> localTurboMcpServer.callTool(
                    McpToolsParams(name = toolName, arguments = arguments)
                )
                "save_fuelings_stat" -> localDatabaseMcpServer.callTool(
                    McpToolsParams(name = toolName, arguments = arguments)
                )
                "execute_shell_command" -> localShellMcpServer.callTool(
                    McpToolsParams(name = toolName, arguments = arguments)
                )
                "gitlab_get_latest_pipeline", "gitlab_get_pipeline_jobs", "gitlab_run_job" ->
                    gitlabMcpService.callTool(toolName, arguments) ?: McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "GitLab tool returned null"),
                            isError = true
                        )
                    )
                "github_list_bug_issues", "github_list_issues", "github_create_issue", "github_update_issue" ->
                    gitHubMcpService.callTool(toolName, arguments, owner = "aolenev", repo = "ai-advent-challenge-5") ?: McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "CronJob tool returned null"),
                            isError = true
                        )
                    )
                "schedule_qa_deployment", "stop_deployment_scheduling" ->
                    cronJobService.callTool(toolName, arguments) ?: McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "CronJob tool returned null"),
                            isError = true
                        )
                    )
                else -> {
                    log.error("Unknown tool: $toolName")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: $toolName"),
                            isError = true
                        )
                    )
                }
            }

            log.info("Tool result: $toolResult")

            if (toolResult.result.content != null) {
                toolResultMessages.add(
                    OllamaChatMessage(
                        role = "tool",
                        content = toolResult.result.content.asMap().toString(),
                        toolCallId = toolCall.id
                    )
                )
            }
        }

        // Add assistant message with tool calls and tool result messages to history
        val newMessages = currentChat.messages +
                OllamaChatMessage(
                    role = "assistant",
                    toolCalls = assistantMessage?.toolCalls
                ) +
                toolResultMessages

        val updatedChat = currentChat.copy(messages = newMessages)
        chatCache.put(chatId, updatedChat)

        // Make follow-up request
        val followUpResponse = requestOllamaChat(
            request = OllamaChatRequest(
                model = model,
                messages = updatedChat.messages,
                tools = allTools,
                temperature = temperature ?: 0.7
            )
        )

        log.info("Follow-up response: $followUpResponse")

        return ToolUseResult(followUpResponse, updatedChat)
    }

    private fun memoizeMessage(chatId: String, newMessage: OllamaChatMessage, messageType: MessageType) {
        MessageTable.saveMessage(
            messageContent = newMessage.content ?: mapper.writeValueAsString(newMessage.toolCalls),
            chatId = chatId,
            messageType = messageType
        )

        val chat = chatCache.get(chatId)
        chatCache.put(
            chat.id,
            chat.copy(messages = chat.messages + newMessage)
        )
    }

    private val chatCache: LoadingCache<String, OllamaChat> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build { key ->
            val aiRole = ChatTable.findAiRole(key)
            if (aiRole != null) {
                OllamaChat(
                    id = key,
                    aiRole = aiRole,
                    messages = listOf(OllamaChatMessage(role = "system", content = aiRole)) +
                            MessageTable.readLastMessages(key).map {
                                OllamaChatMessage(
                                    role = it.messageType.toOllamaRole(),
                                    content = it.messageContent
                                )
                            },
                    isFinished = false
                )
            } else null
        }

    private suspend fun requestOllamaChat(request: OllamaChatRequest): OllamaChatResponse {
        return httpClient.post("$ollamaUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    private fun McpTool.toOllama(): OllamaTool = OllamaTool(
        type = "function",
        function = OllamaToolFunction(
            name = name,
            description = description,
            parameters = inputSchema
        )
    )

    private fun MessageType.toOllamaRole(): String = when (this) {
        MessageType.USER -> "user"
        MessageType.ASSISTANT -> "assistant"
        MessageType.SUMMARY -> "user"
    }
}

data class OllamaChat(
    val id: String,
    val aiRole: String,
    val messages: List<OllamaChatMessage>,
    val isFinished: Boolean
)


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

// Chat API models for tool support
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaChatRequest(
    @JsonProperty("model") val model: String,
    @JsonProperty("messages") val messages: List<OllamaChatMessage>,
    @JsonProperty("tools") val tools: List<OllamaTool>? = null,
    @JsonProperty("max_tokens") val maxTokens: Int = 2048,
    @JsonProperty("temperature") val temperature: Double = 0.7
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaChatMessage(
    @JsonProperty("role") val role: String,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("tool_calls") val toolCalls: List<OllamaToolCall>? = null,
    @JsonProperty("tool_call_id") val toolCallId: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaTool(
    @JsonProperty("type") val type: String = "function",
    @JsonProperty("function") val function: OllamaToolFunction
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaToolFunction(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("parameters") val parameters: Any?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaToolCall(
    @JsonProperty("id") val id: String,
    @JsonProperty("type") val type: String = "function",
    @JsonProperty("function") val function: OllamaToolCallFunction
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaToolCallFunction(
    @JsonProperty("name") val name: String,
    @JsonProperty("arguments") val arguments: String
)

data class OllamaChatResponse(
    @JsonProperty("id") val id: String,
    @JsonProperty("object") val objectType: String,
    @JsonProperty("created") val created: Long,
    @JsonProperty("model") val model: String,
    @JsonProperty("choices") val choices: List<OllamaChatChoice>,
    @JsonProperty("usage") val usage: OllamaCompletionUsage
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaChatChoice(
    @JsonProperty("index") val index: Int,
    @JsonProperty("message") val message: OllamaChatMessage,
    @JsonProperty("finish_reason") val finishReason: String?
)