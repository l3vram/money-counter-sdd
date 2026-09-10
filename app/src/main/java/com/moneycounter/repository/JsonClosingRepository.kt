package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Closing
import com.moneycounter.domain.ClosingStockLine
import com.moneycounter.domain.Money
import com.moneycounter.domain.MovementType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

/**
 * Repository for closing ("cierre") snapshots. Mirrors [JsonMovementRepository]:
 * versioned, tolerant of malformed entries (skips them rather than failing the
 * whole load), and writes atomically via a temp file + rename.
 */
class JsonClosingRepository(private val context: Context) : ClosingRepository {

    private val fileName = "closings.json"

    override fun load(): List<Closing> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) emptyList() else ClosingJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(closings: List<Closing>) {
        try {
            val json = ClosingJson.toJson(closings)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other repositories
        }
    }
}

object ClosingJson {

    private const val VERSION = 1

    fun toJson(closings: List<Closing>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (c in closings) {
            val item = JSONObject()
            item.put("id", c.id)
            item.put("at", c.at)
            item.put("currencyId", c.currencyId)

            val movementIdsArray = JSONArray()
            for (mid in c.movementIds) movementIdsArray.put(mid)
            item.put("movementIds", movementIdsArray)

            val totalsObj = JSONObject()
            for ((type, amount) in c.totalsByType) {
                totalsObj.put(type.name, amount.toPlainString())
            }
            item.put("totalsByType", totalsObj)

            item.put("netCash", c.netCash.toPlainString())
            item.put("sellerUid", c.sellerUid)
            item.put("sellerName", c.sellerName)

            val stockArray = JSONArray()
            for (line in c.stockSnapshot) {
                val si = JSONObject()
                si.put("name", line.name)
                si.put("unit", line.unit)
                si.put("quantity", line.quantity.toPlainString())
                stockArray.put(si)
            }
            item.put("stockSnapshot", stockArray)

            array.put(item)
        }

        root.put("closings", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Closing> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("closings") ?: return emptyList()
        val result = mutableListOf<Closing>()

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val at = entry.optLong("at", 0L)
            val currencyId = entry.optString("currencyId", "")
            if (currencyId.isBlank()) continue

            val movementIds = mutableListOf<String>()
            entry.optJSONArray("movementIds")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val mid = arr.optString(j, "")
                    if (mid.isNotBlank()) movementIds.add(mid)
                }
            }

            val totalsByType = mutableMapOf<MovementType, BigDecimal>()
            entry.optJSONObject("totalsByType")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val type = runCatching { MovementType.valueOf(key) }.getOrNull() ?: continue
                    val amount = runCatching { BigDecimal(obj.optString(key, "")) }.getOrNull() ?: continue
                    totalsByType[type] = amount.setScale(Money.SCALE)
                }
            }

            val netCash = runCatching { BigDecimal(entry.optString("netCash", "")) }.getOrNull()
                ?: continue

            val stockSnapshot = mutableListOf<ClosingStockLine>()
            entry.optJSONArray("stockSnapshot")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val si = arr.optJSONObject(j) ?: continue
                    val name = si.optString("name", "")
                    val unit = si.optString("unit", "")
                    if (name.isBlank() || unit.isBlank()) continue
                    val quantity = runCatching { BigDecimal(si.optString("quantity", "")) }.getOrNull()
                        ?: continue
                    stockSnapshot.add(
                        ClosingStockLine(name = name, unit = unit, quantity = quantity.setScale(Money.SCALE))
                    )
                }
            }

            val closing = runCatching {
                Closing(
                    id = id,
                    at = at,
                    currencyId = currencyId,
                    movementIds = movementIds,
                    totalsByType = totalsByType,
                    netCash = netCash.setScale(Money.SCALE),
                    stockSnapshot = stockSnapshot,
                    sellerUid = entry.optString("sellerUid", ""),
                    sellerName = entry.optString("sellerName", "")
                )
            }.getOrNull() ?: continue

            result.add(closing)
        }

        return result.sortedByDescending { it.at }
    }
}
