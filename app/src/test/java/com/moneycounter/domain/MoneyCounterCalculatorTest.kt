package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyCounterCalculatorTest {

    private fun denomination(id: String, value: Long) = Denomination(id, value)

    private fun bd(v: String): BigDecimal = Money.of(v)

    @Test
    fun `empty state returns EMPTY status`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 0)
        )
        assertEquals(CounterStatus.EMPTY, result.status)
        assertEquals(bd("0.00"), result.countedTotal)
    }

    @Test
    fun `single denomination with quantity`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 10)
        )
        assertEquals(bd("50000.00"), result.countedTotal)
        assertEquals(CounterStatus.COUNTING, result.status)
    }

    @Test
    fun `multiple denominations`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(
                denomination("d1", 5000),
                denomination("d2", 2000)
            ),
            quantities = mapOf("d1" to 10, "d2" to 5)
        )
        assertEquals(bd("60000.00"), result.countedTotal)
    }

    @Test
    fun `zero quantity produces zero subtotal`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 0)
        )
        assertEquals(bd("0.00"), result.countedTotal)
        assertEquals(CounterStatus.EMPTY, result.status)
    }

    @Test
    fun `target greater than total shows remaining`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100000.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 15)
        )
        assertEquals(bd("75000.00"), result.countedTotal)
        assertEquals(bd("25000.00"), result.remaining)
        assertEquals(bd("0.00"), result.excess)
        assertEquals(CounterStatus.COUNTING, result.status)
    }

    @Test
    fun `target equal to total shows COMPLETED`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100000.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 20)
        )
        assertEquals(bd("100000.00"), result.countedTotal)
        assertEquals(bd("0.00"), result.remaining)
        assertEquals(bd("0.00"), result.excess)
        assertEquals(CounterStatus.COMPLETED, result.status)
    }

    @Test
    fun `target less than total shows excess`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100000.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 21)
        )
        assertEquals(bd("105000.00"), result.countedTotal)
        assertEquals(bd("0.00"), result.remaining)
        assertEquals(bd("5000.00"), result.excess)
        assertEquals(CounterStatus.OVER, result.status)
    }

    @Test
    fun `negative quantity is ignored`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to -5)
        )
        assertEquals(bd("0.00"), result.countedTotal)
        assertEquals(CounterStatus.EMPTY, result.status)
    }

    @Test
    fun `missing quantity defaults to zero`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(
                denomination("d1", 5000),
                denomination("d2", 2000)
            ),
            quantities = mapOf("d1" to 10)
        )
        assertEquals(bd("50000.00"), result.countedTotal)
    }

    @Test
    fun `target zero with positive count shows COUNTING`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("0.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 10)
        )
        assertEquals(CounterStatus.COUNTING, result.status)
        assertEquals(bd("50000.00"), result.countedTotal)
    }

    @Test
    fun `target null with positive count shows COUNTING`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 10)
        )
        assertEquals(CounterStatus.COUNTING, result.status)
    }

    @Test
    fun `AC-003 test case`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(
                denomination("d1", 5000),
                denomination("d2", 2000)
            ),
            quantities = mapOf("d1" to 10, "d2" to 5)
        )
        assertEquals(bd("60000.00"), result.countedTotal)
    }

    @Test
    fun `AC-004 test case`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100000.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 15)
        )
        assertEquals(bd("25000.00"), result.remaining)
    }

    @Test
    fun `AC-006 test case`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100000.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 21)
        )
        assertEquals(bd("5000.00"), result.excess)
        assertEquals(CounterStatus.OVER, result.status)
    }

    @Test
    fun `all BigDecimal fields are scale 2`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = bd("100.00"),
            denominations = listOf(denomination("d1", 5000)),
            quantities = mapOf("d1" to 10)
        )
        assertEquals(2, result.countedTotal.scale())
        assertEquals(2, result.remaining.scale())
        assertEquals(2, result.excess.scale())
    }

    @Test
    fun `large values do not overflow`() {
        val result = MoneyCounterCalculator.calculate(
            targetAmount = null,
            denominations = listOf(denomination("d1", 999999999)),
            quantities = mapOf("d1" to 999999999)
        )
        assertEquals(2, result.countedTotal.scale())
    }

    @Test
    fun `raw text quantity parses and calculates correctly`() {
        // Because the operator reported "typing 1 gave 10" as a UI-parsing artifact,
        // these cases pin the end-to-end path: raw text -> QuantityParser -> calculator.

        fun calcFor(raw: String, value: Long): BigDecimal {
            val parsed = QuantityParser.parse(raw) ?: 0L
            return MoneyCounterCalculator.calculate(
                targetAmount = null,
                denominations = listOf(denomination("d1", value)),
                quantities = mapOf("d1" to parsed)
            ).countedTotal
        }

        // empty (nothing) counts as no contribution
        assertEquals(bd("0.00"), calcFor("", 5000))

        // explicit zero contributes nothing
        assertEquals(bd("0.00"), calcFor("0", 5000))

        // "1" on a 5000 bill is 5000, never 10x
        assertEquals(bd("5000.00"), calcFor("1", 5000))

        // leading zero ignored -> still the same as "1"
        assertEquals(bd("5000.00"), calcFor("01", 5000))

        // trailing zero respected -> 10 bills of 5000
        assertEquals(bd("50000.00"), calcFor("10", 5000))
        assertEquals(bd("50000.00"), calcFor("010", 5000))
    }
}
