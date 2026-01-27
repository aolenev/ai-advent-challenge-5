package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.AddNoteToolInput
import ru.aolenev.model.AddNoteToolOutput
import ru.aolenev.model.ListNotesToolOutput
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import ru.aolenev.model.NoteInfo
import ru.aolenev.model.RemoveNoteToolInput
import ru.aolenev.model.RemoveNoteToolOutput
import ru.aolenev.repo.NotesTable

class PersonalizationMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading personalization tools")

            val tools = listOf(
                loadTool("/tool-templates/add_note.json"),
                loadTool("/tool-templates/remove_note.json"),
                loadTool("/tool-templates/list_notes.json")
            )

            log.info("Successfully loaded ${tools.size} personalization tools")

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
                "add_note" -> handleAddNote(params)
                "remove_note" -> handleRemoveNote(params)
                "list_notes" -> handleListNotes()
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

    private fun handleAddNote(params: McpToolsParams): McpToolsResponse {
        log.info("Calling add_note tool with arguments: ${params.arguments}")

        val input = mapper.convertValue(params.arguments, AddNoteToolInput::class.java)
        val noteId = NotesTable.addNote(input.note)

        val output = AddNoteToolOutput(
            success = true,
            id = noteId,
            message = "Note added successfully"
        )

        log.info("Note added with id=$noteId")

        return McpToolsResponse(
            jsonrpc = "2.0",
            id = 2,
            result = McpToolsResult(content = output, isError = false)
        )
    }

    private fun handleRemoveNote(params: McpToolsParams): McpToolsResponse {
        log.info("Calling remove_note tool with arguments: ${params.arguments}")

        val input = mapper.convertValue(params.arguments, RemoveNoteToolInput::class.java)
        val removed = NotesTable.removeNote(input.id)

        val output = RemoveNoteToolOutput(
            success = removed,
            message = if (removed) "Note removed successfully" else "Note not found"
        )

        log.info("Remove note id=${input.id}, success=$removed")

        return McpToolsResponse(
            jsonrpc = "2.0",
            id = 2,
            result = McpToolsResult(content = output, isError = false)
        )
    }

    private fun handleListNotes(): McpToolsResponse {
        log.info("Calling list_notes tool")

        val notes = NotesTable.listNotes()

        val output = ListNotesToolOutput(
            notes = notes.map { NoteInfo(id = it.id, note = it.note) },
            totalCount = notes.size
        )

        log.info("Listed ${notes.size} notes")

        return McpToolsResponse(
            jsonrpc = "2.0",
            id = 2,
            result = McpToolsResult(content = output, isError = false)
        )
    }
}
