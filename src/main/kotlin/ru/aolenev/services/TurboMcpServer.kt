package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.CompletedFuelingsToolInput
import ru.aolenev.model.CompletedFuelingsToolOutput
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import java.math.BigDecimal

class TurboMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()
    private val turboApiService: TurboApiService by context.instance()

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading tools from get_completed_fuelings.json")

            val toolJson = this::class.java
                .getResource("/tool-templates/get_completed_fuelings.json")!!
                .readText()

            val tool = mapper.readValue(toolJson, McpTool::class.java)

            log.info("Successfully loaded tool: ${tool.name}")

            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(tools = listOf(tool), isError = false)
            )
        } catch (e: Exception) {
            log.error("Error loading tools", e)
            throw e
        }
    }

    suspend fun callTool(params: McpToolsParams): McpToolsResponse {
        return try {
            when (params.name) {
                "get_completed_fuelings" -> {
                    log.info("Calling get_completed_fuelings tool with arguments: ${params.arguments}")

                    val input = mapper.convertValue(
                        params.arguments,
                        CompletedFuelingsToolInput::class.java
                    )

                    val fuelings = turboApiService.getFuelings(input.from, input.to)
                        ?: return McpToolsResponse(
                            jsonrpc = "2.0",
                            id = 2,
                            result = McpToolsResult(
                                content = mapOf("error" to "Failed to call get_completed_fuelings tool"),
                                isError = true
                            )
                        )

                    val completedFuelings = fuelings.filter { it.status == "completed" }

                    val count = completedFuelings.size
                    val volume = completedFuelings
                        .mapNotNull { it.actualAmount }
                        .fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

                    val output = CompletedFuelingsToolOutput(
                        count = count,
                        volume = volume
                    )

                    log.info("Completed fuelings: count=$count, volume=$volume")

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }
                else -> {
                    log.warn("Unknown tool name: ${params.name}")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: ${params.name}"),
                            isError = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error calling tool", e)
            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(
                    content = mapOf("error" to e.message),
                    isError = true
                )
            )
        }
    }
}