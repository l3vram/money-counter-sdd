package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DefaultCurrencies
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonCurrencyRepository(private val context: Context) : CurrencyRepository {

    private val fileName = "currencies.json"

    override fun load(): CurrencySettings {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                val defaults = CurrencySettings(DefaultCurrencies.get(), "cup")
                save(defaults)
                return defaults
            }

            val jsonString = file.readText()
            if (jsonString.isBlank()) {
                return CurrencySettings(DefaultCurrencies.get(), "cup")
            }

            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version != 1) {
                return CurrencySettings(DefaultCurrencies.get(), "cup")
            }

            val array = root.optJSONArray("currencies")
            val currencies = mutableListOf<Currency>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val code = item.optString("code", "")
                    val name = item.optString("name", "")
                    val symbol = item.optString("symbol", "$")
                    if (id.isBlank() || code.isBlank()) continue
                    currencies.add(Currency(id, code, name, symbol))
                }
            }

            if (currencies.isEmpty()) {
                currencies.addAll(DefaultCurrencies.get())
            }

            val selectedId = root.optString("selectedCurrencyId", currencies.first().id)
                .takeIf { sel -> currencies.any { it.id == sel } }
                ?: currencies.first().id

            CurrencySettings(currencies, selectedId)
        } catch (e: Exception) {
            CurrencySettings(DefaultCurrencies.get(), "cup")
        }
    }

    override fun save(settings: CurrencySettings) {
        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("selectedCurrencyId", settings.selectedCurrencyId)

            val array = JSONArray()
            for (currency in settings.currencies) {
                val item = JSONObject()
                item.put("id", currency.id)
                item.put("code", currency.code)
                item.put("name", currency.name)
                item.put("symbol", currency.symbol)
                array.put(item)
            }
            root.put("currencies", array)

            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(root.toString(2))
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the denominations repository
        }
    }
}