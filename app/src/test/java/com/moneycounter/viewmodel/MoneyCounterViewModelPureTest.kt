package com.moneycounter.viewmodel

import com.moneycounter.domain.Money
import com.moneycounter.domain.MovementDenomination
import com.moneycounter.domain.MovementProductLine
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.Receivable
import com.moneycounter.domain.ReceivableStatus
import com.moneycounter.domain.SavedProductItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests the real entry logic used by the fiado/cobro flows: the pure guard that
 * fixes the "fiado is unregisterable" bug (no COMPLETED requirement), the pure
 * journal-movement builders, and the pure settlement piece behind cobro.
 */
class MoneyCounterViewModelPureTest {

    private fun productLine(
        name: String = "Arroz",
        unit: String = "Lb",
        quantity: String = "2",
        unitPrice: String = "2.00",
        subtotal: String = "4.00"
    ) = MovementProductLine(
        name = name,
        unit = unit,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        unitPrice = BigDecimal(unitPrice).setScale(Money.SCALE),
        subtotal = BigDecimal(subtotal).setScale(Money.SCALE)
    )

    private fun denom(value: Long = 500, quantity: Long = 2, subtotal: String = "1000.00") =
        MovementDenomination(value = value, quantity = quantity, subtotal = BigDecimal(subtotal).setScale(Money.SCALE))

    private fun receivable(
        id: String = "r1",
        at: Long = 1000L,
        debtorName: String = "Juan",
        amount: String = "4.00",
        currencyId: String = "cup",
        status: ReceivableStatus = ReceivableStatus.OPEN
    ) = Receivable(
        id = id,
        at = at,
        debtorName = debtorName,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        currencyId = currencyId,
        products = listOf(
            SavedProductItem(
                name = "Arroz",
                unit = "Lb",
                quantity = BigDecimal("2.00"),
                unitPrice = BigDecimal("2.00"),
                subtotal = BigDecimal("4.00")
            )
        ),
        status = status
    )

    // ---- REGRESSION: the actual bug being fixed ----

    @Test
    fun `canRegisterCreditSale is true with products and a debtor name, no COMPLETED status required`() {
        assertTrue(MoneyCounterViewModel.canRegisterCreditSale(true, "Juan"))
    }

    @Test
    fun `canRegisterCreditSale is false when debtor name is blank`() {
        assertFalse(MoneyCounterViewModel.canRegisterCreditSale(true, ""))
        assertFalse(MoneyCounterViewModel.canRegisterCreditSale(true, "   "))
    }

    @Test
    fun `canRegisterCreditSale is false when there is no products total`() {
        assertFalse(MoneyCounterViewModel.canRegisterCreditSale(false, "Juan"))
    }

    // ---- buildFiadoMovement ----

    @Test
    fun `buildFiadoMovement produces VENTA_FIADO with no denominations, amount equal to products total, concept equal to debtor`() {
        val m = MoneyCounterViewModel.buildFiadoMovement(
            id = "r1",
            at = 1000L,
            currencyId = "cup",
            debtorName = "Juan",
            products = listOf(productLine()),
            amount = BigDecimal("4.00")
        )
        assertEquals(MovementType.VENTA_FIADO, m.type)
        assertTrue(m.denominations.isEmpty())
        assertEquals(BigDecimal("4.00"), m.amount)
        assertEquals("Juan", m.concept)
        assertEquals(1, m.products.size)
        assertNull(m.linkId)
    }

    // ---- buildVentaMovement ----

    @Test
    fun `buildVentaMovement produces VENTA with denominations and products`() {
        val m = MoneyCounterViewModel.buildVentaMovement(
            id = "sc1",
            at = 1000L,
            currencyId = "cup",
            products = listOf(productLine()),
            denominations = listOf(denom()),
            amount = BigDecimal("4.00")
        )
        assertEquals(MovementType.VENTA, m.type)
        assertEquals(1, m.denominations.size)
        assertEquals(1, m.products.size)
        assertEquals(BigDecimal("4.00"), m.amount)
    }

    // ---- buildMermaMovement ----

    @Test
    fun `buildMermaMovement produces MERMA carrying the loss product line`() {
        val m = MoneyCounterViewModel.buildMermaMovement(
            id = "w1",
            at = 1000L,
            currencyId = "cup",
            reason = "spoiled",
            products = listOf(productLine()),
            amount = BigDecimal("4.00")
        )
        assertEquals(MovementType.MERMA, m.type)
        assertEquals("spoiled", m.concept)
        assertEquals(1, m.products.size)
        assertTrue(m.denominations.isEmpty())
    }

    // ---- buildCobroMovement ----

    @Test
    fun `buildCobroMovement produces COBRO with denominations and linkId`() {
        val m = MoneyCounterViewModel.buildCobroMovement(
            id = "p1",
            at = 1000L,
            currencyId = "cup",
            debtorName = "Juan",
            denominations = listOf(denom()),
            amount = BigDecimal("4.00"),
            linkId = "r1"
        )
        assertEquals(MovementType.COBRO, m.type)
        assertEquals(1, m.denominations.size)
        assertEquals("r1", m.linkId)
        assertEquals("Juan", m.concept)
    }

    // ---- Cobro end-to-end pure path: settleReceivablePure + buildCobroMovement ----

    @Test
    fun `collection path settles an OPEN receivable and yields a matching COBRO movement`() {
        val open = receivable(amount = "1000.00")
        val (updated, payment) = MoneyCounterViewModel.settleReceivablePure(
            receivables = listOf(open),
            receivableId = open.id,
            paymentId = "p1",
            now = 2000L
        )

        requireNotNull(payment)
        assertEquals(open.amount, payment.amount)
        assertEquals(open.id, payment.receivableId)

        val settled = updated.first { it.id == open.id }
        assertEquals(ReceivableStatus.SETTLED, settled.status)
        assertEquals(2000L, settled.settledAt)

        val counted = listOf(denom(value = 500, quantity = 2, subtotal = "1000.00"))
        val movement = MoneyCounterViewModel.buildCobroMovement(
            id = payment.id,
            at = payment.at,
            currencyId = payment.currencyId,
            debtorName = payment.debtorName,
            denominations = counted,
            amount = payment.amount,
            linkId = payment.receivableId
        )
        assertEquals(MovementType.COBRO, movement.type)
        assertEquals(open.amount, movement.amount)
        assertTrue(movement.denominations.isNotEmpty())
        assertEquals(open.id, movement.linkId)
    }

    @Test
    fun `collection path is a no-op for a receivable that is not OPEN`() {
        val settled = receivable(status = ReceivableStatus.SETTLED)
        val (updated, payment) = MoneyCounterViewModel.settleReceivablePure(
            receivables = listOf(settled),
            receivableId = settled.id,
            paymentId = "p1",
            now = 2000L
        )
        assertNull(payment)
        assertEquals(listOf(settled), updated)
    }
}
