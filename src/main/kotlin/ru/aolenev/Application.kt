package ru.aolenev

import dbMigration
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.kodein.di.instance
import ru.aolenev.model.ChatPrompt
import ru.aolenev.model.HelpRequest
import ru.aolenev.model.LocalModelPrompt
import ru.aolenev.model.McpToolsRequest
import ru.aolenev.model.SinglePrompt
import ru.aolenev.services.*
import java.math.BigDecimal

private val claude: ClaudeService by context.instance()
private val yandex: GptService by context.instance(tag = "yandex")
private val openai: GptService by context.instance(tag = "openai")
private val mcpService: CommonMcpService by context.instance()
private val turboMcpServer: TurboMcpServer by context.instance()
private val cronJobService: CronJobService by context.instance()
private val ollamaRagService: OllamaRagService by context.instance()
private val ollamaService: OllamaService by context.instance()

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    dbMigration()
//    cron()

    routing {
        commonSettings()
        routes()
        wsRoutes()
    }
}

private fun cron() {
    val schedulerScope = CoroutineScope(Dispatchers.Default)
    cronJobService.startScheduler(schedulerScope)
}

private fun Routing.routes() {
    post("/prompt") { req: SinglePrompt ->
        val response = when (req.gptService?.lowercase()) {
            "yandex" -> yandex.singlePrompt(req)
            "openrouter" -> openai.singlePrompt(req)
            "claude", null -> claude.singlePrompt(req)
            else -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown GPT service: ${req.gptService}. Use 'claude', 'yandex', or 'openrouter'"))
                return@post
            }
        }
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/structured-prompt") { req: SinglePrompt ->
        val response = claude.singlePromptWithStructuredResponse(req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/conversation") { req: ChatPrompt ->
        val response = claude.unstructuredChatWithAutoSummary(chatId = req.chatId, aiRole = req.systemPrompt, userPrompt = req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/finite-conversation") { req: ChatPrompt ->
        val response = claude.chatStructuredByTool(chatId = req.chatId, aiRole = req.systemPrompt, userPrompt = req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/tooled-conversation") { req: ChatPrompt ->
        val response = when (req.modelType.lowercase()) {
            "local" -> ollamaService.tooledChat(
                chatId = req.chatId,
                aiRoleOpt = req.systemPrompt,
                userPrompt = req.prompt,
                withRag = req.withRag,
                minSimilarity = req.minSimilarity,
                model = req.model ?: "qwen2.5:3b",
                temperature = req.temperature?.toDouble()
            )
            "cloud" -> claude.tooledChat(
                chatId = req.chatId,
                aiRoleOpt = req.systemPrompt,
                userPrompt = req.prompt,
                withRag = req.withRag,
                minSimilarity = req.minSimilarity,
                temperature = req.temperature
            )
            else -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown modelType: ${req.modelType}. Use 'cloud' or 'local'"))
                return@post
            }
        }

        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/mcp/tools") {
        val mcpSessionId = mcpService.initializeSession()
        if (mcpSessionId == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Failed to initialize MCP session"))
        } else {
            call.respond(HttpStatusCode.OK, mcpService.getTools(mcpSessionId)!!)
        }
    }

    post("/mcp-server/tools") { req: McpToolsRequest ->
        when(req.method) {
            "tools/list" -> call.respond(HttpStatusCode.OK, turboMcpServer.listTools())
            "tools/call" -> call.respond(HttpStatusCode.OK, turboMcpServer.callTool(req.params))
            else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown MCP method: ${req.method}"))
        }
    }

    post("/rag/process") { req: CreateEmbeddings ->
        try {
            if (req.fileName != null) {
                ollamaRagService.processAndStoreEmbeddings(inputFileName = req.fileName, separator = req.separator)
            } else {
                ollamaRagService.processAndStoreEmbeddings(separator = req.separator)
            }
            call.respond(HttpStatusCode.OK, mapOf("result" to "Embeddings processed and stored successfully"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    post("/help") { req: HelpRequest ->
        val response = claude.helpWithRag(question = req.question, minSimilarity = req.minSimilarity)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot process help request"))
    }

    post("/make-review") {
        val response = claude.reviewPullRequest(
            owner = "aolenev",
            repo = "ai-advent-challenge-5",
            minSimilarity = BigDecimal(0.7)
        )
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot process review request"))
    }

    post("/local-models") { req: LocalModelPrompt ->
        val response = ollamaService.callModel(prompt = req.prompt, model = req.model)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to local Ollama"))
    }
}