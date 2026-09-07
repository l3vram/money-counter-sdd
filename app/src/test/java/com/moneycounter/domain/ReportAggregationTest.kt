package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ReportAggregationTest {

    private fun count(
        id: String,
        targetAmount: String = "1000",
        items: List<SavedCountItem> = emptyList(),
        currency: String = "$",
        products: List<SavedProductItem> = emptyList(),
        currencyId: String = DefaultCurrencies.CUP.id,
        savedAt: Long = System.currentTimeMillis()
    ) = SavedCount(
        id = id,
        savedAt = savedAt,
        targetAmount = BigDecimal(targetAmount).setScale(Money.SCALE),
        items = items,
        currency = currency,
        products = products,
        currencyId = currencyId
    )

    private fun item(value: Long, quantity: Long) = SavedCountItem(
        denominationValue = value,
        quantity = quantity,
        subtotal = Money.fromLong(value * quantity)
    )

    private fun product(
        name: String = "Arroz",
        unit: String = "Lb",
        unitPrice: String = "500",
        quantity: String = "1",
        surcharge: String = "0"
    ) = SavedProductItem(
        name = name,
        unit = unit,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        unitPrice = BigDecimal(unitPrice).setScale(Money.SCALE),
        surcharge = BigDecimal(surcharge).setScale(Money.SCALE),
        subtotal = BigDecimal(unitPrice).add(BigDecimal(surcharge))
            .multiply(BigDecimal(quantity)).setScale(Money.SCALE)
    )

    @Test
    fun `mixing currencies throws`() {
        val list = listOf(
            count(id = "a", currencyId = DefaultCurrencies.CUP.id),
            count(id = "b", currencyId = DefaultCurrencies.USD.id)
        )
        assertThrows(IllegalArgumentException::class.java) {
            uniteCounts(list, "CUP")
        }
    }

    @Test
    fun `empty list throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            uniteCounts(emptyList())
        }
    }

    @Test
    fun `denominations sum by value`() {
        val list = listOf(
            count(id = "a", items = listOf(item(100, 3))),
            count(id = "b", items = listOf(item(100, 2), item(50, 1)))
        )
        val united = uniteCounts(list, "CUP")

        assertEquals(2, united.items.size)
        assertEquals(
            UnitedDenomination(100, 5, BigDecimal("500.00").setScale(Money.SCALE)),
            united.items.first()
        )
        assertEquals(
            UnitedDenomination(50, 1, BigDecimal("50.00").setScale(Money.SCALE)),
            united.items.last()
        )
    }

    @Test
    fun `same product name and unit merges across counts`() {
        val list = listOf(
            count(id = "a", products = listOf(product(quantity = "1.5"))),
            count(id = "b", products = listOf(product(quantity = "2.5")))
        )
        val united = uniteCounts(list, "CUP")

        assertEquals(1, united.products.size)
        val merged = united.products.first()
        assertEquals("Arroz", merged.name)
        assertEquals("Lb", merged.unit)
        assertEquals(BigDecimal("4.00").setScale(Money.SCALE), merged.quantity)
        assertEquals(BigDecimal("500.00").setScale(Money.SCALE), merged.unitPrice)
        assertEquals(BigDecimal("2000.00").setScale(Money.SCALE), merged.subtotal)
    }

    @Test
    fun `different units stay separate`() {
        val list = listOf(
            count(id = "a", products = listOf(product(unit = "Lb"))),
            count(id = "b", products = listOf(product(unit = "Kg")))
        )
        val united = uniteCounts(list, "CUP")

        assertEquals(2, united.products.size)
        assertEquals(listOf("Kg", "Lb"), united.products.map { it.unit })
    }

    @Test
    fun `totals match`() {
        val list = listOf(
            count(id = "a", targetAmount = "1000"),
            count(id = "b", targetAmount = "250.50")
        )
        val united = uniteCounts(list, "CUP")

        val expected = list.fold(Money.ZERO) { acc, c -> acc.add(c.targetAmount) }
        assertEquals(2, united.count)
        assertEquals(BigDecimal("1250.50").setScale(Money.SCALE), united.targetAmountTotal)
        assertEquals(BigDecimal("1250.50").setScale(Money.SCALE), united.total())
        assertEquals(expected, united.total())
    }

    @Test
    fun `same currency id different symbols still merge`() {
        val list = listOf(
            count(id = "a", currency = "$", currencyId = "cup"),
            count(id = "b", currency = "$", currencyId = "cup")
        )
        val united = uniteCounts(list, "CUP")

        assertEquals("$", united.currencySymbol)
        assertEquals("cup", united.currencyId)
    }
}