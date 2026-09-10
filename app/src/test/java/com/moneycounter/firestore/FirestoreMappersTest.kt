package com.moneycounter.firestore

import com.moneycounter.domain.Organization
import com.moneycounter.domain.Branch
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import org.junit.Test
import org.junit.Assert.*

class FirestoreMappersTest {

    @Test
    fun `organization toMap and fromMap round trip`() {
        val org = Organization(
            id = "org1",
            name = "MyOrg",
            ownerUid = "owner123",
            whatsappNumber = "555-1234",
            createdAt = 1620000000000L
        )
        val map = org.toMap()
        val restored = organizationFromMap(org.id, map)
        assertEquals(org, restored)
    }

    @Test
    fun `organization fromMap returns null when required field missing`() {
        val map = mapOf(
            FirestoreFields.FIELD_NAME to "MyOrg",
            FirestoreFields.FIELD_OWNER_UID to "owner123"
            // missing createdAt
        )
        assertNull(organizationFromMap("orgX", map))
    }

    @Test
    fun `branch toMap and fromMap round trip`() {
        val branch = Branch(id = "b1", orgId = "org1", name = "Main")
        val map = branch.toMap()
        val restored = branchFromMap(branch.id, map)
        assertEquals(branch, restored)
    }

    @Test
    fun `branch fromMap returns null when blank name`() {
        val map = mapOf(
            FirestoreFields.FIELD_ORG_ID to "org1",
            FirestoreFields.FIELD_NAME to "   "
        )
        assertNull(branchFromMap("b2", map))
    }

    @Test
    fun `member toMap and fromMap round trip`() {
        val member = Member(
            uid = "uid99",
            orgId = "org1",
            role = Role.OWNER,
            branchIds = listOf("b1", "b2")
        )
        val map = member.toMap()
        val restored = memberFromMap(member.uid, map)
        assertEquals(member, restored)
    }

    @Test
    fun `member fromMap returns null when unknown role`() {
        val map = mapOf(
            FirestoreFields.FIELD_ORG_ID to "org1",
            FirestoreFields.FIELD_ROLE to "unknown_role",
            FirestoreFields.FIELD_BRANCH_IDS to listOf<String>()
        )
        assertNull(memberFromMap("uidX", map))
    }
}