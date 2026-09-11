package com.moneycounter.appwrite

import com.moneycounter.access.MembershipRepository
import com.moneycounter.domain.Member
import com.moneycounter.firestore.memberFromMap
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Appwrite-backed membership repository. Reads the `members` table where each
 * row is keyed by the user uid (replicating the former Firestore `members/{uid}`).
 */
class AppwriteMembershipRepository(
    private val tables: TablesDB = TablesDB(Appwrite.client),
    private val databaseId: String = Appwrite.DATABASE_ID,
    private val tableId: String = Appwrite.MEMBERS_TABLE,
    private val refreshIntervalMillis: Long = REFRESH_INTERVAL
) : MembershipRepository {

    override fun observeMember(uid: String): Flow<Member?> = callbackFlow {
        val pollingJob = launch {
            while (isActive) {
                try {
                    val row = tables.getRow(databaseId, tableId, uid)
                    trySend(memberFromMap(uid, row.data))
                } catch (e: Exception) {
                    // Missing row (404) or permission/connectivity issue:
                    // degrade to "no member" (single-user privileges) instead of crashing.
                    trySend(null)
                }
                delay(refreshIntervalMillis)
            }
        }
        awaitClose { pollingJob.cancel() }
    }

    private companion object {
        const val REFRESH_INTERVAL = 10_000L
    }
}