package com.moneycounter.domain

import com.moneycounter.repository.MovementJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Plan 020: the tenant-fields migration must never drop legacy history.
 * v1 JSON (no organizationId/branchId keys) must still load; v2 round-trips
 * the new fields; stampTenant fills only blanks; version 3 is rejected.
 */
class MovementJsonMigrationTest {

    private fun v2Movement(
        id: String = "m1",
        organizationId: String = "org-1",
        branchId: String = "br-1"
    ) = Movement(
        id = id,
        at = 1000L,
        type = MovementType.VENTA,
        currencyId = "cup",
        amount = BigDecimal("4.00").setScale(Money.SCALE),
        organizationId = organizationId,
        branchId = branchId
    )

    @Test
    fun `v1 json without tenant keys loads and yields blank tenant fields`() {
        val json = """
            {
              "version": 1,
              "movements": [
                {"id": "legacy-1", "at": 1, "type": "VENTA", "currencyId": "cup", "concept": null, "amount": "5.00", "linkId": null, "closingId": null, "products": [], "denominations": []}
              ]
            }
        """.trimIndent()

        val loaded = MovementJson.fromJson(json)
        assertEquals(1, loaded.size)
        assertEquals("legacy-1", loaded[0].id)
        assertEquals("", loaded[0].organizationId)
        assertEquals("", loaded[0].branchId)
    }

    @Test
    fun `v2 round trip preserves tenant fields`() {
        val original = v2Movement(organizationId = "org-9", branchId = "br-9")
        val loaded = MovementJson.fromJson(MovementJson.toJson(listOf(original)))
        assertEquals(1, loaded.size)
        assertEquals("org-9", loaded[0].organizationId)
        assertEquals("br-9", loaded[0].branchId)
    }

    @Test
    fun `stampTenant fills only blanks and never overwrites existing stamps`() {
        val stamped = v2Movement(id = "kept", organizationId = "org-1", branchId = "br-1")
        val legacyOrgBlank = v2Movement(id = "org-blank", organizationId = "", branchId = "br-2")
        val legacyBranchBlank = v2Movement(id = "branch-blank", organizationId = "org-3", branchId = "")
        val fullyBlank = v2Movement(id = "all-blank", organizationId = "", branchId = "")

        val result = MovementJson.stampTenant(
            listOf(stamped, legacyOrgBlank, legacyBranchBlank, fullyBlank),
            "org-x",
            "br-x"
        ).associateBy { it.id }

        assertEquals("org-1", result.getValue("kept").organizationId)
        assertEquals("br-1", result.getValue("kept").branchId)
        assertEquals("org-x", result.getValue("org-blank").organizationId)
        assertEquals("br-2", result.getValue("org-blank").branchId)
        assertEquals("org-3", result.getValue("branch-blank").organizationId)
        assertEquals("br-x", result.getValue("branch-blank").branchId)
        assertEquals("org-x", result.getValue("all-blank").organizationId)
        assertEquals("br-x", result.getValue("all-blank").branchId)
    }

    @Test
    fun `stampTenant with blank context is a no-op`() {
        val original = v2Movement(organizationId = "", branchId = "")
        val result = MovementJson.stampTenant(listOf(original), "", "")
        assertEquals("", result[0].organizationId)
        assertEquals("", result[0].branchId)
    }

    @Test
    fun `version 3 is rejected after the v1-v2 compatibility layer`() {
        val json = """{"version": 3, "movements": []}"""
        assertTrue(MovementJson.fromJson(json).isEmpty())
    }
}