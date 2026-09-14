package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 033: no usable membership means no access. These cases are the security boundary —
 * before this, a missing membership row handed the session every permission.
 */
class MemberOperableTest {

    @Test
    fun `no membership row cannot operate`() {
        val member: Member? = null
        assertFalse(member.isOperable())
    }

    @Test
    fun `superuser cannot operate — its place is the admin panel`() {
        val member = Member(uid = "u1", orgId = "platform", role = Role.SUPERUSER)
        assertFalse(member.isOperable())
    }

    @Test
    fun `superuser cannot operate even when branches were assigned by mistake`() {
        val member = Member(uid = "u1", orgId = "org1", role = Role.SUPERUSER, branchIds = listOf("b1"))
        assertFalse(member.isOperable())
    }

    @Test
    fun `membership without a branch cannot operate`() {
        for (role in listOf(Role.SELLER, Role.OWNER, Role.ADMIN)) {
            val member = Member(uid = "u1", orgId = "org1", role = role, branchIds = emptyList())
            assertFalse("$role without a branch must not operate", member.isOperable())
        }
    }

    @Test
    fun `seller owner and admin operate with an org and at least one branch`() {
        for (role in listOf(Role.SELLER, Role.OWNER, Role.ADMIN)) {
            val member = Member(uid = "u1", orgId = "org1", role = role, branchIds = listOf("b1"))
            assertTrue("$role with org and branch must operate", member.isOperable())
        }
    }

    @Test
    fun `several branches still operate`() {
        val member = Member(uid = "u1", orgId = "org1", role = Role.OWNER, branchIds = listOf("b1", "b2", "b3"))
        assertTrue(member.isOperable())
    }

    @Test
    fun `a blank orgId is rejected by the model itself, so isOperable never sees it`() {
        try {
            Member(uid = "u1", orgId = "", role = Role.SELLER, branchIds = listOf("b1"))
            fail("Member must reject a blank orgId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("orgId"))
        }
    }
}
