package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

class PermissionServiceTest {

    /** Plan 033: no membership means every permission denied, without exception. */
    private fun assertNone(ps: PermissionService) {
        assertFalse(ps.canSell())
        assertFalse(ps.canRegisterCreditSaleAndCollect())
        assertFalse(ps.canRegisterExpense())
        assertFalse(ps.canAddStock())
        assertFalse(ps.canEditStock())
        assertFalse(ps.canRegisterWriteoff())
        assertFalse(ps.canCreateProduct())
        assertFalse(ps.canEditProduct())
        assertFalse(ps.canDeleteProduct())
        assertFalse(ps.canViewInventory())
        assertFalse(ps.canViewBranchHistory())
        assertFalse(ps.canViewOrganizationHistory())
        assertFalse(ps.canCreateSellerClosing())
        assertFalse(ps.canCreateBranchClosing())
        assertFalse(ps.canViewReports())
        assertFalse(ps.canManageCatalog())
        assertFalse(ps.canViewAllSellersDashboard())
        assertFalse(ps.canManageAccounts())
    }

    private fun assertFull(ps: PermissionService) {
        assertTrue(ps.canSell())
        assertTrue(ps.canRegisterCreditSaleAndCollect())
        assertTrue(ps.canRegisterExpense())
        assertTrue(ps.canAddStock())
        assertTrue(ps.canEditStock())
        assertTrue(ps.canRegisterWriteoff())
        assertTrue(ps.canCreateProduct())
        assertTrue(ps.canEditProduct())
        assertTrue(ps.canDeleteProduct())
        assertTrue(ps.canViewInventory())
        assertTrue(ps.canViewBranchHistory())
        assertTrue(ps.canCreateSellerClosing())
        assertTrue(ps.canCreateBranchClosing())
        assertTrue(ps.canViewReports())
        assertTrue(ps.canManageCatalog())
    }

    private fun assertStockOpsBlocked(ps: PermissionService) {
        assertFalse(ps.canRegisterExpense())
        assertFalse(ps.canAddStock())
        assertFalse(ps.canEditStock())
        assertFalse(ps.canRegisterWriteoff())
        assertFalse(ps.canCreateProduct())
        assertFalse(ps.canEditProduct())
        assertFalse(ps.canDeleteProduct())
    }

    @Test
    fun `SELLER permission service matrix`() {
        val ps = RolePermissionService(Role.SELLER)
        assertTrue(ps.canSell())
        assertTrue(ps.canRegisterCreditSaleAndCollect())
        assertTrue(ps.canViewInventory())
        assertTrue(ps.canCreateSellerClosing())
        assertStockOpsBlocked(ps)
        assertFalse(ps.canViewBranchHistory())
        assertFalse(ps.canViewOrganizationHistory())
        assertFalse(ps.canCreateBranchClosing())
        assertFalse(ps.canViewReports())
        assertFalse(ps.canManageCatalog())
        assertFalse(ps.canViewAllSellersDashboard())
        assertFalse(ps.canManageAccounts())
    }

    @Test
    fun `OWNER permission service matrix`() {
        val ps = RolePermissionService(Role.OWNER)
        assertFull(ps)
        assertTrue(ps.canViewOrganizationHistory())
        assertTrue(ps.canViewAllSellersDashboard())
        assertFalse(ps.canManageAccounts())
    }

    @Test
    fun `ADMIN permission service matrix`() {
        val ps = RolePermissionService(Role.ADMIN)
        assertFull(ps)
        assertFalse(ps.canViewOrganizationHistory())
        assertFalse(ps.canViewAllSellersDashboard())
        assertFalse(ps.canManageAccounts())
    }

    @Test
    fun `SUPERUSER permission service matrix`() {
        val ps = RolePermissionService(Role.SUPERUSER)
        assertFalse(ps.canSell())
        assertFalse(ps.canRegisterCreditSaleAndCollect())
        assertStockOpsBlocked(ps)
        assertFalse(ps.canViewInventory())
        assertFalse(ps.canViewBranchHistory())
        assertFalse(ps.canViewOrganizationHistory())
        assertFalse(ps.canCreateSellerClosing())
        assertFalse(ps.canCreateBranchClosing())
        assertFalse(ps.canViewReports())
        assertFalse(ps.canManageCatalog())
        assertFalse(ps.canViewAllSellersDashboard())
        assertTrue(ps.canManageAccounts())
    }

    @Test
    fun `NoAccessPermissionService denies every permission (plan 033)`() {
        val ps = NoAccessPermissionService
        assertNone(ps)
    }

    @Test
    fun `forRole maps null to NoAccessPermissionService`() {
        assertSame(NoAccessPermissionService, NoAccessPermissionService.forRole(null))
    }
}