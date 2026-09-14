package com.moneycounter.domain

data class Member(
    val uid: String,
    val orgId: String,
    val role: Role,
    val branchIds: List<String> = emptyList()
) {
    init {
        require(uid.isNotBlank()) { "Member uid must not be blank" }
        require(orgId.isNotBlank()) { "Member orgId must not be blank" }
    }
}

/**
 * A membership can operate only once the SUPERUSER has fully assigned it: a business, at
 * least one branch, and an operational role (plan 033).
 *
 * `null` means no membership row, which is the case this predicate exists for: the absence
 * of a membership used to hand the session every permission through
 * [DefaultPermissionService]. It now means no access at all.
 *
 * Two of the rules plan 033 asked for are unreachable by construction and deliberately
 * absent: [Member] requires a non-blank `orgId` in its `init`, and its `role` is not
 * nullable. The checks left are the ones the type system cannot make.
 */
fun Member?.isOperable(): Boolean = when {
    this == null -> false
    // The SUPERUSER administers the platform from the web panel and is blocked from every
    // operation (`Role.kt`, plan 018 contract). It belongs to no business.
    role == Role.SUPERUSER -> false
    branchIds.isEmpty() -> false
    else -> true
}

/** Membership helper extensions **/
fun Member.belongsToBranch(branchId: String): Boolean = branchIds.contains(branchId)

fun Member.belongsToOrg(orgId: String): Boolean = this.orgId == orgId