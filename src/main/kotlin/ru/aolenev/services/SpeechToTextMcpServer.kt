package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.lordcodes.turtle.shellRun
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import ru.aolenev.model.SpeechToTextToolInput
import ru.aolenev.model.SpeechToTextToolOutput
import ru.aolenev.model.ListAudioFilesToolOutput
import java.io.File

class SpeechToTextMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()

    private val resourcesPath = "/Users/a.olenev/work/hobbies/ai-challenge-5/src/main/resources/audio"
    private val whisperModelPath = "/Users/a.olenev/work/whisper-models/ggml-base.bin"

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading speech-to-text tools")

            val tools = listOf(
                loadTool("/tool-templates/speech_to_text.json"),
                loadTool("/tool-templates/list_audio_files.json")
            )

            log.info("Successfully loaded ${tools.size} speech-to-text tools")

            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(tools = tools, isError = false)
            )
        } catch (e: Exception) {
            log.error("Error loading tools", e)
            throw e
        }
    }

    private fun loadTool(resourcePath: String): McpTool {
        val toolJson = this::class.java
            .getResource(resourcePath)!!
            .readText()
        return mapper.readValue(toolJson, McpTool::class.java)
    }

    fun callTool(params: McpToolsParams): McpToolsResponse {
        return try {
            when (params.name) {
                "speech_to_text" -> {
                    log.info("Calling speech_to_text tool with arguments: ${params.arguments}")

                    val input = mapper.convertValue(
                        params.arguments,
                        SpeechToTextToolInput::class.java
                    )

                    val audioFilePath = "$resourcesPath/${input.fileName}.wav"
                    val srtFilePath = "$resourcesPath/${input.fileName}.wav.srt"

                    log.info("Running whisper-cli on file: $audioFilePath")

                    // Run whisper-cli command
                    shellRun(
                        "whisper-cli",
                        listOf("-m", whisperModelPath, "-osrt", audioFilePath)
                    )

                    // Read the resulting SRT file
                    val srtFile = File(srtFilePath)
                    val transcribedText = if (srtFile.exists()) {
                        srtFile.readText()
                    } else {
                        throw RuntimeException("SRT file not found at: $srtFilePath")
                    }

                    val output = SpeechToTextToolOutput(
                        success = true,
                        text = transcribedText,
                        srtFilePath = srtFilePath
                    )

                    log.info("Speech to text completed successfully")

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }
                "list_audio_files" -> {
                    log.info("Calling list_audio_files tool")

                    val resourcesDir = File(resourcesPath)
                    val audioFiles = resourcesDir.listFiles { file ->
                        file.isFile && file.extension.lowercase() == "wav"
                    }?.map { it.nameWithoutExtension } ?: emptyList()

                    val output = ListAudioFilesToolOutput(
                        files = audioFiles,
                        totalCount = audioFiles.size
                    )

                    log.info("Found ${audioFiles.size} audio files")

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
                    content = mapOf("error" to (e.message ?: "Unknown error")),
                    isError = true
                )
            )
        }
    }
}
