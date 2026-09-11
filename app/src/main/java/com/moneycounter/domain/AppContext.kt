package com.moneycounter.domain

/** Session context: who is acting, in which org/branch, with which role.
 *  Seed for plan 019 (org/branch context). Blank uid is rejected: a session
 *  must always know the actor. */
data class AppContext(
    val uid: String,
    val organizationId: String? = null,
    val branchId: String? = null,
    val role: Role? = null
) {
    init {
        require(uid.isNotBlank()) { "AppContext uid must not be blank" }
    }
}

fun AppContext.isSeller(): Boolean = role == Role.SELLER