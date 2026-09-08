package com.moneycounter.auth

import java.io.IOException

fun mapAuthError(exception: Exception): String = when {
    exception is IOException -> "Sin conexión. Verifica tu internet."
    exception.message?.contains("cancelled", ignoreCase = true) == true ||
    exception.message?.contains("cancel", ignoreCase = true) == true -> "Inicio de sesión cancelado"
    exception.message?.contains("no credential", ignoreCase = true) == true -> "Cuenta no disponible"
    exception.javaClass.simpleName.contains("FirebaseAuth", ignoreCase = true) ||
    exception.message?.contains("FirebaseAuth", ignoreCase = true) == true -> "Error de autenticación"
    else -> "Error inesperado"
}
