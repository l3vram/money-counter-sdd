package com.moneycounter.auth

/**
 * Local email shape check, so a malformed address never reaches the server. Appwrite rejects
 * it with an English message that used to land on screen verbatim; catching it in the form
 * saves a round trip and says it in Spanish.
 *
 * Deliberately **not** RFC 5322: that grammar allows things no user ever types (quoted local
 * parts, comments, IP literals) and a regex for it is unreadable and slow. This checks the
 * shape people actually mistype — a missing `@`, a missing dot in the domain, spaces, a
 * double dot — and lets the server be the final authority. A validator stricter than the
 * server would reject addresses that actually work, which is the worse failure.
 */
private val EMAIL_SHAPE = Regex("^[A-Za-z0-9!#\$%&'*+/=?^_`{|}~.-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty() || trimmed.length > 254) return false
    if (trimmed.contains("..")) return false
    val local = trimmed.substringBefore('@', missingDelimiterValue = "")
    if (local.isEmpty() || local.startsWith('.') || local.endsWith('.')) return false
    return EMAIL_SHAPE.matches(trimmed)
}
