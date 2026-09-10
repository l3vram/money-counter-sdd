package com.moneycounter.domain

enum class Role {
    SELLER,
    OWNER,
    SUPERUSER;

    companion object {
        fun fromStorage(value: String?): Role? = value?.let { v -> values().find { it.name == v } }
        fun toStorage(x: Role): String = x.name
    }
}

/** Permission matrix extensions */
fun Role.canRegisterSale(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    else -> false
}

fun Role.canAddStock(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
    else -> false
}

fun Role.canDecreaseStock(): Boolean = when (this) {
    Role.OWNER -> true
    else -> false
}

fun Role.canRegisterCreditSaleAndCollect(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true
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