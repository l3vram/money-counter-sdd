package com.moneycounter.auth

import org.junit.Assert.*
import org.junit.Test

class EmailValidationTest {

    @Test
    fun `accepts the addresses people actually use`() {
        for (ok in listOf(
            "juanperez890621@gmail.com",
            "luisricoblanco2014@gmail.com",
            "a@b.co",
            "nombre.apellido@empresa.com.ar",
            "con+etiqueta@dominio.org",
            "guion-medio@mi-negocio.com",
            "MAYUSCULAS@Dominio.COM"
        )) {
            assertTrue("debería aceptar $ok", isValidEmail(ok))
        }
    }

    @Test
    fun `rejects a missing at sign`() {
        assertFalse(isValidEmail("juanperez890621gmail.com"))
    }

    @Test
    fun `rejects a domain with no dot`() {
        assertFalse(isValidEmail("juan@gmail"))
    }

    @Test
    fun `rejects blanks and spaces`() {
        for (bad in listOf("", "   ", "juan perez@gmail.com", "juan@gmail .com")) {
            assertFalse("debería rechazar '$bad'", isValidEmail(bad))
        }
    }

    @Test
    fun `rejects a missing local part or domain`() {
        for (bad in listOf("@gmail.com", "juan@", "@", "juan@.com", "juan@com.")) {
            assertFalse("debería rechazar '$bad'", isValidEmail(bad))
        }
    }

    @Test
    fun `rejects consecutive dots and dots at the edge of the local part`() {
        for (bad in listOf("juan..perez@gmail.com", ".juan@gmail.com", "juan.@gmail.com")) {
            assertFalse("debería rechazar '$bad'", isValidEmail(bad))
        }
    }

    @Test
    fun `trims before deciding, so trailing spaces do not reject a good address`() {
        assertTrue(isValidEmail("  juan@gmail.com  "))
    }

    @Test
    fun `rejects an absurdly long address`() {
        assertFalse(isValidEmail("a".repeat(250) + "@gmail.com"))
    }
}
