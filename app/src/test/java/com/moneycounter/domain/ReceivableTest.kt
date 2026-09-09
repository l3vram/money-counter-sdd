package com.moneycounter.domain

import com.moneycounter.repository.ReceivableJson
import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReceivableTest {

    private fun product(id: String, stock: String) =
        Product(
            id,
            "Name $id",
            "Lb",
            BigDecimal(stock).setScale(Money.SCALE),
            prices = mapOf("cup" to ProductPrice(BigDecimal("2.00"), BigDecimal("0.50")))
        )

    private fun sel(productId: String, qty: String) = ProductSelection(productId = productId, quantityText = qty)

    private fun savedProductItem(
        name: String = "Name p1",
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

    private fun receivable(
        id: String = "r1",
        at: Long = 1000L,
        debtorName: String = "Juan Perez",
        amount: String = "5.00",
        currencyId: String = "cup",
        products: List<SavedProductItem> = listOf(savedProductItem()),
        status: ReceivableStatus = ReceivableStatus.OPEN,
        settledAt: Long? = null
    ) = Receivable(
        id = id,
        at = at,
        debtorName = debtorName,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        currencyId = currencyId,
        products = products,
        status = status,
        settledAt = settledAt
    )

    // ---- domain invariants ----

    @Test
    fun `blank id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            receivable(id = "")
        }
    }

    @Test
    fun `blank debtor name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            receivable(debtorName = "  ")
        }
    }

    @Test
    fun `zero amount is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            receivable(amount = "0.00")
        }
    }

    // ---- credit sale must deduct stock but not create SavedCount / cash ----

    @Test
    fun `credit sale deducts stock same as a cash sale via applyStockDeduction`() {
        val result = MoneyCounterViewModel.applyStockDeduction(
            listOf(product("p1", "40")),
            listOf(sel("p1", "3"))
        )
        assertEquals(BigDecimal("37.00"), result.single().stock)
    }

    @Test
    fun `receivable amount matches sum of product subtotals (no cash total involved)`() {
        val items = listOf(
            savedProductItem(name = "A", subtotal = "5.00"),
            savedProductItem(name = "B", subtotal = "3.00")
        )
        val total = items.fold(Money.ZERO) { acc, i -> acc.add(i.subtotal) }.setScale(Money.SCALE)
        val r = receivable(amount = total.toPlainString(), products = items)
        assertEquals(BigDecimal("8.00"), r.amount)
        assertEquals(ReceivableStatus.OPEN, r.status)
        assertNull(r.settledAt)
    }

    // ---- ReceivableJson round trip ----

    @Test
    fun `round trip preserves fields including products snapshot`() {
        val r = receivable(
            products = listOf(
                savedProductItem(name = "Arroz", unit = "Lb", quantity = "2", unitPrice = "2.00", surcharge = "0.50", subtotal = "5.00")
            )
        )
        val loaded = ReceivableJson.fromJson(ReceivableJson.toJson(listOf(r)))

        assertEquals(1, loaded.size)
        val l = loaded[0]
        assertEquals(r.id, l.id)
        assertEquals(r.at, l.at)
        assertEquals(r.debtorName, l.debtorName)
        assertEquals(r.amount, l.amount)
        assertEquals(r.currencyId, l.currencyId)
        assertEquals(r.status, l.status)
        assertNull(l.settledAt)
        assertEquals(1, l.products.size)
        assertEquals("Arroz", l.products[0].name)
        assertEquals(BigDecimal("2.00"), l.products[0].quantity)
        assertEquals(BigDecimal("2.00"), l.products[0].unitPrice)
        assertEquals(BigDecimal("0.50"), l.products[0].surcharge)
        assertEquals(BigDecimal("5.00"), l.products[0].subtotal)
    }

    @Test
    fun `round trip preserves settledAt when present`() {
        val r = receivable(status = ReceivableStatus.SETTLED, settledAt = 2000L)
        val loaded = ReceivableJson.fromJson(ReceivableJson.toJson(listOf(r)))
        assertEquals(1, loaded.size)
        assertEquals(ReceivableStatus.SETTLED, loaded[0].status)
        assertEquals(2000L, loaded[0].settledAt)
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """
            {
              "version": 1,
              "receivables": [
                {"id": "", "at": 1, "debtorName": "X", "amount": "5.00", "currencyId": "cup", "status": "OPEN", "settledAt": null, "products": []},
                {"id": "ok", "at": 1, "debtorName": "X", "amount": "5.00", "currencyId": "cup", "status": "OPEN", "settledAt": null, "products": []}
              ]
            }
        """.trimIndent()

        val loaded = ReceivableJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `unknown version returns empty list`() {
        val json = """{"version": 99, "receivables": []}"""
        assertTrue(ReceivableJson.fromJson(json).isEmpty())
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(ReceivableJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = receivable(id = "a", at = 100L)
        val newer = receivable(id = "b", at = 200L)
        val loaded = ReceivableJson.fromJson(ReceivableJson.toJson(listOf(older, newer)))
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }
}
