package com.moneycounter.auth

import io.appwrite.exceptions.AppwriteException
import java.io.IOException

/**
 * Maps a thrown login exception to an end-user-friendly message.
 * Never swallows the root cause: unknown/undiagnosed errors keep the raw
 * detail in the message so support can see exactly why sign-in failed.
 */
fun mapAuthError(exception: Exception): String {
    val message = exception.message.orEmpty()
    val type = (exception as? AppwriteException)?.type.orEmpty()
    return when {
        exception is IOException -> "Sin conexión. Verifica tu internet."
        type.contains("user_not_found") ||
            message.contains("user_not_found", ignoreCase = true) -> "La cuenta no existe"
        type.contains("user_already_exists") ||
            message.contains("already exists", ignoreCase = true) -> "La cuenta ya existe"
        type.contains("invalid_credentials") ||
            type.contains("user_invalid_password") ||
            message.contains("invalid_credentials", ignoreCase = true) ||
            message.contains("credentials", ignoreCase = true) -> "Correo o contraseña incorrectos"
        message.contains("cancelled", ignoreCase = true) ||
            message.contains("cancel", ignoreCase = true) -> "Inicio de sesión cancelado"
        message.contains("no credential", ignoreCase = true) -> "Cuenta no disponible"
        type.contains("network") ||
            message.contains("network", ignoreCase = true) -> "Sin conexión. Verifica tu internet."
        else -> "Error inesperado (${message.ifBlank { exception.javaClass.simpleName }})"
    }
}