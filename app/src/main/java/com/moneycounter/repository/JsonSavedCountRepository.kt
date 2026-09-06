package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.SavedCountItem
import com.moneycounter.domain.SavedProductItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class JsonSavedCountRepository(private val context: Context) : SavedCountRepository {

    private val fileName = "count_history.json"

    override fun load(): List<SavedCount> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            SavedCountJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(history: List<SavedCount>) {
        try {
            val json = SavedCountJson.toJson(history)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}

object SavedCountJson {

    private const val VERSION = 2

    fun toJson(history: List<SavedCount>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val historyArray = JSONArray()
        for (saved in history) {
            val item = JSONObject()
            item.put("id", saved.id)
            item.put("savedAt", saved.savedAt)
            item.put("targetAmount", saved.targetAmount.toPlainString())
            item.put("currency", saved.currency)

            val itemsArray = JSONArray()
            for (entry in saved.items) {
                val ei = JSONObject()
                ei.put("denominationValue", entry.denominationValue)
                ei.put("quantity", entry.quantity)
                ei.put("subtotal", entry.subtotal.toPlainString())
                itemsArray.put(ei)
            }
            item.put("items", itemsArray)

            val productsArray = JSONArray()
            for (p in saved.products) {
                val pi = JSONObject()
                pi.put("name", p.name)
                pi.put("unit", p.unit)
                pi.put("quantity", p.quantity.toPlainString())
                pi.put("unitPrice", p.unitPrice.toPlainString())
                pi.put("surcharge", p.surcharge.toPlainString())
                pi.put("subtotal", p.subtotal.toPlainString())
                productsArray.put(pi)
            }
            item.put("products", productsArray)
            historyArray.put(item)
        }

        root.put("history", historyArray)
        return root.toString(2)
    }

    fun fromJson(json: String): List<SavedCount> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION && version != 1) return emptyList()

        val historyArray = root.optJSONArray("history") ?: return emptyList()
        val result = mutableListOf<SavedCount>()

        for (i in 0 until historyArray.length()) {
            val entry = historyArray.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val savedAt = entry.optLong("savedAt", 0L)

            val targetStr = entry.optString("targetAmount", "")
            val target = runCatching { BigDecimal(targetStr).setScale(Money.SCALE) }.getOrNull()
            if (target == null || target.signum() <= 0) continue

            val items = mutableListOf<SavedCountItem>()
            val itemsArray = entry.optJSONArray("items")
            if (itemsArray != null) {
                for (j in 0 until itemsArray.length()) {
                    val it = itemsArray.optJSONObject(j) ?: continue
                    val denomValue = it.optLong("denominationValue", 0)
                    val quantity = it.optLong("quantity", 0)
                    if (denomValue <= 0 || quantity <= 0) continue
                    val subtotalStr = it.optString("subtotal", "")
                    val subtotal = runCatching { BigDecimal(subtotalStr) }.getOrNull()
                        ?: BigDecimal.valueOf(denomValue * quantity).setScale(Money.SCALE)
                    items.add(SavedCountItem(denomValue, quantity, subtotal))
                }
            }

            val products = mutableListOf<SavedProductItem>()
            val productsArray = entry.optJSONArray("products")
            if (productsArray != null) {
                for (j in 0 until productsArray.length()) {
                    val pi = productsArray.optJSONObject(j) ?: continue
                    val pName = pi.optString("name", "")
                    val pUnit = pi.optString("unit", "")
                    if (pName.isBlank() || pUnit.isBlank()) continue
                    val pQty = runCatching { BigDecimal(pi.optString("quantity", "")) }.getOrNull() ?: continue
                    if (pQty.signum() < 0) continue
                    val pUnitPrice = runCatching { BigDecimal(pi.optString("unitPrice", "")) }.getOrNull()
                        ?: continue
                    val pSurcharge = runCatching { BigDecimal(pi.optString("surcharge", "0")) }
                        .getOrElse { BigDecimal.ZERO }
                    val pSubtotal = runCatching { BigDecimal(pi.optString("subtotal", "")) }.getOrNull()
                        ?: pUnitPrice.add(pSurcharge).multiply(pQty).setScale(Money.SCALE)
                    products.add(SavedProductItem(
                        name = pName,
                        unit = pUnit,
                        quantity = pQty,
                        unitPrice = pUnitPrice,
                        surcharge = pSurcharge,
                        subtotal = pSubtotal
                    ))
                }
            }

            val currency = entry.optString("currency", "$")
            result.add(SavedCount(
                id = id,
                savedAt = savedAt,
                targetAmount = target,
                items = items,
                currency = currency,
                products = products
            ))
        }

        return result.sortedByDescending { it.savedAt }
    }
}
