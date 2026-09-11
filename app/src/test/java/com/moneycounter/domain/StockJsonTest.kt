package com.moneycounter.domain

import com.moneycounter.repository.StockJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class StockJsonTest {

    private fun item(
        id: String = "si-p1",
        orgId: String = "org-1",
        branchId: String = "br-1",
        productId: String = "p1",
        quantity: String = "10.00",
        updatedAt: Long = 1234L
    ) = StockItem(id, orgId, branchId, productId, BigDecimal(quantity), updatedAt)

    @Test
    fun `round trip preserves every field`() {
        val items = listOf(
            item(quantity = "10.00"),
            item(id = "si-p2", branchId = "br-2", productId = "p2", quantity = "0.55", updatedAt = 99L)
        )

        val loaded = StockJson.fromJson(StockJson.toJson(items))

        assertEquals(2, loaded.size)
        assertEquals(items, loaded)
    }

    @Test
    fun `malformed rows are skipped but valid rows survive`() {
        val json = """
            {
              "version": 1,
              "stock": [
                {"id": "si-p1", "organizationId": "org-1", "branchId": "br-1", "productId": "p1", "quantity": "10.00", "updatedAt": 1},
                {"id": "", "organizationId": "org-1", "branchId": "br-1", "productId": "p2", "quantity": "1.00", "updatedAt": 2},
                {"id": "si-p3", "organizationId": "org-1", "branchId": "br-1", "productId": "p3", "quantity": "nope", "updatedAt": 3},
                {"id": "si-p4", "organizationId": "org-1", "branchId": "br-1", "productId": "p4", "quantity": "-2.00", "updatedAt": 4},
                {"id": "si-p5", "organizationId": "org-1", "branchId": "br-1", "productId": "p5", "quantity": "3.00", "updatedAt": 5}
              ]
            }
        """.trimIndent()

        val loaded = StockJson.fromJson(json)

        assertEquals(2, loaded.size)
        assertEquals("p1", loaded[0].productId)
        assertEquals("p5", loaded[1].productId)
    }

    @Test
    fun `version mismatch returns empty list`() {
        val json = """
            {
              "version": 2,
              "stock": [
                {"id": "si-p1", "organizationId": "org-1", "branchId": "br-1", "productId": "p1", "quantity": "10.00", "updatedAt": 1}
              ]
            }
        """.trimIndent()

        assertTrue(StockJson.fromJson(json).isEmpty())
    }

    @Test
    fun `blank input returns empty list`() {
        assertTrue(StockJson.fromJson("").isEmpty())
        assertTrue(StockJson.fromJson("not json").isEmpty())
    }

    @Test
    fun `negative quantity row never round trips`() {
        val json = """
            {
              "version": 1,
              "stock": [
                {"id": "neg", "organizationId": "org-1", "branchId": "br-1", "productId": "p9", "quantity": "-5.00", "updatedAt": 1}
              ]
            }
        """.trimIndent()

        assertTrue(StockJson.fromJson(json).isEmpty())
    }
}