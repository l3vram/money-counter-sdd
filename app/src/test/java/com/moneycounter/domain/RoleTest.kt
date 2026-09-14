package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

class RoleTest {

    @Test
    fun `SELLER permission matrix`() {
        val role = Role.SELLER
        assertTrue(role.canSell())
        assertTrue(role.canRegisterCreditSaleAndCollect())
        assertTrue(role.canViewInventory())
        assertTrue(role.canViewHistory())
        assertTrue(role.canCreateSellerClosing())
        assertFalse(role.canRegisterExpense())
        assertFalse(role.canAddStock())
        assertFalse(role.canEditStock())
        assertFalse(role.canRegisterWriteoff())
        assertFalse(role.canCreateProduct())
        assertFalse(role.canEditProduct())
        assertFalse(role.canDeleteProduct())
        assertFalse(role.canViewBranchHistory())
        assertFalse(role.canViewOrganizationHistory())
        assertFalse(role.canCreateBranchClosing())
        assertFalse(role.canViewReports())
        assertFalse(role.canManageCatalog())
        assertFalse(role.canViewAllSellersDashboard())
        assertFalse(role.canManageAccounts())
    }

    @Test
    fun `OWNER permission matrix`() {
        val role = Role.OWNER
        assertTrue(role.canSell())
        assertTrue(role.canRegisterCreditSaleAndCollect())
        assertTrue(role.canRegisterExpense())
        assertTrue(role.canAddStock())
        assertTrue(role.canEditStock())
        assertTrue(role.canRegisterWriteoff())
        assertTrue(role.canCreateProduct())
        assertTrue(role.canEditProduct())
        assertTrue(role.canDeleteProduct())
        assertTrue(role.canViewInventory())
        assertTrue(role.canViewHistory())
        assertTrue(role.canViewBranchHistory())
        assertTrue(role.canViewOrganizationHistory())
        assertTrue(role.canCreateSellerClosing())
        assertTrue(role.canCreateBranchClosing())
        assertTrue(role.canViewReports())
        assertTrue(role.canManageCatalog())
        assertTrue(role.canViewAllSellersDashboard())
        assertFalse(role.canManageAccounts())
    }

    @Test
    fun `ADMIN permission matrix`() {
        val role = Role.ADMIN
        assertTrue(role.canSell())
        assertTrue(role.canRegisterCreditSaleAndCollect())
        assertTrue(role.canRegisterExpense())
        assertTrue(role.canAddStock())
        assertTrue(role.canEditStock())
        assertTrue(role.canRegisterWriteoff())
        assertTrue(role.canCreateProduct())
        assertTrue(role.canEditProduct())
        assertTrue(role.canDeleteProduct())
        assertTrue(role.canViewInventory())
        assertTrue(role.canViewHistory())
        assertTrue(role.canViewBranchHistory())
        assertTrue(role.canCreateSellerClosing())
        assertTrue(role.canCreateBranchClosing())
        assertTrue(role.canViewReports())
        assertTrue(role.canManageCatalog())
        assertFalse(role.canViewOrganizationHistory())
        assertFalse(role.canViewAllSellersDashboard())
        assertFalse(role.canManageAccounts())
    }

    @Test
    fun `SUPERUSER permission matrix`() {
        val role = Role.SUPERUSER
        assertFalse(role.canSell())
        assertFalse(role.canRegisterCreditSaleAndCollect())
        assertFalse(role.canRegisterExpense())
        assertFalse(role.canAddStock())
        assertFalse(role.canEditStock())
        assertFalse(role.canRegisterWriteoff())
        assertFalse(role.canCreateProduct())
        assertFalse(role.canEditProduct())
        assertFalse(role.canDeleteProduct())
        assertFalse(role.canViewInventory())
        assertFalse(role.canViewHistory())
        assertFalse(role.canViewBranchHistory())
        assertFalse(role.canViewOrganizationHistory())
        assertFalse(role.canCreateSellerClosing())
        assertFalse(role.canCreateBranchClosing())
        assertFalse(role.canViewReports())
        assertFalse(role.canManageCatalog())
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
    fun `null role closes every UI gate (plan 033)`() {
        // Before plan 033 these defaulted to `true`, so a session whose membership had not
        // resolved showed the full OWNER-grade UI. No membership now shows nothing.
        assertFalse((null as Role?).mayRegisterWriteoff())
        assertFalse((null as Role?).mayEditStock())
        assertFalse((null as Role?).mayAddStock())
        assertFalse((null as Role?).mayRegisterExpense())
        assertFalse((null as Role?).mayCreateSellerClosing())
        assertFalse((null as Role?).mayCreateBranchClosing())
        assertFalse((null as Role?).mayManageCatalog())
        assertFalse((null as Role?).mayViewBranchHistory())
        assertFalse((null as Role?).mayViewOwnerDashboard())
    }

    @Test
    fun `mayRegisterWriteoff gating`() {
        assertTrue(Role.OWNER.mayRegisterWriteoff())
        assertTrue(Role.ADMIN.mayRegisterWriteoff())
        assertFalse(Role.SELLER.mayRegisterWriteoff())
        assertFalse(Role.SUPERUSER.mayRegisterWriteoff())
    }

    @Test
    fun `mayEditStock gating`() {
        assertTrue(Role.OWNER.mayEditStock())
        assertTrue(Role.ADMIN.mayEditStock())
        assertFalse(Role.SELLER.mayEditStock())
        assertFalse(Role.SUPERUSER.mayEditStock())
    }

    @Test
    fun `mayAddStock gating`() {
        assertTrue(Role.OWNER.mayAddStock())
        assertTrue(Role.ADMIN.mayAddStock())
        assertFalse(Role.SELLER.mayAddStock())
        assertFalse(Role.SUPERUSER.mayAddStock())
    }

    @Test
    fun `mayRegisterExpense gating`() {
        assertTrue(Role.OWNER.mayRegisterExpense())
        assertTrue(Role.ADMIN.mayRegisterExpense())
        assertFalse(Role.SELLER.mayRegisterExpense())
        assertFalse(Role.SUPERUSER.mayRegisterExpense())
    }

    @Test
    fun `mayManageCatalog gating`() {
        assertTrue(Role.OWNER.mayManageCatalog())
        assertTrue(Role.ADMIN.mayManageCatalog())
        assertFalse(Role.SELLER.mayManageCatalog())
        assertFalse(Role.SUPERUSER.mayManageCatalog())
    }

    @Test
    fun `mayViewOwnerDashboard gating`() {
        assertTrue(Role.OWNER.mayViewOwnerDashboard())
        assertFalse(Role.ADMIN.mayViewOwnerDashboard())
        assertFalse(Role.SELLER.mayViewOwnerDashboard())
        assertFalse(Role.SUPERUSER.mayViewOwnerDashboard())
    }
}