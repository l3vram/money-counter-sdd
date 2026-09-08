package com.moneycounter.access

import com.moneycounter.auth.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessFlowMapperTest {

    private val user = AuthUser(uid = "uid-1", email = "u@example.com", displayName = "U", photoUrl = null)

    @Test
    fun `signed out when authUser is null`() {
        val result = toAppAccessState(null, AccessStatus.PENDING)
        assertEquals(AppAccessState.SignedOut, result)
    }

    @Test
    fun `error when authUser not null but access is null`() {
        val result = toAppAccessState(user, null)
        assertEquals(AppAccessState.Error("No se pudo verificar la autorización"), result)
    }

    @Test
    fun `pending state mapping`() {
        val result = toAppAccessState(user, AccessStatus.PENDING)
        assertEquals(AppAccessState.Pending(user), result)
    }

    @Test
    fun `approved state mapping`() {
        val result = toAppAccessState(user, AccessStatus.APPROVED)
        assertEquals(AppAccessState.Approved(user), result)
    }

    @Test
    fun `blocked state mapping`() {
        val result = toAppAccessState(user, AccessStatus.BLOCKED)
        assertEquals(AppAccessState.Blocked(user), result)
    }
}