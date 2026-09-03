package com.moneycounter.domain

/**
 * Parses raw quantity text typed by the user into a non-negative Long.
 *
 * Rules (matching the counting UI contract):
 *  - blank / whitespace-only text represents "nothing entered" and returns null.
 *  - any non-digit character is rejected (returns null); the field only accepts digits.
 *  - leading zeros are ignored: "007" -> 7, "010" -> 10.
 *  - trailing zeros are preserved: "10" -> 10, "100" -> 100.
 *  - "0" / "00" -> 0.
 *  - a value that overflows Long returns null (treated as nothing entered).
 *
 * Returns null only when there is no valid quantity to count; callers should treat
 * null and 0 identically for summation (0 contributes nothing).
 */
object QuantityParser {

    fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.all { it.isDigit() }) return null
        return text.toLongOrNull()
    }
}
