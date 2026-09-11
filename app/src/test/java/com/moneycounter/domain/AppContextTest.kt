package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

class AppContextTest {

    @Test
    fun `construction with uid only`() {
        val ctx = AppContext("u1")
        assertEquals("u1", ctx.uid)
        assertNull(ctx.organizationId)
        assertNull(ctx.branchId)
        assertNull(ctx.role)
    }

    @Test
    fun `construction with full session context`() {
        val ctx = AppContext("u1", organizationId = "org1", branchId = "b1", role = Role.SELLER)
        assertEquals("u1", ctx.uid)
        assertEquals("org1", ctx.organizationId)
        assertEquals("b1", ctx.branchId)
        assertEquals(Role.SELLER, ctx.role)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank uid is rejected`() {
        AppContext("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace uid is rejected`() {
        AppContext("   ")
    }

    @Test
    fun `isSeller true only for SELLER role`() {
        assertTrue(AppContext("u1", role = Role.SELLER).isSeller())
        assertFalse(AppContext("u1").isSeller())
        assertFalse(AppContext("u1", role = Role.OWNER).isSeller())
        assertFalse(AppContext("u1", role = Role.ADMIN).isSeller())
        assertFalse(AppContext("u1", role = Role.SUPERUSER).isSeller())
    }
}