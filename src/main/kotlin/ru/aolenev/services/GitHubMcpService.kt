package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.GithubPullRequestInfo
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult

class GitHubMcpService {
    private val mapper: ObjectMapper by context.instance()
    private val httpClient: HttpClient by context.instance()
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val githubApiUrl = "https://api.github.com"

    val bearerToken = System.getenv("GITHUB_PAT")

    fun getTools(): List<McpTool> {
        return try {
            val listPullRequestsToolJson = this::class.java
                .getResource("/tool-templates/github_list_pull_requests.json")!!
                .readText()

            val listPullRequestsTool = mapper.readValue(listPullRequestsToolJson, McpTool::class.java)

            val getDiffToolJson = this::class.java
                .getResource("/tool-templates/github_get_diff.json")!!
                .readText()

            val getDiffTool = mapper.readValue(getDiffToolJson, McpTool::class.java)

            listOf(listPullRequestsTool, getDiffTool)
        } catch (e: Exception) {
            log.error("Error loading tools", e)
            throw e
        }
    }

    suspend fun callTool(toolName: String, arguments: Map<String, Any>, owner: String, repo: String): McpToolsResponse? {
        return try {
            when (toolName) {
                "github_get_diff" -> {
                    val prNumber = arguments.getOrDefault("number", 1)
                    val response = httpClient.get("$githubApiUrl/repos/$owner/$repo/pulls/$prNumber") {
                        bearerAuth(bearerToken)
                        accept(ContentType("application", "vnd.github.diff"))
                    }.bodyAsText()

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("diff" to response),
                            isError = true
                        )
                    )
                }

                "github_list_pull_requests" -> {
                    val response = httpClient.get("$githubApiUrl/repos/$owner/$repo/pulls") {
                        bearerAuth(bearerToken)
                        accept(ContentType("application", "vnd.github.diff"))
                    }.body<List<GithubPullRequestInfo>>()

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("pullRequests" to response),
                            isError = true
                        )
                    )
                }

                else -> {
                    log.warn("Unknown tool name: $toolName")
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