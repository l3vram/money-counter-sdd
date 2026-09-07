package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductPrice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

object ProductJson {

    private const val VERSION = 4

    fun toJson(products: List<Product>): String {
        val root = JSONObject()
        root.put("version", VERSION)

        val array = JSONArray()
        for (product in products) {
            val item = JSONObject()
            item.put("id", product.id)
            item.put("name", product.name)
            item.put("unit", product.unit)
            item.put("stock", product.stock.toPlainString())
            val prices = JSONObject()
            for ((currencyId, price) in product.prices) {
                val priceObj = JSONObject()
                priceObj.put("unitPrice", price.unitPrice.toPlainString())
                priceObj.put("surcharge", price.surcharge.toPlainString())
                prices.put(currencyId, priceObj)
            }
            item.put("prices", prices)
            array.put(item)
        }
        root.put("products", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Product> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version != VERSION && version != 1 && version != 2 && version != 3) return emptyList()

        val array = root.optJSONArray("products") ?: return emptyList()
        val products = mutableListOf<Product>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val name = item.optString("name", "")
            val unit = item.optString("unit", "")
            val stock = runCatching { BigDecimal(item.optString("stock", "0")).setScale(Money.SCALE) }
                .getOrDefault(Money.ZERO)

            if (id.isBlank() || name.isBlank() || unit.isBlank()) continue
            if (!seenIds.add(id)) continue

            if (version >= 4) {
                val pricesObject = item.optJSONObject("prices") ?: JSONObject()
                val prices = mutableMapOf<String, ProductPrice>()
                val keys = pricesObject.keys()
                while (keys.hasNext()) {
                    val currencyId = keys.next()
                    val priceObj = pricesObject.optJSONObject(currencyId) ?: continue
                    val unitPrice = runCatching { BigDecimal(priceObj.optString("unitPrice", "")).setScale(Money.SCALE) }
                        .getOrNull() ?: continue
                    val surcharge = runCatching { BigDecimal(priceObj.optString("surcharge", "0")).setScale(Money.SCALE) }
                        .getOrDefault(Money.ZERO)
                    if (unitPrice.signum() < 0 || surcharge.signum() < 0) continue
                    prices[currencyId] = ProductPrice(unitPrice, surcharge)
                }
                products.add(Product(id, name, unit, stock, prices))
            } else {
                val unitPriceStr = item.optString("unitPrice", "")
                val surchargeStr = item.optString("surcharge", "0")
                val unitPrice = runCatching { BigDecimal(unitPriceStr).setScale(Money.SCALE) }
                    .getOrNull() ?: continue
                val surcharge = runCatching { BigDecimal(surchargeStr).setScale(Money.SCALE) }
                    .getOrDefault(Money.ZERO)
                if (unitPrice.signum() < 0 || surcharge.signum() < 0) continue
                val currencyId = item.optString("currencyId", DefaultCurrencies.CUP.id)
                products.add(
                    Product(id, name, unit, stock, mapOf(currencyId to ProductPrice(unitPrice, surcharge)))
                )
            }
        }

        return mergeDuplicates(products)
    }

    private fun mergeDuplicates(products: List<Product>): List<Product> {
        return products
            .groupBy { it.name.trim().lowercase() to it.unit.trim().lowercase() }
            .map { (_, group) ->
                val first = group.first()
                val mergedStock = group.maxOfOrNull { it.stock } ?: Money.ZERO
                val mergedPrices = mutableMapOf<String, ProductPrice>()
                for (p in group) {
                    mergedPrices.putAll(p.prices)
                }
                first.copy(stock = mergedStock, prices = mergedPrices)
            }
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
