package ru.aolenev.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal


data class McpInitializeRequest(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("method") val method: String,
    @JsonProperty("params") val params: McpInitializeParams
)

data class McpInitializeParams(
    @JsonProperty("protocolVersion") val protocolVersion: String,
    @JsonProperty("capabilities") val capabilities: McpClientCapabilities,
    @JsonProperty("clientInfo") val clientInfo: McpClientInfo
)

data class McpClientCapabilities(
    @JsonProperty("roots") val roots: McpRootsCapability,
    @JsonProperty("sampling") val sampling: Map<String, Any>,
    @JsonProperty("elicitation") val elicitation: Map<String, Any>
)

data class McpRootsCapability(
    @JsonProperty("listChanged") val listChanged: Boolean
)

data class McpClientInfo(
    @JsonProperty("name") val name: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("version") val version: String
)

data class McpInitializedNotification(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("method") val method: String
)

data class McpToolsRequest(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("method") val method: String,
    @JsonProperty("params") val params: McpToolsParams
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpToolsParams(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("arguments") val arguments: Map<String, Any>? = null
)

data class McpToolsResponse(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("result") val result: McpToolsResult
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpToolsResult(
    @JsonProperty("tools") val tools: List<McpTool>? = null,
    @JsonProperty("content") val content: Any? = null,
    @JsonProperty("isError") val isError: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpTool(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("inputSchema") val inputSchema: Any?,
    @JsonProperty("outputSchema") val outputSchema: Any?
)

data class CompletedFuelingsToolInput(
    @JsonProperty("from") val from: Long,
    @JsonProperty("to") val to: Long? = null
)

data class CompletedFuelingsToolOutput(
    @JsonProperty("count") val count: Int,
    @JsonProperty("volume") val volume: BigDecimal
)

data class SaveFuelingsStatToolInput(
    @JsonProperty("count") val count: Int,
    @JsonProperty("volume") val volume: BigDecimal,
    @JsonProperty("from") val from: Long,
    @JsonProperty("to") val to: Long? = null
)

data class SaveFuelingsStatToolOutput(
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("message") val message: String
)

data class ExecuteShellCommandToolInput(
    @JsonProperty("command") val command: String,
    @JsonProperty("commandParameters") val commandParameters: List<String>,
    @JsonProperty("workingDirectory") val workingDirectory: String? = null
)

data class ExecuteShellCommandToolOutput(
    @JsonProperty("stdout") val stdout: String
)

data class GithubPullRequestInfo(
    @JsonProperty("state") val state: String,
    @JsonProperty("number") val number: Int
)

data class GithubIssueInfo(
    @JsonProperty("number") val number: Int,
    @JsonProperty("title") val title: String,
    @JsonProperty("state") val state: String,
    @JsonProperty("labels") val labels: List<IssueLabel>,
    @JsonProperty("body") val body: String? = null,
    @JsonProperty("created_at") val createdAt: String? = null,
    @JsonProperty("updated_at") val updatedAt: String? = null
)

data class IssueLabel(
    @JsonProperty("name") val name: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GithubListIssuesToolInput(
    @JsonProperty("state") val state: String? = "all",
    @JsonProperty("labels") val labels: List<String>? = null,
    @JsonProperty("priority") val priority: String? = null
)

data class GithubListIssuesToolOutput(
    @JsonProperty("issues") val issues: List<GithubIssueInfo>,
    @JsonProperty("total_count") val totalCount: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GithubCreateIssueToolInput(
    @JsonProperty("title") val title: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("priority") val priority: String? = null,
    @JsonProperty("labels") val labels: List<String>? = null
)

data class GithubCreateIssueToolOutput(
    @JsonProperty("number") val number: Int,
    @JsonProperty("url") val url: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("state") val state: String,
    @JsonProperty("labels") val labels: List<IssueLabel>,
    @JsonProperty("created_at") val createdAt: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GithubUpdateIssueToolInput(
    @JsonProperty("issue_number") val issueNumber: Int,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("state") val state: String? = null,
    @JsonProperty("priority") val priority: String? = null,
    @JsonProperty("labels") val labels: List<String>? = null
)

data class GithubUpdateIssueToolOutput(
    @JsonProperty("number") val number: Int,
    @JsonProperty("url") val url: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("state") val state: String,
    @JsonProperty("labels") val labels: List<IssueLabel>,
    @JsonProperty("updated_at") val updatedAt: String
)

// GitLab MCP Tool Models

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitlabGetLatestPipelineToolInput(
    @JsonProperty("project_id") val projectId: String
)

data class GitlabPipelineInfo(
    @JsonProperty("id") val id: Int,
    @JsonProperty("project_id") val projectId: Int,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("ref") val ref: String? = null,
    @JsonProperty("created_at") val createdAt: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitlabGetPipelineJobsToolInput(
    @JsonProperty("project_id") val projectId: String,
    @JsonProperty("pipeline_id") val pipelineId: Int
)

data class GitlabJobInfo(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("stage") val stage: String? = null,
    @JsonProperty("created_at") val createdAt: String? = null
)

data class GitlabGetPipelineJobsToolOutput(
    @JsonProperty("jobs") val jobs: List<GitlabJobInfo>,
    @JsonProperty("total_count") val totalCount: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GitlabRunJobToolInput(
    @JsonProperty("project_id") val projectId: String,
    @JsonProperty("job_id") val jobId: Int
)

data class GitlabRunJobToolOutput(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("stage") val stage: String? = null,
    @JsonProperty("started_at") val startedAt: String? = null,
    @JsonProperty("web_url") val webUrl: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScheduleQaDeploymentToolInput(
    @JsonProperty("project_id") val projectId: String
)

data class ScheduleQaDeploymentToolOutput(
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("message") val message: String,
    @JsonProperty("project_id") val projectId: String? = null
)

data class StopDeploymentSchedulingToolOutput(
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("message") val message: String,
    @JsonProperty("stopped_count") val stoppedCount: Int
)