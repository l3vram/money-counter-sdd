package com.moneycounter.domain

enum class Role {
    SELLER,
    OWNER,
    SUPERUSER;

    companion object {
        fun fromStorage(value: String?): Role? =
            value?.let { v -> values().find { it.name == v } }

        fun toStorage(x: Role): String = x.name
    }
}

/** Permission matrix extensions **/

fun Role.canRegisterSale(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    Role.SUPERUSER -> false
}

fun Role.canAddStock(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    Role.SUPERUSER -> false
}

/** Registrar una baja por merma (writeoff): el SELLER sí puede documentar bajas de inventario. */
fun Role.canRegisterWriteoff(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    Role.SUPERUSER -> false
}

/** Editar/ajustar el stock de un producto directamente: solo el OWNER. */
fun Role.canEditStock(): Boolean = when (this) {
    Role.OWNER -> true
    else -> false
}

fun Role.canRegisterCreditSaleAndCollect(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    Role.SUPERUSER -> false
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

fun Role?.mayViewOwnerDashboard(): Boolean = this?.canViewAllSellersDashboard() ?: false