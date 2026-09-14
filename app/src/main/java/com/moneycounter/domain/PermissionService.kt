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
 * No membership, no access (plan 033). Returned when the role is `null` — no `members` row,
 * or a row that is not usable yet — and used as the initial value before the session role is
 * known.
 *
 * It denies **everything**, on purpose. Until plan 033 this object granted every operational
 * permission "for legacy single-user installs"; there were none, the app had never shipped,
 * and the result was that an unresolved role meant OWNER-grade powers. The name says what it
 * does: a thing called "Default" that denies everything is a trap for the next reader.
 *
 * The session only opens the app with a membership that [Member.isOperable] accepts, so in
 * practice this service backs a locked screen rather than a usable one.
 */
object NoAccessPermissionService : PermissionService {
    override fun canSell() = false
    override fun canRegisterCreditSaleAndCollect() = false
    override fun canRegisterExpense() = false
    override fun canAddStock() = false
    override fun canEditStock() = false
    override fun canRegisterWriteoff() = false
    override fun canCreateProduct() = false
    override fun canEditProduct() = false
    override fun canDeleteProduct() = false
    override fun canViewInventory() = false
    override fun canViewBranchHistory() = false
    override fun canViewOrganizationHistory() = false
    override fun canCreateSellerClosing() = false
    override fun canCreateBranchClosing() = false
    override fun canViewReports() = false
    override fun canManageCatalog() = false
    override fun canViewAllSellersDashboard() = false
    override fun canManageAccounts() = false
}

/** Delegates every permission to the [Role.kt] matrix. */
class RolePermissionService(private val role: Role?) : PermissionService {
    override fun canSell(): Boolean = role?.canSell() ?: NoAccessPermissionService.canSell()
    override fun canRegisterCreditSaleAndCollect(): Boolean =
        role?.canRegisterCreditSaleAndCollect() ?: NoAccessPermissionService.canRegisterCreditSaleAndCollect()
    override fun canRegisterExpense(): Boolean = role?.canRegisterExpense() ?: NoAccessPermissionService.canRegisterExpense()
    override fun canAddStock(): Boolean = role?.canAddStock() ?: NoAccessPermissionService.canAddStock()
    override fun canEditStock(): Boolean = role?.canEditStock() ?: NoAccessPermissionService.canEditStock()
    override fun canRegisterWriteoff(): Boolean = role?.canRegisterWriteoff() ?: NoAccessPermissionService.canRegisterWriteoff()
    override fun canCreateProduct(): Boolean = role?.canCreateProduct() ?: NoAccessPermissionService.canCreateProduct()
    override fun canEditProduct(): Boolean = role?.canEditProduct() ?: NoAccessPermissionService.canEditProduct()
    override fun canDeleteProduct(): Boolean = role?.canDeleteProduct() ?: NoAccessPermissionService.canDeleteProduct()
    override fun canViewInventory(): Boolean = role?.canViewInventory() ?: NoAccessPermissionService.canViewInventory()
    override fun canViewBranchHistory(): Boolean = role?.canViewBranchHistory() ?: NoAccessPermissionService.canViewBranchHistory()
    override fun canViewOrganizationHistory(): Boolean = role?.canViewOrganizationHistory() ?: NoAccessPermissionService.canViewOrganizationHistory()
    override fun canCreateSellerClosing(): Boolean = role?.canCreateSellerClosing() ?: NoAccessPermissionService.canCreateSellerClosing()
    override fun canCreateBranchClosing(): Boolean = role?.canCreateBranchClosing() ?: NoAccessPermissionService.canCreateBranchClosing()
    override fun canViewReports(): Boolean = role?.canViewReports() ?: NoAccessPermissionService.canViewReports()
    override fun canManageCatalog(): Boolean = role?.canManageCatalog() ?: NoAccessPermissionService.canManageCatalog()
    override fun canViewAllSellersDashboard(): Boolean = role?.canViewAllSellersDashboard() ?: NoAccessPermissionService.canViewAllSellersDashboard()
    override fun canManageAccounts(): Boolean = role?.canManageAccounts() ?: NoAccessPermissionService.canManageAccounts()
}

/** Resolves the permission service for a role: no role means no access. */
fun PermissionService.forRole(role: Role?): PermissionService =
    if (role == null) NoAccessPermissionService else RolePermissionService(role)