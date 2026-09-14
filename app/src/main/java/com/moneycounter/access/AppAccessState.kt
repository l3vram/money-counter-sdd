package com.moneycounter.access

import com.moneycounter.auth.AuthUser
import com.moneycounter.signup.SignupRequest

sealed interface AppAccessState {
    object Loading : AppAccessState
    object SignedOut : AppAccessState
    data class Pending(val user: AuthUser) : AppAccessState
    data class Approved(val user: AuthUser) : AppAccessState

    /**
     * Approved account, but the SUPERUSER has not finished assigning it a business, a branch
     * and a role (plan 033). The account cannot operate yet — before plan 033 this same
     * situation opened the app with *every* permission.
     */
    data class AwaitingAssignment(val user: AuthUser) : AppAccessState

    /** A SUPERUSER signing into the app: its place is the web panel, not the operation. */
    data class PanelOnlyAccount(val user: AuthUser) : AppAccessState
    data class Blocked(val user: AuthUser) : AppAccessState
    data class PasswordChangeRequired(val user: AuthUser) : AppAccessState
    data class SignUpPending(val tempPassword: String, val request: SignupRequest) : AppAccessState
    data class Error(val message: String) : AppAccessState
}