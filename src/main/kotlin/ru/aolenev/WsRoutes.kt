package ru.aolenev

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.zip.Deflater
import kotlin.time.Duration.Companion.seconds

fun Application.wsRoutes() {

    install(WebSockets) {
        contentConverter = JacksonWebsocketContentConverter()
        pingPeriod = 10.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        extensions {
            install(WebSocketDeflateExtension) {
                compressionLevel = Deflater.DEFAULT_COMPRESSION
                compressIfBiggerThan(bytes = 4 * 1024)
            }
        }
    }

    routing {
        webSocket("/wss/{chatId}") {
            val chatId = call.parameters["chatId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing chatId"))

            // Store the session
            WsSessionsStorage.addSession(chatId, this)

            // Launch a coroutine to send periodic ping frames
            val pingJob = launch {
                while (isActive) {
                    delay(10.seconds)
                    try {
                        send(Frame.Ping(ByteArray(0)))
                        application.log.debug("Sent ping to chatId: $chatId")
                    } catch (e: Exception) {
                        application.log.error("Failed to send ping to chatId: $chatId", e)
                        break
                    }
                }
            }

            try {
                // Handle incoming frames
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Pong -> {
                            application.log.debug("Received pong from chatId: $chatId")
                        }
                        is Frame.Ping -> {
                            application.log.debug("Received ping from chatId: $chatId")
                        }
                        is Frame.Text -> {
                            val receivedText = frame.readText()
                            application.log.info("Received text from chatId: $chatId: $receivedText")
                        }
                        is Frame.Binary -> {
                            application.log.info("Received binary frame from chatId: $chatId")
                        }
                        is Frame.Close -> {
                            application.log.info("Received close frame from chatId: $chatId")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                application.log.error("WebSocket error for chatId: $chatId", e)
            } finally {
                pingJob.cancel()
                WsSessionsStorage.removeSession(chatId)
                application.log.info("WebSocket connection closed for chatId: $chatId")
            }
        }
    }
}