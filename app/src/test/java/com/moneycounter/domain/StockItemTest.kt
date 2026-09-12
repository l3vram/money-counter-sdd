package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class StockItemTest {

    private fun item(
        id: String = "si-1",
        orgId: String = "org-1",
        branchId: String = "br-1",
        productId: String = "p1",
        quantity: String = "10.00",
        updatedAt: Long = 1000L
    ) = StockItem(id, orgId, branchId, productId, BigDecimal(quantity), updatedAt)

    private fun product(id: String, stock: String = "10.00") =
        Product(id, "Name $id", "Lb", BigDecimal(stock), emptyMap())

    @Test
    fun `blank id throws`() {
        assertThrows(IllegalArgumentException::class.java) { item(id = " ") }
    }

    @Test
    fun `blank organization id throws`() {
        assertThrows(IllegalArgumentException::class.java) { item(orgId = "") }
    }

    @Test
    fun `blank branch id throws`() {
        assertThrows(IllegalArgumentException::class.java) { item(branchId = "") }
    }

    @Test
    fun `blank product id throws`() {
        assertThrows(IllegalArgumentException::class.java) { item(productId = "") }
    }

    @Test
    fun `negative quantity is warn-and-allow not rejected`() {
        val item = item(quantity = "-1.00")
        assertEquals(BigDecimal("-1.00"), item.quantity)
    }

    @Test
    fun `increase adds quantity only to the matched item`() {
        val items = listOf(item(productId = "p1", quantity = "10.00"), item(id = "si-2", productId = "p2"))
        val result = increaseStock(items, "p1", BigDecimal("2.50"), "org-1", "br-1")

        assertEquals(BigDecimal("12.50"), result[0].quantity)
        assertEquals(BigDecimal("10.00"), result[1].quantity)
    }

    @Test
    fun `decrease subtracts quantity only to the matched item`() {
        val items = listOf(item(productId = "p1", quantity = "10.00"), item(id = "si-2", productId = "p2"))
        val result = decreaseStock(items, "p1", BigDecimal("3.00"), "org-1", "br-1")

        assertEquals(BigDecimal("7.00"), result[0].quantity)
        assertEquals(BigDecimal("10.00"), result[1].quantity)
    }

    @Test
    fun `adjust sets absolute quantity on the matched item`() {
        val items = listOf(item(productId = "p1", quantity = "10.00"), item(id = "si-2", productId = "p2"))
        val result = adjustStock(items, "p1", BigDecimal("42.00"), "org-1", "br-1")

        assertEquals(BigDecimal("42.00"), result[0].quantity)
        assertEquals(BigDecimal("10.00"), result[1].quantity)
    }

    @Test
    fun `increase and decrease bump updatedAt only on the matched item`() {
        val items = listOf(item(productId = "p1", updatedAt = 100L), item(id = "si-2", productId = "p2", updatedAt = 100L))
        val result = increaseStock(items, "p1", BigDecimal("1.00"), "org-1", "br-1")

        assertTrue(result[0].updatedAt > 100L)
        assertEquals(100L, result[1].updatedAt)
    }

    @Test
    fun `unknown product id is a no-op for increase decrease and adjust`() {
        val items = listOf(item(), item(id = "si-2", productId = "p2"))
        assertEquals(items, increaseStock(items, "p9", BigDecimal("1.00"), "org-1", "br-1"))
        assertEquals(items, decreaseStock(items, "p9", BigDecimal("1.00"), "org-1", "br-1"))
        assertEquals(items, adjustStock(items, "p9", BigDecimal("1.00"), "org-1", "br-1"))
    }

    @Test
    fun `decrease past zero is warn-and-allow negative`() {
        val result = decreaseStock(listOf(item(quantity = "2.00")), "p1", BigDecimal("5.00"), "org-1", "br-1")
        assertEquals(BigDecimal("-3.00"), result.single().quantity)
    }

    @Test
    fun `increase can also push negative only via negative-free path and scales to two`() {
        val result = increaseStock(listOf(item(quantity = "1.00")), "p1", BigDecimal("-2.00"), "org-1", "br-1")
        assertEquals(BigDecimal("-1.00"), result.single().quantity)
    }

    @Test
    fun `same product across branches is scoped to the targeted branch only`() {
        val items = listOf(
            item(id = "si-a1", branchId = "br-1", productId = "p1", quantity = "10.00"),
            item(id = "si-b1", branchId = "br-2", productId = "p1", quantity = "10.00")
        )

        val increased = increaseStock(items, "p1", BigDecimal("2.50"), "org-1", "br-1")
        assertEquals(BigDecimal("12.50"), increased[0].quantity)
        assertEquals(BigDecimal("10.00"), increased[1].quantity)

        val decreased = decreaseStock(items, "p1", BigDecimal("3.00"), "org-1", "br-1")
        assertEquals(BigDecimal("7.00"), decreased[0].quantity)
        assertEquals(BigDecimal("10.00"), decreased[1].quantity)

        val adjusted = adjustStock(items, "p1", BigDecimal("42.00"), "org-1", "br-1")
        assertEquals(BigDecimal("42.00"), adjusted[0].quantity)
        assertEquals(BigDecimal("10.00"), adjusted[1].quantity)
    }

    @Test
    fun `backfill creates a row per product seeded with product stock`() {
        val items = listOf(item(productId = "p2"))
        val products = listOf(product("p1", "40.00"), product("p3", "3.50"))
        val result = backfillStock(items, products, "org-1", "br-1", now = 1234L)

        val p1 = result.firstOrNull { it.productId == "p1" }
        val p3 = result.firstOrNull { it.productId == "p3" }
        assertEquals(3, result.size)
        assertEquals(BigDecimal("40.00"), p1?.quantity)
        assertEquals(BigDecimal("3.50"), p3?.quantity)
        assertEquals("si-p1", p1?.id)
        assertEquals("org-1", p1?.organizationId)
        assertEquals("br-1", p1?.branchId)
        assertEquals(1234L, p1?.updatedAt)
    }

    @Test
    fun `backfill does not overwrite existing rows or touch other branches`() {
        val existing = listOf(item(productId = "p1", quantity = "99.00"), item(id = "si-x", branchId = "br-2", productId = "p1", quantity = "5.00"))
        val result = backfillStock(existing, listOf(product("p1", "10.00")), "org-1", "br-1", now = 9L)

        assertEquals(existing, result)
        assertEquals(BigDecimal("99.00"), result[0].quantity)
        assertEquals("br-2", result[1].branchId)
    }

    @Test
    fun `backfill is idempotent second pass creates none`() {
        val products = listOf(product("p1", "10.00"), product("p2", "20.00"))
        val first = backfillStock(emptyList(), products, "org-1", "br-1", now = 1L)
        val second = backfillStock(first, products, "org-1", "br-1", now = 2L)

        assertEquals(first, second)
        assertEquals(2, second.size)
    }

    @Test
    fun `withOrgId fills only blank organization ids`() {
        assertEquals("org-1", product("p1").withOrgId("org-1").organizationId)
        val already = Product("p1", "n", "Lb", Money.ZERO, emptyMap(), "org-9")
        assertEquals("org-9", already.withOrgId("org-1").organizationId)
        assertEquals("org-9", already.withOrgId("").organizationId)
    }

    @Test
    fun `stockForBranch maps product to quantity for the branch only`() {
        val items = listOf(
            item(productId = "p1", quantity = "10.00"),
            item(productId = "p2", quantity = "4.50"),
            item(id = "si-3", branchId = "br-2", productId = "p2", quantity = "77.00")
        )
        val map = stockForBranch(items, "br-1")

        assertEquals(BigDecimal("10.00"), map["p1"])
        assertEquals(BigDecimal("4.50"), map["p2"])
        assertEquals(2, map.size)
    }

    @Test
    fun `resolveBranchStock falls back to product stock when no row exists`() {
        val products = listOf(product("p1", "40.00"))
        val result = resolveBranchStock(emptyList(), products, "org-1", "br-1", "p1")

        assertEquals(BigDecimal("40.00"), result)
    }

    @Test
    fun `resolveBranchStock prefers the row for the current org and branch`() {
        val items = listOf(item(productId = "p1", quantity = "99.00"))
        val products = listOf(product("p1", "40.00"))
        val result = resolveBranchStock(items, products, "org-1", "br-1", "p1")

        assertEquals(BigDecimal("99.00"), result)
    }

    @Test
    fun `resolveBranchStock never leaks rows from unknown org or branch`() {
        val products = listOf(product("p1", "40.00"))
        val items = listOf(
            item(branchId = "br-2", productId = "p1", quantity = "55.00"),
            item(orgId = "org-9", productId = "p1", quantity = "66.00")
        )

        assertEquals(BigDecimal("40.00"), resolveBranchStock(items, products, "org-1", "br-1", "p1"))
        assertEquals(BigDecimal("55.00"), resolveBranchStock(items, products, "org-1", "br-2", "p1"))
        assertEquals(BigDecimal("66.00"), resolveBranchStock(items, products, "org-9", "br-1", "p1"))
    }

    @Test
    fun `resolveBranchStock unknown product returns zero`() {
        assertEquals(Money.ZERO, resolveBranchStock(emptyList(), emptyList(), "org-1", "br-1", "p9"))
    }
}