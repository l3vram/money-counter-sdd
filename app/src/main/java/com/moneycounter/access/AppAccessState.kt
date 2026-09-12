package com.moneycounter.access

import com.moneycounter.auth.AuthUser
import com.moneycounter.signup.SignupRequest

sealed interface AppAccessState {
    object Loading : AppAccessState
    object SignedOut : AppAccessState
    data class Pending(val user: AuthUser) : AppAccessState
    data class Approved(val user: AuthUser) : AppAccessState
    data class Blocked(val user: AuthUser) : AppAccessState
    data class PasswordChangeRequired(val user: AuthUser) : AppAccessState
    data class SignUpPending(val tempPassword: String, val request: SignupRequest) : AppAccessState
    data class Error(val message: String) : AppAccessState
}