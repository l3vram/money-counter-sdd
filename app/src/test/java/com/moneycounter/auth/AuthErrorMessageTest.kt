package com.moneycounter.auth

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AuthErrorMessageTest {

    @Test
    fun `maps cancellation message to friendly message`() {
        val ex = Exception("User cancelled the sign-in flow")
        assertEquals("Inicio de sesión cancelado", mapAuthError(ex))
    }

    @Test
    fun `maps no-credentials message to friendly message`() {
        val ex = Exception("No credential available")
        assertEquals("Cuenta no disponible", mapAuthError(ex))
    }

    @Test
    fun `maps IOException to network error message`() {
        val ex = IOException("Network unavailable")
        assertEquals("Sin conexión. Verifica tu internet.", mapAuthError(ex))
    }

    @Test
    fun `maps FirebaseAuthException to generic firebase message`() {
        val ex = RuntimeException("FirebaseAuthException: token expired")
        assertEquals("Error de autenticación", mapAuthError(ex))
    }

    @Test
    fun `maps unknown exception to generic unexpected message`() {
        val ex = IllegalStateException("boom")
        assertEquals("Error inesperado", mapAuthError(ex))
    }
}
