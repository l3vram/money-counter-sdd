package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ClosingComputeTest {

    private fun movement(
        id: String,
        type: MovementType,
        amount: String,
        currencyId: String = "cup",
        closingId: String? = null
    ) = Movement(
        id = id,
        at = 1000L,
        type = type,
        currencyId = currencyId,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        closingId = closingId
    )

    private fun product(name: String, unit: String, stock: String) = Product(
        id = name,
        name = name,
        unit = unit,
        stock = BigDecimal(stock).setScale(Money.SCALE)
    )

    @Test
    fun `totalsByType sums amount per type`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("v2", MovementType.VENTA, "50.00"),
            movement("g1", MovementType.GASTO, "20.00"),
            movement("c1", MovementType.COBRO, "30.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")

        assertEquals(BigDecimal("150.00"), closing.totalsByType.getValue(MovementType.VENTA))
        assertEquals(BigDecimal("20.00"), closing.totalsByType.getValue(MovementType.GASTO))
        assertEquals(BigDecimal("30.00"), closing.totalsByType.getValue(MovementType.COBRO))
        assertEquals(BigDecimal("0.00"), closing.totalsByType.getValue(MovementType.MERMA))
    }

    @Test
    fun `netCash is VENTA plus COBRO minus GASTO, excluding fiado merma alta entrada`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("c1", MovementType.COBRO, "40.00"),
            movement("g1", MovementType.GASTO, "10.00"),
            movement("f1", MovementType.VENTA_FIADO, "500.00"),
            movement("m1", MovementType.MERMA, "15.00"),
            movement("a1", MovementType.ALTA, "1000.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")

        // 100 + 40 - 10 = 130, ignoring fiado/merma/alta entirely
        assertEquals(BigDecimal("130.00"), closing.netCash)
    }

    @Test
    fun `stockSnapshot mirrors products with nonzero stock`() {
        val products = listOf(
            product("Arroz", "Lb", "10.00"),
            product("Frijoles", "Lb", "0.00"),
            product("Azucar", "Lb", "-2.00")
        )
        val closing = computeClosing("cl1", 2000L, emptyList(), products, "cup")

        assertEquals(2, closing.stockSnapshot.size)
        assertTrue(closing.stockSnapshot.any { it.name == "Arroz" && it.quantity == BigDecimal("10.00") })
        assertTrue(closing.stockSnapshot.any { it.name == "Azucar" && it.quantity == BigDecimal("-2.00") })
        assertTrue(closing.stockSnapshot.none { it.name == "Frijoles" })
    }

    @Test
    fun `empty selection yields zero totals, zero netCash, movementIds empty`() {
        val closing = computeClosing("cl1", 2000L, emptyList(), emptyList(), "cup")

        assertTrue(closing.movementIds.isEmpty())
        assertTrue(closing.stockSnapshot.isEmpty())
        assertEquals(BigDecimal("0.00"), closing.netCash)
        MovementType.entries.forEach { type ->
            assertEquals(BigDecimal("0.00"), closing.totalsByType.getValue(type))
        }
    }

    @Test
    fun `movementIds carries the ids of the movements given`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("v2", MovementType.VENTA, "50.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")
        assertEquals(listOf("v1", "v2"), closing.movementIds)
    }

    // ---- no double-close: openMovements pure filter ----

    @Test
    fun `openMovements excludes movements already stamped with a closingId`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00", closingId = null),
            movement("v2", MovementType.VENTA, "50.00", closingId = "cl0"),
            movement("v3", MovementType.VENTA, "10.00", currencyId = "usd", closingId = null)
        )
        val open = com.moneycounter.viewmodel.MoneyCounterViewModel.openMovementsPure(movements, "cup")
        assertEquals(listOf("v1"), open.map { it.id })
    }
}
