package ru.aolenev.repo

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.Message
import ru.aolenev.MessageType
import ru.aolenev.context

object MessageTable : LongIdTable(name = "messages", columnName = "id") {
    private val db: Database by context.instance()
    private val mapper: ObjectMapper by context.instance()

    private val messageContent = text("message_content")
    private val chatId = text("chat_id")
    private val messageType = text("message_type")
    private val createdAt = datetime("created_at")

    fun readLastMessages(chatId: String): List<Message> = transaction(db) {
        val sql = """
            SELECT *
            FROM messages m
            LEFT JOIN (
              SELECT max(created_at) AS created_at
              FROM messages
              WHERE message_type = 'SUMMARY') AS summary ON TRUE
            WHERE m.chat_id = ?
              AND m.created_at >= summary.created_at
              ORDER BY m.created_at
        """.trimIndent()
        exec(
            stmt = sql,
            args = listOf(Pair(TextColumnType(), chatId)),
            explicitStatementType = StatementType.SELECT,
            transform = { rs ->
                val messages = mutableListOf<Message>()
                while(rs.next()) {
                    messages.add(
                        Message(
                            id = rs.getLong("id"),
                            chatId = rs.getString("chat_id"),
                            messageType =  MessageType.valueOf(rs.getString("message_type")),
                            messageContent = rs.getString("message_content"),
                            createdAt = rs.getTimestamp("created_at").toLocalDateTime().toKotlinLocalDateTime()
                        )
                    )
                }
            }
        )
        selectAll()
            .where { MessageTable.chatId eq chatId }
            .orderBy(createdAt)
            .map { it.toMessage() }
    }

    fun readAllMessages(): List<Message> = transaction(db) {
        selectAll()
            .orderBy(createdAt)
            .map { it.toMessage() }
    }

    fun saveMessage(messageContent: Any, chatId: String, messageType: MessageType) = transaction(db) {
        insert {
            it[MessageTable.messageContent] = messageContent.toString()
            it[MessageTable.chatId] = chatId
            it[MessageTable.messageType] = messageType.name
            it[createdAt] = java.time.LocalDateTime.now().toKotlinLocalDateTime()
        }
    }

    private fun ResultRow.toMessage(): Message {
        return Message(
            id = this[id].value,
            messageContent = this[messageContent],
            chatId = this[chatId],
            messageType = MessageType.valueOf(this[messageType]),
            createdAt = this[createdAt]
        )
    }
}