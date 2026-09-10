package com.moneycounter.domain

import com.moneycounter.repository.PaymentJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PaymentTest {

    private fun payment(
        id: String = "p1",
        at: Long = 2000L,
        receivableId: String = "r1",
        debtorName: String = "Juan Perez",
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

    // ---- domain invariants ----

    @Test
    fun `blank id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            payment(id = "")
        }
    }

    @Test
    fun `blank receivableId is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            payment(receivableId = "")
        }
    }

    @Test
    fun `zero amount is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            payment(amount = "0.00")
        }
    }

    // ---- PaymentJson round trip ----

    @Test
    fun `round trip preserves fields`() {
        val p = payment()
        val loaded = PaymentJson.fromJson(PaymentJson.toJson(listOf(p)))

        assertEquals(1, loaded.size)
        val l = loaded[0]
        assertEquals(p.id, l.id)
        assertEquals(p.at, l.at)
        assertEquals(p.receivableId, l.receivableId)
        assertEquals(p.debtorName, l.debtorName)
        assertEquals(p.amount, l.amount)
        assertEquals(p.currencyId, l.currencyId)
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """
            {
              "version": 1,
              "payments": [
                {"id": "", "at": 1, "receivableId": "r1", "debtorName": "X", "amount": "5.00", "currencyId": "cup"},
                {"id": "ok", "at": 1, "receivableId": "r1", "debtorName": "X", "amount": "5.00", "currencyId": "cup"}
              ]
            }
        """.trimIndent()

        val loaded = PaymentJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `unknown version returns empty list`() {
        val json = """{"version": 99, "payments": []}"""
        assertTrue(PaymentJson.fromJson(json).isEmpty())
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(PaymentJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = payment(id = "a", at = 100L)
        val newer = payment(id = "b", at = 200L)
        val loaded = PaymentJson.fromJson(PaymentJson.toJson(listOf(older, newer)))
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }

    // NOTE: settleReceivablePure was removed (plan 013) — cobro now writes only a
    // journal COBRO movement linked to the fiado VENTA_FIADO movement. That pure
    // path (openFiadoMovementsPure / isFiadoOpen / buildCobroMovement) is covered
    // in MoneyCounterViewModelPureTest.
}
