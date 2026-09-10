package com.moneycounter.domain

import com.moneycounter.repository.WriteoffJson
import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InventoryWriteoffTest {

    private fun product(id: String, stock: String) =
        Product(
            id,
            "Name $id",
            "Lb",
            BigDecimal(stock).setScale(Money.SCALE),
            prices = mapOf("cup" to ProductPrice(BigDecimal("2.00"), BigDecimal("0.50")))
        )

    private fun writeoff(
        id: String = "w1",
        at: Long = 1000L,
        productId: String = "p1",
        name: String = "Name p1",
        unit: String = "Lb",
        quantity: String = "2",
        unitPrice: String = "2.00",
        currencyId: String = "cup",
        reason: String? = null,
        sellerUid: String = "",
        sellerName: String = ""
    ): InventoryWriteoff {
        val qty = BigDecimal(quantity).setScale(Money.SCALE)
        val price = BigDecimal(unitPrice).setScale(Money.SCALE)
        return InventoryWriteoff(
            id = id,
            at = at,
            productId = productId,
            name = name,
            unit = unit,
            quantity = qty,
            unitPrice = price,
            lossValue = price.multiply(qty).setScale(Money.SCALE),
            currencyId = currencyId,
            reason = reason,
            sellerUid = sellerUid,
            sellerName = sellerName
        )
    }

    // ---- applyWriteoff ----

    @Test
    fun `applyWriteoff reduces the right product stock`() {
        val result = MoneyCounterViewModel.applyWriteoff(
            listOf(product("p1", "40")),
            "p1",
            BigDecimal("3")
        )
        assertEquals(BigDecimal("37.00"), result.single().stock)
    }

    @Test
    fun `applyWriteoff leaves other products untouched`() {
        val result = MoneyCounterViewModel.applyWriteoff(
            listOf(product("p1", "10"), product("p2", "20")),
            "p1",
            BigDecimal("3")
        )
        assertEquals(BigDecimal("7.00"), result[0].stock)
        assertEquals(BigDecimal("20.00"), result[1].stock)
    }

    @Test
    fun `applyWriteoff unknown id is no-op`() {
        val result = MoneyCounterViewModel.applyWriteoff(
            listOf(product("p1", "10")),
            "p9",
            BigDecimal("3")
        )
        assertEquals(BigDecimal("10.00"), result.single().stock)
    }

    @Test
    fun `applyWriteoff decimal quantity deducts to scale two`() {
        val result = MoneyCounterViewModel.applyWriteoff(
            listOf(product("p1", "10")),
            "p1",
            BigDecimal("1.5")
        )
        assertEquals(BigDecimal("8.50"), result.single().stock)
    }

    @Test
    fun `applyWriteoff over-writeoff pushes stock negative`() {
        val result = MoneyCounterViewModel.applyWriteoff(
            listOf(product("p1", "2")),
            "p1",
            BigDecimal("5")
        )
        assertEquals(BigDecimal("-3.00"), result.single().stock)
    }

    // ---- lossValue ----

    @Test
    fun `lossValue equals quantity times unitPrice at money scale`() {
        val quantity = BigDecimal("1.5").setScale(Money.SCALE)
        val unitPrice = BigDecimal("2.00").setScale(Money.SCALE)
        val loss = unitPrice.multiply(quantity).setScale(Money.SCALE)
        assertEquals(BigDecimal("3.00"), loss)
    }

    // ---- WriteoffJson round trip ----

    @Test
    fun `round trip preserves fields`() {
        val w = writeoff(reason = "Dañado por lluvia")
        val json = WriteoffJson.toJson(listOf(w))
        val loaded = WriteoffJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals(w.id, loaded[0].id)
        assertEquals(w.at, loaded[0].at)
        assertEquals(w.productId, loaded[0].productId)
        assertEquals(w.name, loaded[0].name)
        assertEquals(w.unit, loaded[0].unit)
        assertEquals(w.quantity, loaded[0].quantity)
        assertEquals(w.unitPrice, loaded[0].unitPrice)
        assertEquals(w.lossValue, loaded[0].lossValue)
        assertEquals(w.currencyId, loaded[0].currencyId)
        assertEquals(w.reason, loaded[0].reason)
    }

    @Test
    fun `round trip with null reason`() {
        val w = writeoff(reason = null)
        val loaded = WriteoffJson.fromJson(WriteoffJson.toJson(listOf(w)))
        assertEquals(1, loaded.size)
        assertEquals(null, loaded[0].reason)
    }

    @Test
    fun `round trip preserves author stamp`() {
        val w = writeoff(sellerUid = "seller-2", sellerName = "Vendedor Dos")
        val loaded = WriteoffJson.fromJson(WriteoffJson.toJson(listOf(w)))
        assertEquals("seller-2", loaded[0].sellerUid)
        assertEquals("Vendedor Dos", loaded[0].sellerName)
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """
            {
              "version": 1,
              "writeoffs": [
                {"id": "", "at": 1, "productId": "p1", "name": "N", "unit": "Lb", "quantity": "1", "unitPrice": "1.00", "lossValue": "1.00", "currencyId": "cup"},
                {"id": "ok", "at": 1, "productId": "p1", "name": "N", "unit": "Lb", "quantity": "1", "unitPrice": "1.00", "lossValue": "1.00", "currencyId": "cup"}
              ]
            }
        """.trimIndent()

        val loaded = WriteoffJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `unknown version returns empty list`() {
        val json = """{"version": 99, "writeoffs": []}"""
        assertTrue(WriteoffJson.fromJson(json).isEmpty())
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(WriteoffJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = writeoff(id = "a", at = 100L)
        val newer = writeoff(id = "b", at = 200L)
        val loaded = WriteoffJson.fromJson(WriteoffJson.toJson(listOf(older, newer)))
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }
}
