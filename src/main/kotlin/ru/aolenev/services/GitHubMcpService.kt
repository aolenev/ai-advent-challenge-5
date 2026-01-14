package ru.aolenev.services

import ru.aolenev.model.McpTool

class GitHubMcpService : CommonMcpService() {
    override val bearerToken = System.getenv("GITHUB_PAT")
    override val mcpServerUrl = "https://api.githubcopilot.com/mcp/"

    override suspend fun getTools(mcpSessionId: String): List<McpTool>? {
        return super.getTools(mcpSessionId)?.let { tools ->
            tools.filter {
                listOf("list_pull_requests", "pull_request_read", "get_file_contents").contains(it.name)
            }
        }
    }
}