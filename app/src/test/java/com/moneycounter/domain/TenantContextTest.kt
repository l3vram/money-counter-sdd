package com.moneycounter.domain

import com.moneycounter.repository.branchBelongsToOrg
import com.moneycounter.repository.resolveOrganizationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantContextTest {

    @Test
    fun `blank member org falls back to bootstrap org`() {
        assertEquals("bootstrap-org", resolveOrganizationId(null, "bootstrap-org"))
        assertEquals("bootstrap-org", resolveOrganizationId("", "bootstrap-org"))
        assertEquals("bootstrap-org", resolveOrganizationId("   ", "bootstrap-org"))
    }

    @Test
    fun `present member org wins over bootstrap org`() {
        assertEquals("membership-org", resolveOrganizationId("membership-org", "bootstrap-org"))
    }

    @Test
    fun `branch belongs to matching org only`() {
        val branch = Branch(id = "b1", orgId = "org-1", name = "Principal")
        assertTrue(branchBelongsToOrg(branch, "org-1"))
        assertFalse(branchBelongsToOrg(branch, "org-2"))
    }
}