package com.moneycounter.access

import com.moneycounter.auth.AuthUser

sealed interface AppAccessState {
    object Loading : AppAccessState
    object SignedOut : AppAccessState
    data class Pending(val user: AuthUser) : AppAccessState
    data class Approved(val user: AuthUser) : AppAccessState
    data class Blocked(val user: AuthUser) : AppAccessState
    data class Error(val message: String) : AppAccessState
}