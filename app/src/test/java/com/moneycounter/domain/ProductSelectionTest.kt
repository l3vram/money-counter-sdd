package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ProductSelectionTest {

    private fun product(id: String, unitPrice: String, surcharge: String = "0") =
        Product(
            id,
            "Producto $id",
            "Lb",
            prices = mapOf(DefaultCurrencies.CUP.id to ProductPrice(BigDecimal(unitPrice), BigDecimal(surcharge)))
        )

    @Test
    fun `parseQuantity handles decimal with comma`() {
        assertEquals(BigDecimal("1.50"), ProductSelection(quantityText = "1,5").quantity())
    }

    @Test
    fun `parseQuantity with dot`() {
        assertEquals(BigDecimal("2.00"), ProductSelection(quantityText = "2.00").quantity())
    }

    @Test
    fun `parseQuantity empty returns zero`() {
        assertEquals(BigDecimal.ZERO.setScale(Money.SCALE), ProductSelection(quantityText = "").quantity())
    }

    @Test
    fun `effectiveUnitPrice adds surcharge`() {
        val p = product("p1", "500", "250")
        assertEquals(BigDecimal("750.00"), p.effectiveUnitPriceFor(DefaultCurrencies.CUP.id))
    }

    @Test
    fun `effectiveUnitPrice without surcharge`() {
        val p = product("p1", "500")
        assertEquals(BigDecimal("500.00"), p.effectiveUnitPriceFor(DefaultCurrencies.CUP.id))
    }
}