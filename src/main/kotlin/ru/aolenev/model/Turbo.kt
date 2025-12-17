package ru.aolenev.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class FuelingsResponse(
    @JsonProperty("fuelings") val fuelings: List<Fueling>
)

data class Fueling(
    @JsonProperty("fuelingId") val fuelingId: String,
    @JsonProperty("partnerFuelingId") val partnerFuelingId: String?,
    @JsonProperty("status") val status: String,
    @JsonProperty("reason") val reason: String?,
    @JsonProperty("paymentType") val paymentType: String,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("actualAmount") val actualAmount: BigDecimal?,
    @JsonProperty("sum") val sum: BigDecimal?,
    @JsonProperty("actualSum") val actualSum: BigDecimal?,
    @JsonProperty("partnerFuelPrice") val partnerFuelPrice: BigDecimal?,
    @JsonProperty("discountFuelPrice") val discountFuelPrice: BigDecimal?,
    @JsonProperty("fuelType") val fuelType: String?,
    @JsonProperty("gasStationId") val gasStationId: String,
    @JsonProperty("gasPumpId") val gasPumpId: String?,
    @JsonProperty("refuelingGunId") val refuelingGunId: String?,
    @JsonProperty("createdAt") val createdAt: Long,
    @JsonProperty("updatedAt") val updatedAt: Long,
    @JsonProperty("extra") val extra: FuelingExtra?
)

data class FuelingExtra(
    @JsonProperty("factFuelType") val factFuelType: String?,
    @JsonProperty("fuelTypeName") val fuelTypeName: String?,
    @JsonProperty("locationId") val locationId: String?,
    @JsonProperty("brand") val brand: String?,
    @JsonProperty("estimatedCost") val estimatedCost: BigDecimal?,
    @JsonProperty("extraHold") val extraHold: BigDecimal?,
    @JsonProperty("totalSum") val totalSum: BigDecimal?,
    @JsonProperty("serviceFee") val serviceFee: BigDecimal?,
    @JsonProperty("serviceFeeRate") val serviceFeeRate: BigDecimal?
)