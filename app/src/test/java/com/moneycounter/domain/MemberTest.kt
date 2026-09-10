package com.moneycounter.domain

import org.junit.Test
import org.junit.Assert.*

class MemberTest {

    @Test fun `belongsToBranch returns true when branch id present`() {
        val member = Member(uid = "u1", orgId = "o1", role = Role.SELLER, branchIds = listOf("b1", "b2"))
        assertTrue(member.belongsToBranch("b1"))
    }

    @Test fun `belongsToBranch returns false when branch id absent`() {
        val member = Member(uid = "u1", orgId = "o1", role = Role.SELLER, branchIds = listOf("b1"))
        assertFalse(member.belongsToBranch("b2"))
    }

    @Test fun `belongsToOrg returns true for matching org id`() {
        val member = Member(uid = "u1", orgId = "org123", role = Role.OWNER)
        assertTrue(member.belongsToOrg("org123"))
    }

    @Test fun `belongsToOrg returns false for non‑matching org id`() {
        val member = Member(uid = "u1", orgId = "org123", role = Role.OWNER)
        assertFalse(member.belongsToOrg("otherOrg"))
    }

    @Test fun `creating Member with blank uid throws`() {
        try {
            Member(uid = "", orgId = "org", role = Role.SELLER)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `creating Member with blank orgId throws`() {
        try {
            Member(uid = "uid", orgId = "", role = Role.SELLER)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}