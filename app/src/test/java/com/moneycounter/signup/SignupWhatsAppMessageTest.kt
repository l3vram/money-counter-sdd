package com.moneycounter.signup

import com.moneycounter.domain.Role
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupWhatsAppMessageTest {

    private val email = "nuevo@dueno.com"
    private val tempPassword = "AbC123xyz789"

    @Test
    fun build_owner_includesRoleEmailBusinessAndBranches() {
        val message = SignupWhatsAppMessage.build(
            email = email,
            role = Role.OWNER,
            businessName = "Mi Negocio",
            branches = listOf("Centro", "Plaza"),
            tempPassword = tempPassword
        )

        assertTrue(message.contains("Rol solicitado: OWNER"))
        assertTrue(message.contains(email))
        assertTrue(message.contains("Mi Negocio"))
        assertTrue(message.contains("Centro, Plaza"))
        assertTrue(message.contains(tempPassword))
    }

    @Test
    fun build_admin_omitsBusinessAndBranches() {
        val message = SignupWhatsAppMessage.build(
            email = email,
            role = Role.ADMIN,
            businessName = null,
            branches = emptyList(),
            tempPassword = tempPassword
        )

        assertTrue(message.contains("Rol solicitado: ADMIN"))
        assertFalse(message.contains("Negocio:"))
        assertFalse(message.contains("Sucursales:"))
        assertTrue(message.contains(email))
        assertTrue(message.contains(tempPassword))
    }

    @Test
    fun build_seller_omitsBusinessAndBranches() {
        val message = SignupWhatsAppMessage.build(
            email = email,
            role = Role.SELLER,
            businessName = null,
            branches = emptyList(),
            tempPassword = tempPassword
        )

        assertTrue(message.contains("Rol solicitado: SELLER"))
        assertFalse(message.contains("Negocio:"))
        assertFalse(message.contains("Sucursales:"))
        assertTrue(message.contains(email))
        assertTrue(message.contains(tempPassword))
    }

    @Test
    fun build_owner_withBlankBusiness_doesNotBreak() {
        val message = SignupWhatsAppMessage.build(
            email = email,
            role = Role.OWNER,
            businessName = null,
            branches = emptyList(),
            tempPassword = tempPassword
        )

        assertTrue(message.contains(email))
        assertTrue(message.contains("Sucursales:"))
        assertTrue(message.contains(tempPassword))
    }
}