package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.Branch
import com.moneycounter.domain.Organization
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pure (de)serialization + seed decision for the local tenant master data.
 * No Android I/O here: unit-tested directly.
 */
object TenantJson {

    private const val ORG_VERSION = 1
    private const val BRANCH_VERSION = 1

    const val DEFAULT_ORG_ID = "org-1"
    const val DEFAULT_ORG_NAME = "Mi Negocio"
    const val DEFAULT_BRANCH_ID = "branch-1"
    const val DEFAULT_BRANCH_NAME = "Principal"
    const val LOCAL_OWNER_FALLBACK = "local-owner"

    fun orgToJson(org: Organization): String {
        val root = JSONObject()
        root.put("version", ORG_VERSION)
        root.put("id", org.id)
        root.put("name", org.name)
        root.put("ownerUid", org.ownerUid)
        root.putOpt("whatsappNumber", org.whatsappNumber)
        root.put("createdAt", org.createdAt)
        root.put("active", org.active)
        return root.toString(2)
    }

    fun orgFromJson(json: String): Organization? {
        if (json.isBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrElse { return null }
        if (root.optInt("version", 1) != ORG_VERSION) return null
        val id = root.optString("id", "")
        val name = root.optString("name", "")
        val ownerUid = root.optString("ownerUid", "")
        if (id.isBlank() || name.isBlank() || ownerUid.isBlank()) return null
        return try {
            Organization(
                id = id,
                name = name,
                ownerUid = ownerUid,
                whatsappNumber = root.optString("whatsappNumber").takeIf { it.isNotBlank() },
                createdAt = root.optLong("createdAt", 0L),
                active = root.optBoolean("active", true)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun branchesToJson(branches: List<Branch>): String {
        val root = JSONObject()
        root.put("version", BRANCH_VERSION)
        val array = JSONArray()
        for (branch in branches) {
            val item = JSONObject()
            item.put("id", branch.id)
            item.put("orgId", branch.orgId)
            item.put("name", branch.name)
            item.putOpt("address", branch.address)
            item.put("active", branch.active)
            item.put("createdAt", branch.createdAt)
            array.put(item)
        }
        root.put("branches", array)
        return root.toString(2)
    }

    fun branchesFromJson(json: String): List<Branch> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        if (root.optInt("version", 1) != BRANCH_VERSION) return emptyList()

        val array = root.optJSONArray("branches") ?: return emptyList()
        val branches = mutableListOf<Branch>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val orgId = item.optString("orgId", "")
            val name = item.optString("name", "")
            if (id.isBlank() || orgId.isBlank() || name.isBlank()) continue
            try {
                branches.add(
                    Branch(
                        id = id,
                        orgId = orgId,
                        name = name,
                        address = item.optString("address").takeIf { it.isNotBlank() },
                        active = item.optBoolean("active", true),
                        createdAt = item.optLong("createdAt", 0L)
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
        return branches
    }

    /**
     * Idempotent seed decision: returns the org/branch to use, creating the
     * bootstrap defaults only when they are absent. A blank `uid` falls back to
     * LOCAL_OWNER_FALLBACK so offline boot works.
     */
    fun ensureSeeded(
        existingOrg: Organization?,
        existingBranches: List<Branch>,
        uid: String,
        now: Long = System.currentTimeMillis()
    ): Pair<Organization, Branch> {
        val owner = uid.takeIf { it.isNotBlank() } ?: LOCAL_OWNER_FALLBACK
        val org = existingOrg ?: Organization(
            id = DEFAULT_ORG_ID,
            name = DEFAULT_ORG_NAME,
            ownerUid = owner,
            createdAt = now
        )
        val branch = existingBranches.firstOrNull()
            ?: Branch(
                id = DEFAULT_BRANCH_ID,
                orgId = org.id,
                name = DEFAULT_BRANCH_NAME,
                createdAt = now
            )
        return org to branch
    }

    /**
     * Idempotent cloud-merge decision (plan 028): [cloudOrg]/[cloudBranches]
     * win only when the local config has no real org yet. The bootstrap default
     * org (DEFAULT_ORG_ID) is a local-only placeholder and is superseded by the
     * cloud org; an existing different org and its branches are never overwritten.
     * Returns the org/branches to persist.
     */
    fun mergeCloudSeed(
        existingOrg: Organization?,
        existingBranches: List<Branch>,
        cloudOrg: Organization,
        cloudBranches: List<Branch>
    ): Pair<Organization, List<Branch>> {
        val branchesOwned = cloudBranches.filter { it.orgId == cloudOrg.id }
        if (existingOrg == null || existingOrg.id == DEFAULT_ORG_ID) {
            return cloudOrg to branchesOwned
        }
        if (existingOrg.id == cloudOrg.id) {
            val missing = branchesOwned.filter { cloudBranch ->
                existingBranches.none { it.id == cloudBranch.id }
            }
            return existingOrg to (existingBranches + missing)
        }
        return existingOrg to existingBranches
    }
}

class JsonTenantRepository(private val context: Context) : TenantRepository {

    private val orgFile = File(context.filesDir, "org.json")
    private val branchesFile = File(context.filesDir, "branches.json")

    override fun loadOrganization(): Organization? {
        return try {
            if (!orgFile.exists()) return null
            TenantJson.orgFromJson(orgFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override fun loadBranches(): List<Branch> {
        return try {
            if (!branchesFile.exists()) return emptyList()
            TenantJson.branchesFromJson(branchesFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveOrganization(organization: Organization) {
        try {
            val temp = File(context.filesDir, "org.json.tmp")
            temp.writeText(TenantJson.orgToJson(organization))
            temp.renameTo(orgFile)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other Json repos
        }
    }

    override fun saveBranches(branches: List<Branch>) {
        try {
            val temp = File(context.filesDir, "branches.json.tmp")
            temp.writeText(TenantJson.branchesToJson(branches))
            temp.renameTo(branchesFile)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other Json repos
        }
    }
}