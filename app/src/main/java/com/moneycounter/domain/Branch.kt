package com.moneycounter.domain

data class Branch(
    val id: String,
    val orgId: String,
    val name: String
) {
    init {
        require(id.isNotBlank()) { "Branch id must not be blank" }
        require(orgId.isNotBlank()) { "Branch orgId must not be blank" }
        require(name.isNotBlank()) { "Branch name must not be blank" }
    }
}