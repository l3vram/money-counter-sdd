package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.StockItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

/** Pure JSON serialization for [StockItem] — testable without Android.
 *  Version 1. Tolerant read: malformed rows are skipped, unsupported versions
 *  return an empty list. No id-dedup on read — every valid row is kept and
 *  branch filtering happens in the read path. */
object StockJson {

    private const val VERSION = 1

    fun toJson(items: List<StockItem>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (item in items) {
            val row = JSONObject()
            row.put("id", item.id)
            row.put("organizationId", item.organizationId)
            row.put("branchId", item.branchId)
            row.put("productId", item.productId)
            row.put("quantity", item.quantity.toPlainString())
            row.put("updatedAt", item.updatedAt)
            array.put(row)
        }
        root.put("stock", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<StockItem> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("stock") ?: return emptyList()
        val items = mutableListOf<StockItem>()

        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val id = row.optString("id", "")
            val organizationId = row.optString("organizationId", "")
            val branchId = row.optString("branchId", "")
            val productId = row.optString("productId", "")
            val quantity = runCatching { BigDecimal(row.optString("quantity", "0")).setScale(Money.SCALE) }
                .getOrNull() ?: continue
            if (id.isBlank() || organizationId.isBlank() || branchId.isBlank() || productId.isBlank()) continue
            if (quantity.signum() < 0) continue

            items.add(
                StockItem(
                    id = id,
                    organizationId = organizationId,
                    branchId = branchId,
                    productId = productId,
                    quantity = quantity,
                    updatedAt = row.optLong("updatedAt", 0L)
                )
            )
        }

        return items
    }
}

class JsonStockRepository(private val context: Context) : StockRepository {

    private val fileName = "stock.json"

    override fun load(): List<StockItem> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()

            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()

            StockJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(items: List<StockItem>) {
        try {
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(StockJson.toJson(items))
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other repositories
        }
    }
}