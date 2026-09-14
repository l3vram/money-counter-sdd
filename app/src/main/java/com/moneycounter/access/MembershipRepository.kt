package com.moneycounter.access

import com.moneycounter.domain.Member
import kotlinx.coroutines.flow.Flow

/**
 * What the membership poll learned (plan 034).
 *
 * This used to be a plain `Member?`, which squeezed three different answers into two values:
 * a real membership, a genuinely absent one, and "the server did not tell me". The third was
 * silently dropped, so a **401 — a revoked session — looked exactly like a network hiccup**
 * and the app kept running on a dead session until the next restart.
 *
 * Making it explicit matters because the three lead to opposite actions: open the app, show
 * "waiting for assignment" (plan 033), or sign the user out.
 */
sealed interface MembershipUpdate {
    /** The row exists and is usable. */
    data class Assigned(val member: Member) : MembershipUpdate

    /** The row is genuinely absent (404) — or unreadable garbage, which is as good as absent. */
    object Missing : MembershipUpdate

    /** The session itself is gone (401): revoked, deleted, or access withdrawn. */
    object Revoked : MembershipUpdate
}

interface MembershipRepository {
    /**
     * Emits on every poll that learned something. A transient failure — no connectivity, a
     * 5xx — emits **nothing**, which is what keeps the last known membership in place
     * (plan 030).
     */
    fun observeMember(uid: String): Flow<MembershipUpdate>
}
