package com.moneycounter.domain

import com.moneycounter.repository.TenantJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantRepositoryTest {

    @Test
    fun `empty or malformed org json degrades to null`() {
        assertNull(TenantJson.orgFromJson(""))
        assertNull(TenantJson.orgFromJson("not-json{"))
        assertNull(TenantJson.orgFromJson("{invalid"))
        assertNull(TenantJson.orgFromJson("""{"version":1,"id":"o1","name":"","ownerUid":"u"}"""))
        assertNull(TenantJson.orgFromJson("""{"version":99,"id":"o1","name":"N","ownerUid":"u"}"""))
    }

    @Test
    fun `empty or malformed branches json degrades to empty list`() {
        assertTrue(TenantJson.branchesFromJson("").isEmpty())
        assertTrue(TenantJson.branchesFromJson("not-json").isEmpty())
        assertTrue(TenantJson.branchesFromJson("""{"version":1,"branches":"oops"}""").isEmpty())
        assertTrue(TenantJson.branchesFromJson("""{"version":99,"branches":[]}""").isEmpty())
        val withInvalidEntry = """
            {"version":1,"branches":[{"id":"","orgId":"o","name":"x"},{"id":"ok","orgId":"o","name":"Fina"}]}
        """.trimIndent()
        val loaded = TenantJson.branchesFromJson(withInvalidEntry)
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].id)
    }

    @Test
    fun `org round trip preserves fields including optional ones`() {
        val org = Organization(
            id = "org-1",
            name = "Mi Negocio",
            ownerUid = "user-7",
            whatsappNumber = "+53 555 1234",
            createdAt = 1750000000000L,
            active = false
        )
        val loaded = TenantJson.orgFromJson(TenantJson.orgToJson(org))
        assertEquals(org, loaded)
        assertEquals("org-1", loaded!!.id)
        assertEquals("Mi Negocio", loaded.name)
        assertEquals("user-7", loaded.ownerUid)
        assertEquals("+53 555 1234", loaded.whatsappNumber)
        assertEquals(1750000000000L, loaded.createdAt)
        assertFalse(loaded.active)
    }

    @Test
    fun `org round trip tolerates missing optionals`() {
        val org = Organization("org-2", "Bodega", "user-8", createdAt = 42L)
        val json = TenantJson.orgToJson(org)
        val loaded = TenantJson.orgFromJson(json)
        assertEquals(org, loaded)
        assertNull(loaded!!.whatsappNumber)
        assertTrue(loaded.active)
    }

    @Test
    fun `branches round trip preserves fields including optional ones`() {
        val branches = listOf(
            Branch(
                id = "branch-1",
                orgId = "org-1",
                name = "Principal",
                address = "Calle 12 #34",
                active = false,
                createdAt = 1750000000001L
            ),
            Branch("branch-2", "org-1", "Sucursal 2")
        )
        val loaded = TenantJson.branchesFromJson(TenantJson.branchesToJson(branches))
        assertEquals(branches, loaded)
        assertEquals("Calle 12 #34", loaded[0].address)
        assertFalse(loaded[0].active)
        assertEquals(1750000000001L, loaded[0].createdAt)
        assertNull(loaded[1].address)
        assertTrue(loaded[1].active)
    }

    @Test
    fun `seed creates default org and branch on empty state`() {
        val (org, branch) = TenantJson.ensureSeeded(null, emptyList(), "user-1", now = 12345L)
        assertEquals(TenantJson.DEFAULT_ORG_ID, org.id)
        assertEquals(TenantJson.DEFAULT_ORG_NAME, org.name)
        assertEquals("user-1", org.ownerUid)
        assertEquals(12345L, org.createdAt)
        assertEquals(TenantJson.DEFAULT_BRANCH_ID, branch.id)
        assertEquals(TenantJson.DEFAULT_BRANCH_NAME, branch.name)
        assertEquals(org.id, branch.orgId)
        assertEquals(12345L, branch.createdAt)
    }

    @Test
    fun `seed is idempotent on second call`() {
        val seeded = TenantJson.ensureSeeded(null, emptyList(), "user-1", now = 777L)
        val again = TenantJson.ensureSeeded(seeded.first, listOf(seeded.second), "user-1", now = 999L)
        assertEquals(seeded, again)
        assertEquals(777L, again.first.createdAt)
        assertEquals(777L, again.second.createdAt)
    }

    @Test
    fun `seed keeps existing org but seeds missing branch`() {
        val existingOrg = Organization("org-9", "Tienda", "user-3", createdAt = 111L)
        val (org, branch) = TenantJson.ensureSeeded(existingOrg, emptyList(), "user-3", now = 222L)
        assertEquals(existingOrg, org)
        assertEquals("org-9", branch.orgId)
    }

    @Test
    fun `blank uid falls back to local owner`() {
        val (org, _) = TenantJson.ensureSeeded(null, emptyList(), "", now = 1L)
        assertEquals(TenantJson.LOCAL_OWNER_FALLBACK, org.ownerUid)
        val (org2, _) = TenantJson.ensureSeeded(null, emptyList(), "   ", now = 1L)
        assertEquals(TenantJson.LOCAL_OWNER_FALLBACK, org2.ownerUid)
    }
}