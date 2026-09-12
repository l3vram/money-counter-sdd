package com.moneycounter.signup

import com.moneycounter.domain.Role

object SignupFields {
    const val EMAIL = "email"
    const val ROLE = "role"
    const val BUSINESS_NAME = "businessName"
    const val BRANCHES = "branches"
    const val MUST_CHANGE_PASSWORD = "mustChangePassword"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
}

fun signupRequestToPayload(request: SignupRequest): Map<String, Any?> = mapOf(
    SignupFields.EMAIL to request.email,
    SignupFields.ROLE to Role.toStorage(request.role),
    SignupFields.BUSINESS_NAME to request.businessName,
    SignupFields.BRANCHES to request.branches,
    SignupFields.MUST_CHANGE_PASSWORD to request.mustChangePassword,
    SignupFields.STATUS to request.status,
    SignupFields.CREATED_AT to request.createdAtMs
)

fun signupRowToRequest(uid: String, data: Map<String, Any?>): SignupRequest? {
    if (uid.isBlank()) return null
    val email = data[SignupFields.EMAIL] as? String ?: return null
    val role = Role.fromStorage(data[SignupFields.ROLE] as? String) ?: return null
    val branches = (data[SignupFields.BRANCHES] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    return SignupRequest(
        uid = uid,
        email = email,
        role = role,
        businessName = data[SignupFields.BUSINESS_NAME] as? String,
        branches = branches,
        mustChangePassword = (data[SignupFields.MUST_CHANGE_PASSWORD] as? Boolean) ?: true,
        status = data[SignupFields.STATUS] as? String ?: "PENDING",
        createdAtMs = (data[SignupFields.CREATED_AT] as? Number)?.toLong() ?: 0L
    )
}