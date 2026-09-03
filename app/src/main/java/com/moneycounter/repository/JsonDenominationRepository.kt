package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.DefaultDenominations
import com.moneycounter.domain.Denomination
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonDenominationRepository(private val context: Context) : DenominationRepository {

    private val fileName = "denominations.json"

    override fun load(): List<Denomination> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                val defaults = DefaultDenominations.get()
                save(defaults)
                return defaults
            }

            val jsonString = file.readText()
            if (jsonString.isBlank()) {
                return DefaultDenominations.get()
            }

            parseJson(jsonString)
        } catch (e: Exception) {
            DefaultDenominations.get()
        }
    }

    override fun save(denominations: List<Denomination>) {
        try {
            val json = buildJson(denominations)
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        } catch (e: Exception) {
        }
    }

    private fun parseJson(jsonString: String): List<Denomination> {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        if (version != 1) {
            return DefaultDenominations.get()
        }

        val denominationsArray = root.getJSONArray("denominations")
        val denominations = mutableListOf<Denomination>()
        val seenValues = mutableSetOf<Long>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until denominationsArray.length()) {
            val item = denominationsArray.getJSONObject(i)
            val id = item.optString("id", "")
            val value = item.optLong("value", 0)

            if (id.isBlank() || value <= 0) continue
            if (!seenIds.add(id)) continue
            if (!seenValues.add(value)) continue

            denominations.add(Denomination(id, value))
        }

        if (denominations.isEmpty()) {
            return DefaultDenominations.get()
        }

        return denominations
    }

    private fun buildJson(denominations: List<Denomination>): String {
        val root = JSONObject()
        root.put("version", 1)

        val array = JSONArray()
        for (denomination in denominations) {
            val item = JSONObject()
            item.put("id", denomination.id)
            item.put("value", denomination.value)
            array.put(item)
        }

        root.put("denominations", array)
        return root.toString(2)
    }
}
