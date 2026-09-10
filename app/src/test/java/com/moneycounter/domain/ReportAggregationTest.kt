package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ReportAggregationTest {

    private fun movement(
        id: String,
        amount: String = "1000",
        currencyId: String = DefaultCurrencies.CUP.id,
        at: Long = System.currentTimeMillis(),
        denominations: List<MovementDenomination> = emptyList(),
        products: List<MovementProductLine> = emptyList()
    ) = Movement(
        id = id,
        at = at,
        type = MovementType.VENTA,
        currencyId = currencyId,
        denominations = denominations,
        products = products,
        amount = BigDecimal(amount).setScale(Money.SCALE)
    )

    private fun item(value: Long, quantity: Long) = MovementDenomination(
        value = value,
        quantity = quantity,
        subtotal = Money.fromLong(value * quantity)
    )

    private fun product(
        name: String = "Arroz",
        unit: String = "Lb",
        unitPrice: String = "500",
        quantity: String = "1"
    ) = MovementProductLine(
        name = name,
        unit = unit,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        unitPrice = BigDecimal(unitPrice).setScale(Money.SCALE),
        subtotal = BigDecimal(unitPrice).multiply(BigDecimal(quantity)).setScale(Money.SCALE)
    )

    @Test
    fun `mixing currencies throws`() {
        val list = listOf(
            movement(id = "a", currencyId = DefaultCurrencies.CUP.id),
            movement(id = "b", currencyId = DefaultCurrencies.USD.id)
        )
        assertThrows(IllegalArgumentException::class.java) {
            uniteMovements(list, "CUP")
        }
    }

    @Test
    fun `empty list throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            uniteMovements(emptyList())
        }
    }

    @Test
    fun `denominations sum by value`() {
        val list = listOf(
            movement(id = "a", denominations = listOf(item(100, 3))),
            movement(id = "b", denominations = listOf(item(100, 2), item(50, 1)))
        )
        val united = uniteMovements(list, "CUP")

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
    fun `same product name and unit merges across movements`() {
        val list = listOf(
            movement(id = "a", products = listOf(product(quantity = "1.5"))),
            movement(id = "b", products = listOf(product(quantity = "2.5")))
        )
        val united = uniteMovements(list, "CUP")

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
            movement(id = "a", products = listOf(product(unit = "Lb"))),
            movement(id = "b", products = listOf(product(unit = "Kg")))
        )
        val united = uniteMovements(list, "CUP")

        assertEquals(2, united.products.size)
        assertEquals(listOf("Kg", "Lb"), united.products.map { it.unit })
    }

    @Test
    fun `totals match`() {
        val list = listOf(
            movement(id = "a", amount = "1000"),
            movement(id = "b", amount = "250.50")
        )
        val united = uniteMovements(list, "CUP")

        val expected = list.fold(Money.ZERO) { acc, m -> acc.add(m.amount) }
        assertEquals(2, united.count)
        assertEquals(BigDecimal("1250.50").setScale(Money.SCALE), united.targetAmountTotal)
        assertEquals(BigDecimal("1250.50").setScale(Money.SCALE), united.total())
        assertEquals(expected, united.total())
    }

    @Test
    fun `currency id and code are preserved`() {
        val list = listOf(
            movement(id = "a", currencyId = "cup"),
            movement(id = "b", currencyId = "cup")
        )
        val united = uniteMovements(list, "CUP")

        assertEquals("cup", united.currencyId)
        assertEquals("CUP", united.currencyCode)
    }
}