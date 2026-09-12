package com.moneycounter.signup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun generate_hasAtLeastAppwriteMinimumLength() {
        assertTrue(PasswordGenerator.generate().length >= PasswordGenerator.MIN_LENGTH)
    }

    @Test
    fun generate_usesOnlyAlphanumericCharset() {
        val password = PasswordGenerator.generate()
        assertTrue(password.all { it.isLetterOrDigit() })
    }

    @Test
    fun generate_skipsAmbiguousCharacters() {
        val password = PasswordGenerator.generate()
        assertFalse(password.any { it in "0O1lI" })
    }

    @Test
    fun generate_producesRandomizedDistinctValues() {
        val values = (1..50).map { PasswordGenerator.generate(16) }.toSet()
        assertTrue(values.size > 1)
    }
}