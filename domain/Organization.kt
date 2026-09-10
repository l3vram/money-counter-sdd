package com.moneycounter.domain

data class Organization(
    val id: String,
    val name: String,
    val ownerUid: String,
    val whatsappNumber: String? = null,
    val createdAt: Long
) {
    init {
        require(id.isNotBlank()) { "Organization id must not be blank" }
        require(name.isNotBlank()) { "Organization name must not be blank" }
        require(ownerUid.isNotBlank()) { "Organization ownerUid must not be blank" }
    }
}