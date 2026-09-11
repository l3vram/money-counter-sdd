package com.moneycounter.domain

/**
 * Single source of truth for permissions. Used by the ViewModel (security boundary)
 * and by the UI (UX visibility).
 */
interface PermissionService {
    fun canSell(): Boolean
    fun canRegisterCreditSaleAndCollect(): Boolean
    fun canRegisterExpense(): Boolean
    fun canAddStock(): Boolean
    fun canEditStock(): Boolean
    fun canRegisterWriteoff(): Boolean
    fun canCreateProduct(): Boolean
    fun canEditProduct(): Boolean
    fun canDeleteProduct(): Boolean
    fun canViewInventory(): Boolean
    fun canViewBranchHistory(): Boolean
    fun canViewOrganizationHistory(): Boolean
    fun canCreateSellerClosing(): Boolean
    fun canCreateBranchClosing(): Boolean
    fun canViewReports(): Boolean
    fun canManageCatalog(): Boolean
    fun canViewAllSellersDashboard(): Boolean
    fun canManageAccounts(): Boolean
}

/**
 * Default single-user behavior: everything allowed except the org-level views.
 * Returned when [role] is `null` (legacy installs / no member doc yet) or as the
 * initial value before the session role is known.
 */
object DefaultPermissionService : PermissionService {
    override fun canSell() = true
    override fun canRegisterCreditSaleAndCollect() = true
    override fun canRegisterExpense() = true
    override fun canAddStock() = true
    override fun canEditStock() = true
    override fun canRegisterWriteoff() = true
    override fun canCreateProduct() = true
    override fun canEditProduct() = true
    override fun canDeleteProduct() = true
    override fun canViewInventory() = true
    override fun canViewBranchHistory() = true
    override fun canViewOrganizationHistory() = true
    override fun canCreateSellerClosing() = true
    override fun canCreateBranchClosing() = true
    override fun canViewReports() = true
    override fun canManageCatalog() = true
    override fun canViewAllSellersDashboard() = false
    override fun canManageAccounts() = false
}

/** Delegates every permission to the [Role.kt] matrix. */
class RolePermissionService(private val role: Role?) : PermissionService {
    override fun canSell(): Boolean = role?.canSell() ?: DefaultPermissionService.canSell()
    override fun canRegisterCreditSaleAndCollect(): Boolean =
        role?.canRegisterCreditSaleAndCollect() ?: DefaultPermissionService.canRegisterCreditSaleAndCollect()
    override fun canRegisterExpense(): Boolean = role?.canRegisterExpense() ?: DefaultPermissionService.canRegisterExpense()
    override fun canAddStock(): Boolean = role?.canAddStock() ?: DefaultPermissionService.canAddStock()
    override fun canEditStock(): Boolean = role?.canEditStock() ?: DefaultPermissionService.canEditStock()
    override fun canRegisterWriteoff(): Boolean = role?.canRegisterWriteoff() ?: DefaultPermissionService.canRegisterWriteoff()
    override fun canCreateProduct(): Boolean = role?.canCreateProduct() ?: DefaultPermissionService.canCreateProduct()
    override fun canEditProduct(): Boolean = role?.canEditProduct() ?: DefaultPermissionService.canEditProduct()
    override fun canDeleteProduct(): Boolean = role?.canDeleteProduct() ?: DefaultPermissionService.canDeleteProduct()
    override fun canViewInventory(): Boolean = role?.canViewInventory() ?: DefaultPermissionService.canViewInventory()
    override fun canViewBranchHistory(): Boolean = role?.canViewBranchHistory() ?: DefaultPermissionService.canViewBranchHistory()
    override fun canViewOrganizationHistory(): Boolean = role?.canViewOrganizationHistory() ?: DefaultPermissionService.canViewOrganizationHistory()
    override fun canCreateSellerClosing(): Boolean = role?.canCreateSellerClosing() ?: DefaultPermissionService.canCreateSellerClosing()
    override fun canCreateBranchClosing(): Boolean = role?.canCreateBranchClosing() ?: DefaultPermissionService.canCreateBranchClosing()
    override fun canViewReports(): Boolean = role?.canViewReports() ?: DefaultPermissionService.canViewReports()
    override fun canManageCatalog(): Boolean = role?.canManageCatalog() ?: DefaultPermissionService.canManageCatalog()
    override fun canViewAllSellersDashboard(): Boolean = role?.canViewAllSellersDashboard() ?: DefaultPermissionService.canViewAllSellersDashboard()
    override fun canManageAccounts(): Boolean = role?.canManageAccounts() ?: DefaultPermissionService.canManageAccounts()
}

/** Resolves the permission service for a role: `null` role ⇒ [DefaultPermissionService]. */
fun PermissionService.forRole(role: Role?): PermissionService =
    if (role == null) DefaultPermissionService else RolePermissionService(role)