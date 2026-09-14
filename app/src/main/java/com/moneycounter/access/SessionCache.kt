package com.moneycounter.access

import android.content.Context
import com.moneycounter.auth.AuthUser
import org.json.JSONObject
import java.io.File

/**
 * The last known signed-in identity and its access status (plan 034).
 *
 * The app is offline-first for its data but was online-only for its session: `currentUser()`
 * and `ensureUserDocument()` both hit the network, so launching without connectivity landed on
 * the connection-error screen and the user could not even look at what they already had.
 * This cache is what lets the app open offline.
 *
 * Pure (de)serialization, no Android I/O — unit-tested directly, mirroring
 * [MemberCacheJson] and [com.moneycounter.repository.TenantJson].
 *
 * **It never stores the password.** The ViewModel keeps the session password in memory for the
 * forced change, and that must not reach disk. This holds identity and access status only: the
 * two answers the startup path needs from the server.
 *
 * Like [MemberCacheJson], this is a convenience and NOT an authorization boundary. The real
 * boundary is server-side. What keeps a revoked account from living here forever is the other
 * half of plan 034: an explicit 401 signs the session out.
 */
data class CachedSession(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val access: AccessStatus,
    val savedAtMs: Long
) {
    fun toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl
    )
}

object SessionCacheJson {

    private const val VERSION = 1

    fun toJson(session: CachedSession): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("uid", session.uid)
        root.put("email", session.email ?: JSONObject.NULL)
        root.put("displayName", session.displayName ?: JSONObject.NULL)
        root.put("photoUrl", session.photoUrl ?: JSONObject.NULL)
        root.put("access", session.access.name)
        root.put("savedAtMs", session.savedAtMs)
        return root.toString(2)
    }

    /**
     * Returns null on anything unusable — blank input, wrong version, malformed document,
     * blank uid or an unrecognized access status. Never throws, and never returns a partially
     * built session: a half-read cache must not decide whether the app opens.
     */
    fun fromJson(json: String): CachedSession? {
        if (json.isBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrElse { return null }
        if (root.optInt("version", 1) != VERSION) return null
        val uid = root.optString("uid", "")
        if (uid.isBlank()) return null
        val access = AccessStatus.fromStorage(
            root.optString("access").takeIf { it.isNotBlank() }
        ) ?: return null
        return CachedSession(
            uid = uid,
            email = root.optStringOrNull("email"),
            displayName = root.optStringOrNull("displayName"),
            photoUrl = root.optStringOrNull("photoUrl"),
            access = access,
            savedAtMs = root.optLong("savedAtMs", 0L)
        )
    }

    /** `optString` turns a JSON null into the string "null"; this keeps it nullable. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/**
 * Seam for the last known session. The JSON implementation is the local cache; nothing else is
 * expected to implement it.
 */
interface SessionCacheRepository {
    fun load(): CachedSession?
    fun save(session: CachedSession)
    fun clear()
}

class JsonSessionCacheRepository(private val context: Context) : SessionCacheRepository {

    private val cacheFile = File(context.filesDir, "session-cache.json")

    override fun load(): CachedSession? {
        return try {
            if (!cacheFile.exists()) return null
            SessionCacheJson.fromJson(cacheFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override fun save(session: CachedSession) {
        try {
            val temp = File(context.filesDir, "session-cache.json.tmp")
            temp.writeText(SessionCacheJson.toJson(session))
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
