package ru.aolenev

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.instance

private val claude: ClaudeService by context.instance()

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    routing {
        commonSettings()
        routes()
    }
}

private fun Routing.routes() {
    post("/prompt") { req: SimplePrompt ->
        val response = claude.singlePrompt(req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/structured-prompt") { req: SimplePrompt ->
        val response = claude.singlePromptWithStructuredResponse(req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }

    post("/conversation") { req: ChatPrompt ->
        val response = claude.chat(chatId = req.chatId, aiRole = req.systemPrompt, userPrompt = req.prompt)
        if (response != null) {
            call.respond(HttpStatusCode.OK, mapOf("result" to response))
        } else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("result" to "Cannot send prompt to AI"))
    }
}