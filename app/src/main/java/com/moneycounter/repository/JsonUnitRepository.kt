package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.MeasurementUnit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonUnitRepository(private val context: Context) : UnitRepository {

    private val fileName = "units.json"

    companion object {
        fun defaults(): List<MeasurementUnit> = listOf(
            MeasurementUnit("u1", "Lb"),
            MeasurementUnit("u2", "Kg"),
            MeasurementUnit("u3", "L"),
            MeasurementUnit("u4", "g"),
            MeasurementUnit("u5", "mL")
        )
    }

    override fun load(): List<MeasurementUnit> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                val defaults = defaults()
                save(defaults)
                return defaults
            }

            val jsonString = file.readText()
            if (jsonString.isBlank()) return defaults()

            val root = JSONObject(jsonString)
            if (root.optInt("version", 1) != 1) return defaults()

            val array = root.optJSONArray("units") ?: return defaults()
            val units = mutableListOf<MeasurementUnit>()
            val seenNames = mutableSetOf<String>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val name = item.optString("name", "").trim()
                if (id.isBlank() || name.isBlank()) continue
                if (!seenNames.add(name)) continue
                units.add(MeasurementUnit(id, name))
            }

            if (units.isEmpty()) return defaults()
            units
        } catch (e: Exception) {
            defaults()
        }
    }

    override fun save(units: List<MeasurementUnit>) {
        try {
            val root = JSONObject()
            root.put("version", 1)

            val array = JSONArray()
            for (unit in units) {
                val item = JSONObject()
                item.put("id", unit.id)
                item.put("name", unit.name)
                array.put(item)
            }
            root.put("units", array)

            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(root.toString(2))
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors
        }
    }
}