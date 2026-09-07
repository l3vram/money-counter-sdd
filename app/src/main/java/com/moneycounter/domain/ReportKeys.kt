package com.moneycounter.domain

import java.util.Calendar

data class MonthKey(val year: Int, val month: Int)          // month 1..12
data class DayKey(val year: Int, val month: Int, val day: Int)

data class DayGroup(val key: DayKey, val counts: List<SavedCount>)
data class MonthGroup(val key: MonthKey, val days: List<DayGroup>)

fun monthKeyOf(millis: Long): MonthKey {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return MonthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

fun dayKeyOf(millis: Long): DayKey {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return DayKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun isCurrentMonth(key: MonthKey): Boolean = monthKeyOf(System.currentTimeMillis()) == key
fun isToday(key: DayKey): Boolean = dayKeyOf(System.currentTimeMillis()) == key

/** "2026-09" — month expansion key (matches Collapse-key strings). */
fun MonthKey.keyString(): String = "%04d-%02d".format(year, month)

/** "2026-09-07" — day expansion key. */
fun DayKey.keyString(): String = "%04d-%02d-%02d".format(year, month, day)

/** Groups history into months, each with days, each day's counts sorted by savedAt.
 *  ascending=false → newest first (months, days, counts). ascending=true → oldest first. */
fun groupByMonthDay(history: List<SavedCount>, ascending: Boolean = false): List<MonthGroup> {
    val dir = if (ascending) 1 else -1
    val dayGroups = history
        .groupBy { dayKeyOf(it.savedAt) }
        .map { (key, counts) ->
            DayGroup(key, if (ascending) counts.sortedBy { it.savedAt } else counts.sortedByDescending { it.savedAt })
        }
        .sortedWith(compareBy { dir * (it.key.year * 10_000 + it.key.month * 100 + it.key.day) })
    return dayGroups
        .groupBy { MonthKey(it.key.year, it.key.month) }
        .map { (key, days) -> MonthGroup(key, days) }
        .sortedWith(compareBy { dir * (it.key.year * 100 + it.key.month) })
}