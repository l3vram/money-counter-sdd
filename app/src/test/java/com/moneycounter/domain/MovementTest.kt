package com.moneycounter.domain

import com.moneycounter.repository.MovementJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MovementTest {

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

    private fun movement(
        id: String = "m1",
        at: Long = 1000L,
        type: MovementType = MovementType.VENTA,
        currencyId: String = "cup",
        concept: String? = null,
        products: List<MovementProductLine> = listOf(productLine()),
        denominations: List<MovementDenomination> = listOf(denom()),
        amount: String = "4.00",
        linkId: String? = null,
        closingId: String? = null,
        sellerUid: String = "",
        sellerName: String = ""
    ) = Movement(
        id = id,
        at = at,
        type = type,
        currencyId = currencyId,
        concept = concept,
        products = products,
        denominations = denominations,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        linkId = linkId,
        closingId = closingId,
        sellerUid = sellerUid,
        sellerName = sellerName
    )

    // ---- invariants ----

    @Test
    fun `blank id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { movement(id = "") }
    }

    @Test
    fun `negative amount is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { movement(amount = "-1.00") }
    }

    @Test
    fun `zero amount is allowed`() {
        val m = movement(amount = "0.00")
        assertEquals(BigDecimal("0.00"), m.amount)
    }

    // ---- helpers ----

    @Test
    fun `affectsStockSign is correct per type`() {
        assertEquals(1, MovementType.ALTA.affectsStockSign())
        assertEquals(1, MovementType.ENTRADA.affectsStockSign())
        assertEquals(-1, MovementType.MERMA.affectsStockSign())
        assertEquals(-1, MovementType.VENTA.affectsStockSign())
        assertEquals(-1, MovementType.VENTA_FIADO.affectsStockSign())
        assertEquals(0, MovementType.GASTO.affectsStockSign())
        assertEquals(0, MovementType.COBRO.affectsStockSign())
    }

    @Test
    fun `hasDenominations is true only for VENTA and COBRO`() {
        assertTrue(MovementType.VENTA.hasDenominations())
        assertTrue(MovementType.COBRO.hasDenominations())
        assertTrue(!MovementType.ALTA.hasDenominations())
        assertTrue(!MovementType.ENTRADA.hasDenominations())
        assertTrue(!MovementType.GASTO.hasDenominations())
        assertTrue(!MovementType.MERMA.hasDenominations())
        assertTrue(!MovementType.VENTA_FIADO.hasDenominations())
    }

    @Test
    fun `cashSign is correct per type`() {
        assertEquals(1, MovementType.VENTA.cashSign())
        assertEquals(1, MovementType.COBRO.cashSign())
        assertEquals(-1, MovementType.GASTO.cashSign())
        assertEquals(-1, MovementType.MERMA.cashSign())
        assertNull(MovementType.ALTA.cashSign())
        assertNull(MovementType.ENTRADA.cashSign())
        assertNull(MovementType.VENTA_FIADO.cashSign())
    }

    @Test
    fun `netCashTotal adds VENTA and COBRO, subtracts GASTO and MERMA, excludes the rest`() {
        val list = listOf(
            movement(id = "v", type = MovementType.VENTA, amount = "100.00"),
            movement(id = "c", type = MovementType.COBRO, amount = "50.00"),
            movement(id = "g", type = MovementType.GASTO, amount = "30.00"),
            movement(id = "m", type = MovementType.MERMA, amount = "10.00"),
            movement(id = "f", type = MovementType.VENTA_FIADO, amount = "999.00"),
            movement(id = "a", type = MovementType.ALTA, amount = "999.00"),
            movement(id = "e", type = MovementType.ENTRADA, amount = "999.00")
        )
        assertEquals(BigDecimal("110.00").setScale(Money.SCALE), netCashTotal(list))
    }

    @Test
    fun `netCashTotal of empty list is zero`() {
        assertEquals(BigDecimal.ZERO, netCashTotal(emptyList()))
    }

    @Test
    fun `receivableTotal sums only VENTA_FIADO`() {
        val list = listOf(
            movement(id = "v", type = MovementType.VENTA, amount = "100.00"),
            movement(id = "f1", type = MovementType.VENTA_FIADO, amount = "40.00"),
            movement(id = "f2", type = MovementType.VENTA_FIADO, amount = "60.00")
        )
        assertEquals(BigDecimal("100.00").setScale(Money.SCALE), receivableTotal(list))
    }

    // ---- MovementJson round trip ----

    @Test
    fun `round trip preserves all fields for every type incl products, denominations, linkId, closingId`() {
        val types = MovementType.entries
        val movements = types.mapIndexed { i, t ->
            movement(
                id = "id-$i",
                at = 1000L + i,
                type = t,
                concept = if (i % 2 == 0) "concept-$i" else null,
                linkId = if (i % 3 == 0) "link-$i" else null,
                closingId = if (i % 4 == 0) "closing-$i" else null
            )
        }
        val loaded = MovementJson.fromJson(MovementJson.toJson(movements))
        assertEquals(movements.size, loaded.size)

        val byId = loaded.associateBy { it.id }
        for (original in movements) {
            val l = byId.getValue(original.id)
            assertEquals(original.at, l.at)
            assertEquals(original.type, l.type)
            assertEquals(original.currencyId, l.currencyId)
            assertEquals(original.concept, l.concept)
            assertEquals(original.amount, l.amount)
            assertEquals(original.linkId, l.linkId)
            assertEquals(original.closingId, l.closingId)
            assertEquals(original.products.size, l.products.size)
            if (original.products.isNotEmpty()) {
                assertEquals(original.products[0].name, l.products[0].name)
                assertEquals(original.products[0].unit, l.products[0].unit)
                assertEquals(original.products[0].quantity, l.products[0].quantity)
                assertEquals(original.products[0].unitPrice, l.products[0].unitPrice)
                assertEquals(original.products[0].subtotal, l.products[0].subtotal)
            }
            assertEquals(original.denominations.size, l.denominations.size)
            if (original.denominations.isNotEmpty()) {
                assertEquals(original.denominations[0].value, l.denominations[0].value)
                assertEquals(original.denominations[0].quantity, l.denominations[0].quantity)
                assertEquals(original.denominations[0].subtotal, l.denominations[0].subtotal)
            }
        }
    }

    @Test
    fun `round trip preserves author stamp`() {
        val m = movement(sellerUid = "seller-1", sellerName = "Vendedor Uno")
        val loaded = MovementJson.fromJson(MovementJson.toJson(listOf(m)))
        assertEquals("seller-1", loaded[0].sellerUid)
        assertEquals("Vendedor Uno", loaded[0].sellerName)
    }

    @Test
    fun `missing author fields default to empty`() {
        val json = """
            {
              "version": 1,
              "movements": [
                {"id": "ok", "at": 1, "type": "VENTA", "currencyId": "cup", "concept": null, "amount": "5.00", "linkId": null, "closingId": null, "products": [], "denominations": []}
              ]
            }
        """.trimIndent()
        val loaded = MovementJson.fromJson(json)
        assertEquals("", loaded[0].sellerUid)
        assertEquals("", loaded[0].sellerName)
    }

    @Test
    fun `round trip preserves null concept`() {
        val m = movement(concept = null)
        val loaded = MovementJson.fromJson(MovementJson.toJson(listOf(m)))
        assertNull(loaded[0].concept)
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """
            {
              "version": 1,
              "movements": [
                {"id": "", "at": 1, "type": "VENTA", "currencyId": "cup", "concept": null, "amount": "5.00", "linkId": null, "closingId": null, "products": [], "denominations": []},
                {"id": "ok", "at": 1, "type": "VENTA", "currencyId": "cup", "concept": null, "amount": "5.00", "linkId": null, "closingId": null, "products": [], "denominations": []}
              ]
            }
        """.trimIndent()

        val loaded = MovementJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `unknown type is skipped`() {
        val json = """
            {
              "version": 1,
              "movements": [
                {"id": "bad", "at": 1, "type": "NOPE", "currencyId": "cup", "concept": null, "amount": "5.00", "linkId": null, "closingId": null, "products": [], "denominations": []}
              ]
            }
        """.trimIndent()
        assertTrue(MovementJson.fromJson(json).isEmpty())
    }

    @Test
    fun `unknown version returns empty list`() {
        val json = """{"version": 99, "movements": []}"""
        assertTrue(MovementJson.fromJson(json).isEmpty())
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(MovementJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = movement(id = "a", at = 100L)
        val newer = movement(id = "b", at = 200L)
        val loaded = MovementJson.fromJson(MovementJson.toJson(listOf(older, newer)))
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }
}
