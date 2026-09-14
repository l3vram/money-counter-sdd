package com.moneycounter.access

import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import com.moneycounter.domain.isOperable

/**
 * Plan 033: being approved is not enough to operate. `users.access = APPROVED` only says the
 * SUPERUSER let the account in; the `members` row says what it may do. Until that row exists
 * and is complete — business, at least one branch, an operational role — the app stays shut.
 *
 * Pure on purpose: the two inputs come from separate flows (the access status and the
 * membership poll) and the decision between them is the security-relevant part, so it is
 * unit-tested directly instead of through the ViewModel.
 *
 * [membershipResolved] distinguishes "the poll has not answered yet" from "the poll answered
 * and there is no row". Without it, a first launch would flash the locked screen before the
 * membership arrives. Unresolved shows [AppAccessState.Loading], which grants nothing either,
 * so the fail-closed property holds throughout.
 */
fun effectiveAccessState(
    state: AppAccessState,
    member: Member?,
    membershipResolved: Boolean
): AppAccessState {
    // Every other state already denies access or is mid-flow; only an approved session can
    // be widened into the operation, so only it needs the membership check.
    if (state !is AppAccessState.Approved) return state

    if (member == null && !membershipResolved) return AppAccessState.Loading

    return when {
        member?.role == Role.SUPERUSER -> AppAccessState.PanelOnlyAccount(state.user)
        member.isOperable() -> state
        else -> AppAccessState.AwaitingAssignment(state.user)
    }
}
