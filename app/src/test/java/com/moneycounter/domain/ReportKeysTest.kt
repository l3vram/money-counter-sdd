package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

class ReportKeysTest {

    private fun millisFor(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun count(id: String, savedAt: Long) =
        SavedCount(
            id = id,
            savedAt = savedAt,
            targetAmount = BigDecimal("100.00"),
            items = emptyList()
        )

    private fun movement(id: String, at: Long) =
        Movement(
            id = id,
            at = at,
            type = MovementType.VENTA,
            currencyId = "cup",
            amount = BigDecimal("50.00")
        )

    @Test
    fun `monthKeyOf extracts year and month`() {
        assertEquals(MonthKey(2026, 9), monthKeyOf(millisFor(2026, 9, 7)))
    }

    @Test
    fun `dayKeyOf extracts year month and day`() {
        assertEquals(DayKey(2026, 9, 7), dayKeyOf(millisFor(2026, 9, 7)))
    }

    @Test
    fun `month boundary does not roll over`() {
        assertEquals(MonthKey(2026, 1), monthKeyOf(millisFor(2026, 1, 1, 0, 0)))
        assertEquals(MonthKey(2026, 12), monthKeyOf(millisFor(2026, 12, 31, 23, 59)))
    }

    @Test
    fun `day boundary does not roll over`() {
        assertEquals(DayKey(2026, 1, 1), dayKeyOf(millisFor(2026, 1, 1, 0, 0)))
        assertEquals(DayKey(2026, 12, 31), dayKeyOf(millisFor(2026, 12, 31, 23, 59)))
    }

    @Test
    fun `keyString zero pads month and day`() {
        assertEquals("2026-09", MonthKey(2026, 9).keyString())
        assertEquals("2026-09-07", DayKey(2026, 9, 7).keyString())
    }

    @Test
    fun `isCurrentMonth and isToday true for now and false for past key`() {
        val now = System.currentTimeMillis()
        assertTrue(isCurrentMonth(monthKeyOf(now)))
        assertTrue(isToday(dayKeyOf(now)))
        val past = now - 32L * 24 * 60 * 60 * 1000
        assertFalse(isCurrentMonth(monthKeyOf(past)))
        assertFalse(isToday(dayKeyOf(past)))
    }

    @Test
    fun `groupByMonthDay groups by month and day sorting newest first`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day1c = millisFor(2026, 9, 7, 12)
        val day2 = millisFor(2026, 9, 5, 10)
        val day2b = millisFor(2026, 9, 5, 11)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val history = listOf(
            count("a", day1), count("b", day1b), count("c", day1c),
            count("d", day2), count("e", day2b),
            count("f", otherMonth)
        )

        val groups = groupByMonthDay(history)

        assertEquals(2, groups.size)
        assertEquals(MonthKey(2026, 9), groups[0].key)
        assertEquals(MonthKey(2026, 8), groups[1].key)

        val september = groups[0]
        assertEquals(2, september.days.size)
        assertEquals(DayKey(2026, 9, 7), september.days[0].key)
        assertEquals(listOf("c", "b", "a"), september.days[0].counts.map { it.id })
        assertEquals(DayKey(2026, 9, 5), september.days[1].key)
        assertEquals(listOf("e", "d"), september.days[1].counts.map { it.id })

        assertEquals(DayKey(2026, 8, 20), groups[1].days[0].key)
        assertEquals(listOf("f"), groups[1].days[0].counts.map { it.id })
    }

    @Test
    fun `groupByMonthDay is stable under reversed input order`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day2 = millisFor(2026, 9, 5, 10)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val forward = listOf(
            count("a", day1),
            count("b", day1b),
            count("c", day2),
            count("d", otherMonth)
        )
        val reversed = forward.reversed()

        val g1 = groupByMonthDay(forward)
        val g2 = groupByMonthDay(reversed)

