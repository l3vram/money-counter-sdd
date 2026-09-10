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

/** Membership helper extensions **/
fun Member.belongsToBranch(branchId: String): Boolean = branchIds.contains(branchId)

fun Member.belongsToOrg(orgId: String): Boolean = this.orgId == orgId