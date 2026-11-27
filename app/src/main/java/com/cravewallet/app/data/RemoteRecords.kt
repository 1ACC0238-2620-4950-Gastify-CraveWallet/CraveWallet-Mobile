package com.cravewallet.app.data

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

data class DeliveryExpenseRecord(
    val id: String,
    val merchant: String,
    val amount: BigDecimal,
    val category: String,
    val date: LocalDate,
) {
    fun correctionBody(): JSONObject = JSONObject().put("merchant", merchant).put("amount", amount)
        .put("category", category).put("expenseDate", date.toString())

    companion object {
        fun fromJson(value: JSONObject) = DeliveryExpenseRecord(
            value.getString("id"), value.getString("merchant"), BigDecimal(value.getString("amount")),
            value.getString("category"), LocalDate.parse(value.getString("expenseDate")),
        )
    }
}

data class ConversionCycle(val billingDate: LocalDate, val rate: BigDecimal, val amountPen: BigDecimal)
enum class RateDirection { UP, DOWN, UNCHANGED }
data class ExchangeRateVariation(val percentChange: BigDecimal, val direction: RateDirection)
data class ExchangeRateHistory(val cycles: List<ConversionCycle>, val variation: ExchangeRateVariation?) {
    companion object {
        fun fromJson(value: JSONObject): ExchangeRateHistory {
            val records = value.getJSONArray("cycles")
            val cycles = (0 until records.length()).map { index ->
                val cycle = records.getJSONObject(index)
                ConversionCycle(LocalDate.parse(cycle.getString("billingDate")),
                    BigDecimal(cycle.getString("rate")), BigDecimal(cycle.getString("amountPen")))
            }
            val variation = value.optJSONObject("variation")?.let {
                ExchangeRateVariation(BigDecimal(it.getString("percentChange")), RateDirection.valueOf(it.getString("direction")))
            }
            return ExchangeRateHistory(cycles, variation)
        }
    }
}

/** Typed contracts shared by the delivery screen and subscription detail. */
class RemoteRecordsApi(private val api: BackendApi) {
    suspend fun deliveryExpenses(year: Int, month: Int): List<DeliveryExpenseRecord> {
        val items = api.request("GET", "/api/v1/delivery-expenses?year=$year&month=$month").getJSONArray("items")
        return (0 until items.length()).map { DeliveryExpenseRecord.fromJson(items.getJSONObject(it)) }
    }

    suspend fun correctDeliveryExpense(expense: DeliveryExpenseRecord): DeliveryExpenseRecord =
        DeliveryExpenseRecord.fromJson(api.request("PUT", "/api/v1/delivery-expenses/${expense.id}", expense.correctionBody()))

    suspend fun deleteDeliveryExpense(id: String) { api.request("DELETE", "/api/v1/delivery-expenses/$id") }

    suspend fun exchangeRateHistory(id: String): ExchangeRateHistory =
        ExchangeRateHistory.fromJson(api.request("GET", "/api/v1/subscriptions/$id/exchange-rate-history"))
}
