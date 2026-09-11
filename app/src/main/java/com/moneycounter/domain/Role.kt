package com.moneycounter.domain

enum class Role {
    SELLER,
    OWNER,
    ADMIN,
    SUPERUSER;

    companion object {
        fun fromStorage(value: String?): Role? =
            value?.let { v -> values().find { it.name == v } }

        fun toStorage(x: Role): String = x.name
    }
}

/**
 * Definitive permission matrix (plan 018). Contract:
 * - SELLER: vende, cobra fiado, cierra su caja, ve inventario y su historial — read-only en inventario.
 * - OWNER: todo operativo + catálogo + reportes + historial de sucursal/org + dashboard de vendedores.
 * - ADMIN: como OWNER salvo historial de organización y dashboard de vendedores.
 * - SUPERUSER: solo gestión de cuentas (bloqueado de las operaciones).
 */
fun Role.canSell(): Boolean = when (this) {
    Role.SELLER, Role.OWNER, Role.ADMIN -> true
    Role.SUPERUSER -> false
}

fun Role.canRegisterCreditSaleAndCollect(): Boolean = when (this) {
    Role.SELLER, Role.OWNER, Role.ADMIN -> true
    Role.SUPERUSER -> false
}

fun Role.canRegisterExpense(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canAddStock(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canEditStock(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canRegisterWriteoff(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canCreateProduct(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canEditProduct(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canDeleteProduct(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canViewInventory(): Boolean = when (this) {
    Role.SELLER, Role.OWNER, Role.ADMIN -> true
    Role.SUPERUSER -> false
}

fun Role.canViewHistory(): Boolean = when (this) {
    Role.SELLER, Role.OWNER, Role.ADMIN -> true
    Role.SUPERUSER -> false
}

fun Role.canViewBranchHistory(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canViewOrganizationHistory(): Boolean = when (this) {
    Role.OWNER -> true
    else -> false
}

fun Role.canCreateSellerClosing(): Boolean = when (this) {
    Role.SELLER, Role.OWNER, Role.ADMIN -> true
    Role.SUPERUSER -> false
}

fun Role.canCreateBranchClosing(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canViewReports(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canManageCatalog(): Boolean = when (this) {
    Role.OWNER, Role.ADMIN -> true
    else -> false
}

fun Role.canViewAllSellersDashboard(): Boolean = when (this) {
    Role.OWNER -> true
    else -> false
}

fun Role.canManageAccounts(): Boolean = when (this) {
    Role.SUPERUSER -> true
    else -> false
}

/**
 * Gate helpers for UI visibility. A `null` role means the user has no member doc yet
 * (not assigned to an org/branch), which keeps today's single-user privileges.
 */
fun Role?.mayRegisterWriteoff(): Boolean = this?.canRegisterWriteoff() ?: true

fun Role?.mayEditStock(): Boolean = this?.canEditStock() ?: true

fun Role?.mayAddStock(): Boolean = this?.canAddStock() ?: true

fun Role?.mayRegisterExpense(): Boolean = this?.canRegisterExpense() ?: true

fun Role?.mayCreateSellerClosing(): Boolean = this?.canCreateSellerClosing() ?: true

fun Role?.mayCreateBranchClosing(): Boolean = this?.canCreateBranchClosing() ?: true

fun Role?.mayManageCatalog(): Boolean = this?.canManageCatalog() ?: true

fun Role?.mayViewBranchHistory(): Boolean = this?.canViewBranchHistory() ?: true

fun Role?.mayViewOwnerDashboard(): Boolean = this?.canViewAllSellersDashboard() ?: false