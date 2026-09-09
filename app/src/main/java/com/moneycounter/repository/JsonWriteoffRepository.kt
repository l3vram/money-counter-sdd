package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.InventoryWriteoff
import com.moneycounter.domain.Money
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class JsonWriteoffRepository(private val context: Context) : WriteoffRepository {

    private val fileName = "writeoffs.json"

    override fun load(): List<InventoryWriteoff> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            WriteoffJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(writeoffs: List<InventoryWriteoff>) {
        try {
            val json = WriteoffJson.toJson(writeoffs)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}

object WriteoffJson {

    private const val VERSION = 1

    fun toJson(writeoffs: List<InventoryWriteoff>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (w in writeoffs) {
            val item = JSONObject()
            item.put("id", w.id)
            item.put("at", w.at)
            item.put("productId", w.productId)
            item.put("name", w.name)
            item.put("unit", w.unit)
            item.put("quantity", w.quantity.toPlainString())
            item.put("unitPrice", w.unitPrice.toPlainString())
            item.put("lossValue", w.lossValue.toPlainString())
            item.put("currencyId", w.currencyId)
            item.put("reason", w.reason ?: "")
            array.put(item)
        }

        root.put("writeoffs", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<InventoryWriteoff> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("writeoffs") ?: return emptyList()
        val result = mutableListOf<InventoryWriteoff>()

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val at = entry.optLong("at", 0L)
            val productId = entry.optString("productId", "")
            if (productId.isBlank()) continue
            val name = entry.optString("name", "")
            val unit = entry.optString("unit", "")
            if (name.isBlank() || unit.isBlank()) continue

            val quantity = runCatching { BigDecimal(entry.optString("quantity", "")) }.getOrNull()
                ?: continue
            if (quantity.signum() <= 0) continue

            val unitPrice = runCatching { BigDecimal(entry.optString("unitPrice", "")) }.getOrNull()
                ?: continue

            val lossValue = runCatching { BigDecimal(entry.optString("lossValue", "")) }.getOrNull()
                ?: unitPrice.multiply(quantity).setScale(Money.SCALE)

            val currencyId = entry.optString("currencyId", "")
            if (currencyId.isBlank()) continue

            val reason = entry.optString("reason", "").ifBlank { null }

            result.add(
                InventoryWriteoff(
                    id = id,
                    at = at,
                    productId = productId,
                    name = name,
                    unit = unit,
                    quantity = quantity.setScale(Money.SCALE),
                    unitPrice = unitPrice.setScale(Money.SCALE),
                    lossValue = lossValue.setScale(Money.SCALE),
                    currencyId = currencyId,
                    reason = reason
                )
            )
        }

        return result.sortedByDescending { it.at }
    }
}
