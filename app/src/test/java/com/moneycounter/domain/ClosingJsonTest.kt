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
        organizationId: String = "",
        branchId: String = "",
        sellerUid: String = "",
        sellerName: String = "",
        scope: ClosingScope = ClosingScope.BRANCH
    ) = Closing(
        id = id,
        at = at,
        currencyId = currencyId,
        movementIds = movementIds,
        totalsByType = totalsByType,
        netCash = BigDecimal(netCash),
        stockSnapshot = stockSnapshot,
        organizationId = organizationId,
        branchId = branchId,
        sellerUid = sellerUid,
        sellerName = sellerName,
        scope = scope
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

    // ---- plan 020: tenant-fields migration ----

    @Test
    fun `v1 json without tenant keys loads and yields blank tenant fields`() {
        val json = """
            {
              "version": 1,
              "closings": [
                {"id": "legacy-1", "at": 1, "currencyId": "cup", "movementIds": [], "totalsByType": {}, "netCash": "0.00", "stockSnapshot": []}
              ]
            }
        """.trimIndent()

        val loaded = ClosingJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("legacy-1", loaded[0].id)
        assertEquals("", loaded[0].organizationId)
        assertEquals("", loaded[0].branchId)
    }

    @Test
    fun `v2 round trip preserves tenant fields`() {
        val original = closing(organizationId = "org-9", branchId = "br-9")
        val loaded = ClosingJson.fromJson(ClosingJson.toJson(listOf(original)))
        assertEquals(1, loaded.size)
        assertEquals("org-9", loaded[0].organizationId)
        assertEquals("br-9", loaded[0].branchId)
    }

    @Test
    fun `stampTenant fills only blanks and never overwrites existing stamps`() {
        val stamped = closing(id = "kept", organizationId = "org-1", branchId = "br-1")
        val orgBlank = closing(id = "org-blank", organizationId = "", branchId = "br-2")
        val branchBlank = closing(id = "branch-blank", organizationId = "org-3", branchId = "")

        val result = ClosingJson.stampTenant(
            listOf(stamped, orgBlank, branchBlank),
            "org-x",
            "br-x"
        ).associateBy { it.id }

        assertEquals("org-1", result.getValue("kept").organizationId)
        assertEquals("br-1", result.getValue("kept").branchId)
        assertEquals("org-x", result.getValue("org-blank").organizationId)
        assertEquals("br-2", result.getValue("org-blank").branchId)
        assertEquals("org-3", result.getValue("branch-blank").organizationId)
        assertEquals("br-x", result.getValue("branch-blank").branchId)
    }

    @Test
    fun `version 4 is rejected after the v1-v3 compatibility layer`() {
        val json = """{"version": 4, "closings": []}"""
        assertTrue(ClosingJson.fromJson(json).isEmpty())
    }

    // ---- plan 023: scope (v3) ----

    @Test
    fun `v2 doc without scope reads as SELLER keeping netCash and totals`() {
        val json = """
            {
              "version": 2,
              "closings": [
                {"id": "legacy-2", "at": 1, "currencyId": "cup", "movementIds": ["m1"],
                 "totalsByType": {"VENTA": "100.00", "COBRO": "40.00", "GASTO": "10.00"},
                 "netCash": "130.00", "stockSnapshot": [],
                 "organizationId": "org-1", "branchId": "br-9",
                 "sellerUid": "s1", "sellerName": "Ana"}
              ]
            }
        """.trimIndent()

        val loaded = ClosingJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals(ClosingScope.SELLER, loaded[0].scope)
        assertEquals("130.00", loaded[0].netCash.toPlainString())
        assertEquals(BigDecimal("100.00"), loaded[0].totalsByType.getValue(MovementType.VENTA))
        assertEquals(BigDecimal("40.00"), loaded[0].totalsByType.getValue(MovementType.COBRO))
        assertEquals(BigDecimal("10.00"), loaded[0].totalsByType.getValue(MovementType.GASTO))
        assertEquals("Ana", loaded[0].sellerName)
    }

    @Test
    fun `v1 doc without scope also reads as SELLER`() {
        val json = """
            {
              "version": 1,
              "closings": [
                {"id": "legacy-1", "at": 1, "currencyId": "cup", "movementIds": [],
                 "totalsByType": {}, "netCash": "0.00", "stockSnapshot": []}
              ]
            }
        """.trimIndent()

        val loaded = ClosingJson.fromJson(json)
        assertEquals(ClosingScope.SELLER, loaded[0].scope)
    }

    @Test
    fun `v3 round trip preserves scope`() {
        val branch = closing(id = "br", scope = ClosingScope.BRANCH)
        val seller = closing(id = "sl", scope = ClosingScope.SELLER)
        val loaded = ClosingJson.fromJson(ClosingJson.toJson(listOf(branch, seller)))
        assertEquals(2, loaded.size)
        assertEquals(ClosingScope.BRANCH, loaded.first { it.id == "br" }.scope)
        assertEquals(ClosingScope.SELLER, loaded.first { it.id == "sl" }.scope)
    }

    @Test
    fun `v2 to v3 upgrade preserves netCash and totals`() {
        val json = """
            {
              "version": 2,
              "closings": [
                {"id": "c1", "at": 1, "currencyId": "cup", "movementIds": ["m1"],
                 "totalsByType": {"VENTA": "100.00"},
                 "netCash": "100.00",
                 "stockSnapshot": [{"name": "Arroz", "unit": "Lb", "quantity": "10.00"}]}
              ]
            }
        """.trimIndent()

        val v2 = ClosingJson.fromJson(json)
        assertEquals(1, v2.size)
        val upgraded = ClosingJson.fromJson(ClosingJson.toJson(v2))
        assertEquals(1, upgraded.size)
        assertEquals(ClosingScope.SELLER, upgraded[0].scope)
        assertEquals(v2[0].netCash, upgraded[0].netCash)
        assertEquals(v2[0].totalsByType, upgraded[0].totalsByType)
        assertEquals(v2[0].stockSnapshot, upgraded[0].stockSnapshot)
    }
}
