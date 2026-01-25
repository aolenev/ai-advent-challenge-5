package ru.aolenev.repo

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.instance
import ru.aolenev.context
import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseInfo(
    val id: Long,
    val objectInfo: String
)

data class PurchaseInfoWithCategory(
    val id: Long,
    val objectInfo: String,
    val category: String
)

object PurchaseTable : LongIdTable(name = "purchases", columnName = "id") {
    private val db: Database by context.instance()

    val deliveryPlaces = text("delivery_places").nullable()
    val maxPrice = decimal("max_price", precision = 19, scale = 2).nullable()
    val updatedAt = datetime("updated_at").nullable()
    val objectInfo = text("object_info").nullable()
    val category = text("category").nullable()
    val createdAt = datetime("created_at")

    fun insertPurchase(
        deliveryPlaces: String?,
        maxPrice: BigDecimal,
        updatedAt: LocalDateTime,
        objectInfo: String?
    ) = transaction(db) {
        insert {
            it[PurchaseTable.deliveryPlaces] = deliveryPlaces
            it[PurchaseTable.maxPrice] = maxPrice
            it[PurchaseTable.updatedAt] = updatedAt.toKotlinLocalDateTime()
            it[PurchaseTable.objectInfo] = objectInfo
            it[createdAt] = LocalDateTime.now().toKotlinLocalDateTime()
        }
    }

    fun findAllWithObjectInfo(): List<PurchaseInfo> = transaction(db) {
        selectAll()
            .where { objectInfo.isNotNull() and category.isNull() }
            .map { row ->
                PurchaseInfo(
                    id = row[PurchaseTable.id].value,
                    objectInfo = row[objectInfo]!!
                )
            }
    }

    fun updateCategory(purchaseId: Long, newCategory: String): Boolean = transaction(db) {
        update({ PurchaseTable.id eq purchaseId }) {
            it[category] = newCategory
        } > 0
    }

    fun findAllWithCategory(): List<PurchaseInfoWithCategory> = transaction(db) {
        selectAll()
            .where { objectInfo.isNotNull() and category.isNotNull() }
            .map { row ->
                PurchaseInfoWithCategory(
                    id = row[PurchaseTable.id].value,
                    objectInfo = row[objectInfo]!!,
                    category = row[category]!!
                )
            }
    }
}
