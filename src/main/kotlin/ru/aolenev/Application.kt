package ru.aolenev

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.instance

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
    route("/promptMe") {
        post { req: SimplePrompt ->
            val claude: ClaudeClient by context.instance()
            val response = claude.sendPrompt(req.prompt)
            if (response != null) {
                call.respond(HttpStatusCode.OK, mapOf("result" to response))
            } else call.respond(HttpStatusCode.Conflict, mapOf("result" to "Cannot send prompt to AI"))
        }
    }
}

data class SimplePrompt(val prompt: String)