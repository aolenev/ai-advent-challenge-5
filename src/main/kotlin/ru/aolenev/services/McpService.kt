package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.*

class McpService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()
    private val mapper: ObjectMapper by context.instance()

    private val mcpServerUrl: String by lazy {
        config.getString("ai-challenge.mcp.serverUrl")
    }

    private val protocolVersion = "2024-11-05"

    suspend fun initializeSession(): String? {
        return try {
            log.info("Initializing MCP session with server: $mcpServerUrl")

            val initRequest = McpInitializeRequest(
                jsonrpc = "2.0",
                id = 1,
                method = "initialize",
                params = McpInitializeParams(
                    protocolVersion = protocolVersion,
                    capabilities = McpClientCapabilities(
                        roots = McpRootsCapability(listChanged = true),
                        sampling = emptyMap(),
                        elicitation = emptyMap()
                    ),
                    clientInfo = McpClientInfo(
                        name = "ai-challenge-client",
                        title = "AI Challenge Client",
                        version = "1.0.0"
                    )
                )
            )

            val response: HttpResponse = httpClient.post(mcpServerUrl) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                accept(ContentType("text", "event-stream"))
                setBody(initRequest)
            }

            val sessionId = response.headers["mcp-session-id"]

            if (sessionId == null) {
                log.warn("MCP session initialization response did not contain mcp-session-id header")
                return null
            }

            log.info("MCP session initialized successfully with ID: $sessionId")

            val initializedNotification = McpInitializedNotification(
                jsonrpc = "2.0",
                method = "notifications/initialized"
            )

            httpClient.post(mcpServerUrl) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                accept(ContentType("text", "event-stream"))
                header("mcp-session-id", sessionId)
                setBody(initializedNotification)
            }

            log.info("Sent initialized notification for session: $sessionId")

            sessionId
        } catch (e: Exception) {
            log.error("Error initializing MCP session", e)
            null
        }
    }

    suspend fun getTools(mcpSessionId: String): List<McpTool>? {
        return try {
            log.info("Fetching tools list for session: $mcpSessionId")

            val toolsListRequest = McpToolsRequest(
                jsonrpc = "2.0",
                id = 2,
                method = "tools/list",
                params = McpToolsParams()
            )

            val response: HttpResponse = httpClient.post(mcpServerUrl) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                accept(ContentType("text", "event-stream"))
                header("mcp-session-id", mcpSessionId)
                setBody(toolsListRequest)
            }

            // Parse SSE response to extract JSON data
            val responseText = response.bodyAsText()
            log.debug("Raw SSE response: $responseText")

            val jsonData = parseSSEResponse(responseText)
            if (jsonData == null) {
                log.error("Failed to parse SSE response")
                return null
            }

            val toolsResponse = mapper.readValue(jsonData, McpToolsResponse::class.java)
            log.info("Successfully fetched ${toolsResponse.result.tools?.size ?: 0} tools")

            toolsResponse.result.tools
        } catch (e: Exception) {
            log.error("Error fetching tools list", e)
            null
        }
    }

    private fun parseSSEResponse(sseText: String): String? {
        // SSE format: lines starting with "data: " contain the JSON payload
        val lines = sseText.lines()
        val dataLines = lines.filter { it.startsWith("data: ") }

        if (dataLines.isEmpty()) {
            log.warn("No data lines found in SSE response")
            return null
        }

        // Extract JSON from the first data line (removing "data: " prefix)
        return dataLines.first().removePrefix("data: ").trim()
    }
}