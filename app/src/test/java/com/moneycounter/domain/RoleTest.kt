package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

class RoleTest {

    @Test
    fun `SELLER permission matrix`() {
        val role = Role.SELLER
        assertTrue(role.canRegisterSale())
        assertTrue(role.canAddStock())
        assertFalse(role.canDecreaseStock())
        assertTrue(role.canRegisterCreditSaleAndCollect())
        assertFalse(role.canViewAllSellersDashboard())
        assertFalse(role.canManageAccounts())
    }

    @Test
    fun `OWNER permission matrix`() {
        val role = Role.OWNER
        assertTrue(role.canRegisterSale())
        assertTrue(role.canAddStock())
        assertTrue(role.canDecreaseStock())
        assertTrue(role.canRegisterCreditSaleAndCollect())
        assertTrue(role.canViewAllSellersDashboard())
        assertFalse(role.canManageAccounts())
    }

    @Test
    fun `SUPERUSER permission matrix`() {
        val role = Role.SUPERUSER
        assertFalse(role.canRegisterSale())
        assertFalse(role.canAddStock())
        assertFalse(role.canDecreaseStock())
        assertFalse(role.canRegisterCreditSaleAndCollect())
        assertFalse(role.canViewAllSellersDashboard())
        assertTrue(role.canManageAccounts())
    }

    @Test
    fun `storage conversion works`() {
        Role.values().forEach { role ->
            val stored = Role.toStorage(role)
            val restored = Role.fromStorage(stored)
            assertEquals(role, restored)
        }
        assertNull(Role.fromStorage(null))
        assertNull(Role.fromStorage("NON_EXISTENT"))
    }

    @Test
    fun `null role keeps single-user privileges`() {
        assertTrue((null as Role?).mayDecreaseStock())
        assertFalse((null as Role?).mayViewOwnerDashboard())
    }

    @Test
    fun `mayDecreaseStock gating`() {
        assertTrue(Role.OWNER.mayDecreaseStock())
        assertFalse(Role.SELLER.mayDecreaseStock())
        assertFalse(Role.SUPERUSER.mayDecreaseStock())
    }

    @Test
    fun `mayViewOwnerDashboard gating`() {
        assertTrue(Role.OWNER.mayViewOwnerDashboard())
        assertFalse(Role.SELLER.mayViewOwnerDashboard())
        assertFalse(Role.SUPERUSER.mayViewOwnerDashboard())
    }
}