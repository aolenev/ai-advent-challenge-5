package ru.aolenev.services

import com.typesafe.config.Config
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.WsSessionsStorage
import ru.aolenev.context
import java.math.BigDecimal
import java.util.*
import kotlin.time.Duration.Companion.seconds

class CronJobService {
    private val claudeService: ClaudeService by context.instance()
    private val config: Config by context.instance()
    private val log = LoggerFactory.getLogger(CronJobService::class.java)
    private var schedulerJob: Job? = null

    private val intervalSeconds: Long by lazy {
        config.getLong("ai-challenge.cron.fuelingStatIntervalSeconds")
    }

    suspend fun pushFuelingStat() {
        val randomChatId = UUID.randomUUID().toString()
        val userPrompt = "How much fuel did I consume since 15th of November 2025?"
        val aiRole = "You are personal assistant. Do not provide me data about 2024 year, you can only operate with data from 2025 year"

        val response = claudeService.tooledChat(
            chatId = randomChatId,
            userPrompt = userPrompt,
            aiRoleOpt = aiRole,
            withRag = false,
            minSimilarity = BigDecimal(0.7)
        )?.response

        if (response != null) {
            val sessions = WsSessionsStorage.getAllSessions()
            log.info("Sending fueling statistics to ${sessions.size} websocket sessions")

            sessions.forEach { (chatId, session) ->
                try {
                    session.send(Frame.Text(response))
                    log.info("Sent fueling statistics to chatId: $chatId")
                } catch (e: Exception) {
                    log.error("Failed to send fueling statistics to chatId: $chatId", e)
                }
            }
        } else {
            log.warn("No response received from Claude service")
        }
    }

    fun startScheduler(scope: CoroutineScope) {
        schedulerJob = scope.launch {
            log.info("Fueling statistics scheduler started with interval: $intervalSeconds seconds")
            while (isActive) {
                try {
                    log.info("Running scheduled fueling statistics report")
                    pushFuelingStat()
                    log.info("Fueling statistics report completed successfully")
                } catch (e: Exception) {
                    log.error("Error executing scheduled fueling statistics report", e)
                }
                delay(intervalSeconds.seconds)
            }
        }
    }

    fun stopScheduler() {
        schedulerJob?.cancel()
        log.info("Fueling statistics scheduler stopped")
    }
}