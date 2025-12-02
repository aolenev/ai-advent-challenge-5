package ru.aolenev

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.logging.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.dataconversion.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.util.*
import io.ktor.utils.io.*
import org.slf4j.MDC
import org.slf4j.event.Level
import java.util.*

private val RequestBody = AttributeKey<String>("requestBody")

fun Application.commonSettings() {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
    install(DoubleReceive)

    val LogBody = createApplicationPlugin(
        name = "LogBody",
    ) {
        onCall { call ->
            try {
                val logBody: String = call.receiveChannel().readRemaining(max = 2048).readText()
                call.attributes.put(RequestBody, logBody)
            } catch (t: Throwable) {
                call.application.log.error("Error while reading request body", t)
            }
        }
    }

    install(LogBody)

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("correlation_id")
        format { call ->
            val time: String =
                when (val startTime: Long? = call.attributes.getOrNull(AttributeKey("CallStartTime"))) {
                    null -> "? ms"
                    else -> "${System.currentTimeMillis() - startTime} ms"
                }

            val status = call.response.status() ?: "Unhandled"
            val body = call.attributes.getOrNull(RequestBody) ?: "{{Empty body}}"
            val headers = call.request.headers.toMap().map { entry ->
                val headerName = entry.key
                val values = entry.value
                "$headerName: $values \n"
            }.fold("", {acc, h -> acc + h})
            "$status: ${call.request.toLogString()} $time $headers $body"
        }
    }
    install(CallId) {
        retrieve { call ->
            val id: String = call.request.header(HttpHeaders.XRequestId) ?: UUID.randomUUID().toString()
            val ip: String = call.request.header(HttpHeaders.XForwardedFor) ?: "127.0.0.1"
            MDC.put("correlation_id", id)
            MDC.put("http_x_forwarded_for", ip)
            id
        }
    }
}