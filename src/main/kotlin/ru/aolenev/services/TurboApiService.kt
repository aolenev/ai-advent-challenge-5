package ru.aolenev.services

import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.Fueling
import ru.aolenev.model.FuelingsResponse

class TurboApiService {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val httpClient: HttpClient by context.instance()
    private val config: Config by context.instance()

    private val baseUrl: String by lazy {
        config.getString("ai-challenge.turbo.baseUrl")
    }

    private val apiKey: String by lazy {
        config.getString("ai-challenge.turbo.apiKey")
    }

    private val partnerCode: String by lazy {
        config.getString("ai-challenge.turbo.partnerCode")
    }

    /**
     * Fetch fuelings by HTTP from remote service Turbo (API description: https://docs.google.com/document/d/1sYM5Zlih_sIoy8WL28pCZ6Mo3ZWjgh5Grs7UogM-MDI/edit?tab=t.0#heading=h.f7jievuch5r6)
     * Return list of fuelings
     */
    suspend fun getFuelings(from: Long, to: Long?): List<Fueling>? {
        return try {
            log.info("Fetching fuelings from Turbo API: from=$from, to=$to")

            val response = httpClient.get("$baseUrl/partners/v1.2/$partnerCode/fuelings") {
                header("Api-Key", apiKey)
                parameter("from", from)
                to?.let{ parameter("to", to) }
            }

            if (response.status == HttpStatusCode.OK) {
                val fuelingsResponse = response.body<FuelingsResponse>()
                log.info("Successfully fetched ${fuelingsResponse.fuelings.size} fuelings")
                fuelingsResponse.fuelings
            } else {
                log.error("Failed to fetch fuelings: $response")
                null
            }
        } catch (e: Exception) {
            log.error("Error fetching fuelings from Turbo API", e)
            null
        }
    }
}