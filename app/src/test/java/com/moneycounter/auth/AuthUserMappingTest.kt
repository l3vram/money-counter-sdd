package com.moneycounter.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Simple unit test that verifies the data class construction works as expected.
 * No Firebase dependencies are required.
 */
class AuthUserMappingTest {

    @Test
    fun `AuthUser contains the supplied values`() {
        val uid = "user-123"
        val email = "test@example.com"
        val displayName = "John Doe"
        val photoUrl = "https://example.com/photo.png"

        val user = AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl
        )

        assertEquals(uid, user.uid)
        assertEquals(email, user.email)
        assertEquals(displayName, user.displayName)
        assertEquals(photoUrl, user.photoUrl)
    }

    @Test
    fun `AuthUser allows null optional fields`() {
        val user = AuthUser(uid = "uid", email = null, displayName = null, photoUrl = null)
        assertNull(user.email)
        assertNull(user.displayName)
        assertNull(user.photoUrl)
    }
}