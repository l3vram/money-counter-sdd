package com.moneycounter.signup

/**
 * Repositorio de solicitudes de registro (`signups`) y configuración (`settings`).
 */
interface SignupRepository {
    suspend fun submit(request: SignupRequest)

    suspend fun settingsSuperuserWhatsapp(): String?

    suspend fun setMustChangePassword(uid: String, flag: Boolean)

    suspend fun readMustChangePassword(uid: String): Boolean
}