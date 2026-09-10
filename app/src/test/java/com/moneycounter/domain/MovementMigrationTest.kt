package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MovementMigrationTest {

    private fun savedProductItem(
        name: String = "Arroz",
        unit: String = "Lb",
        quantity: String = "2",
        unitPrice: String = "2.00",
        surcharge: String = "0.50",
        subtotal: String = "5.00"
    ) = SavedProductItem(
        name = name,
        unit = unit,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        unitPrice = BigDecimal(unitPrice).setScale(Money.SCALE),
        surcharge = BigDecimal(surcharge).setScale(Money.SCALE),
        subtotal = BigDecimal(subtotal).setScale(Money.SCALE)
    )

    private fun savedCount(
        id: String = "sc1",
        savedAt: Long = 1000L,
        targetAmount: String = "5.00",
        currencyId: String = "cup"
    ) = SavedCount(
        id = id,
        savedAt = savedAt,
        targetAmount = BigDecimal(targetAmount).setScale(Money.SCALE),
        items = listOf(SavedCountItem(500, 1, BigDecimal("5.00"))),
        products = listOf(savedProductItem()),
        currencyId = currencyId
    )

    private fun receivable(
        id: String = "r1",
        at: Long = 2000L,
        debtorName: String = "Juan",
        amount: String = "5.00",
        currencyId: String = "cup",
        status: ReceivableStatus = ReceivableStatus.OPEN,
        settledAt: Long? = null
    ) = Receivable(
        id = id,
        at = at,
        debtorName = debtorName,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        currencyId = currencyId,
        products = listOf(savedProductItem()),
        status = status,
        settledAt = settledAt
    )

    private fun payment(
        id: String = "p1",
        at: Long = 3000L,
        receivableId: String = "r1",
        debtorName: String = "Juan",
        amount: String = "5.00",
        currencyId: String = "cup"
    ) = Payment(
        id = id,
        at = at,
        receivableId = receivableId,
        debtorName = debtorName,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        currencyId = currencyId
    )

    private fun writeoff(
        id: String = "w1",
        at: Long = 4000L,
        productId: String = "prod1",
        name: String = "Azucar",
        unit: String = "Lb",
        quantity: String = "3",
        unitPrice: String = "1.00",
        lossValue: String = "3.00",
        currencyId: String = "cup",
        reason: String? = "spoiled"
    ) = InventoryWriteoff(
        id = id,
        at = at,
        productId = productId,
        name = name,
        unit = unit,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        unitPrice = BigDecimal(unitPrice).setScale(Money.SCALE),
        lossValue = BigDecimal(lossValue).setScale(Money.SCALE),
        currencyId = currencyId,
        reason = reason
    )

    @Test
    fun `empty legacy input returns empty list`() {
        val result = MovementMigration.fromLegacy(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `SavedCount maps to VENTA preserving denominations, products and id, amount from targetAmount`() {
        val sc = savedCount()
        val result = MovementMigration.fromLegacy(listOf(sc), emptyList(), emptyList(), emptyList())

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals(sc.id, m.id)
        assertEquals(sc.savedAt, m.at)
        assertEquals(MovementType.VENTA, m.type)
        assertEquals(sc.currencyId, m.currencyId)
        assertEquals(sc.targetAmount, m.amount)
        assertEquals(1, m.denominations.size)
        assertEquals(500L, m.denominations[0].value)
        assertEquals(1L, m.denominations[0].quantity)
        assertEquals(BigDecimal("5.00"), m.denominations[0].subtotal)
        assertEquals(1, m.products.size)
        assertEquals("Arroz", m.products[0].name)
        assertEquals(BigDecimal("5.00"), m.products[0].subtotal)
    }

    @Test
    fun `Receivable maps to VENTA_FIADO preserving id, no denominations`() {
        val r = receivable()
        val result = MovementMigration.fromLegacy(emptyList(), listOf(r), emptyList(), emptyList())

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals(r.id, m.id)
        assertEquals(r.at, m.at)
        assertEquals(MovementType.VENTA_FIADO, m.type)
        assertEquals(r.debtorName, m.concept)
        assertEquals(r.amount, m.amount)
        assertTrue(m.denominations.isEmpty())
        assertNull(m.linkId)
        assertEquals(1, m.products.size)
    }

    @Test
    fun `Payment maps to COBRO with linkId equal to receivableId, id preserved`() {
        val p = payment()
        val result = MovementMigration.fromLegacy(emptyList(), emptyList(), listOf(p), emptyList())

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals(p.id, m.id)
        assertEquals(p.at, m.at)
        assertEquals(MovementType.COBRO, m.type)
        assertEquals(p.debtorName, m.concept)
        assertEquals(p.amount, m.amount)
        assertEquals(p.receivableId, m.linkId)
        assertTrue(m.denominations.isEmpty())
    }

    @Test
    fun `InventoryWriteoff maps to MERMA with subtotal equal to lossValue`() {
        val w = writeoff()
        val result = MovementMigration.fromLegacy(emptyList(), emptyList(), emptyList(), listOf(w))

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals(w.id, m.id)
        assertEquals(w.at, m.at)
        assertEquals(MovementType.MERMA, m.type)
        assertEquals(w.reason, m.concept)
        assertEquals(w.lossValue, m.amount)
        assertEquals(1, m.products.size)
        assertEquals(w.lossValue, m.products[0].subtotal)
        assertEquals(w.name, m.products[0].name)
    }

    @Test
    fun `SETTLED receivable does not itself emit a COBRO (payments are the source of truth)`() {
        val settled = receivable(id = "r1", status = ReceivableStatus.SETTLED, settledAt = 5000L)
        // No corresponding Payment passed in this scenario on purpose.
        val result = MovementMigration.fromLegacy(emptyList(), listOf(settled), emptyList(), emptyList())

        assertEquals(1, result.size)
        assertEquals(MovementType.VENTA_FIADO, result[0].type)
        assertTrue(result.none { it.type == MovementType.COBRO })
    }

    @Test
    fun `settled receivable plus its payment yields exactly one VENTA_FIADO and one COBRO, no double count`() {
        val settled = receivable(id = "r1", status = ReceivableStatus.SETTLED, settledAt = 5000L)
        val pay = payment(id = "p1", receivableId = "r1")
        val result = MovementMigration.fromLegacy(emptyList(), listOf(settled), listOf(pay), emptyList())

        assertEquals(2, result.size)
        assertEquals(1, result.count { it.type == MovementType.VENTA_FIADO })
        assertEquals(1, result.count { it.type == MovementType.COBRO })
    }

    @Test
    fun `combined legacy sources are sorted by at descending`() {
        val sc = savedCount(id = "sc1", savedAt = 100L)
        val r = receivable(id = "r1", at = 300L)
        val p = payment(id = "p1", at = 200L, receivableId = "r1")
        val w = writeoff(id = "w1", at = 400L)

        val result = MovementMigration.fromLegacy(listOf(sc), listOf(r), listOf(p), listOf(w))

        assertEquals(listOf("w1", "r1", "p1", "sc1"), result.map { it.id })
    }
}
