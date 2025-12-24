package ru.aolenev.repo

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.context
import java.math.BigDecimal

class VectorColumnType(private val dimension: Int) : ColumnType<List<Double>>() {
    override fun sqlType(): String = "VECTOR($dimension)"

    override fun valueFromDB(value: Any): List<Double> {
        return when (value) {
            is String -> parseVector(value)
            is org.postgresql.util.PGobject -> parseVector(value.value ?: "[]")
            else -> emptyList()
        }
    }

    override fun notNullValueToDB(value: List<Double>): Any {
        return value.toDoubleArray()
    }

    private fun parseVector(value: String): List<Double> {
        return value.trim('[', ']')
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.trim().toDouble() }
    }
}

fun Table.vector(name: String, dimension: Int): Column<List<Double>> {
    return registerColumn(name, VectorColumnType(dimension))
}

object RagEmbeddingsTable : LongIdTable(name = "rag_embeddings", columnName = "id") {
    private val db: Database by context.instance()

    val chunk = text("chunk")
    val embedding = vector("embedding", 768)

    fun insertEmbedding(chunkText: String, embeddingVector: List<Double>) = transaction(db) {
        insert {
            it[chunk] = chunkText
            it[embedding] = embeddingVector
        }
    }

    /**
     * Finds all chunks with cosine similarity > 0.7 to the given embedding vector.
     * Uses pgvector's cosine distance operator (<=>).
     * Cosine distance = 1 - cosine similarity, so similarity > 0.7 means distance < 0.3.
     *
     * @param queryEmbedding The embedding vector to compare against
     * @return List of RagEmbedding with similarity scores, ordered by similarity (highest first)
     */
    fun findSimilarChunks(queryEmbedding: List<Double>, minSimilarity: BigDecimal): List<String> = transaction(db) {
        val sql = """
            SELECT chunk,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM rag_embeddings
            WHERE 1 - (embedding <=> ?::vector) > ?
            ORDER BY embedding <=> ?::vector
        """.trimIndent()

        exec(
            stmt = sql,
            args = listOf(
                Pair(VectorColumnType(768), queryEmbedding),
                Pair(VectorColumnType(768), queryEmbedding),
                Pair(DecimalColumnType(5, 2), minSimilarity),
                Pair(VectorColumnType(768), queryEmbedding)
            ),
            explicitStatementType = StatementType.SELECT
        ) { rs ->
            val results = mutableListOf<String>()
            while (rs.next()) {
                results += rs.getString("chunk")
            }
            results
        } ?: emptyList()
    }
}