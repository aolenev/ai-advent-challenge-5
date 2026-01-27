package ru.aolenev.repo

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.context

data class Note(val id: Long, val note: String)

object NotesTable : LongIdTable(name = "notes", columnName = "id") {
    private val db: Database by context.instance()

    val note = text("note")

    fun addNote(noteText: String): Long = transaction(db) {
        insertAndGetId {
            it[note] = noteText
        }.value
    }

    fun removeNote(noteId: Long): Boolean = transaction(db) {
        deleteWhere { NotesTable.id eq noteId } > 0
    }

    fun listNotes(): List<Note> = transaction(db) {
        selectAll().map { row ->
            Note(
                id = row[NotesTable.id].value,
                note = row[note]
            )
        }
    }
}
