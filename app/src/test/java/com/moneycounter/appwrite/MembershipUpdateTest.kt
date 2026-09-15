package com.moneycounter.appwrite

import com.moneycounter.access.MembershipUpdate
import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 034. Antes los tres casos caían en el mismo catch silencioso, así que **una sesión
 * revocada era indistinguible de un corte de red** y la app seguía operando con datos
 * cacheados hasta el siguiente reinicio.
 */
class MembershipUpdateTest {

    @Test
    fun `un 404 significa que no hay membresia`() {
        assertEquals(MembershipUpdate.Missing, membershipUpdateFor(404))
    }

    @Test
    fun `un 401 significa que no hay sesion`() {
        assertEquals(MembershipUpdate.Revoked, membershipUpdateFor(401))
    }

    @Test
    fun `un fallo de transporte no reporta nada, para conservar lo ultimo conocido`() {
        assertNull(membershipUpdateFor(null))
    }

    @Test
    fun `los problemas del servidor tampoco reportan nada`() {
        for (code in listOf(403, 429, 500, 502, 503)) {
            assertNull("$code no debe cambiar la membresía conocida", membershipUpdateFor(code))
        }
    }

    @Test
    fun `no confundir las dos preguntas`() {
        // 404 y 401 llevan a pantallas opuestas: "falta asignarte tu puesto" contra volver al
        // login. Intercambiarlas deja al usuario esperando algo que nunca va a pasar, o lo
        // expulsa sin motivo.
        assertNotEquals(membershipUpdateFor(404), membershipUpdateFor(401))
    }
}
