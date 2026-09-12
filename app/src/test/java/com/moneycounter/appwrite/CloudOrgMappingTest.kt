package com.moneycounter.appwrite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOrgMappingTest {

    private fun orgRow(
        name: String = "Mi Tienda",
        whatsapp: String? = "+53 555",
        status: String = TenantCloudFields.STATUS_ACTIVE,
        createdAt: Long = 1000L
    ) = mapOf(
        TenantCloudFields.FIELD_NAME to name,
        TenantCloudFields.FIELD_WHATSAPP to whatsapp,
        TenantCloudFields.FIELD_STATUS to status,
        TenantCloudFields.FIELD_CREATED_AT to createdAt
    )

    private fun branchRow(
        orgId: String = "org-cloud",
        name: String = "Principal",
        status: String = TenantCloudFields.STATUS_ACTIVE,
        createdAt: Long = 1000L
    ) = mapOf(
        TenantCloudFields.FIELD_ORG_ID to orgId,
        TenantCloudFields.FIELD_NAME to name,
        TenantCloudFields.FIELD_STATUS to status,
        TenantCloudFields.FIELD_CREATED_AT to createdAt
    )

    @Test
    fun `active org row maps to OrgInfo`() {
        val org = TenantCloudFields.orgInfoFromRow("org-cloud", orgRow())
        assertEquals("org-cloud", org!!.id)
        assertEquals("Mi Tienda", org.name)
        assertEquals("+53 555", org.whatsappNumber)
        assertEquals(TenantCloudFields.STATUS_ACTIVE, org.status)
        assertEquals(1000L, org.createdAt)
        assertTrue(org.isActive())
    }

    @Test
    fun `blank id or blank name degrades to null`() {
        assertNull(TenantCloudFields.orgInfoFromRow("", orgRow()))
        assertNull(TenantCloudFields.orgInfoFromRow("org-cloud", orgRow(name = " ")))
        assertNull(TenantCloudFields.orgInfoFromRow("org-cloud", orgRow().minus(TenantCloudFields.FIELD_NAME)))
    }

    @Test
    fun `missing or malformed createdAt degrades to zero without failing`() {
        val noCreatedAt = TenantCloudFields.orgInfoFromRow("org-cloud", orgRow().minus(TenantCloudFields.FIELD_CREATED_AT))
        assertEquals(0L, noCreatedAt!!.createdAt)
        val doubleCreatedAt = TenantCloudFields.orgInfoFromRow(
            "org-cloud",
            orgRow().plus(TenantCloudFields.FIELD_CREATED_AT to 1500.0)
        )
        assertEquals(1500L, doubleCreatedAt!!.createdAt)
    }

    @Test
    fun `suspended org maps but is filtered out as inactive`() {
        val org = TenantCloudFields.orgInfoFromRow(
            "org-cloud",
            orgRow(status = TenantCloudFields.STATUS_SUSPENDED)
        )
        assertEquals(TenantCloudFields.STATUS_SUSPENDED, org!!.status)
        assertFalse(org.isActive())
    }

    @Test
    fun `missing status degrades to null`() {
        assertNull(TenantCloudFields.orgInfoFromRow("org-cloud", orgRow().minus(TenantCloudFields.FIELD_STATUS)))
        assertNull(TenantCloudFields.branchInfoFromRow("b1", branchRow().minus(TenantCloudFields.FIELD_STATUS)))
    }

    @Test
    fun `branch row maps to BranchInfo`() {
        val branch = TenantCloudFields.branchInfoFromRow("b1", branchRow())
        assertEquals("b1", branch!!.id)
        assertEquals("org-cloud", branch.orgId)
        assertEquals("Principal", branch.name)
        assertEquals(TenantCloudFields.STATUS_ACTIVE, branch.status)
        assertEquals(1000L, branch.createdAt)
        assertTrue(branch.isActive())
    }

    @Test
    fun `suspended branch maps but is filtered out as inactive`() {
        val branch = TenantCloudFields.branchInfoFromRow(
            "b1",
            branchRow(status = TenantCloudFields.STATUS_SUSPENDED)
        )
        assertFalse(branch!!.isActive())
        assertEquals(TenantCloudFields.STATUS_SUSPENDED, branch.status)
    }

    @Test
    fun `branch without orgId or blank name degrades to null`() {
        assertNull(TenantCloudFields.branchInfoFromRow("b1", branchRow().minus(TenantCloudFields.FIELD_ORG_ID)))
        assertNull(TenantCloudFields.branchInfoFromRow("", branchRow()))
        assertNull(TenantCloudFields.branchInfoFromRow("b1", branchRow(name = "")))
    }
}