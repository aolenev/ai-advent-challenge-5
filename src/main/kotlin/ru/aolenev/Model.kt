package ru.aolenev

import java.math.BigDecimal

data class SimplePrompt(
    val prompt: String,
    val systemPrompt: String? = null,
    val temperature: BigDecimal? = null
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