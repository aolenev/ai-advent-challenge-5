package ru.aolenev.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Serializable
data class EmbeddingRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class EmbeddingResponse(
    val embedding: List<Double>
)

class OllamaRagService {
    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()
    private val objectMapper: ObjectMapper by context.instance()

    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val ollamaUrl: String = config.getString("ai-challenge.ollama.baseUrl")
    private val model: String = config.getString("ai-challenge.ollama.model")

    fun splitByChunks(text: String, chunkSize: Int = 300, overlap: Int = 50): List<String> {
        val chunks = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))

        var start = 0
        var end = 0

        while (end <= words.size) {
            end = start + chunkSize
            chunks += words.subList(start, min(end, words.size)).joinToString(separator = " ")
            start = max(0, end - overlap)
        }

        return chunks
    }

    private suspend fun sendChunksToOllama(chunks: List<String>): List<List<Double>> {
        val embeddings = mutableListOf<List<Double>>()
        var currentChunkNumber = 0
        chunks.forEach { chunk ->
            currentChunkNumber += 1
            log.info("Отправляем чанк $currentChunkNumber")
            val response = httpClient.post("$ollamaUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(model = model, prompt = chunk))
            }
            val embeddingResponse = response.body<EmbeddingResponse>()
            embeddings.add(embeddingResponse.embedding)
            log.info("Чанк $currentChunkNumber обработан")
        }

        log.info("Обработка текста завершена")
        return embeddings
    }

    suspend fun processAndStoreEmbeddings(
        inputFileName: String = "sample.txt",
        outputFileName: String = "embeddings.json",
        chunkSize: Int = 300,
        overlap: Int = 50
    ) {
        val resourceStream = this::class.java.classLoader.getResourceAsStream(inputFileName)
            ?: throw IllegalArgumentException("File $inputFileName not found in resources")

        val text = resourceStream.bufferedReader().use { it.readText() }

        val chunks = splitByChunks(text, chunkSize, overlap)
        val embeddings = sendChunksToOllama(chunks)

        // Prepare output data
        val output = mapOf(
            "metadata" to mapOf(
                "model" to model,
                "chunkSize" to chunkSize,
                "overlap" to overlap,
                "totalChunks" to chunks.size
            ),
            "embeddings" to embeddings
        )

        val outputFile = File("src/main/resources/$outputFileName")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, output)
    }
}