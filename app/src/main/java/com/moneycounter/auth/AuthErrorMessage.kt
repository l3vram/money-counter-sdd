package com.moneycounter.auth

import java.io.IOException

/**
 * Maps a thrown login exception to an end-user-friendly message.
 * Never swallows the root cause: unknown/undiagnosed errors keep the raw
 * detail in the message so support can see exactly why sign-in failed
 * (e.g. a DEVELOPER_ERROR from a missing SHA fingerprint or bad client id).
 */
fun mapAuthError(exception: Exception): String {
    val message = exception.message.orEmpty()
    return when {
        exception is IOException -> "Sin conexión. Verifica tu internet."
        message.contains("cancelled", ignoreCase = true) ||
            message.contains("cancel", ignoreCase = true) -> "Inicio de sesión cancelado"
        message.contains("no credential", ignoreCase = true) -> "Cuenta no disponible"
        exception.javaClass.simpleName.contains("FirebaseAuth", ignoreCase = true) ||
            message.contains("FirebaseAuth", ignoreCase = true) -> "Error de autenticación"
        isDeveloperError(message) -> "Error de autenticación: la firma (SHA-1/SHA-256) de esta " +
            "copia del app no está registrada en Firebase. Instala el APK oficial y reintenta."
        else -> "Error inesperado (${message.ifBlank { exception.javaClass.simpleName }})"
    }
}

/**
 * True when the failure is a Google Play services "result code 10" DEVELOPER_ERROR
 * or a related configuration problem (unregistered SHA fingerprint, invalid OAuth
 * client id). The underlying message varies by device/Play-services version, so we
 * match on a set of telltale fragments.
 */
private fun isDeveloperError(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("developer") ||
        lower.contains("internal error") ||
        lower.contains("error: 10") ||
        lower.contains("result code 10") ||
        lower.contains("signing") ||
        lower.contains("fingerprint") ||
        lower.contains("sha-1") ||
        lower.contains("sha1") ||
        lower.contains("oauth2 client") ||
        lower.contains("client id") ||
        lower.contains("invalid client")
}