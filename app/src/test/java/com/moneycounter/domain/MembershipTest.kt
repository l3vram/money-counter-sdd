package com.moneycounter.domain

import org.junit.Assert.*
import org.junit.Test

class MembershipTest {

    @Test
    fun `member belongs to branch when branch id is present`() {
        val member = Member(uid = "u1", orgId = "org1", role = Role.SELLER, branchIds = listOf("b1", "b2"))
        assertTrue(member.belongsToBranch("b1"))
        assertTrue(member.belongsToBranch("b2"))
    }

    @Test
    fun `member does not belong to branch when branch id is absent`() {
        val member = Member(uid = "u1", orgId = "org1", role = Role.SELLER, branchIds = listOf("b1"))
        assertFalse(member.belongsToBranch("b2"))
    }

    @Test
    fun `member belongs to org when org id matches`() {
        val member = Member(uid = "u1", orgId = "orgX", role = Role.OWNER)
        assertTrue(member.belongsToOrg("orgX"))
    }

    @Test
    fun `member does not belong to org when org id does not match`() {
        val member = Member(uid = "u1", orgId = "orgX", role = Role.OWNER)
        assertFalse(member.belongsToOrg("orgY"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `organization requires non‑blank id`() {
        Organization(id = "", name = "Name", ownerUid = "owner", createdAt = System.currentTimeMillis())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `organization requires non‑blank name`() {
        Organization(id = "org1", name = "   ", ownerUid = "owner", createdAt = System.currentTimeMillis())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `organization requires non‑blank ownerUid`() {
        Organization(id = "org1", name = "Name", ownerUid = "", createdAt = System.currentTimeMillis())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `branch requires non‑blank id`() {
        Branch(id = "", orgId = "org1", name = "Branch")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `branch requires non‑blank orgId`() {
        Branch(id = "b1", orgId = "   ", name = "Branch")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `branch requires non‑blank name`() {
        Branch(id = "b1", orgId = "org1", name = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `member requires non‑blank uid`() {
        Member(uid = "", orgId = "org1", role = Role.SELLER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `member requires non‑blank orgId`() {
        Member(uid = "u1", orgId = "   ", role = Role.SELLER)
    }
}