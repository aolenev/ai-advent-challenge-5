package ru.aolenev.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class SinglePrompt(
    val prompt: String,
    val systemPrompt: String? = null,
    val temperature: BigDecimal? = null,
    val gptService: String? = "claude",
    val model: String? = null,
    val maxTokens: Int = 1000
)

data class ResponseWithUsageDetails(
    val response: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: String
)

data class StructuredResponse(
    val currentDateTime: String,
    val response: String,
    val correlationId: String
)

data class ResponseWithFinishFlag(
    val response: String,
    val isFinished: Boolean
)

data class ResponseWithHistory(
    val response: String,
    val usage: TokenUsage,
    val messageHistory: List<Map<String, Any>>
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

data class ChatPrompt(
    val chatId: String,
    val prompt: String,
    val systemPrompt: String?,
    val withRag: Boolean = false,
    val minSimilarity: BigDecimal = BigDecimal(0.7),
    val model: String? = null,
    val modelType: String = "cloud",
    val temperature: BigDecimal? = null
)

data class HelpRequest(
    val question: String,
    val minSimilarity: BigDecimal = BigDecimal(0.7)
)

data class LocalModelPrompt(
    val prompt: String,
    val model: String = "qwen2.5:3b"
)

data class FiniteChatResponse(
    val response: String,
    val isChatFinished: Boolean
)


data class Message(
    val id: Long,
    val messageContent: String,
    val chatId: String,
    val messageType: MessageType,
    val createdAt: LocalDateTime
)

enum class MessageType {
    USER,
    ASSISTANT,
    SUMMARY
}

fun MessageType.toClaudeRole(): String = when (this) {
    MessageType.USER, MessageType.SUMMARY -> "user"
    MessageType.ASSISTANT -> "assistant"
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClaudeMcpTool(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("input_schema") val inputSchema: Any?
)

fun McpTool.toClaude(): ClaudeMcpTool = ClaudeMcpTool(
    name = name,
    description = description,
    inputSchema = inputSchema
)