package com.moneycounter.viewmodel

import com.moneycounter.domain.Role
import com.moneycounter.domain.RolePermissionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan 030 regression guard. The bug: the role came from a network `members`
 * row, and any failure — including simply being offline — produced a null role,
 * which meant DefaultPermissionService and therefore *every* permission. A
 * SELLER without signal operated as an OWNER.
 */
class RoleRetentionTest {

    @Test
    fun `a transient null never downgrades a known role`() {
        assertEquals(Role.SELLER, retainRole(incoming = null, current = Role.SELLER))
    }

    @Test
    fun `a real role update still wins`() {
        assertEquals(Role.ADMIN, retainRole(incoming = Role.ADMIN, current = Role.SELLER))
    }

    @Test
    fun `no role at all stays null so legacy installs keep their behavior`() {
        assertNull(retainRole(incoming = null, current = null))
    }

    @Test
    fun `the first resolved role is adopted`() {
        assertEquals(Role.SELLER, retainRole(incoming = Role.SELLER, current = null))
    }

    @Test
    fun `a retained SELLER still cannot modify inventory`() {
        val retained = retainRole(incoming = null, current = Role.SELLER)
        val permissions = RolePermissionService(retained)

        assertFalse(permissions.canAddStock())
        assertFalse(permissions.canEditStock())
        assertFalse(permissions.canDeleteProduct())
        assertFalse(permissions.canCreateProduct())
        assertFalse(permissions.canRegisterWriteoff())
        assertFalse(permissions.canCreateBranchClosing())
    }

    @Test
    fun `a retained SELLER keeps the permissions it should have`() {
        val permissions = RolePermissionService(retainRole(incoming = null, current = Role.SELLER))

        assertTrue(permissions.canSell())
        assertTrue(permissions.canRegisterCreditSaleAndCollect())
        assertTrue(permissions.canViewInventory())
        assertTrue(permissions.canCreateSellerClosing())
    }
}
