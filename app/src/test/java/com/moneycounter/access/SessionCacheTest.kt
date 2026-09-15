package com.moneycounter.access

import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 034. This cache decides whether the app opens without connectivity, so a malformed
 * file must yield null rather than a half-built session: fail closed, then the startup path
 * falls back to the connection screen instead of guessing.
 */
class SessionCacheTest {

    private val session = CachedSession(
        uid = "u1",
        email = "juan@gmail.com",
        displayName = "Juan Pérez",
        photoUrl = null,
        access = AccessStatus.APPROVED,
        savedAtMs = 1_789_411_393_754
    )

    @Test
    fun `round trip keeps every field`() {
        val decoded = SessionCacheJson.fromJson(SessionCacheJson.toJson(session))
        assertEquals(session, decoded)
    }

    @Test
    fun `round trip survives a session with no optional fields`() {
        val minimal = session.copy(email = null, displayName = null, photoUrl = null)
        val decoded = SessionCacheJson.fromJson(SessionCacheJson.toJson(minimal))
        assertEquals(minimal, decoded)
    }

    @Test
    fun `every access status round trips`() {
        for (status in AccessStatus.values()) {
            val decoded = SessionCacheJson.fromJson(SessionCacheJson.toJson(session.copy(access = status)))
            assertEquals(status, decoded?.access)
        }
    }

    @Test
    fun `toAuthUser rebuilds the identity the startup path needs`() {
        val user = session.toAuthUser()
        assertEquals("u1", user.uid)
        assertEquals("juan@gmail.com", user.email)
        assertEquals("Juan Pérez", user.displayName)
        assertNull(user.photoUrl)
    }

    @Test
    fun `blank and malformed input yields null`() {
        for (bad in listOf("", "   ", "not json", "{", "[]", "{\"uid\":")) {
            assertNull("debería rechazar '$bad'", SessionCacheJson.fromJson(bad))
        }
    }

    @Test
    fun `a future version yields null instead of being misread`() {
        val json = SessionCacheJson.toJson(session).replace("\"version\": 1", "\"version\": 2")
        assertNull(SessionCacheJson.fromJson(json))
    }

    @Test
    fun `a blank uid yields null`() {
        val json = SessionCacheJson.toJson(session).replace("\"u1\"", "\"\"")
        assertNull(SessionCacheJson.fromJson(json))
    }

    @Test
    fun `an unrecognized access status yields null, never a default`() {
        // Defaulting to APPROVED would open the app on a corrupt file; defaulting to PENDING
        // would lock out a legitimate user. Neither: the cache is simply unusable.
        val json = SessionCacheJson.toJson(session).replace("\"APPROVED\"", "\"WHATEVER\"")
        assertNull(SessionCacheJson.fromJson(json))
    }

    @Test
    fun `a missing access status yields null`() {
        val json = """{"version":1,"uid":"u1","savedAtMs":1}"""
        assertNull(SessionCacheJson.fromJson(json))
    }

    @Test
    fun `a JSON null email decodes as null, not as the string "null"`() {
        val decoded = SessionCacheJson.fromJson(SessionCacheJson.toJson(session.copy(email = null)))
        assertNull(decoded?.email)
    }

    @Test
    fun `a missing savedAtMs decodes as zero rather than failing`() {
        // savedAtMs is informational: it must never be the reason a usable session is refused.
        val json = """{"version":1,"uid":"u1","access":"APPROVED"}"""
        val decoded = SessionCacheJson.fromJson(json)
        assertNotNull(decoded)
        assertEquals(0L, decoded!!.savedAtMs)
    }

    @Test
    fun `the cache never contains the password`() {
        // Guard rail: the session password lives in memory only. If someone adds it to
        // CachedSession, this fails and they have to argue with the reason.
        val json = SessionCacheJson.toJson(
            session.copy(email = "a@b.c", displayName = "clave-secreta-en-el-nombre")
        )
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("contrasena", ignoreCase = true))
    }
}
