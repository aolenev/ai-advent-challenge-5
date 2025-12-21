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
    @JsonProperty("inputSchema") val inputSchema: Any,
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