package com.moneycounter.domain

import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class StockDeductionTest {

    private fun product(id: String, stock: String) =
        Product(id, "Name $id", "Lb", BigDecimal("2.00"), surcharge = BigDecimal("0.50"), stock = BigDecimal(stock).setScale(Money.SCALE))

    private fun sel(productId: String, qty: String) = ProductSelection(productId = productId, quantityText = qty)

    @Test
    fun `happy path deducts sold quantity from stock`() {
        val result = MoneyCounterViewModel.applyStockDeduction(listOf(product("p1", "40")), listOf(sel("p1", "3")))
        assertEquals(BigDecimal("37.00"), result.single().stock)
    }

    @Test
    fun `same product on two rows aggregates and deducts once`() {
        val result = MoneyCounterViewModel.applyStockDeduction(
            listOf(product("p1", "5")),
            listOf(sel("p1", "1"), sel("p1", "2"))
        )
        assertEquals(BigDecimal("2.00"), result.single().stock)
    }

    @Test
    fun `over-sell allowed pushes stock negative`() {
        val result = MoneyCounterViewModel.applyStockDeduction(listOf(product("p1", "2")), listOf(sel("p1", "5")))
        assertEquals(BigDecimal("-3.00"), result.single().stock)
    }

    @Test
    fun `blank quantity deducts nothing`() {
        val result = MoneyCounterViewModel.applyStockDeduction(listOf(product("p1", "40")), listOf(sel("p1", "")))
        assertEquals(BigDecimal("40.00"), result.single().stock)
    }

    @Test
    fun `selections for unknown products leave others untouched`() {
        val result = MoneyCounterViewModel.applyStockDeduction(
            listOf(product("p1", "10"), product("p2", "20")),
            listOf(sel("p9", "3"))
        )
        assertEquals(BigDecimal("10.00"), result[0].stock)
        assertEquals(BigDecimal("20.00"), result[1].stock)
    }

    @Test
    fun `decimal quantities deduct to scale two`() {
        val result = MoneyCounterViewModel.applyStockDeduction(listOf(product("p1", "10")), listOf(sel("p1", "1.5")))
        assertEquals(BigDecimal("8.50"), result.single().stock)
    }
}