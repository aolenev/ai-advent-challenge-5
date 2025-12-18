package ru.aolenev.repo

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.context
import java.math.BigDecimal

object FuelingStatTable : LongIdTable(name = "fueling_stat", columnName = "id") {
    private val db: Database by context.instance()

    val createdAt = datetime("created_at")
    val fuelingCount = decimal("fueling_count", precision = 19, scale = 2)
    val fuelingLiters = decimal("fueling_liters", precision = 19, scale = 2)
    val fromMs = long("from_ms")
    val toMs = long("to_ms").nullable()

    fun saveStat(
        fuelingCount: BigDecimal,
        fuelingLiters: BigDecimal,
        fromMs: Long,
        toMs: Long?
    ) = transaction(db) {
        insert {
            it[createdAt] = java.time.LocalDateTime.now().toKotlinLocalDateTime()
            it[FuelingStatTable.fuelingCount] = fuelingCount
            it[FuelingStatTable.fuelingLiters] = fuelingLiters
            it[FuelingStatTable.fromMs] = fromMs
            it[FuelingStatTable.toMs] = toMs
        }
    }
}