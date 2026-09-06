package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

object ProductJson {

    private const val VERSION = 2

    fun toJson(products: List<Product>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (product in products) {
            val item = JSONObject()
            item.put("id", product.id)
            item.put("name", product.name)
            item.put("unit", product.unit)
            item.put("unitPrice", product.unitPrice.toPlainString())
            item.put("surcharge", product.surcharge.toPlainString())
            item.put("stock", product.stock.toPlainString())
            array.put(item)
        }
        root.put("products", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Product> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION && version != 1) return emptyList()

        val array = root.optJSONArray("products") ?: return emptyList()
        val products = mutableListOf<Product>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val name = item.optString("name", "")
            val unit = item.optString("unit", "")
            val unitPriceStr = item.optString("unitPrice", "")
            val surchargeStr = item.optString("surcharge", "0")
            val stockStr = item.optString("stock", "0")

            if (id.isBlank() || name.isBlank() || unit.isBlank()) continue
            if (!seenIds.add(id)) continue

            val unitPrice = runCatching { BigDecimal(unitPriceStr).setScale(Money.SCALE) }
                .getOrNull() ?: continue
            val surcharge = runCatching { BigDecimal(surchargeStr).setScale(Money.SCALE) }
                .getOrDefault(Money.ZERO)
            val stock = runCatching { BigDecimal(stockStr).setScale(Money.SCALE) }
                .getOrDefault(Money.ZERO)
            if (unitPrice.signum() < 0 || surcharge.signum() < 0 || stock.signum() < 0) continue

            products.add(Product(id, name, unit, unitPrice, surcharge, stock))
        }

        return products
    }
}

class JsonProductRepository(private val context: Context) : ProductRepository {

    private val fileName = "products.json"

    override fun load(): List<Product> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()

            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()

            ProductJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun save(products: List<Product>) {
        try {
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(ProductJson.toJson(products))
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}