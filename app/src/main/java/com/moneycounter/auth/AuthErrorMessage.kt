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
            message.contains("user_not_found", ignoreCase = true) ->
            "No existe una cuenta con ese correo. Crea una cuenta primero."
        type.contains("user_already_exists") ||
            message.contains("already exists", ignoreCase = true) -> "La cuenta ya existe"
        // Distinct from the login case below: this one is the CURRENT password being wrong
        // while changing it, where offering to create an account makes no sense.
        type.contains("user_invalid_password") ||
            message.contains("user_invalid_password", ignoreCase = true) ->
            "La contraseña actual es incorrecta"
        type.contains("invalid_credentials") ||
            message.contains("invalid_credentials", ignoreCase = true) ||
            message.contains("credentials", ignoreCase = true) ->
            // Appwrite answers the same error for a wrong password and for an email that does
            // not exist, on purpose, so that the login form cannot be used to discover who
            // has an account. The message has to cover both readings honestly.
            "Correo o contraseña incorrectos. Si todavía no tienes cuenta, crea una primero."
        message.contains("cancelled", ignoreCase = true) ||
            message.contains("cancel", ignoreCase = true) -> "Inicio de sesión cancelado"
        message.contains("no credential", ignoreCase = true) -> "Cuenta no disponible"
        type.contains("network") ||
            message.contains("network", ignoreCase = true) -> "Sin conexión. Verifica tu internet."
        else -> "Error inesperado (${message.ifBlank { exception.javaClass.simpleName }})"
    }
}