package com.moneycounter.appwrite

import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB

/** Cloud `orgs` row shape (columns: id, name, whatsappNumber, status, createdAt). */
data class OrgInfo(
    val id: String,
    val name: String,
    val whatsappNumber: String?,
    val status: String,
    val createdAt: Long
)

/** Cloud `branches` row shape (columns: id, orgId, name, status, createdAt). */
data class BranchInfo(
    val id: String,
    val orgId: String,
    val name: String,
    val status: String,
    val createdAt: Long
)

object TenantCloudFields {
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_SUSPENDED = "SUSPENDED"
    const val FIELD_NAME = "name"
    const val FIELD_WHATSAPP = "whatsappNumber"
    const val FIELD_STATUS = "status"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_ORG_ID = "orgId"

    fun orgInfoFromRow(id: String, data: Map<String, Any?>): OrgInfo? {
        if (id.isBlank()) return null
        val name = data[FIELD_NAME] as? String ?: return null
        if (name.isBlank()) return null
        val status = data[FIELD_STATUS] as? String ?: return null
        val createdAt = (data[FIELD_CREATED_AT] as? Number)?.toLong() ?: 0L
        return OrgInfo(id, name, data[FIELD_WHATSAPP] as? String, status, createdAt)
    }

    fun branchInfoFromRow(id: String, data: Map<String, Any?>): BranchInfo? {
        if (id.isBlank()) return null
        val orgId = data[FIELD_ORG_ID] as? String ?: return null
        if (orgId.isBlank()) return null
        val name = data[FIELD_NAME] as? String ?: return null
        if (name.isBlank()) return null
        val status = data[FIELD_STATUS] as? String ?: return null
        val createdAt = (data[FIELD_CREATED_AT] as? Number)?.toLong() ?: 0L
        return BranchInfo(id, orgId, name, status, createdAt)
    }
}

fun OrgInfo.isActive(): Boolean = status == TenantCloudFields.STATUS_ACTIVE

fun BranchInfo.isActive(): Boolean = status == TenantCloudFields.STATUS_ACTIVE

/** Read-only view of org/branch master data in Appwrite TablesDB (plan 028). */
interface CloudOrgRepository {
    suspend fun getOrg(orgId: String): OrgInfo?
    suspend fun getBranches(orgId: String): List<BranchInfo>
}

/**
 * Appwrite-backed cloud org repository. Reads the `orgs` and `branches` tables
 * (read("users")), mapping only ACTIVE rows into the local model.
 */
class AppwriteCloudOrgRepository(
    private val tables: TablesDB = TablesDB(Appwrite.client),
    private val databaseId: String = Appwrite.DATABASE_ID,
    private val orgsTableId: String = Appwrite.ORGS_TABLE,
    private val branchesTableId: String = Appwrite.BRANCHES_TABLE
) : CloudOrgRepository {

    override suspend fun getOrg(orgId: String): OrgInfo? {
        val row = try {
            tables.getRow(databaseId, orgsTableId, orgId)
        } catch (e: AppwriteException) {
            if (e.code == 404) return null
            throw e
        }
        return TenantCloudFields.orgInfoFromRow(row.id, row.data)
            ?.takeIf { it.isActive() }
    }

    override suspend fun getBranches(orgId: String): List<BranchInfo> {
        val result = tables.listRows(
            databaseId,
            branchesTableId,
            listOf(Query.equal(TenantCloudFields.FIELD_ORG_ID, orgId))
        )
        return result.rows
            .mapNotNull { TenantCloudFields.branchInfoFromRow(it.id, it.data) }
            .filter { it.orgId == orgId && it.isActive() }
    }
}