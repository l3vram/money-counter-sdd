package com.moneycounter.access

import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plan 030: the cached membership is what keeps an offline session from
 *  degrading to "no member" (which would grant every permission). */
class MemberCacheTest {

    @Test
    fun `round trip preserves uid orgId role and branch ids in order`() {
        val member = Member(
            uid = "u-1",
            orgId = "org-1",
            role = Role.SELLER,
            branchIds = listOf("b1", "b2")
        )

        val restored = MemberCacheJson.fromJson(MemberCacheJson.toJson(member))

        assertEquals(member, restored)
        assertEquals(listOf("b1", "b2"), restored?.branchIds)
    }

    @Test
    fun `round trip preserves a membership with no branches`() {
        val member = Member(uid = "u-1", orgId = "org-1", role = Role.OWNER)

        assertEquals(member, MemberCacheJson.fromJson(MemberCacheJson.toJson(member)))
    }

    @Test
    fun `blank json returns null`() {
        assertNull(MemberCacheJson.fromJson(""))
        assertNull(MemberCacheJson.fromJson("   "))
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(MemberCacheJson.fromJson("{ not json"))
    }

    @Test
    fun `unknown version returns null`() {
        val json = """{"version":2,"uid":"u-1","orgId":"org-1","role":"SELLER","branchIds":[]}"""

        assertNull(MemberCacheJson.fromJson(json))
    }

    @Test
    fun `unknown role returns null`() {
        val json = """{"version":1,"uid":"u-1","orgId":"org-1","role":"PRESIDENT","branchIds":[]}"""

        assertNull(MemberCacheJson.fromJson(json))
    }

    @Test
    fun `missing role returns null`() {
        val json = """{"version":1,"uid":"u-1","orgId":"org-1","branchIds":[]}"""

        assertNull(MemberCacheJson.fromJson(json))
    }

    @Test
    fun `blank uid returns null`() {
        val json = """{"version":1,"uid":"","orgId":"org-1","role":"SELLER","branchIds":[]}"""

        assertNull(MemberCacheJson.fromJson(json))
    }

    @Test
    fun `blank orgId returns null`() {
        val json = """{"version":1,"uid":"u-1","orgId":"","role":"SELLER","branchIds":[]}"""

        assertNull(MemberCacheJson.fromJson(json))
    }
}
