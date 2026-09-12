package com.moneycounter.signup

import com.moneycounter.domain.Role

/**
 * Construye el mensaje de WhatsApp con la solicitud de acceso y la contraseña temporal.
 * Para ADMIN/SELLER se omite la parte de negocio/sucursales.
 */
object SignupWhatsAppMessage {
    fun build(
        email: String,
        role: Role,
        businessName: String?,
        branches: List<String>,
        tempPassword: String
    ): String {
        val businessPart = if (role == Role.OWNER) {
            " Negocio: ${businessName.orEmpty()}. Sucursales: ${branches.joinToString(", ")}."
        } else {
            ""
        }
        return "Hola, quiero acceso a El Luiso. Rol solicitado: ${role.name}. " +
            "Correo: $email.$businessPart Contraseña temporal: $tempPassword"
    }
}