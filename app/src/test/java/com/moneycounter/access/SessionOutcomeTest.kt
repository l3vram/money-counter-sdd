package com.moneycounter.access

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan 034. Getting this wrong in one direction locks out a legitimate user offline; in the
 * other it keeps a revoked one working. Both directions are covered here.
 */
class SessionOutcomeTest {

    @Test
    fun `a 401 signs out, with or without a cache`() {
        assertEquals(SessionOutcome.SIGN_OUT, sessionOutcomeFor(401, hasCachedSession = true))
        assertEquals(SessionOutcome.SIGN_OUT, sessionOutcomeFor(401, hasCachedSession = false))
    }

    @Test
    fun `a transport failure with a cache works offline`() {
        assertEquals(SessionOutcome.USE_CACHE, sessionOutcomeFor(null, hasCachedSession = true))
    }

    @Test
    fun `a transport failure with no cache shows the connection screen`() {
        assertEquals(SessionOutcome.FAIL, sessionOutcomeFor(null, hasCachedSession = false))
    }

    @Test
    fun `a 403 is not a revocation`() {
        // Only a 401 means "this session is gone". A 403 is a permission problem on some row,
        // and signing the user out over it would be a bug that is very hard to diagnose.
        assertEquals(SessionOutcome.USE_CACHE, sessionOutcomeFor(403, hasCachedSession = true))
    }

    @Test
    fun `server trouble is not a revocation either`() {
        for (code in listOf(429, 500, 502, 503, 504)) {
            assertEquals(
                "$code no debe cerrar la sesión",
                SessionOutcome.USE_CACHE,
                sessionOutcomeFor(code, hasCachedSession = true)
            )
        }
    }

    @Test
    fun `any failure without a cache fails, never signs out silently`() {
        for (code in listOf(null, 403, 404, 429, 500)) {
            assertEquals(
                "$code sin caché debe mostrar la pantalla de conexión",
                SessionOutcome.FAIL,
                sessionOutcomeFor(code, hasCachedSession = false)
            )
        }
    }

    @Test
    fun `a 404 with a cache keeps working offline`() {
        // A missing row is not a missing session; plan 033 decides what a missing membership
        // means, and it is a different question from whether the session is valid.
        assertEquals(SessionOutcome.USE_CACHE, sessionOutcomeFor(404, hasCachedSession = true))
    }
}
