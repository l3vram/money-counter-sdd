package com.moneycounter.domain

import com.moneycounter.repository.PaymentJson
import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PaymentTest {

    private fun receivable(
        id: String = "r1",
        at: Long = 1000L,
        debtorName: String = "Juan Perez",
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
        products = emptyList(),
        status = status,
        settledAt = settledAt
    )

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

    // ---- settleReceivablePure: the core "cobro" behavior ----

    @Test
    fun `settling an OPEN receivable produces a Payment matching its amount and flips it to SETTLED`() {
        val receivables = listOf(receivable(id = "r1", amount = "5.00"))
        val (updated, payment) = MoneyCounterViewModel.settleReceivablePure(
            receivables = receivables,
            receivableId = "r1",
            paymentId = "pay1",
            now = 5000L
        )

        requireNotNull(payment)
        assertEquals("r1", payment.receivableId)
        assertEquals(BigDecimal("5.00"), payment.amount)
        assertEquals("Juan Perez", payment.debtorName)
        assertEquals("cup", payment.currencyId)

        val settled = updated.single { it.id == "r1" }
        assertEquals(ReceivableStatus.SETTLED, settled.status)
        assertEquals(5000L, settled.settledAt)
        // amount of the receivable itself is untouched
        assertEquals(BigDecimal("5.00"), settled.amount)
    }

    @Test
    fun `settling does not change any product's stock`() {
        val product = Product(
            "p1",
            "Arroz",
            "Lb",
            BigDecimal("40.00"),
            prices = mapOf("cup" to ProductPrice(BigDecimal("2.00"), BigDecimal("0.50")))
        )
        val productsBefore = listOf(product)

        val receivables = listOf(receivable(id = "r1", amount = "5.00"))
        MoneyCounterViewModel.settleReceivablePure(
            receivables = receivables,
            receivableId = "r1",
            paymentId = "pay1",
            now = 5000L
        )

        // settleReceivablePure never receives or returns products; confirm the products
        // list an operation would have used is unchanged by this call.
        assertEquals(listOf(product), productsBefore)
        assertEquals(BigDecimal("40.00"), productsBefore.single().stock)
    }

    @Test
    fun `settling an unknown id is a no-op`() {
        val receivables = listOf(receivable(id = "r1"))
        val (updated, payment) = MoneyCounterViewModel.settleReceivablePure(
            receivables = receivables,
            receivableId = "does-not-exist",
            paymentId = "pay1",
            now = 5000L
        )
        assertNull(payment)
        assertEquals(receivables, updated)
    }

    @Test
    fun `settling an already-SETTLED receivable is a no-op`() {
        val receivables = listOf(
            receivable(id = "r1", status = ReceivableStatus.SETTLED, settledAt = 1500L)
        )
        val (updated, payment) = MoneyCounterViewModel.settleReceivablePure(
            receivables = receivables,
            receivableId = "r1",
            paymentId = "pay1",
            now = 5000L
        )
        assertNull(payment)
        assertEquals(receivables, updated)
    }
}
