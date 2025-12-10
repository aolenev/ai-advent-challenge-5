package ru.aolenev

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

data class ChatPrompt(
    val chatId: String,
    val prompt: String,
    val systemPrompt: String?
)

data class FiniteChatResponse(
    val response: String,
    val isChatFinished: Boolean
)