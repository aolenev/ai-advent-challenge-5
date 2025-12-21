package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.lordcodes.turtle.ShellLocation
import com.lordcodes.turtle.shellRun
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.ExecuteShellCommandToolInput
import ru.aolenev.model.ExecuteShellCommandToolOutput
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import java.io.File

class ShellMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading tools from execute_shell_command.json")

            val toolJson = this::class.java
                .getResource("/tool-templates/execute_shell_command.json")!!
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
                "execute_shell_command" -> {
                    log.info("Calling execute_shell_command tool with arguments: ${params.arguments}")

                    val input = mapper.convertValue(
                        params.arguments,
                        ExecuteShellCommandToolInput::class.java
                    )

                    val workingDir = input.workingDirectory?.let { File(it) } ?: ShellLocation.HOME

                    log.info("Executing command: ${input.command} in directory: ${workingDir.absolutePath}")

                    val result = shellRun(input.command, input.commandParameters)

                    val output = ExecuteShellCommandToolOutput(
                        stdout = result
                    )

                    log.info("Command executed successfully. Output: $result")

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
                    content = mapOf(
                        "error" to (e.message ?: "Unknown error"),
                        "stdout" to "",
                        "stderr" to (e.message ?: ""),
                        "exitCode" to -1
                    ),
                    isError = true
                )
            )
        }
    }
}