        assertEquals(g1.map { it.key }, g2.map { it.key })
        assertEquals(g1[0].days.map { it.key }, g2[0].days.map { it.key })
        assertEquals(g1[0].days[0].counts.map { it.id }, g2[0].days[0].counts.map { it.id })
    }

    @Test
    fun `groupByMonthDayAscending orders months days and counts oldest first`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day1c = millisFor(2026, 9, 7, 12)
        val day2 = millisFor(2026, 9, 5, 10)
        val day2b = millisFor(2026, 9, 5, 11)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val history = listOf(
            count("a", day1), count("b", day1b), count("c", day1c),
            count("d", day2), count("e", day2b),
            count("f", otherMonth)
        )

        val groups = groupByMonthDay(history, ascending = true)

        assertEquals(2, groups.size)
        assertEquals(MonthKey(2026, 8), groups[0].key)
        assertEquals(MonthKey(2026, 9), groups[1].key)

        val august = groups[0]
        assertEquals(DayKey(2026, 8, 20), august.days[0].key)
        assertEquals(listOf("f"), august.days[0].counts.map { it.id })

        val september = groups[1]
        assertEquals(2, september.days.size)
        assertEquals(DayKey(2026, 9, 5), september.days[0].key)
        assertEquals(listOf("d", "e"), september.days[0].counts.map { it.id })
        assertEquals(DayKey(2026, 9, 7), september.days[1].key)
        assertEquals(listOf("a", "b", "c"), september.days[1].counts.map { it.id })
    }

    @Test
    fun `groupByMonthDayAscending is stable under reversed input order`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day2 = millisFor(2026, 9, 5, 10)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val forward = listOf(
            count("a", day1),
            count("b", day1b),
            count("c", day2),
            count("d", otherMonth)
        )
        val reversed = forward.reversed()

        val g1 = groupByMonthDay(forward, ascending = true)
        val g2 = groupByMonthDay(reversed, ascending = true)

        assertEquals(g1.map { it.key }, g2.map { it.key })

        val monthsEqual = g1.zip(g2).all { (a, b) ->
            a.days.map { it.key } == b.days.map { it.key } &&
                a.days.zip(b.days).all { (da, db) ->
                    da.counts.map { it.id } == db.counts.map { it.id }
                }
        }
        assertTrue(monthsEqual)
    }

    @Test
    fun `groupByMonthDayDefaultStillDescending keeps newest first`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day2 = millisFor(2026, 9, 5, 10)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val history = listOf(
            count("a", day1), count("b", day1b),
            count("c", day2),
            count("d", otherMonth)
        )

        val groups = groupByMonthDay(history)

        assertEquals(MonthKey(2026, 9), groups[0].key)
        assertEquals(MonthKey(2026, 8), groups[1].key)
        assertEquals(DayKey(2026, 9, 7), groups[0].days[0].key)
        assertEquals(listOf("b", "a"), groups[0].days[0].counts.map { it.id })
    }

    @Test
    fun `groupMovementsByMonthDay groups by month and day newest first by default`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day1b = millisFor(2026, 9, 7, 11)
        val day2 = millisFor(2026, 9, 5, 10)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val movements = listOf(
            movement("a", day1), movement("b", day1b),
            movement("c", day2),
            movement("d", otherMonth)
        )

        val groups = groupMovementsByMonthDay(movements)

        assertEquals(2, groups.size)
        assertEquals(MonthKey(2026, 9), groups[0].key)
        assertEquals(MonthKey(2026, 8), groups[1].key)

        val september = groups[0]
        assertEquals(DayKey(2026, 9, 7), september.days[0].key)
        assertEquals(listOf("b", "a"), september.days[0].movements.map { it.id })
        assertEquals(DayKey(2026, 9, 5), september.days[1].key)
        assertEquals(listOf("c"), september.days[1].movements.map { it.id })

        assertEquals(DayKey(2026, 8, 20), groups[1].days[0].key)
        assertEquals(listOf("d"), groups[1].days[0].movements.map { it.id })
    }

    @Test
    fun `groupMovementsByMonthDay ascending orders oldest first`() {
        val day1 = millisFor(2026, 9, 7, 10)
        val day2 = millisFor(2026, 9, 5, 10)
        val otherMonth = millisFor(2026, 8, 20, 10)

        val movements = listOf(
            movement("a", day1),
            movement("b", day2),
            movement("c", otherMonth)
        )

        val groups = groupMovementsByMonthDay(movements, ascending = true)

        assertEquals(MonthKey(2026, 8), groups[0].key)
        assertEquals(MonthKey(2026, 9), groups[1].key)
        assertEquals(listOf("b"), groups[1].days[0].movements.map { it.id })
        assertEquals(listOf("a"), groups[1].days[1].movements.map { it.id })
    }

    @Test
    fun `groupMovementsByMonthDay empty input yields empty groups`() {
        assertTrue(groupMovementsByMonthDay(emptyList()).isEmpty())
    }
}