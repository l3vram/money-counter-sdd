package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityParserTest {

    @Test
    fun `empty string is nothing (null)`() {
        assertNull(QuantityParser.parse(""))
    }

    @Test
    fun `whitespace only is nothing (null)`() {
        assertNull(QuantityParser.parse("  "))
    }

    @Test
    fun `single zero is zero`() {
        assertEquals(0L, QuantityParser.parse("0"))
    }

    @Test
    fun `multiple zeros are zero`() {
        assertEquals(0L, QuantityParser.parse("00"))
        assertEquals(0L, QuantityParser.parse("000"))
    }

    @Test
    fun `single digit parses to that digit`() {
        assertEquals(1L, QuantityParser.parse("1"))
        assertEquals(7L, QuantityParser.parse("7"))
    }

    @Test
    fun `leading zero is ignored`() {
        assertEquals(1L, QuantityParser.parse("01"))
        assertEquals(7L, QuantityParser.parse("007"))
        assertEquals(10L, QuantityParser.parse("010"))
    }

    @Test
    fun `trailing zero is preserved`() {
        assertEquals(10L, QuantityParser.parse("10"))
        assertEquals(100L, QuantityParser.parse("100"))
        assertEquals(120L, QuantityParser.parse("120"))
    }

    @Test
    fun `mixed digits parse correctly`() {
        assertEquals(12345L, QuantityParser.parse("12345"))
        assertEquals(10500L, QuantityParser.parse("10500"))
    }

    @Test
    fun `non-digit characters are rejected`() {
        assertNull(QuantityParser.parse("1a"))
        assertNull(QuantityParser.parse("a1"))
        assertNull(QuantityParser.parse("-1"))
        assertNull(QuantityParser.parse("1.5"))
    }

    @Test
    fun `overflowing number is nothing (null)`() {
        assertNull(QuantityParser.parse("999999999999999999999999"))
    }
}
