package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import ru.aolenev.model.SaveFuelingsStatToolInput
import ru.aolenev.model.SaveFuelingsStatToolOutput
import ru.aolenev.repo.FuelingStatTable
import java.math.BigDecimal

class DatabaseMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading tools from save_fuelings_stat.json")

            val toolJson = this::class.java
                .getResource("/tool-templates/save_fuelings_stat.json")!!
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

    fun callTool(params: McpToolsParams): McpToolsResponse {
        return try {
            when (params.name) {
                "save_fuelings_stat" -> {
                    log.info("Calling save_fuelings_stat tool with arguments: ${params.arguments}")

                    val input = mapper.convertValue(
                        params.arguments,
                        SaveFuelingsStatToolInput::class.java
                    )

                    FuelingStatTable.saveStat(
                        fuelingCount = BigDecimal.valueOf(input.count.toLong()),
                        fuelingLiters = input.volume,
                        fromMs = input.from,
                        toMs = input.to
                    )

                    val output = SaveFuelingsStatToolOutput(
                        success = true,
                        message = "Fueling statistics saved successfully"
                    )

                    log.info("Fueling stat saved: count=${input.count}, volume=${input.volume}, from=${input.from}, to=${input.to}")

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