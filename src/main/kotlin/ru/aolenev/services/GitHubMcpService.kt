package ru.aolenev.services

class GitHubMcpService : CommonMcpService() {
    override val bearerToken = System.getenv("GITHUB_PAT")
}