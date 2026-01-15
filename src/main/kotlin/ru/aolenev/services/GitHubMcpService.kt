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
import ru.aolenev.model.*

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

            val listBugIssuesToolJson = this::class.java
                .getResource("/tool-templates/github_list_bug_issues.json")!!
                .readText()

            val listBugIssuesTool = mapper.readValue(listBugIssuesToolJson, McpTool::class.java)

            val listIssuesToolJson = this::class.java
                .getResource("/tool-templates/github_list_issues.json")!!
                .readText()

            val listIssuesTool = mapper.readValue(listIssuesToolJson, McpTool::class.java)

            val createIssueToolJson = this::class.java
                .getResource("/tool-templates/github_create_issue.json")!!
                .readText()

            val createIssueTool = mapper.readValue(createIssueToolJson, McpTool::class.java)

            val updateIssueToolJson = this::class.java
                .getResource("/tool-templates/github_update_issue.json")!!
                .readText()

            val updateIssueTool = mapper.readValue(updateIssueToolJson, McpTool::class.java)

            listOf(listPullRequestsTool, getDiffTool, listBugIssuesTool, listIssuesTool, createIssueTool, updateIssueTool)
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

                "github_list_bug_issues" -> {
                    val response = httpClient.get("$githubApiUrl/repos/$owner/$repo/issues?state=open&labels=bug") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/vnd.github+json")
                    }.body<List<GithubIssueInfo>>()

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("issues" to response),
                            isError = false
                        )
                    )
                }

                "github_list_issues" -> {
                    val input = mapper.convertValue(arguments, GithubListIssuesToolInput::class.java)

                    // Build query parameters
                    val params = mutableListOf<String>()

                    // Add state filter
                    if (input.state != null && input.state != "all") {
                        params.add("state=${input.state}")
                    }

                    // Add labels filter
                    val labelsToFilter = mutableListOf<String>()
                    input.labels?.let { labelsToFilter.addAll(it) }
                    input.priority?.let { labelsToFilter.add(it) }

                    if (labelsToFilter.isNotEmpty()) {
                        params.add("labels=${labelsToFilter.joinToString(",")}")
                    }

                    val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""

                    val response = httpClient.get("$githubApiUrl/repos/$owner/$repo/issues$queryString") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/vnd.github+json")
                    }.body<List<GithubIssueInfo>>()

                    // Filter by priority if needed (case-insensitive label matching)
                    val filteredResponse = if (input.priority != null) {
                        response.filter { issue ->
                            issue.labels.any { label ->
                                label.name.contains(input.priority, ignoreCase = true)
                            }
                        }
                    } else {
                        response
                    }

                    val output = GithubListIssuesToolOutput(
                        issues = filteredResponse,
                        totalCount = filteredResponse.size
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

                "github_create_issue" -> {
                    val input = mapper.convertValue(arguments, GithubCreateIssueToolInput::class.java)

                    // Build labels list
                    val labels = mutableListOf<String>()
                    input.labels?.let { labels.addAll(it) }
                    input.priority?.let { labels.add("priority: $it") }

                    // Create request body
                    val requestBody = buildMap {
                        put("title", input.title)
                        put("body", input.description)
                        if (labels.isNotEmpty()) {
                            put("labels", labels)
                        }
                    }

                    val response = httpClient.post("$githubApiUrl/repos/$owner/$repo/issues") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/vnd.github+json")
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }.body<Map<String, Any>>()

                    @Suppress("UNCHECKED_CAST")
                    val labelsResponse = (response["labels"] as? List<Map<String, Any>>) ?: emptyList()
                    val issueLabels = labelsResponse.map { labelMap ->
                        IssueLabel(name = labelMap["name"] as? String ?: "")
                    }

                    val output = GithubCreateIssueToolOutput(
                        number = (response["number"] as? Number)?.toInt() ?: 0,
                        url = response["html_url"] as? String ?: "",
                        title = response["title"] as? String ?: "",
                        state = response["state"] as? String ?: "",
                        labels = issueLabels,
                        createdAt = response["created_at"] as? String ?: ""
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

                "github_update_issue" -> {
                    val input = mapper.convertValue(arguments, GithubUpdateIssueToolInput::class.java)

                    // Build labels list
                    val labels = mutableListOf<String>()
                    input.labels?.let { labels.addAll(it) }
                    input.priority?.let { labels.add("priority: $it") }

                    // Build request body with only the fields that are provided
                    val requestBody = buildMap<String, Any> {
                        input.title?.let { put("title", it) }
                        input.description?.let { put("body", it) }
                        input.state?.let { put("state", it) }
                        if (labels.isNotEmpty()) {
                            put("labels", labels)
                        }
                    }

                    val response = httpClient.patch("$githubApiUrl/repos/$owner/$repo/issues/${input.issueNumber}") {
                        bearerAuth(bearerToken)
                        header("Accept", "application/vnd.github+json")
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }.body<Map<String, Any>>()

                    @Suppress("UNCHECKED_CAST")
                    val labelsResponse = (response["labels"] as? List<Map<String, Any>>) ?: emptyList()
                    val issueLabels = labelsResponse.map { labelMap ->
                        IssueLabel(name = labelMap["name"] as? String ?: "")
                    }

                    val output = GithubUpdateIssueToolOutput(
                        number = (response["number"] as? Number)?.toInt() ?: 0,
                        url = response["html_url"] as? String ?: "",
                        title = response["title"] as? String ?: "",
                        state = response["state"] as? String ?: "",
                        labels = issueLabels,
                        updatedAt = response["updated_at"] as? String ?: ""
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