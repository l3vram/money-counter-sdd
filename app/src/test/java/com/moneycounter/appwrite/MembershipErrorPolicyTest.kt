package com.moneycounter.appwrite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan 030: which membership-read failures are allowed to mean "no member".
 *  Getting this wrong grants every permission to an offline session. */
class MembershipErrorPolicyTest {

    @Test
    fun `a 404 is the only genuine missing row`() {
        assertTrue(isMemberRowMissing(404))
    }

    @Test
    fun `a throwable with no http status is a transport failure not a missing row`() {
        assertFalse(isMemberRowMissing(null))
    }

    @Test
    fun `auth and permission failures are not a missing row`() {
        assertFalse(isMemberRowMissing(401))
        assertFalse(isMemberRowMissing(403))
    }

    @Test
    fun `server failures are not a missing row`() {
        assertFalse(isMemberRowMissing(500))
        assertFalse(isMemberRowMissing(503))
    }

    @Test
    fun `rate limiting is not a missing row`() {
        assertFalse(isMemberRowMissing(429))
    }
}
