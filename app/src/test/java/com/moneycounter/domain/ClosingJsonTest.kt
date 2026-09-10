package com.moneycounter.domain

import com.moneycounter.repository.ClosingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ClosingJsonTest {

    private fun closing(
        id: String = "cl1",
        at: Long = 1000L,
        currencyId: String = "cup",
        movementIds: List<String> = listOf("m1", "m2"),
        totalsByType: Map<MovementType, BigDecimal> = MovementType.entries.associateWith { BigDecimal("0.00") },
        netCash: String = "150.00",
        stockSnapshot: List<ClosingStockLine> = listOf(ClosingStockLine("Arroz", "Lb", BigDecimal("10.00"))),
        sellerUid: String = "",
        sellerName: String = ""
    ) = Closing(
        id = id,
        at = at,
        currencyId = currencyId,
        movementIds = movementIds,
        totalsByType = totalsByType,
        netCash = BigDecimal(netCash),
        stockSnapshot = stockSnapshot,
        sellerUid = sellerUid,
        sellerName = sellerName
    )

    @Test
    fun `round trip preserves author stamp`() {
        val original = closing(sellerUid = "seller-5", sellerName = "Vendedor Cinco")
        val loaded = ClosingJson.fromJson(ClosingJson.toJson(listOf(original)))
        assertEquals("seller-5", loaded[0].sellerUid)
        assertEquals("Vendedor Cinco", loaded[0].sellerName)
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = closing()
        val loaded = ClosingJson.fromJson(ClosingJson.toJson(listOf(original)))

        assertEquals(1, loaded.size)
        val l = loaded[0]
        assertEquals(original.id, l.id)
        assertEquals(original.at, l.at)
        assertEquals(original.currencyId, l.currencyId)
        assertEquals(original.movementIds, l.movementIds)
        assertEquals(original.netCash, l.netCash)
        assertEquals(original.stockSnapshot, l.stockSnapshot)
        MovementType.entries.forEach { type ->
            assertEquals(original.totalsByType.getValue(type), l.totalsByType.getValue(type))
        }
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """
            {
              "version": 1,
              "closings": [
                {"id": "", "at": 1, "currencyId": "cup", "movementIds": [], "totalsByType": {}, "netCash": "0.00", "stockSnapshot": []},
                {"id": "ok", "at": 1, "currencyId": "cup", "movementIds": [], "totalsByType": {}, "netCash": "0.00", "stockSnapshot": []}
              ]
            }
        """.trimIndent()

        val loaded = ClosingJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `unknown version returns empty list`() {
        val json = """{"version": 99, "closings": []}"""
        assertTrue(ClosingJson.fromJson(json).isEmpty())
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(ClosingJson.fromJson("").isEmpty())
    }

    @Test
    fun `results sorted newest first`() {
        val older = closing(id = "a", at = 100L)
        val newer = closing(id = "b", at = 200L)
        val loaded = ClosingJson.fromJson(ClosingJson.toJson(listOf(older, newer)))
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[0].id)
        assertEquals("a", loaded[1].id)
    }
}
