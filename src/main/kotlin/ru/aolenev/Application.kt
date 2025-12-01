package ru.aolenev

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    routing {
        route ("/test") {
            get {
                call.respond(HttpStatusCode.OK, mapOf("result" to "ok"))
            }
        }
    }
}