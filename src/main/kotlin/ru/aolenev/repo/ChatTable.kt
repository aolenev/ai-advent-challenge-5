package ru.aolenev.repo

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.context

object ChatTable : Table("chats") {
    private val db: Database by context.instance()

    private val id = text("id")
    private val aiRole = text("ai_role")

    fun findAiRole(chatId: String): String? = transaction(db) {
        selectAll()
            .where(ChatTable.id eq chatId)
            .map { it[aiRole] }
            .firstOrNull()
    }

    fun addChat(chatId: String, aiRole: String) = transaction(db) {
        insert {
            it[id] = chatId
            it[ChatTable.aiRole] = aiRole
        }
    }

    override val primaryKey = PrimaryKey(id, name = "id")
}