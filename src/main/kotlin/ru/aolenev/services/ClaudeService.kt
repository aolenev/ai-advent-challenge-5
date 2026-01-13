package ru.aolenev.services

import asMap
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import deserializeToolInput
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.*
import ru.aolenev.model.*
import ru.aolenev.repo.ChatTable
import ru.aolenev.repo.MessageTable
import ru.aolenev.repo.RagEmbeddingsTable
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class ClaudeService : GptService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()
    private val mapper: ObjectMapper by context.instance()
    private val localTurboMcpServer: TurboMcpServer by context.instance()
    private val localDatabaseMcpServer: DatabaseMcpServer by context.instance()
    private val localShellMcpServer: ShellMcpServer by context.instance()
    private val ollamaRagService: OllamaRagService by context.instance()
    private val gitHubMcpService: GitHubMcpService by context.instance()

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
                    tools = listOf(mapper.convertValue(singlePromptStructResponseTool, McpTool::class.java).toClaude()),
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
            MessageTable.saveMessage(chatId = chat.id, messageContent = responseMessage, messageType = MessageType.SUMMARY)
            // чистим кэш чата от старых сообщений до суммаризации
            chatCache.invalidate(chat.id)

            listOf(ClaudeMessage(role = "user", content = responseMessage))
        } else chat.messages
    }

    private suspend fun enrichPromptWithRagContext(userPrompt: String, minSimilarity: BigDecimal): String {
        log.info("Обогащаем промпт контекстом из RAG")

        // Split prompt into chunks
        val chunks = ollamaRagService.splitByChunks(userPrompt, chunkSize = 50, overlap = 10)
        log.info("Разбили промпт на ${chunks.size} чанков")

        // Get embeddings for chunks
        val embeddingsMap = ollamaRagService.getEmbeddings(chunks)

        // Find similar contexts for each chunk
        val allSimilarChunks = mutableSetOf<String>()
        embeddingsMap.forEach { (chunk, embedding) ->
            val similarChunks = RagEmbeddingsTable.findSimilarChunks(embedding, minSimilarity)
            allSimilarChunks.addAll(similarChunks)
            log.info("Найдено ${similarChunks.size} похожих чанков для chunk: ${chunk.take(50)}...")
        }

        return if (allSimilarChunks.isNotEmpty()) {
            val context = allSimilarChunks.joinToString("\n\n")
            log.info("Добавляем ${allSimilarChunks.size} уникальных чанков как контекст")
            """
            |Additional context from knowledge base:
            |
            |$context
            |
            |User question: $userPrompt
            """.trimMargin()
        } else {
            log.info("Похожих чанков не найдено, используем оригинальный промпт")
            userPrompt
        }
    }

    suspend fun unstructuredChatWithAutoSummary(chatId: String, aiRole: String?, userPrompt: String): ResponseWithHistory? {
        if (aiRole.isNullOrEmpty()) throw BadRequestException(message = "You didn't provide an AI role")
        try {
            val existingChat = chatCache.get(chatId)
            val currentChat = if (existingChat == null) { // это первое сообщение в чате
                val chat = Chat(
                    id = chatId,
                    aiRole = aiRole,
                    messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
                    isFinished = false
                )
                chatCache.put(chatId, chat)
                ChatTable.addChat(chatId = chatId, aiRole = aiRole)
                MessageTable.saveMessage(messageContent = userPrompt, chatId = chatId, messageType = MessageType.USER)
                chat
            } else { // Сообщения в чате уже были, делаем автосуммирование истории, если требуется, а потом добавляем ещё одно сообщение в хвост

                val compressedMessages = performAutoSummaryIfNeeded(existingChat)
                MessageTable.saveMessage(chatId = chatId, messageContent = userPrompt, messageType = MessageType.USER)

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
                    system = currentChat.aiRole,
                    tools = localTurboMcpServer.listTools().result.tools?.map { it.toClaude() }
                )
            ).body<ClaudeSimpleResponse>()

            val responseMessage = response.content.first().content
            MessageTable.saveMessage(messageContent = responseMessage, chatId = chatId, messageType = MessageType.ASSISTANT)

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
                messageHistory = chatCache.get(chatId).messages.map { mapOf(it.role to it.content) },
                usage = response.usage.toTokenUsage()
            )
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    suspend fun chatStructuredByTool(chatId: String, aiRole: String?, userPrompt: String): FiniteChatResponse? {
        try {
            val existingChat = chatCache.get(chatId)
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
                    response = "Your conversation is finished, last response is: ${(existingChat.messages.last().content as List<ClaudeTooledContent>).first().input}",
                    isChatFinished = true
                )
            } else { // Сообщения в чате уже были, значит были и ответы

                // Последний ответ в цепочке должен соответствовать той схеме, которую мы отправили в tool
                val tooledResponse = existingChat.messages.last()
                val toolId = (tooledResponse.content as List<ClaudeTooledContent>)[0].id
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
                    tools = listOf(mapper.convertValue(multiQuestionsTool, McpTool::class.java).toClaude()),
                    messages = currentChat.messages,
                    system = currentChat.aiRole
                )
            ).body<ClaudeMultiQuestionsResponse>()

            val tooledResponse = response.content
                .filter { it.type == "tool_use" }
                .map { ClaudeTooledContent(id = it.id!!, name = it.name!!, input = it.input!!) }
                .last()

            val input = deserializeToolInput(toolName = tooledResponse.name, toolInput = tooledResponse.input) as ResponseWithFinishFlag
            chatCache.put(
                chatId,
                currentChat.copy(
                    messages = currentChat.messages + ClaudeMessage(
                        role = "assistant",
                        content = listOf(tooledResponse)
                    ),
                    isFinished = input.isFinished
                )
            )

            return FiniteChatResponse(response = input.response, isChatFinished = input.isFinished)
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    suspend fun tooledChat(chatId: String, userPrompt: String, aiRoleOpt: String?, withRag: Boolean, minSimilarity: BigDecimal): ResponseWithHistory? {
        val aiRole = aiRoleOpt ?: "Use tools if needed"
        try {
            val richPrompt = if (withRag) enrichPromptWithRagContext(userPrompt, minSimilarity) else userPrompt
            val existingChat = chatCache.get(chatId)
            val currentChat = if (existingChat == null) { // это первое сообщение в чате
                val chat = Chat(
                    id = chatId,
                    aiRole = aiRole,
                    messages = listOf(ClaudeMessage(role = "user", content = richPrompt)),
                    isFinished = false
                )
                chatCache.put(chatId, chat)
                ChatTable.addChat(chatId = chatId, aiRole = aiRole)
                MessageTable.saveMessage(messageContent = richPrompt, chatId = chatId, messageType = MessageType.USER)
                chat
            } else { // Сообщения в чате уже были, делаем автосуммирование истории, если требуется, а потом добавляем ещё одно сообщение в хвост

                val compressedMessages = performAutoSummaryIfNeeded(existingChat)
                MessageTable.saveMessage(chatId = chatId, messageContent = richPrompt, messageType = MessageType.USER)

                existingChat.copy(
                    aiRole = aiRole,
                    messages = compressedMessages + ClaudeMessage(
                        role = "user",
                        content = richPrompt
                    )
                )
            }

            // Combine tools from all MCP servers
            val turboTools = localTurboMcpServer.listTools().result.tools ?: emptyList()
            val databaseTools = localDatabaseMcpServer.listTools().result.tools ?: emptyList()
            val shellTools = localShellMcpServer.listTools().result.tools ?: emptyList()
            val allTools = (turboTools + databaseTools + shellTools).map { it.toClaude() }

            var response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    messages = currentChat.messages,
                    system = currentChat.aiRole,
                    tools = allTools
                )
            ).body<ClaudeResponse>()

            while (response.stopReason == "tool_use") {
                response = handleToolUse(chatId, response, allTools)
            }
            // Deserialize to ClaudeTextContent
            @Suppress("UNCHECKED_CAST")
            val contentList = mapper.convertValue(response.content, List::class.java) as List<Map<String, Any>>
            val textContent = contentList
                .filter { it["type"] == "text" }
                .map { content ->
                    mapper.convertValue(content, ClaudeTextContent::class.java)
                }
                .firstOrNull()

            if (textContent != null) {
                log.info("Text response: ${textContent.content}")
                val assistantMessage = ClaudeMessage(role = "assistant", content = contentList)
                memoizeMessage(currentChat.id, assistantMessage, MessageType.ASSISTANT)

                val finalChat = chatCache.getIfPresent(chatId)!!
                return ResponseWithHistory(
                    response = textContent.content,
                    usage = response.usage.toTokenUsage(),
                    messageHistory = finalChat.messages.map { mapOf(it.role to it.content) }
                )
            }

            return null
        } catch (e: Exception) {
            log.error("ERROR:", e)
            return null
        }
    }

    private suspend fun handleToolUse(chatId: String, response: ClaudeResponse, allTools: List<ClaudeMcpTool>): ClaudeResponse {
        val currentChat = chatCache.get(chatId)
        // Convert response.content to list of ClaudeTooledContent
        @Suppress("UNCHECKED_CAST")
        val contentList = mapper.convertValue(response.content, List::class.java) as List<Map<String, Any>>
        val tooledContent = contentList
            .filter { it["type"] == "tool_use" }
            .map { content ->
                mapper.convertValue(content, ClaudeTooledContent::class.java)
            }
            .firstOrNull()

        if (tooledContent != null) {
            // Route tool call to the appropriate MCP server
            @Suppress("UNCHECKED_CAST")
            val arguments = mapper.convertValue(tooledContent.input, Map::class.java) as Map<String, Any>
            val toolResult = when (tooledContent.name) {
                "get_completed_fuelings" -> localTurboMcpServer.callTool(
                    McpToolsParams(
                        name = tooledContent.name,
                        arguments = arguments
                    )
                )
                "save_fuelings_stat" -> localDatabaseMcpServer.callTool(
                    McpToolsParams(
                        name = tooledContent.name,
                        arguments = arguments
                    )
                )
                "execute_shell_command" -> localShellMcpServer.callTool(
                    McpToolsParams(
                        name = tooledContent.name,
                        arguments = arguments
                    )
                )
                else -> {
                    log.error("Unknown tool: ${tooledContent.name}")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: ${tooledContent.name}"),
                            isError = true
                        )
                    )
                }
            }
            log.info("Tool result: $toolResult")

            // If tool result has content, send it back to Claude
            if (toolResult.result.content != null) {
                val toolResultContent = TooledContent(
                    toolId = tooledContent.id,
                    content = toolResult.result.content.asMap().toString()
                )

                val assistantMessage = ClaudeMessage(role = "assistant", content = contentList)
                val userMessage = ClaudeMessage(role = "user", content = listOf(toolResultContent))

                memoizeMessage(currentChat.id, assistantMessage, MessageType.ASSISTANT)
                memoizeMessage(currentChat.id, userMessage, MessageType.USER)
                val updatedChat = chatCache.get(chatId)

                val followUpResponse = requestClaude(
                    req = ClaudeRawRequest(
                        model = sonnet45,
                        messages = updatedChat.messages,
                        system = updatedChat.aiRole,
                        tools = allTools
                    )
                ).body<ClaudeResponse>()

                log.info("Follow-up response: $followUpResponse")

//                memoizeMessage(currentChat.id, ClaudeMessage(role = "assistant", content = followUpResponse.content), MessageType.ASSISTANT)

                return followUpResponse
            }
        }
        return response
    }

    private fun memoizeMessage(chatId: String, newMessage: ClaudeMessage, messageType: MessageType) {
        MessageTable.saveMessage(
            messageContent = mapper.writeValueAsString(newMessage.content),
            chatId = chatId,
            messageType = messageType
        )

        val chat = chatCache.get(chatId)
        chatCache.put(
            chat.id,
            chat.copy(messages = chat.messages + newMessage)
        )
    }

    private val chatCache: LoadingCache<String, Chat> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build { key ->
            val aiRole = ChatTable.findAiRole(key)
            if (aiRole != null) {
                Chat(
                    id = key,
                    aiRole = aiRole,
                    messages = MessageTable.readLastMessages(key).map { ClaudeMessage(role = it.messageType.toClaudeRole(), content = it.messageContent) },
                    isFinished = false
                )
            } else null
        }

    suspend fun helpWithRag(question: String, minSimilarity: BigDecimal): String? {
        try {
            log.info("Processing help request with RAG for question: $question")

            // Enrich the question with RAG context
            val enrichedPrompt = enrichPromptWithRagContext(question, minSimilarity)

            // Send to Claude
            val response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    messages = listOf(ClaudeMessage(role = "user", content = enrichedPrompt)),
                    system = "You are a helpful assistant. Answer the user's question based on the provided context from the knowledge base. If the context doesn't contain relevant information, say so clearly.",
                    maxTokens = 4096
                )
            ).body<ClaudeSimpleResponse>()

            return response.content.first().content
        } catch (e: Exception) {
            log.error("Error processing help request", e)
            return null
        }
    }

    suspend fun reviewPullRequest(owner: String, repo: String, minSimilarity: BigDecimal): String? {
        return try {
            log.info("Starting PR review for $owner/$repo")

            val sessionId = gitHubMcpService.initializeSession()
            if (sessionId == null) {
                log.error("Failed to initialize GitHub MCP session")
                return "Failed to initialize GitHub MCP session"
            }

            val githubTools = gitHubMcpService.getTools(sessionId)
            if (githubTools == null) {
                log.error("Failed to fetch GitHub tools")
                return "Failed to fetch GitHub tools"
            }

            log.info("Fetched ${githubTools.size} GitHub tools: ${githubTools.map { it.name }}")

            // Create initial prompt asking Claude to review the latest PR
            val initialPrompt = """
                Please review the latest pull request from the repository $owner/$repo.

                Steps:
                1. List the pull requests to find the latest one
                2. Get the details of the latest pull request
                3. Review the changes and provide feedback

                Focus on compliance with provided architecture documentation, code quality, potential bugs, and improvements.
            """.trimIndent()

            // Enrich with RAG context
            val enrichedPrompt = enrichPromptWithRagContext(initialPrompt, minSimilarity)

            // Convert GitHub tools to Claude format
            val claudeTools = githubTools.map { it.toClaude() }

            // Send initial request to Claude with tools
            var messages = listOf(ClaudeMessage(role = "user", content = enrichedPrompt))
            var response = requestClaude(
                req = ClaudeRawRequest(
                    model = sonnet45,
                    messages = messages,
                    system = "You are a code reviewer. Use the available GitHub tools to fetch and review pull requests. Provide constructive feedback.",
                    tools = claudeTools,
                    maxTokens = 4096
                )
            ).body<ClaudeResponse>()

            // Handle tool calls in a loop
            while (response.stopReason == "tool_use") {
                response = handleGitHubToolUse(sessionId, response, claudeTools, messages)
                messages = extractMessagesFromResponse(response, messages)
            }

            // Extract final text response
            @Suppress("UNCHECKED_CAST")
            val contentList = mapper.convertValue(response.content, List::class.java) as List<Map<String, Any>>
            val textContent = contentList
                .filter { it["type"] == "text" }
                .map { content ->
                    mapper.convertValue(content, ClaudeTextContent::class.java)
                }
                .firstOrNull()

            textContent?.content ?: "No text response from Claude"
        } catch (e: Exception) {
            log.error("Error reviewing pull request", e)
            "Error reviewing pull request: ${e.message}"
        }
    }

    private suspend fun handleGitHubToolUse(
        sessionId: String,
        response: ClaudeResponse,
        tools: List<ClaudeMcpTool>,
        previousMessages: List<ClaudeMessage>
    ): ClaudeResponse {
        // Extract tool use from response
        @Suppress("UNCHECKED_CAST")
        val contentList = mapper.convertValue(response.content, List::class.java) as List<Map<String, Any>>
        val tooledContent = contentList
            .filter { it["type"] == "tool_use" }
            .map { content ->
                mapper.convertValue(content, ClaudeTooledContent::class.java)
            }
            .firstOrNull()

        if (tooledContent != null) {
            log.info("Claude wants to use tool: ${tooledContent.name}")

            // Call the GitHub tool via MCP
            @Suppress("UNCHECKED_CAST")
            val arguments = mapper.convertValue(tooledContent.input, Map::class.java) as Map<String, Any>
            val toolResult = gitHubMcpService.callTool(sessionId, tooledContent.name, arguments)

            if (toolResult != null && toolResult.result.content != null) {
                log.info("Tool result: ${toolResult.result.content}")

                // Build new messages including the tool result
                val assistantMessage = ClaudeMessage(role = "assistant", content = contentList)
                val toolResultContent = TooledContent(
                    toolId = tooledContent.id,
                    content = mapper.writeValueAsString(toolResult.result.content)
                )
                val userMessage = ClaudeMessage(role = "user", content = listOf(toolResultContent))

                val newMessages = previousMessages + assistantMessage + userMessage

                // Send back to Claude
                return requestClaude(
                    req = ClaudeRawRequest(
                        model = sonnet45,
                        messages = newMessages,
                        system = "You are a code reviewer. Use the available GitHub tools to fetch and review pull requests. Provide constructive feedback.",
                        tools = tools,
                        maxTokens = 4096
                    )
                ).body<ClaudeResponse>()
            }
        }

        return response
    }

    private fun extractMessagesFromResponse(response: ClaudeResponse, previousMessages: List<ClaudeMessage>): List<ClaudeMessage> {
        @Suppress("UNCHECKED_CAST")
        val contentList = mapper.convertValue(response.content, List::class.java) as List<Map<String, Any>>
        return previousMessages + ClaudeMessage(role = "assistant", content = contentList)
    }

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
    @JsonProperty("tools") val tools: List<ClaudeMcpTool>? = null,
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
    @JsonProperty("input") val input: Any?
)

private data class ClaudeResponse(
    @JsonProperty("usage") val usage: ClaudeUsage,
    @JsonProperty("stop_reason") val stopReason: String,
    @JsonProperty("content") val content: Any
)

private data class ClaudeTooledContent(
    @JsonProperty("type") val type: String = "tool_use",
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("input") val input: Any
)