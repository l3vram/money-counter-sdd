package com.moneycounter.domain

import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class StockDeductionTest {

    private val ORG_A = "org-a"
    private val ORG_B = "org-b"
    private val BRANCH_A = "branch-a"
    private val BRANCH_B = "branch-b"

    private fun product(id: String, stock: String) =
        Product(
            id,
            "Name $id",
            "Lb",
            BigDecimal(stock).setScale(Money.SCALE),
            prices = mapOf("cup" to ProductPrice(BigDecimal("2.00"), BigDecimal("0.50")))
        )

    private fun sel(productId: String, qty: String) = ProductSelection(productId = productId, quantityText = qty)

    private fun bd(value: String) = BigDecimal(value).setScale(Money.SCALE)

    private fun item(id: String, org: String, branch: String, productId: String, qty: String, now: Long) =
        StockItem(
            id = id,
            organizationId = org,
            branchId = branch,
            productId = productId,
            quantity = bd(qty),
            updatedAt = now
        )

    private fun row(items: List<StockItem>, org: String, branch: String, productId: String): StockItem? =
        items.firstOrNull { it.organizationId == org && it.branchId == branch && it.productId == productId }

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

    // ---- plan 022 dual-write consistency (Product.stock + StockItem lockstep) ----

    @Test
    fun `sale deducts equal amount from product stock and branch row`() {
        val products = listOf(product("p1", "40"))
        val items = listOf(item("si-p1", ORG_A, BRANCH_A, "p1", "40", 1L))
        val (newProducts, newItems) = MoneyCounterViewModel.applySaleToStock(
            products, items, listOf(sel("p1", "3")), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("37.00"), newProducts.single().stock)
        assertEquals(bd("37.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
        assertEquals(bd("40.00").subtract(newProducts.single().stock), bd("3.00"))
        assertEquals(
            row(items, ORG_A, BRANCH_A, "p1")!!.quantity.subtract(row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity),
            bd("3.00")
        )
    }

    @Test
    fun `multi-product sale keeps both stores in lockstep per product`() {
        val products = listOf(product("p1", "40"), product("p2", "20"))
        val items = listOf(
            item("si-p1", ORG_A, BRANCH_A, "p1", "40", 1L),
            item("si-p2", ORG_A, BRANCH_A, "p2", "20", 1L)
        )
        val (newProducts, newItems) = MoneyCounterViewModel.applySaleToStock(
            products, items, listOf(sel("p1", "2"), sel("p2", "4")), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("38.00"), newProducts.first { it.id == "p1" }.stock)
        assertEquals(bd("16.00"), newProducts.first { it.id == "p2" }.stock)
        assertEquals(bd("38.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
        assertEquals(bd("16.00"), row(newItems, ORG_A, BRANCH_A, "p2")!!.quantity)
    }

    @Test
    fun `writeoff decreases both stores by the same amount`() {
        val products = listOf(product("p1", "20"))
        val items = listOf(item("si-p1", ORG_A, BRANCH_A, "p1", "20", 1L))
        val (newProducts, newItems) = MoneyCounterViewModel.applyWriteoffToStock(
            products, items, "p1", bd("5"), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("15.00"), newProducts.single().stock)
        assertEquals(bd("15.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
    }

    @Test
    fun `addStock increases both stores by the same amount`() {
        val products = listOf(product("p1", "10"))
        val items = listOf(item("si-p1", ORG_A, BRANCH_A, "p1", "10", 1L))
        val (newProducts, newItems) = MoneyCounterViewModel.applyAddStockToStock(
            products, items, "p1", bd("3"), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("13.00"), newProducts.single().stock)
        assertEquals(bd("13.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
    }

    @Test
    fun `editProduct positive delta aligns both stores to new absolute stock`() {
        val products = listOf(product("p1", "12"))
        val items = listOf(item("si-p1", ORG_A, BRANCH_A, "p1", "5", 1L))
        val (newProducts, newItems) = MoneyCounterViewModel.applyEditProductToStock(
            products, items, "p1", ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("12.00"), newProducts.single().stock)
        assertEquals(bd("12.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
    }

    @Test
    fun `editProduct negative delta aligns both stores to reduced absolute stock`() {
        val products = listOf(product("p1", "3"))
        val items = listOf(item("si-p1", ORG_A, BRANCH_A, "p1", "5", 1L))
        val (newProducts, newItems) = MoneyCounterViewModel.applyEditProductToStock(
            products, items, "p1", ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("3.00"), newProducts.single().stock)
        assertEquals(bd("3.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
    }

    @Test
    fun `editProduct without branch row creates row seeded with absolute stock`() {
        val products = listOf(product("p1", "9"))
        val (_, newItems) = MoneyCounterViewModel.applyEditProductToStock(
            products, emptyList(), "p1", ORG_A, BRANCH_A, now = 7L
        )
        val created = row(newItems, ORG_A, BRANCH_A, "p1")
        assertNotNull(created)
        assertEquals(bd("9.00"), created!!.quantity)
        assertEquals("si-p1", created.id)
        assertEquals("p1", created.productId)
    }

    @Test
    fun `addProduct with initial stock seeds branch row equal to product stock`() {
        val products = listOf(product("p1", "7"))
        val (newProducts, newItems) = MoneyCounterViewModel.applyAddProductToStock(
            products, emptyList(), "p1", ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("7.00"), newProducts.single().stock)
        val created = row(newItems, ORG_A, BRANCH_A, "p1")
        assertNotNull(created)
        assertEquals(bd("7.00"), created!!.quantity)
    }

    @Test
    fun `deleteProduct drops current-branch row but keeps rows in other branches`() {
        val products = listOf(product("p1", "10"), product("p2", "3"))
        val items = listOf(
            item("si-p1", ORG_A, BRANCH_A, "p1", "10", 1L),
            item("si-p1", ORG_A, BRANCH_B, "p1", "7", 1L),
            item("si-p2", ORG_A, BRANCH_A, "p2", "3", 1L)
        )
        val (newProducts, newItems) = MoneyCounterViewModel.applyDeleteProductToStock(
            products, items, "p1", BRANCH_A
        )
        assertTrue(newProducts.none { it.id == "p1" })
        assertEquals(1, newProducts.size)
        assertNull(row(newItems, ORG_A, BRANCH_A, "p1"))
        assertEquals(bd("7.00"), row(newItems, ORG_A, BRANCH_B, "p1")!!.quantity)
        assertEquals(bd("3.00"), row(newItems, ORG_A, BRANCH_A, "p2")!!.quantity)
    }

    @Test
    fun `branch isolation sale never touches other branch rows`() {
        val products = listOf(product("p1", "40"), product("p2", "20"))
        val items = listOf(
            item("si-p1", ORG_A, BRANCH_A, "p1", "40", 1L),
            item("si-p2", ORG_A, BRANCH_B, "p2", "20", 1L)
        )
        val (newProducts, newItems) = MoneyCounterViewModel.applySaleToStock(
            products, items, listOf(sel("p1", "3")), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("37.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
        assertEquals(bd("20.00"), row(newItems, ORG_A, BRANCH_B, "p2")!!.quantity)
        assertEquals(bd("20.00"), newProducts.first { it.id == "p2" }.stock)
    }

    @Test
    fun `sale with missing current-branch row seeds row with post-deduction stock`() {
        val products = listOf(product("p1", "40"))
        val (newProducts, newItems) = MoneyCounterViewModel.applySaleToStock(
            products, emptyList(), listOf(sel("p1", "3")), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("37.00"), newProducts.single().stock)
        val created = row(newItems, ORG_A, BRANCH_A, "p1")
        assertNotNull(created)
        assertEquals(bd("37.00"), created!!.quantity)
        assertEquals("si-p1", created.id)
        assertEquals("p1", created.productId)
        assertEquals(ORG_A, created.organizationId)
        assertEquals(BRANCH_A, created.branchId)
    }

    @Test
    fun `writeoff with missing current-branch row seeds row with post-writeoff stock`() {
        val products = listOf(product("p1", "10"))
        val (newProducts, newItems) = MoneyCounterViewModel.applyWriteoffToStock(
            products, emptyList(), "p1", bd("2"), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("8.00"), newProducts.single().stock)
        val created = row(newItems, ORG_A, BRANCH_A, "p1")
        assertNotNull(created)
        assertEquals(bd("8.00"), created!!.quantity)
    }

    @Test
    fun `addStock with missing current-branch row seeds row with post-add stock without double increase`() {
        val products = listOf(product("p1", "10"))
        val (newProducts, newItems) = MoneyCounterViewModel.applyAddStockToStock(
            products, emptyList(), "p1", bd("3"), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("13.00"), newProducts.single().stock)
        val created = row(newItems, ORG_A, BRANCH_A, "p1")
        assertNotNull(created)
        assertEquals(bd("13.00"), created!!.quantity)
    }

    @Test
    fun `mutation in org A leaves org B rows untouched`() {
        val products = listOf(product("p1", "10"), product("p2", "5"))
        val items = listOf(
            item("si-p1", ORG_A, BRANCH_A, "p1", "10", 1L),
            item("si-p2", ORG_B, BRANCH_B, "p2", "5", 1L)
        )
        val (_, newItems) = MoneyCounterViewModel.applyWriteoffToStock(
            products, items, "p1", bd("2"), ORG_A, BRANCH_A, now = 7L
        )
        assertEquals(bd("8.00"), row(newItems, ORG_A, BRANCH_A, "p1")!!.quantity)
        assertEquals(bd("5.00"), row(newItems, ORG_B, BRANCH_B, "p2")!!.quantity)
    }
}