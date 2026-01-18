package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.*

class GitlabMcpService {
    private val mapper: ObjectMapper by context.instance()
    private val httpClient: HttpClient by context.instance()
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val gitlabApiUrl = "https://gitlab.turboapp.ru/api/v4"
    val bearerToken = System.getenv("GITLAB_PAT")

    fun getTools(): List<McpTool> {
        return try {
            val getLatestPipelineToolJson = this::class.java
                .getResource("/tool-templates/gitlab_get_latest_pipeline.json")!!
                .readText()

            val getLatestPipelineTool = mapper.readValue(getLatestPipelineToolJson, McpTool::class.java)

            val getPipelineJobsToolJson = this::class.java
                .getResource("/tool-templates/gitlab_get_pipeline_jobs.json")!!
                .readText()

            val getPipelineJobsTool = mapper.readValue(getPipelineJobsToolJson, McpTool::class.java)

            val runJobToolJson = this::class.java
                .getResource("/tool-templates/gitlab_run_job.json")!!
                .readText()

            val runJobTool = mapper.readValue(runJobToolJson, McpTool::class.java)

            listOf(getLatestPipelineTool, getPipelineJobsTool, runJobTool)
        } catch (e: Exception) {
            log.error("Error loading GitLab tools", e)
            throw e
        }
    }

    suspend fun callTool(toolName: String, arguments: Map<String, Any>): McpToolsResponse? {
        return try {
            when (toolName) {
                "gitlab_get_latest_pipeline" -> {
                    val input = mapper.convertValue(arguments, GitlabGetLatestPipelineToolInput::class.java)

                    // URL encode the project ID in case it contains slashes (e.g., "group/project")
                    val encodedProjectId = input.projectId.replace("/", "%2F")

                    val response = httpClient.get("$gitlabApiUrl/projects/$encodedProjectId/pipelines") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/json")
                        parameter("per_page", 1)
                        parameter("order_by", "id")
                        parameter("sort", "desc")
                    }.body<List<Map<String, Any>>>()

                    if (response.isEmpty()) {
                        return McpToolsResponse(
                            jsonrpc = "2.0",
                            id = 2,
                            result = McpToolsResult(
                                content = mapOf("error" to "No pipelines found for project ${input.projectId}"),
                                isError = true
                            )
                        )
                    }

                    val pipeline = response.first()
                    val output = GitlabPipelineInfo(
                        id = (pipeline["id"] as? Number)?.toInt() ?: 0,
                        projectId = (pipeline["project_id"] as? Number)?.toInt() ?: 0,
                        name = pipeline["name"] as? String,
                        status = pipeline["status"] as? String,
                        ref = pipeline["ref"] as? String,
                        createdAt = pipeline["created_at"] as? String
                    )

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }

                "gitlab_get_pipeline_jobs" -> {
                    val input = mapper.convertValue(arguments, GitlabGetPipelineJobsToolInput::class.java)

                    // URL encode the project ID in case it contains slashes (e.g., "group/project")
                    val encodedProjectId = input.projectId.replace("/", "%2F")

                    val response = httpClient.get("$gitlabApiUrl/projects/$encodedProjectId/pipelines/${input.pipelineId}/jobs") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/json")
                    }.body<List<Map<String, Any>>>()

                    val jobs = response.map { job ->
                        GitlabJobInfo(
                            id = (job["id"] as? Number)?.toInt() ?: 0,
                            name = job["name"] as? String ?: "",
                            status = job["status"] as? String ?: "",
                            stage = job["stage"] as? String,
                            createdAt = job["created_at"] as? String
                        )
                    }

                    val output = GitlabGetPipelineJobsToolOutput(
                        jobs = jobs,
                        totalCount = jobs.size
                    )

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }

                "gitlab_run_job" -> {
                    val input = mapper.convertValue(arguments, GitlabRunJobToolInput::class.java)

                    // URL encode the project ID in case it contains slashes
                    val encodedProjectId = input.projectId.replace("/", "%2F")

                    val response = httpClient.post("$gitlabApiUrl/projects/$encodedProjectId/jobs/${input.jobId}/play") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/json")
                        contentType(ContentType.Application.Json)
                    }.body<Map<String, Any>>()

                    val output = GitlabRunJobToolOutput(
                        id = (response["id"] as? Number)?.toInt() ?: 0,
                        name = response["name"] as? String ?: "",
                        status = response["status"] as? String ?: "",
                        stage = response["stage"] as? String,
                        startedAt = response["started_at"] as? String,
                        webUrl = response["web_url"] as? String
                    )

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
                    log.warn("Unknown GitLab tool name: $toolName")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: $toolName"),
                            isError = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error calling GitLab tool: $toolName", e)
            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(
                    content = mapOf("error" to (e.message ?: "Unknown error occurred")),
                    isError = true
                )
            )
        }
    }
}
