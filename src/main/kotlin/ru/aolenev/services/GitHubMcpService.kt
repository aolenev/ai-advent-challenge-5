package ru.aolenev.services

class GitHubMcpService : CommonMcpService() {
    override val bearerToken = System.getenv("GITHUB_PAT")
    override val mcpServerUrl = "https://api.githubcopilot.com/mcp/"
}