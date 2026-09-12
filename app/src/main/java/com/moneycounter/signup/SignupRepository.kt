package com.moneycounter.signup

/**
 * Repositorio de solicitudes de registro (`signups`) y configuración (`settings`).
 */
interface SignupRepository {
    suspend fun submit(request: SignupRequest)

    suspend fun settingsSuperuserWhatsapp(): String?
}