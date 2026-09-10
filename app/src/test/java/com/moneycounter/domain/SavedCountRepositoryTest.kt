package com.moneycounter.domain

import com.moneycounter.repository.SavedCountJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SavedCountRepositoryTest {

    private fun item(value: Long, qty: Long, subtotal: String) =
        SavedCountItem(value, qty, BigDecimal(subtotal))

    @Test
    fun `round trip preserves fields`() {
        val saved = SavedCount(
            id = "abc-123",
            savedAt = 1750000000000L,
            targetAmount = BigDecimal("5000.00"),
            items = listOf(
                item(500, 1, "500.00"),
                item(100, 2, "200.00")
            ),
            sellerUid = "seller-6",
            sellerName = "Vendedor Seis"
        )

        val json = SavedCountJson.toJson(listOf(saved))
        val loaded = SavedCountJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals("abc-123", loaded[0].id)
        assertEquals(1750000000000L, loaded[0].savedAt)
        assertEquals(BigDecimal("5000.00"), loaded[0].targetAmount)
        assertEquals("seller-6", loaded[0].sellerUid)
        assertEquals("Vendedor Seis", loaded[0].sellerName)
        assertEquals(2, loaded[0].items.size)
        assertEquals(500L, loaded[0].items[0].denominationValue)
        assertEquals(1L, loaded[0].items[0].quantity)
        assertEquals(BigDecimal("500.00"), loaded[0].items[0].subtotal)
    }

    @Test
    fun `zero quantity items are skipped on load`() {
        val json = """
            {
              "version": 1,
              "history": [
                {
                  "id": "x",
                  "savedAt": 1,
                  "targetAmount": "100.00",
                  "items": [
                    {"denominationValue": 500, "quantity": 0, "subtotal": "0.00"},
                    {"denominationValue": 100, "quantity": 3, "subtotal": "300.00"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val loaded = SavedCountJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals(1, loaded[0].items.size)
        assertEquals(100L, loaded[0].items[0].denominationValue)
        assertEquals(3L, loaded[0].items[0].quantity)
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(SavedCountJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = SavedCount("a", 100L, BigDecimal("10.00"), emptyList())
        val newer = SavedCount("b", 200L, BigDecimal("20.00"), emptyList())

        val loaded = SavedCountJson.fromJson(SavedCountJson.toJson(listOf(older, newer)))

        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }

    @Test
    fun `malformed entries are skipped`() {
        val json = """
            {
              "version": 1,
              "history": [
                {"id": "", "savedAt": 1, "targetAmount": "10.00", "items": []},
                {"id": "ok", "savedAt": 1, "targetAmount": "50.00", "items": []}
              ]
            }
        """.trimIndent()

        val loaded = SavedCountJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }
}
