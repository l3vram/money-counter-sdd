package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Plan 020: role-scoped journal visibility. Blank-tolerant — legacy records with
 * blank org/branch/sellerUid stay visible to everyone, keeping the migration
 * invisible to existing installs.
 */
class MovementScopingTest {

    private fun movement(
        id: String,
        organizationId: String = "",
        branchId: String = "",
        sellerUid: String = "",
        type: MovementType = MovementType.VENTA
    ) = Movement(
        id = id,
        at = 1000L,
        type = type,
        currencyId = "cup",
        amount = BigDecimal("4.00").setScale(Money.SCALE),
        organizationId = organizationId,
        branchId = branchId,
        sellerUid = sellerUid
    )

    // ---- SELLER: own branch + own uid ----

    @Test
    fun `SELLER sees own movements and legacy blank ones, hidden ones excluded`() {
        val movements = listOf(
            movement("own", organizationId = "org1", branchId = "br1", sellerUid = "seller1"),
            movement("other-seller", organizationId = "org1", branchId = "br1", sellerUid = "seller2"),
            movement("other-branch", organizationId = "org1", branchId = "br2", sellerUid = "seller1"),
            movement("legacy", organizationId = "", branchId = "", sellerUid = "")
        )
        val visible = visibleForRole(movements, Role.SELLER, orgId = "org1", branchId = "br1", uid = "seller1")
        assertEquals(listOf("own", "legacy"), visible.map { it.id })
    }

    @Test
    fun `SELLER sees a same-branch movement with blank seller uid`() {
        val movements = listOf(
            movement("no-uid", organizationId = "org1", branchId = "br1", sellerUid = "")
        )
        val visible = visibleForRole(movements, Role.SELLER, orgId = "org1", branchId = "br1", uid = "seller1")
        assertEquals(listOf("no-uid"), visible.map { it.id })
    }

    // ---- ADMIN: whole branch ----

    @Test
    fun `ADMIN sees whole branch but neither other branches nor mirrors org leaks`() {
        val movements = listOf(
            movement("in-branch", organizationId = "org1", branchId = "br1", sellerUid = "seller1"),
            movement("in-branch-other-org", organizationId = "org9", branchId = "br1", sellerUid = "seller2"),
            movement("other-branch", organizationId = "org1", branchId = "br2", sellerUid = "seller1"),
            movement("legacy", organizationId = "", branchId = "")
        )
        val visible = visibleForRole(movements, Role.ADMIN, orgId = "org1", branchId = "br1", uid = "")
        assertEquals(listOf("in-branch", "in-branch-other-org", "legacy"), visible.map { it.id })
    }

    // ---- OWNER: whole org ----

    @Test
    fun `OWNER sees whole org including all branches, not other orgs`() {
        val movements = listOf(
            movement("org", organizationId = "org1", branchId = "br1"),
            movement("org-other-branch", organizationId = "org1", branchId = "br2"),
            movement("other-org", organizationId = "org2", branchId = "br1"),
            movement("legacy", organizationId = "")
        )
        val visible = visibleForRole(movements, Role.OWNER, orgId = "org1", branchId = "br1", uid = "")
        assertEquals(listOf("org", "org-other-branch", "legacy"), visible.map { it.id })
    }

    // ---- null: everything ----

    @Test
    fun `null role sees everything as a single-user install`() {
        val movements = listOf(
            movement("a", organizationId = "org1", branchId = "br1"),
            movement("b", organizationId = "org2", branchId = "br9"),
            movement("c")
        )
        val visible = visibleForRole(movements, null, orgId = "", branchId = "", uid = "")
        assertEquals(movements, visible)
    }

    // ---- SUPERUSER: nothing ----

    @Test
    fun `SUPERUSER sees no operational movements at all`() {
        val movements = listOf(
            movement("a", organizationId = "org1", branchId = "br1"),
            movement("legacy")
        )
        val visible = visibleForRole(movements, Role.SUPERUSER, orgId = "org1", branchId = "br1", uid = "admin")
        assertTrue(visible.isEmpty())
    }

    // ---- empty input ----

    @Test
    fun `empty movements yield empty visible journal for any role`() {
        assertTrue(visibleForRole(emptyList(), Role.OWNER, orgId = "org1", branchId = "br1", uid = "").isEmpty())
        assertTrue(visibleForRole(emptyList(), null, orgId = "", branchId = "", uid = "").isEmpty())
    }
}