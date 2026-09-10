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
        assertEquals("Error inesperado (boom)", mapAuthError(ex))
    }

    @Test
    fun `maps DEVELOPER_ERROR to a clear signature warning`() {
        val ex = Exception("The following error occurred: 10: DEVELOPER_ERROR")
        val message = mapAuthError(ex)
        assertTrue(message.contains("firma"))
        assertTrue(message.contains("SHA-1/SHA-256"))
    }

    @Test
    fun `maps internal error to signature warning`() {
        val ex = Exception("An internal error occurred while processing the credential request")
        assertEquals(mapAuthError(Exception("developer")), mapAuthError(ex))
    }

    @Test
    fun `blank message keeps the exception class as detail`() {
        val ex = Exception()
        assertEquals("Error inesperado (Exception)", mapAuthError(ex))
    }
}
