package com.moneycounter.signup

import java.security.SecureRandom

/**
 * Genera contraseñas temporales seguras con criptografía fuerte.
 * Charset a-z A-Z 0-9 sin caracteres ambiguos (0, O, 1, l, I).
 */
object PasswordGenerator {
    const val DEFAULT_LENGTH = 12
    const val MIN_LENGTH = 8

    private val charset = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(length: Int = DEFAULT_LENGTH): String {
        require(length >= MIN_LENGTH) { "La contraseña debe tener al menos $MIN_LENGTH caracteres" }
        return buildString {
            repeat(length) { append(charset[random.nextInt(charset.length)]) }
        }
    }
}