package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.Receivable
import com.moneycounter.domain.ReceivableStatus
import com.moneycounter.domain.SavedProductItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class JsonReceivableRepository(private val context: Context) : ReceivableRepository {

    private val fileName = "receivables.json"

    override fun load(): List<Receivable> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            ReceivableJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveAll(receivables: List<Receivable>) {
        try {
            val json = ReceivableJson.toJson(receivables)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}

object ReceivableJson {

    private const val VERSION = 1

    fun toJson(receivables: List<Receivable>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (r in receivables) {
            val item = JSONObject()
            item.put("id", r.id)
            item.put("at", r.at)
            item.put("debtorName", r.debtorName)
            item.put("amount", r.amount.toPlainString())
            item.put("currencyId", r.currencyId)
            item.put("status", r.status.name)
            item.put("settledAt", r.settledAt ?: JSONObject.NULL)
            item.put("sellerUid", r.sellerUid)
            item.put("sellerName", r.sellerName)

            val productsArray = JSONArray()
            for (p in r.products) {
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

            array.put(item)
        }

        root.put("receivables", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Receivable> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION) return emptyList()

        val array = root.optJSONArray("receivables") ?: return emptyList()
        val result = mutableListOf<Receivable>()

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isBlank()) continue
            val at = entry.optLong("at", 0L)
            val debtorName = entry.optString("debtorName", "")
            if (debtorName.isBlank()) continue

            val amount = runCatching { BigDecimal(entry.optString("amount", "")) }.getOrNull()
                ?: continue
            if (amount.signum() <= 0) continue

            val currencyId = entry.optString("currencyId", "")
            if (currencyId.isBlank()) continue

            val status = runCatching { ReceivableStatus.valueOf(entry.optString("status", "OPEN")) }
                .getOrElse { ReceivableStatus.OPEN }

            val settledAt = if (entry.isNull("settledAt")) null else {
                val v = entry.optLong("settledAt", -1L)
                if (v <= 0L) null else v
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
                    products.add(
                        SavedProductItem(
                            name = pName,
                            unit = pUnit,
                            quantity = pQty,
                            unitPrice = pUnitPrice,
                            surcharge = pSurcharge,
                            subtotal = pSubtotal
                        )
                    )
                }
            }

            result.add(
                Receivable(
                    id = id,
                    at = at,
                    debtorName = debtorName,
                    amount = amount.setScale(Money.SCALE),
                    currencyId = currencyId,
                    products = products,
                    status = status,
                    settledAt = settledAt,
                    sellerUid = entry.optString("sellerUid", ""),
                    sellerName = entry.optString("sellerName", "")
                )
            )
        }

        return result.sortedByDescending { it.at }
    }
}
