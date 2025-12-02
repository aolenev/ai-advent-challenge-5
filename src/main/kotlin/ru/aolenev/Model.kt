package ru.aolenev

data class SimplePrompt(val prompt: String)

data class StructuredResponse(
    val currentDateTime: String,
    val response: String,
    val correlationId: String
)