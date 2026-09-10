package com.moneycounter.firestore

object FirestorePaths {
    const val USERS = "users"
    const val MEMBERS = "members"
    const val ORGANIZATIONS = "organizations"
    const val BRANCHES = "branches"
    const val CATALOG = "catalog"
    const val STOCK = "stock"
    const val SALES = "sales"
    const val WRITEOFFS = "writeoffs"
    const val RECEIVABLES = "receivables"
    const val PAYMENTS = "payments"

    fun user(uid: String) = requireNotBlank(uid) { "users/$uid" }
    fun member(uid: String) = requireNotBlank(uid) { "members/$uid" }
    fun organization(orgId: String) = requireNotBlank(orgId) { "organizations/$orgId" }
    fun branches(orgId: String) = requireNotBlank(orgId) { "organizations/$orgId/branches" }
    fun branch(orgId: String, branchId: String) = requireNotBlank(orgId) {
        requireNotBlank(branchId) { "organizations/$orgId/branches/$branchId" }
    }
    fun stock(orgId: String, branchId: String) = branch(orgId, branchId) + "/stock"
    fun stockItem(orgId: String, branchId: String, productId: String) =
        stock(orgId, branchId) + "/$productId"

    fun sales(orgId: String, branchId: String) = branch(orgId, branchId) + "/sales"
    fun writeoffs(orgId: String, branchId: String) = branch(orgId, branchId) + "/writeoffs"
    fun receivables(orgId: String, branchId: String) = branch(orgId, branchId) + "/receivables"
    fun payments(orgId: String, branchId: String) = branch(orgId, branchId) + "/payments"
    fun catalog(orgId: String, branchId: String) = branch(orgId, branchId) + "/catalog"

    private inline fun requireNotBlank(value: String, lazyPath: () -> String): String {
        require(value.isNotBlank()) { "Argument must not be blank" }
        return lazyPath()
    }
}