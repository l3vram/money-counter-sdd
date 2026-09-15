package com.moneycounter.viewmodel

import com.moneycounter.domain.Money
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementDenomination
import com.moneycounter.domain.MovementProductLine
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductPrice
import com.moneycounter.domain.ProductSelection
import com.moneycounter.domain.StockItem
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

    private fun fiadoMovement(
        id: String = "r1",
        at: Long = 1000L,
        currencyId: String = "cup",
        debtorName: String = "Juan",
        amount: String = "4.00"
    ) = MoneyCounterViewModel.buildFiadoMovement(
        id = id,
        at = at,
        currencyId = currencyId,
        debtorName = debtorName,
        products = listOf(productLine()),
        amount = BigDecimal(amount).setScale(Money.SCALE)
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

    // ---- isFiadoOpen / openFiadoMovementsPure ----

    @Test
    fun `isFiadoOpen is true for a VENTA_FIADO with no linking COBRO`() {
        val fiado = fiadoMovement(id = "r1")
        assertTrue(MoneyCounterViewModel.isFiadoOpen(fiado, cobroLinkIds = emptySet()))
    }

    @Test
    fun `isFiadoOpen is false once a COBRO links to it`() {
        val fiado = fiadoMovement(id = "r1")
        assertFalse(MoneyCounterViewModel.isFiadoOpen(fiado, cobroLinkIds = setOf("r1")))
    }

    @Test
    fun `isFiadoOpen is false for a non-fiado movement type`() {
        val venta = MoneyCounterViewModel.buildVentaMovement(
            id = "sc1",
            at = 1000L,
            currencyId = "cup",
            products = listOf(productLine()),
            denominations = listOf(denom()),
            amount = BigDecimal("4.00")
        )
        assertFalse(MoneyCounterViewModel.isFiadoOpen(venta, cobroLinkIds = emptySet()))
    }

    @Test
    fun `openFiadoMovementsPure excludes a fiado already linked by a COBRO`() {
        val open = fiadoMovement(id = "r1")
        val closed = fiadoMovement(id = "r2")
        val cobro = MoneyCounterViewModel.buildCobroMovement(
            id = "p1",
            at = 2000L,
            currencyId = "cup",
            debtorName = "Juan",
            denominations = listOf(denom()),
            amount = BigDecimal("4.00"),
            linkId = "r2"
        )
        val movements = listOf(cobro, open, closed)
        val result = MoneyCounterViewModel.openFiadoMovementsPure(movements, currencyId = "cup")
        assertEquals(listOf(open), result)
    }

    @Test
    fun `openFiadoMovementsPure includes a fiado with no linking COBRO`() {
        val open = fiadoMovement(id = "r1")
        val result = MoneyCounterViewModel.openFiadoMovementsPure(listOf(open), currencyId = "cup")
        assertEquals(listOf(open), result)
    }

    @Test
    fun `openFiadoMovementsPure is currency-filtered`() {
        val cup = fiadoMovement(id = "r1", currencyId = "cup")
        val usd = fiadoMovement(id = "r2", currencyId = "usd")
        val result = MoneyCounterViewModel.openFiadoMovementsPure(listOf(cup, usd), currencyId = "cup")
        assertEquals(listOf(cup), result)
    }

    // ---- Cobro end-to-end pure path: fiado Movement + buildCobroMovement ----

    @Test
    fun `collecting an OPEN fiado yields a matching COBRO movement linked to it`() {
        val fiado = fiadoMovement(id = "r1", amount = "1000.00")
        val counted = listOf(denom(value = 500, quantity = 2, subtotal = "1000.00"))
        val movement = MoneyCounterViewModel.buildCobroMovement(
            id = "p1",
            at = 2000L,
            currencyId = fiado.currencyId,
            debtorName = fiado.concept.orEmpty(),
            denominations = counted,
            products = fiado.products,
            amount = fiado.amount,
            linkId = fiado.id
        )
        assertEquals(MovementType.COBRO, movement.type)
        assertEquals(fiado.amount, movement.amount)
        assertTrue(movement.denominations.isNotEmpty())
        assertEquals(fiado.products, movement.products)
        assertEquals(fiado.id, movement.linkId)

        // once collected, the fiado is no longer OPEN
        assertFalse(
            MoneyCounterViewModel.isFiadoOpen(fiado, cobroLinkIds = setOf(movement.linkId!!))
        )
    }

    // ---- buildExpenseMovement ----

    @Test
    fun `buildExpenseMovement produces GASTO with no products or denominations and preserved concept`() {
        val m = MoneyCounterViewModel.buildExpenseMovement(
            id = "g1",
            at = 1000L,
            currencyId = "cup",
            concept = "pago por descarga",
            amount = BigDecimal("50.00")
        )
        assertEquals(MovementType.GASTO, m.type)
        assertTrue(m.products.isEmpty())
        assertTrue(m.denominations.isEmpty())
        assertEquals(BigDecimal("50.00"), m.amount)
        assertEquals("pago por descarga", m.concept)
    }

    // ---- buildStockInMovement ----

    @Test
    fun `buildStockInMovement produces ALTA carrying the product line and no denominations`() {
        val line = productLine(quantity = "10", unitPrice = "2.00", subtotal = "20.00")
        val m = MoneyCounterViewModel.buildStockInMovement(
            id = "a1",
            at = 1000L,
            type = MovementType.ALTA,
            currencyId = "cup",
            productLine = line,
            amount = BigDecimal("20.00")
        )
        assertEquals(MovementType.ALTA, m.type)
        assertEquals(listOf(line), m.products)
        assertTrue(m.denominations.isEmpty())
        assertEquals(BigDecimal("20.00"), m.amount)
    }

    @Test
    fun `buildStockInMovement produces ENTRADA carrying the product line and no denominations`() {
        val line = productLine(quantity = "5", unitPrice = "2.00", subtotal = "10.00")
        val m = MoneyCounterViewModel.buildStockInMovement(
            id = "e1",
            at = 1000L,
            type = MovementType.ENTRADA,
            currencyId = "cup",
            productLine = line,
            amount = BigDecimal("10.00")
        )
        assertEquals(MovementType.ENTRADA, m.type)
        assertEquals(listOf(line), m.products)
        assertTrue(m.denominations.isEmpty())
        assertEquals(BigDecimal("10.00"), m.amount)
    }

    // ---- stockInDelta ----

    @Test
    fun `stockInDelta returns the positive difference when stock increases`() {
        val delta = MoneyCounterViewModel.stockInDelta(BigDecimal("5.00"), BigDecimal("8.00"))
        assertEquals(BigDecimal("3.00"), delta)
    }

    @Test
    fun `stockInDelta returns ZERO when stock does not increase`() {
        assertEquals(Money.ZERO, MoneyCounterViewModel.stockInDelta(BigDecimal("5.00"), BigDecimal("5.00")))
        assertEquals(Money.ZERO, MoneyCounterViewModel.stockInDelta(BigDecimal("5.00"), BigDecimal("2.00")))
    }

    // ---- plan 020: tenant propagation in builders ----

    @Test
    fun `buildVentaMovement propagates organizationId and branchId`() {
        val m = MoneyCounterViewModel.buildVentaMovement(
            id = "sc1",
            at = 1000L,
            currencyId = "cup",
            products = listOf(productLine()),
            denominations = listOf(denom()),
            amount = BigDecimal("4.00"),
            organizationId = "org-1",
            branchId = "br-1"
        )
        assertEquals("org-1", m.organizationId)
        assertEquals("br-1", m.branchId)
    }

    @Test
    fun `buildFiadoMovement defaults tenant to blanks`() {
        val m = MoneyCounterViewModel.buildFiadoMovement(
            id = "r1",
            at = 1000L,
            currencyId = "cup",
            debtorName = "Juan",
            products = listOf(productLine()),
            amount = BigDecimal("4.00")
        )
        assertEquals("", m.organizationId)
        assertEquals("", m.branchId)
    }

    @Test
    fun `buildCobroMovement propagates organizationId and branchId`() {
        val m = MoneyCounterViewModel.buildCobroMovement(
            id = "p1",
            at = 2000L,
            currencyId = "cup",
            debtorName = "Juan",
            denominations = listOf(denom()),
            amount = BigDecimal("4.00"),
            linkId = "r1",
            organizationId = "org-2",
            branchId = "br-2"
        )
        assertEquals("org-2", m.organizationId)
        assertEquals("br-2", m.branchId)
    }

    // ---- plan 022 dual-write consistency (companion transforms) ----

    private fun bd(value: String) = BigDecimal(value).setScale(Money.SCALE)

    private fun stockProduct(id: String, stock: String) = Product(
        id,
        "Name $id",
        "Lb",
        bd(stock),
        prices = mapOf("cup" to ProductPrice(bd("2.00"), bd("0.50")))
    )

    private fun stockRow(id: String, org: String, branch: String, productId: String, qty: String) =
        StockItem(id, org, branch, productId, bd(qty), 1L)

    private fun row(items: List<StockItem>, org: String, branch: String, productId: String): StockItem? =
        items.firstOrNull { it.organizationId == org && it.branchId == branch && it.productId == productId }

    @Test
    fun `sale transform keeps both stores in lockstep`() {
        val products = listOf(stockProduct("p1", "40"))
        val items = listOf(stockRow("si-p1", "org-a", "branch-a", "p1", "40"))
        val (newProducts, newItems) = MoneyCounterViewModel.applySaleToStock(
            products, items,
            listOf(ProductSelection(productId = "p1", quantityText = "3")),
            "org-a", "branch-a", now = 9L
        )
        assertEquals(bd("37.00"), newProducts.single().stock)
        assertEquals(bd("37.00"), row(newItems, "org-a", "branch-a", "p1")!!.quantity)
    }

    @Test
    fun `writeoff transform keeps both stores in lockstep`() {
        val products = listOf(stockProduct("p1", "20"))
        val items = listOf(stockRow("si-p1", "org-a", "branch-a", "p1", "20"))
        val (newProducts, newItems) = MoneyCounterViewModel.applyWriteoffToStock(
            products, items, "p1", bd("5"), "org-a", "branch-a", now = 9L
        )
        assertEquals(bd("15.00"), newProducts.single().stock)
        assertEquals(bd("15.00"), row(newItems, "org-a", "branch-a", "p1")!!.quantity)
    }

    @Test
    fun `addStock transform keeps both stores in lockstep and ignores other branches`() {
        val products = listOf(stockProduct("p1", "10"), stockProduct("p2", "20"))
        val items = listOf(
            stockRow("si-p1", "org-a", "branch-a", "p1", "10"),
            stockRow("si-p2", "org-a", "branch-b", "p2", "20")
        )
        val (newProducts, newItems) = MoneyCounterViewModel.applyAddStockToStock(
            products, items, "p1", bd("3"), "org-a", "branch-a", now = 9L
        )
        assertEquals(bd("13.00"), newProducts.first { it.id == "p1" }.stock)
        assertEquals(bd("13.00"), row(newItems, "org-a", "branch-a", "p1")!!.quantity)
        assertEquals(bd("20.00"), row(newItems, "org-a", "branch-b", "p2")!!.quantity)
    }

    @Test
    fun `editProduct transform aligns both stores to the edited absolute stock`() {
        val products = listOf(stockProduct("p1", "25"))
        val items = listOf(stockRow("si-p1", "org-a", "branch-a", "p1", "30"))
        val (newProducts, newItems) = MoneyCounterViewModel.applyEditProductToStock(
            products, items, "p1", "org-a", "branch-a", now = 9L
        )
        assertEquals(bd("25.00"), newProducts.single().stock)
        assertEquals(bd("25.00"), row(newItems, "org-a", "branch-a", "p1")!!.quantity)
    }

    // ---- Plan 036: the initial state stops claiming what it does not know ----

    @Test
    fun `a fresh MoneyCounterUiState starts in isLoadingData true`() {
        assertTrue(MoneyCounterUiState().isLoadingData)
    }
}
