package com.moneycounter.access

import com.moneycounter.auth.AuthUser

fun toAppAccessState(authUser: AuthUser?, access: AccessStatus?): AppAccessState {
    return when {
        authUser == null -> AppAccessState.SignedOut
        access == null -> AppAccessState.Error("No se pudo verificar la autorización")
        access == AccessStatus.PENDING -> AppAccessState.Pending(authUser)
        access == AccessStatus.APPROVED -> AppAccessState.Approved(authUser)
        access == AccessStatus.BLOCKED -> AppAccessState.Blocked(authUser)
        else -> AppAccessState.Error("Estado de acceso desconocido")
    }
}