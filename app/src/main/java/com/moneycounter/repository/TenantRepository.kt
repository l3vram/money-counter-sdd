package com.moneycounter.repository

import com.moneycounter.domain.Branch
import com.moneycounter.domain.Organization

/**
 * Seam for tenant master data. The local JSON implementation is the bootstrap;
 * a future `AppwriteTenantRepository` swaps in keeping this interface so the
 * ViewModel never changes.
 */
interface TenantRepository {
    fun loadOrganization(): Organization?
    fun loadBranches(): List<Branch>
    fun saveOrganization(organization: Organization)
    fun saveBranches(branches: List<Branch>)

    /**
     * Idempotent bootstrap seed (plan 019): creates the default org/branch only
     * when absent, persists only what was created, returns what to use. Default
     * impl works for any repository (io-free; reuse the pure [TenantJson] decision).
     */
    fun ensureDefaultBootstrap(uid: String): Pair<Organization, Branch> {
        val existingOrg = loadOrganization()
        val existingBranches = loadBranches()
        val seeded = TenantJson.ensureSeeded(existingOrg, existingBranches, uid)
        if (existingOrg == null) saveOrganization(seeded.first)
        if (existingBranches.isEmpty()) saveBranches(listOf(seeded.second))
        return seeded
    }

    /**
     * Idempotent cloud seed (plan 028): merges an approved member's cloud
     * org/branches into the local config without overwriting existing data.
     * Default impl works for any repository (io-free; reuse the pure
     * [TenantJson.mergeCloudSeed] decision).
     */
    fun seedFromCloud(org: Organization, branches: List<Branch>) {
        val existingOrg = loadOrganization()
        val existingBranches = loadBranches()
        val (resultOrg, resultBranches) =
            TenantJson.mergeCloudSeed(existingOrg, existingBranches, org, branches)
        if (resultOrg != existingOrg) saveOrganization(resultOrg)
        if (resultBranches != existingBranches) saveBranches(resultBranches)
    }
}

/** Membership wins: a non-blank cloud-provided orgId overrides the local bootstrap. */
fun resolveOrganizationId(memberOrgId: String?, bootstrapOrgId: String): String =
    memberOrgId?.takeIf { it.isNotBlank() } ?: bootstrapOrgId

fun branchBelongsToOrg(branch: Branch, orgId: String): Boolean = branch.orgId == orgId