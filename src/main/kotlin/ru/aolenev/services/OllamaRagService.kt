package ru.aolenev.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.repo.RagEmbeddingsTable
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

data class EmbeddingRequest(
    val model: String,
    val prompt: String
)

data class EmbeddingResponse(
    val embedding: List<Double>
)

data class CreateEmbeddings(
    val fileName: String?,
    val separator: List<String>? = null
)

class OllamaRagService {
    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()
    private val objectMapper: ObjectMapper by context.instance()

    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }

    private val ollamaUrl: String = config.getString("ai-challenge.ollama.baseUrl")
    private val model: String = config.getString("ai-challenge.ollama.model")

    fun splitByChunks(
        text: String,
        chunkSize: Int = 300,
        overlap: Int = 50,
        separator: List<String>? = null
    ): List<String> {
        // If custom separators are provided, use them and ignore overlap
        if (separator != null && separator.isNotEmpty()) {
            // Create a regex pattern that matches any of the separators
            val pattern = separator.joinToString("|") { Regex.escape(it) }
            return text.split(Regex(pattern)).filter { it.isNotBlank() }
        }

        // Original word-based splitting with overlap
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

    suspend fun getEmbeddings(chunks: List<String>): Map<String, List<Double>> {
        val embeddings = mutableMapOf<String, List<Double>>()
        var currentChunkNumber = 0
        chunks.forEach { chunk ->
            currentChunkNumber += 1
            log.info("Отправляем чанк $currentChunkNumber")
            val response = httpClient.post("$ollamaUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(model = model, prompt = chunk))
            }
            val embeddingResponse = response.body<EmbeddingResponse>()
            embeddings += chunk to embeddingResponse.embedding
            log.info("Чанк $currentChunkNumber обработан")
        }

        log.info("Обработка текста завершена")
        return embeddings
    }

    private fun parseTxtFile(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseJsonFile(inputStream: InputStream): String {
        val jsonNode = objectMapper.readTree(inputStream)
        return extractTextFromJson(jsonNode)
    }

    private fun extractTextFromJson(node: JsonNode): String {
        val textBuilder = StringBuilder()

        when {
            node.isTextual -> textBuilder.append(node.asText()).append(" ")
            node.isArray -> node.forEach { textBuilder.append(extractTextFromJson(it)) }
            node.isObject -> node.fields().forEach { (_, value) ->
                textBuilder.append(extractTextFromJson(value))
            }
        }

        return textBuilder.toString().trim()
    }

    private fun parsePdfFile(inputStream: InputStream): String {
        return PDDocument.load(inputStream).use { document ->
            val stripper = PDFTextStripper()
            stripper.getText(document)
        }
    }

    private fun parseFile(inputStream: InputStream, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        log.info("Parsing file: $fileName with extension: $extension")

        return when (extension) {
            "txt", "md" -> parseTxtFile(inputStream)
            "json" -> parseJsonFile(inputStream)
            "pdf" -> parsePdfFile(inputStream)
            else -> throw IllegalArgumentException("Unsupported file type: $extension. Supported types: .txt, .json, .pdf")
        }
    }

    suspend fun processAndStoreEmbeddings(
        inputFileName: String = "sample.txt",
        chunkSize: Int = 300,
        overlap: Int = 50,
        separator: List<String>? = null
    ) {
        val resourceStream = this::class.java.classLoader.getResourceAsStream(inputFileName)
            ?: throw IllegalArgumentException("File $inputFileName not found in resources")

        val text = parseFile(resourceStream, inputFileName)

        // When custom separator is used, overlap should be 0
        val actualOverlap = if (!separator.isNullOrEmpty()) 0 else overlap
        val chunks = splitByChunks(text, chunkSize, actualOverlap, separator)
        val embeddings = getEmbeddings(chunks)

        // Prepare output data
        val output = mapOf(
            "metadata" to mapOf(
                "model" to model,
                "chunkSize" to chunkSize,
                "overlap" to actualOverlap,
                "separator" to (separator?.joinToString(", ") ?: "whitespace"),
                "totalChunks" to chunks.size,
                "sourceFile" to inputFileName
            ),
        )
        log.info("Embeddings metadata: $output")

        embeddings.forEach { (chunk, embeddings) -> RagEmbeddingsTable.insertEmbedding(chunk, embeddings) }
    }
}