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
 * Only a genuinely missing row means "this install has no membership" (plan 030).
 * Every other failure — connectivity, permissions, server error — must leave the last known
 * member in place. Since plan 033, "no member" means
 * [com.moneycounter.domain.NoAccessPermissionService] and therefore a locked session, so
 * mistaking a transport failure for a missing row now locks out a legitimate user instead of
 * promoting them. Both directions are wrong; the row must be genuinely absent.
 * A null code means the throwable carried no HTTP status, so it is a transport
 * failure, not a missing row.
 */
fun isMemberRowMissing(code: Int?): Boolean = code == 404

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
                    if (isMemberRowMissing((e as? AppwriteException)?.code)) {
                        // Genuinely no membership row: legacy single-user install.
                        trySend(null)
                    }
                    // Otherwise keep the last known member: a connectivity or server
                    // failure must never widen this session's permissions.
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