package ru.aolenev

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import kotlin.jvm.optionals.getOrNull

class ClaudeClient {
    fun sendPrompt(prompt: String): String? {
        val client = AnthropicOkHttpClient.fromEnv()
        val params = MessageCreateParams.builder()
            .model("claude-sonnet-4-5-20250929")
            .maxTokens(1000)
            .addUserMessage(prompt)
            .build()

        return client.messages().create(params).content().first().text().map { it.text() }.getOrNull()
    }
}