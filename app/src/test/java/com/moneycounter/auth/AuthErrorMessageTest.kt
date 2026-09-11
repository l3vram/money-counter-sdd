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
    fun `maps invalid credentials message to friendly message`() {
        val ex = RuntimeException("user (invalid_credentials): A user with the email address was not found")
        assertEquals("Correo o contraseña incorrectos", mapAuthError(ex))
    }

    @Test
    fun `maps user_not_found message to friendly message`() {
        val ex = RuntimeException("user (user_not_found): User was not found.")
        assertEquals("La cuenta no existe", mapAuthError(ex))
    }

    @Test
    fun `maps already-exists message to friendly message`() {
        val ex = RuntimeException("user (user_already_exists): User already exists.")
        assertEquals("La cuenta ya existe", mapAuthError(ex))
    }

    @Test
    fun `maps unknown exception to generic unexpected message`() {
        val ex = IllegalStateException("boom")
        assertEquals("Error inesperado (boom)", mapAuthError(ex))
    }

    @Test
    fun `blank message keeps the exception class as detail`() {
        val ex = Exception()
        assertEquals("Error inesperado (Exception)", mapAuthError(ex))
    }
}
