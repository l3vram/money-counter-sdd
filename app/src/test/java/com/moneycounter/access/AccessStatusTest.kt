package com.moneycounter.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessStatusTest {

    @Test
    fun `fromStorage valid values`() {
        assertEquals(AccessStatus.PENDING, AccessStatus.fromStorage("PENDING"))
        assertEquals(AccessStatus.APPROVED, AccessStatus.fromStorage("APPROVED"))
        assertEquals(AccessStatus.BLOCKED, AccessStatus.fromStorage("BLOCKED"))
    }

    @Test
    fun `fromStorage null returns null`() {
        assertNull(AccessStatus.fromStorage(null))
    }

    @Test
    fun `fromStorage unknown returns null`() {
        assertNull(AccessStatus.fromStorage("UNKNOWN_VALUE"))
    }
}