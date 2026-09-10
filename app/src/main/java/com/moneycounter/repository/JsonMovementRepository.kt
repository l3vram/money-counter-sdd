package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementDenomination
import com.moneycounter.domain.MovementMigration
import com.moneycounter.domain.MovementProductLine
import com.moneycounter.domain.MovementType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

/**
 * FOUNDATION ONLY repository for the unified movement journal. On first load
 * (movements.json absent) it migrates the four legacy stores once via
 * [MovementMigration.fromLegacy] and persists the result. It never reroutes
 * writes and never deletes the legacy files — those stay the source of truth
 * for their own screens/registers until plan 009.
 */
class JsonMovementRepository(private val context: Context) : MovementRepository {

    private val fileName = "movements.json"

    override fun load(): List<Movement> {
        val file = File(context.filesDir, fileName)
        val existing = try {
            if (!file.exists()) null
            else {
                val jsonString = file.readText()
                if (jsonString.isBlank()) null else MovementJson.fromJson(jsonString)
            }
        } catch (e: Exception) {
            null
        }
        if (existing != null) return existing

        val migrated = try {
            MovementMigration.fromLegacy(
                sales = readLegacy("count_history.json", SavedCountJson::fromJson),
                receivables = readLegacy("receivables.json", ReceivableJson::fromJson),
                payments = readLegacy("payments.json", PaymentJson::fromJson),
                writeoffs = readLegacy("writeoffs.json", WriteoffJson::fromJson)
            )
        } catch (e: Exception) {
            emptyList()
        }
        saveAll(migrated)
        return migrated
    }

    private fun <T> readLegacy(fileName: String, parse: (String) -> List<T>): List<T> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            parse(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(movements: List<Movement>) {
        try {
            val json = MovementJson.toJson(movements)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other repositories
        }
    }
}

object MovementJson {

    private const val VERSION = 1

    fun toJson(movements: List<Movement>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (m in movements) {
            val item = JSONObject()
            item.put("id", m.id)
            item.put("at", m.at)
            item.put("type", m.type.name)
            item.put("currencyId", m.currencyId)
            item.put("concept", m.concept ?: JSONObject.NULL)
            item.put("amount", m.amount.toPlainString())
            item.put("linkId", m.linkId ?: JSONObject.NULL)
            item.put("closingId", m.closingId ?: JSONObject.NULL)

            val productsArray = JSONArray()
            for (p in m.products) {
                val pi = JSONObject()
                pi.put("name", p.name)
                pi.put("unit", p.unit)
                pi.put("quantity", p.quantity.toPlainString())
                pi.put("unitPrice", p.unitPrice.toPlainString())
                pi.put("subtotal", p.subtotal.toPlainString())
                productsArray.put(pi)
            }
            item.put("products", productsArray)

            val denomArray = JSONArray()
            for (d in m.denominations) {
                val di = JSONObject()
                di.put("value", d.value)
                di.put("quantity", d.quantity)
                di.put("subtotal", d.subtotal.toPlainString())
                denomArray.put(di)
            }
            item.put("denominations", denomArray)

            array.put(item)
        }

        root.put("movements", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Movement> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("movements") ?: return emptyList()
        val result = mutableListOf<Movement>()

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val at = entry.optLong("at", 0L)

            val type = runCatching { MovementType.valueOf(entry.optString("type", "")) }
                .getOrNull() ?: continue

            val currencyId = entry.optString("currencyId", "")
            if (currencyId.isBlank()) continue

            val concept = if (entry.isNull("concept")) null else entry.optString("concept", "").ifBlank { null }

            val amount = runCatching { BigDecimal(entry.optString("amount", "")) }.getOrNull()
                ?: continue
            if (amount.signum() < 0) continue

            val linkId = if (entry.isNull("linkId")) null else entry.optString("linkId", "").ifBlank { null }
            val closingId = if (entry.isNull("closingId")) null else entry.optString("closingId", "").ifBlank { null }

            val products = mutableListOf<MovementProductLine>()
            val productsArray = entry.optJSONArray("products")
            if (productsArray != null) {
                for (j in 0 until productsArray.length()) {
                    val pi = productsArray.optJSONObject(j) ?: continue
                    val pName = pi.optString("name", "")
                    val pUnit = pi.optString("unit", "")
                    if (pName.isBlank() || pUnit.isBlank()) continue
                    val pQty = runCatching { BigDecimal(pi.optString("quantity", "")) }.getOrNull() ?: continue
                    val pUnitPrice = runCatching { BigDecimal(pi.optString("unitPrice", "")) }.getOrNull()
                        ?: continue
                    val pSubtotal = runCatching { BigDecimal(pi.optString("subtotal", "")) }.getOrNull()
                        ?: pUnitPrice.multiply(pQty).setScale(Money.SCALE)
                    products.add(
                        MovementProductLine(
                            name = pName,
                            unit = pUnit,
                            quantity = pQty,
                            unitPrice = pUnitPrice,
                            subtotal = pSubtotal
                        )
                    )
                }
            }

            val denominations = mutableListOf<MovementDenomination>()
            val denomArray = entry.optJSONArray("denominations")
            if (denomArray != null) {
                for (j in 0 until denomArray.length()) {
                    val di = denomArray.optJSONObject(j) ?: continue
                    val value = di.optLong("value", 0L)
                    val quantity = di.optLong("quantity", 0L)
                    if (value <= 0 || quantity <= 0) continue
                    val subtotal = runCatching { BigDecimal(di.optString("subtotal", "")) }.getOrNull()
                        ?: BigDecimal.valueOf(value * quantity).setScale(Money.SCALE)
                    denominations.add(MovementDenomination(value = value, quantity = quantity, subtotal = subtotal))
                }
            }

            val movement = runCatching {
                Movement(
                    id = id,
                    at = at,
                    type = type,
                    currencyId = currencyId,
                    concept = concept,
                    products = products,
                    denominations = denominations,
                    amount = amount.setScale(Money.SCALE),
                    linkId = linkId,
                    closingId = closingId
                )
            }.getOrNull() ?: continue

            result.add(movement)
        }

        return result.sortedByDescending { it.at }
    }
}
