package com.moneycounter.auth

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String? = null,
    val photoUrl: String? = null
)