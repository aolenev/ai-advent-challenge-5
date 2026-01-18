package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.WsSessionsStorage
import ru.aolenev.context
import ru.aolenev.model.*
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class CronJobService {
    private val claudeService: ClaudeService by context.instance()
    private val mapper: ObjectMapper by context.instance()
    private val config: Config by context.instance()
    private val log = LoggerFactory.getLogger(CronJobService::class.java)
    private var schedulerJob: Job? = null
    private val qaDeploymentJobs = ConcurrentHashMap<String, Job>()

    private val intervalSeconds: Long by lazy {
        config.getLong("ai-challenge.cron.fuelingStatIntervalSeconds")
    }

    private val qaDeploymentCheckIntervalSeconds: Long by lazy {
        config.getLong("ai-challenge.cron.qaDeploymentCheckIntervalSeconds")
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

    fun getTools(): List<McpTool> {
        return try {
            val scheduleQaDeploymentToolJson = this::class.java
                .getResource("/tool-templates/schedule_qa_deployment.json")!!
                .readText()

            val scheduleQaDeploymentTool = mapper.readValue(scheduleQaDeploymentToolJson, McpTool::class.java)

            val stopDeploymentSchedulingToolJson = this::class.java
                .getResource("/tool-templates/stop_deployment_scheduling.json")!!
                .readText()

            val stopDeploymentSchedulingTool = mapper.readValue(stopDeploymentSchedulingToolJson, McpTool::class.java)

            listOf(scheduleQaDeploymentTool, stopDeploymentSchedulingTool)
        } catch (e: Exception) {
            log.error("Error loading CronJob tools", e)
            throw e
        }
    }

    fun callTool(toolName: String, arguments: Map<String, Any>): McpToolsResponse? {
        return try {
            when (toolName) {
                "schedule_qa_deployment" -> {
                    val input = mapper.convertValue(arguments, ScheduleQaDeploymentToolInput::class.java)
                    scheduleQaDeployment(input.projectId)
                }
                "stop_deployment_scheduling" -> {
                    stopAllDeploymentSchedulers()
                }
                else -> {
                    log.warn("Unknown CronJob tool name: $toolName")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: $toolName"),
                            isError = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error calling CronJob tool: $toolName", e)
            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(
                    content = mapOf("error" to (e.message ?: "Unknown error occurred")),
                    isError = true
                )
            )
        }
    }

    private fun scheduleQaDeployment(projectId: String): McpToolsResponse {
        // Check if already scheduled
        if (qaDeploymentJobs.containsKey(projectId)) {
            log.info("QA deployment scheduler already running for project: $projectId")
            return McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(
                    content = ScheduleQaDeploymentToolOutput(
                        success = true,
                        message = "QA deployment scheduler already running for project $projectId",
                        projectId = projectId
                    ),
                    isError = false
                )
            )
        }

        // Start new scheduler job
        val job = GlobalScope.launch {
            log.info("QA deployment scheduler started for project: $projectId with interval: $qaDeploymentCheckIntervalSeconds seconds")
            while (isActive) {
                try {
                    log.info("Running QA deployment check for project: $projectId")
                    checkAndDeployQa(projectId)
                } catch (e: Exception) {
                    log.error("Error during QA deployment check for project: $projectId", e)
                }
                delay(qaDeploymentCheckIntervalSeconds.seconds)
            }
        }

        qaDeploymentJobs[projectId] = job

        return McpToolsResponse(
            jsonrpc = "2.0",
            id = 2,
            result = McpToolsResult(
                content = ScheduleQaDeploymentToolOutput(
                    success = true,
                    message = "QA deployment scheduler started for project $projectId with $qaDeploymentCheckIntervalSeconds second interval",
                    projectId = projectId
                ),
                isError = false
            )
        )
    }

    private suspend fun checkAndDeployQa(projectId: String) {
        val randomChatId = UUID.randomUUID().toString()
        val userPrompt = """
            Check if the latest GitLab pipeline for project $projectId has completed its "test" job successfully.
            If the test job has completed with status "success", then run the job with name "deploy-qa" if it's not completed.
            If deploy-qa job is already in "success" status then stop scheduling

            Use the following tools:
            1. gitlab_get_latest_pipeline - to get the latest pipeline
            2. gitlab_get_pipeline_jobs - to get jobs for that pipeline
            3. gitlab_run_job - to run the deploy-qa job if test is successful
            4. stop_deployment_scheduling - to stop all schedulers

            Only run deploy-qa if the test job status is "success".
        """.trimIndent()

        val aiRole = """
            You are an automated deployment assistant. Your task is to monitor GitLab pipelines and trigger QA deployments when tests complete successfully.
            Always check the current status before running any jobs.
        """.trimIndent()

        try {
            val response = claudeService.tooledChat(
                chatId = randomChatId,
                userPrompt = userPrompt,
                aiRoleOpt = aiRole,
                withRag = false,
                minSimilarity = BigDecimal(0.7)
            )

            if (response != null) {
                log.info("QA deployment check completed for project $projectId: ${response.response}")
            } else {
                log.warn("No response received from Claude service for project $projectId")
            }
        } catch (e: Exception) {
            log.error("Error in checkAndDeployQa for project $projectId", e)
        }
    }

    private fun stopAllDeploymentSchedulers(): McpToolsResponse {
        val count = qaDeploymentJobs.size
        qaDeploymentJobs.forEach { (projectId, job) ->
            job.cancel()
            log.info("QA deployment scheduler stopped for project: $projectId")
        }
        qaDeploymentJobs.clear()

        return McpToolsResponse(
            jsonrpc = "2.0",
            id = 2,
            result = McpToolsResult(
                content = StopDeploymentSchedulingToolOutput(
                    success = true,
                    message = "Successfully stopped $count QA deployment scheduler(s)",
                    stoppedCount = count
                ),
                isError = false
            )
        )
    }
}