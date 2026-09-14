package com.moneycounter.access

import com.moneycounter.auth.AuthUser
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 033: the decision that keeps an approved-but-unassigned account out of the app.
 * Before this, that account opened the app with every permission.
 */
class EffectiveAccessTest {

    private val user = AuthUser(uid = "u1", email = "a@b.c", displayName = "A", photoUrl = null)
    private val approved = AppAccessState.Approved(user)

    private fun member(role: Role, branches: List<String> = listOf("br1"), orgId: String = "org1") =
        Member(uid = "u1", orgId = orgId, role = role, branchIds = branches)

    @Test
    fun `an operable membership opens the app`() {
        for (role in listOf(Role.SELLER, Role.OWNER, Role.ADMIN)) {
            val result = effectiveAccessState(approved, member(role), membershipResolved = true)
            assertEquals("$role should open the app", approved, result)
        }
    }

    @Test
    fun `approved with no membership row waits for assignment`() {
        val result = effectiveAccessState(approved, null, membershipResolved = true)
        assertEquals(AppAccessState.AwaitingAssignment(user), result)
    }

    @Test
    fun `approved with a membership but no branch waits for assignment`() {
        val result = effectiveAccessState(approved, member(Role.SELLER, branches = emptyList()), true)
        assertEquals(AppAccessState.AwaitingAssignment(user), result)
    }

    @Test
    fun `a superuser gets the panel-only screen, never the operation`() {
        val result = effectiveAccessState(approved, member(Role.SUPERUSER, branches = emptyList()), true)
        assertEquals(AppAccessState.PanelOnlyAccount(user), result)
    }

    @Test
    fun `a superuser with branches assigned by mistake still gets the panel-only screen`() {
        val result = effectiveAccessState(approved, member(Role.SUPERUSER, branches = listOf("br1")), true)
        assertEquals(AppAccessState.PanelOnlyAccount(user), result)
    }

    @Test
    fun `an unresolved membership loads instead of flashing the locked screen`() {
        val result = effectiveAccessState(approved, null, membershipResolved = false)
        assertEquals(AppAccessState.Loading, result)
    }

    @Test
    fun `a cached membership opens the app even before the poll answers (plan 030 offline)`() {
        // The cache seeds `member` synchronously, so an assigned user starts working offline.
        val result = effectiveAccessState(approved, member(Role.SELLER), membershipResolved = false)
        assertEquals(approved, result)
    }

    @Test
    fun `every other state passes through untouched, membership notwithstanding`() {
        val others = listOf(
            AppAccessState.Loading,
            AppAccessState.SignedOut,
            AppAccessState.Pending(user),
            AppAccessState.Blocked(user),
            AppAccessState.PasswordChangeRequired(user),
            AppAccessState.Error("boom")
        )
        for (state in others) {
            assertEquals(state, effectiveAccessState(state, null, membershipResolved = true))
            assertEquals(state, effectiveAccessState(state, member(Role.OWNER), true))
        }
    }

    @Test
    fun `a blocked account stays blocked even with a perfect membership`() {
        val blocked = AppAccessState.Blocked(user)
        assertEquals(blocked, effectiveAccessState(blocked, member(Role.OWNER), true))
    }
}
