package com.moneycounter.appwrite

import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.UserProfileData
import com.moneycounter.auth.AuthUser
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Appwrite-backed access repository. Reads/writes the `users` table where each
 * row is keyed by the user uid and stores `email`, `displayName`, `photoUrl`,
 * `access` and timestamps (replicating the former Firestore `users/{uid}`).
 */
class AppwriteAccessRepository(
    private val tables: TablesDB = TablesDB(Appwrite.client),
    private val databaseId: String = Appwrite.DATABASE_ID,
    private val tableId: String = Appwrite.USERS_TABLE,
    private val refreshIntervalMillis: Long = REFRESH_INTERVAL
) : AccessRepository {

    override suspend fun getAccess(uid: String): AccessStatus? {
        val row = try {
            tables.getRow(databaseId, tableId, uid)
        } catch (e: AppwriteException) {
            if (e.code == 404) return null
            throw e
        }
        return AccessStatus.fromStorage(row.data["access"] as? String)
    }

    override suspend fun ensureUserDocument(user: AuthUser): AccessStatus {
        val existing = try {
            tables.getRow(databaseId, tableId, user.uid)
        } catch (e: AppwriteException) {
            if (e.code == 404) null else throw e
        }
        if (existing != null) {
            return AccessStatus.fromStorage(existing.data["access"] as? String)
                ?: AccessStatus.PENDING
        }

        val now = System.currentTimeMillis()
        val data: Map<String, Any?> = mapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl,
            "access" to AccessStatus.PENDING.name,
            "createdAt" to now,
            "updatedAt" to now
        )
        tables.createRow(databaseId, tableId, user.uid, data)
        return AccessStatus.PENDING
    }

    override suspend fun getUserProfile(uid: String): UserProfileData? {
        val row = try {
            tables.getRow(databaseId, tableId, uid)
        } catch (e: AppwriteException) {
            if (e.code == 404) return null
            throw e
        }
        return toProfile(uid, row.data)
    }

    override fun observeUserProfile(uid: String): Flow<UserProfileData?> = callbackFlow {
        val pollingJob = launch {
            while (isActive) {
                try {
                    trySend(getUserProfile(uid))
                } catch (e: Exception) {
                    // Permission or connectivity issue: degrade instead of crashing.
                    trySend(null)
                }
                delay(refreshIntervalMillis)
            }
        }
        awaitClose { pollingJob.cancel() }
    }

    private fun toProfile(uid: String, data: Map<String, Any>): UserProfileData {
        return UserProfileData(
            uid = uid,
            email = data["email"] as? String,
            displayName = data["displayName"] as? String,
            photoUrl = data["photoUrl"] as? String,
            access = AccessStatus.fromStorage(data["access"] as? String),
            createdAtMillis = (data["createdAt"] as? Number)?.toLong(),
            updatedAtMillis = (data["updatedAt"] as? Number)?.toLong()
        )
    }

    private companion object {
        const val REFRESH_INTERVAL = 10_000L
    }
}