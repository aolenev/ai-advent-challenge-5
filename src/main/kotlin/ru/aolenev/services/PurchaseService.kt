package ru.aolenev.services

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.repo.PurchaseTable
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PurchaseResponse(
    @JsonProperty("delivery_places") val deliveryPlaces: List<String>,
    @JsonProperty("max_price") val maxPrice: BigDecimal,
    @JsonProperty("updated_at") val updatedAt: String,
    @JsonProperty("object_info") val objectInfo: String?
)

class PurchaseService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val httpClient: HttpClient by context.instance()
    private val baseUrl = "https://v2test.gosplan.info/fz44"

    suspend fun loadPurchases(): Int {
        return try {
            val purchases = httpClient.get("$baseUrl/purchases?limit=21")
                .body<List<PurchaseResponse>>()
                .filter { it.objectInfo != null }

            var count = 0

            for (purchase in purchases) {
                val updatedAt = parseDateTime(purchase.updatedAt)

                PurchaseTable.insertPurchase(
                    deliveryPlaces = purchase.deliveryPlaces.firstOrNull(),
                    maxPrice = purchase.maxPrice,
                    updatedAt = updatedAt,
                    objectInfo = purchase.objectInfo
                )
                count++
            }

            log.info("Successfully loaded and stored $count purchases")
            count
        } catch (e: Exception) {
            log.error("Error loading purchases", e)
            throw e
        }
    }

    private fun parseDateTime(dateStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            log.warn("Could not parse date: $dateStr")
            throw e
        }
    }
}
