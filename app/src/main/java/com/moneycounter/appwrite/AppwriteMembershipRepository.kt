package com.moneycounter.appwrite

import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.MembershipUpdate
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
 * Classifies a failed membership read (plan 034). Returns null when there is nothing to
 * report — a transport failure or a server problem — which leaves the last known membership
 * untouched (plan 030).
 *
 * The distinction that matters: **404 means no membership, 401 means no session.** Before
 * plan 034 both fell into the same silent catch, so a revoked account kept operating on
 * cached data until the app was restarted.
 */
fun membershipUpdateFor(code: Int?): MembershipUpdate? = when {
    code == 401 -> MembershipUpdate.Revoked
    isMemberRowMissing(code) -> MembershipUpdate.Missing
    else -> null
}

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

    override fun observeMember(uid: String): Flow<MembershipUpdate> = callbackFlow {
        val pollingJob = launch {
            while (isActive) {
                try {
                    val row = tables.getRow(databaseId, tableId, uid)
                    val member = memberFromMap(uid, row.data)
                    // A row we cannot map is as good as absent, and saying so is fail-closed:
                    // plan 033 answers "missing" with a locked screen, not with permissions.
                    trySend(if (member != null) MembershipUpdate.Assigned(member) else MembershipUpdate.Missing)
                } catch (e: Exception) {
                    // Only a classified failure is reported. Everything else stays silent, so
                    // the last known membership survives a connectivity loss (plan 030).
                    membershipUpdateFor((e as? AppwriteException)?.code)?.let { trySend(it) }
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