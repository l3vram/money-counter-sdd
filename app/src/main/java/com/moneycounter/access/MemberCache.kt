package com.moneycounter.access

import android.content.Context
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pure (de)serialization for the locally cached membership (plan 030).
 * No Android I/O here: unit-tested directly, mirroring [com.moneycounter.repository.TenantJson].
 *
 * The cache exists so a connectivity failure cannot degrade the session to
 * "no member" — which would hand out [com.moneycounter.domain.DefaultPermissionService]
 * and therefore every permission. It is defense in depth and an offline-UX
 * enabler, NOT an authorization boundary: the real boundary must live server-side.
 */
object MemberCacheJson {

    private const val VERSION = 1

    fun toJson(member: Member): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("uid", member.uid)
        root.put("orgId", member.orgId)
        root.put("role", Role.toStorage(member.role))
        root.put("branchIds", JSONArray(member.branchIds))
        return root.toString(2)
    }

    /** Returns null on anything unusable — blank input, wrong version, malformed
     *  document, blank uid/orgId or an unrecognized role. Never throws. */
    fun fromJson(json: String): Member? {
        if (json.isBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrElse { return null }
        if (root.optInt("version", 1) != VERSION) return null
        val uid = root.optString("uid", "")
        val orgId = root.optString("orgId", "")
        if (uid.isBlank() || orgId.isBlank()) return null
        val role = Role.fromStorage(root.optString("role").takeIf { it.isNotBlank() }) ?: return null
        val branchIds = mutableListOf<String>()
        root.optJSONArray("branchIds")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let { branchIds.add(it) }
            }
        }
        return try {
            Member(uid = uid, orgId = orgId, role = role, branchIds = branchIds)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Seam for the last known membership. The JSON implementation is the local
 * cache; nothing else is expected to implement it.
 */
interface MemberCacheRepository {
    fun load(): Member?
    fun save(member: Member)
    fun clear()
}

class JsonMemberCacheRepository(private val context: Context) : MemberCacheRepository {

    private val cacheFile = File(context.filesDir, "member-cache.json")

    override fun load(): Member? {
        return try {
            if (!cacheFile.exists()) return null
            MemberCacheJson.fromJson(cacheFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override fun save(member: Member) {
        try {
            val temp = File(context.filesDir, "member-cache.json.tmp")
            temp.writeText(MemberCacheJson.toJson(member))
            temp.renameTo(cacheFile)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other Json repos
        }
    }

    override fun clear() {
        try {
            if (cacheFile.exists()) cacheFile.delete()
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other Json repos
        }
    }
}
