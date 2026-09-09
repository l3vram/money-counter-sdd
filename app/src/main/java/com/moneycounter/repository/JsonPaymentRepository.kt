package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.Payment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class JsonPaymentRepository(private val context: Context) : PaymentRepository {

    private val fileName = "payments.json"

    override fun load(): List<Payment> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            PaymentJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(payments: List<Payment>) {
        try {
            val json = PaymentJson.toJson(payments)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}

object PaymentJson {

    private const val VERSION = 1

    fun toJson(payments: List<Payment>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (p in payments) {
            val item = JSONObject()
            item.put("id", p.id)
            item.put("at", p.at)
            item.put("receivableId", p.receivableId)
            item.put("debtorName", p.debtorName)
            item.put("amount", p.amount.toPlainString())
            item.put("currencyId", p.currencyId)
            array.put(item)
        }

        root.put("payments", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Payment> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("payments") ?: return emptyList()
        val result = mutableListOf<Payment>()

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val at = entry.optLong("at", 0L)
            val receivableId = entry.optString("receivableId", "")
            if (receivableId.isBlank()) continue
            val debtorName = entry.optString("debtorName", "")
            if (debtorName.isBlank()) continue

            val amount = runCatching { BigDecimal(entry.optString("amount", "")) }.getOrNull()
                ?: continue
            if (amount.signum() <= 0) continue

            val currencyId = entry.optString("currencyId", "")
            if (currencyId.isBlank()) continue

            result.add(
                Payment(
                    id = id,
                    at = at,
                    receivableId = receivableId,
                    debtorName = debtorName,
                    amount = amount.setScale(Money.SCALE),
                    currencyId = currencyId
                )
            )
        }

        return result.sortedByDescending { it.at }
    }
}
