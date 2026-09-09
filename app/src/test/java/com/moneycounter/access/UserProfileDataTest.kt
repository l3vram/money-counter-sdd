package com.moneycounter.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserProfileDataTest {

    private val fullMap = mapOf(
        "email" to "user@example.com",
        "displayName" to "Juana",
        "photoUrl" to "https://example.com/p.png",
        "access" to "APPROVED",
        "createdAt" to 1_700_000_000_000L,
        "updatedAt" to 1_700_100_000_000L
    )

    @Test
    fun `fromMap maps all fields`() {
        val profile = UserProfileData.fromMap("uid-1", fullMap)

        assertEquals("uid-1", profile.uid)
        assertEquals("user@example.com", profile.email)
        assertEquals("Juana", profile.displayName)
        assertEquals("https://example.com/p.png", profile.photoUrl)
        assertEquals(AccessStatus.APPROVED, profile.access)
        assertEquals(1_700_000_000_000L, profile.createdAtMillis)
        assertEquals(1_700_100_000_000L, profile.updatedAtMillis)
    }

    @Test
    fun `fromMap handles missing optionals`() {
        val profile = UserProfileData.fromMap("uid-2", mapOf("access" to "PENDING"))

        assertEquals("uid-2", profile.uid)
        assertEquals(AccessStatus.PENDING, profile.access)
        assertNull(profile.email)
        assertNull(profile.displayName)
        assertNull(profile.photoUrl)
        assertNull(profile.createdAtMillis)
        assertNull(profile.updatedAtMillis)
    }

    @Test
    fun `fromMap unknown access yields null access`() {
        val profile = UserProfileData.fromMap("uid-3", mapOf("access" to "MAGIC"))

        assertNull(profile.access)
    }
}