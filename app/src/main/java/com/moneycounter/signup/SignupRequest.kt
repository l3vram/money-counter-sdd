package com.moneycounter.signup

import com.moneycounter.domain.Role

/**
 * Solicitud de registro almacenada en la tabla `signups` (row id = Appwrite uid).
 */
data class SignupRequest(
    val uid: String,
    val email: String,
    val role: Role,
    val businessName: String? = null,
    val branches: List<String> = emptyList(),
    val mustChangePassword: Boolean = true,
    val status: String = "PENDING",
    val createdAtMs: Long
)