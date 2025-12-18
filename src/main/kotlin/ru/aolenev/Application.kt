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
import ru.aolenev.model.McpToolsRequest
import ru.aolenev.model.SinglePrompt
import ru.aolenev.services.ClaudeService
import ru.aolenev.services.CronJobService
import ru.aolenev.services.GptService
import ru.aolenev.services.TurboMcpServer
import ru.aolenev.services.McpService

private val claude: ClaudeService by context.instance()
private val yandex: GptService by context.instance(tag = "yandex")
private val openai: GptService by context.instance(tag = "openai")
private val mcpService: McpService by context.instance()
private val turboMcpServer: TurboMcpServer by context.instance()
private val cronJobService: CronJobService by context.instance()

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
        val response = claude.tooledChat(chatId = req.chatId, aiRoleOpt = req.systemPrompt, userPrompt = req.prompt)
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
}