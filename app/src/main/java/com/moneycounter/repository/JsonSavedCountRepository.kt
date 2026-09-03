package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.SavedCountItem
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

    private const val VERSION = 1

    fun toJson(history: List<SavedCount>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val historyArray = JSONArray()
        for (saved in history) {
            val item = JSONObject()
            item.put("id", saved.id)
            item.put("savedAt", saved.savedAt)
            item.put("targetAmount", saved.targetAmount.toPlainString())

            val itemsArray = JSONArray()
            for (entry in saved.items) {
                val ei = JSONObject()
                ei.put("denominationValue", entry.denominationValue)
                ei.put("quantity", entry.quantity)
                ei.put("subtotal", entry.subtotal.toPlainString())
                itemsArray.put(ei)
            }
            item.put("items", itemsArray)
            historyArray.put(item)
        }

        root.put("history", historyArray)
        return root.toString(2)
    }

    fun fromJson(json: String): List<SavedCount> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        if (root.optInt("version", 1) != VERSION) return emptyList()

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

            result.add(SavedCount(id = id, savedAt = savedAt, targetAmount = target, items = items))
        }

        return result.sortedByDescending { it.savedAt }
    }
}
