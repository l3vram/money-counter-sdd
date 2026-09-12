# Plan 030 — Execution log

**Purpose**: durable state of this execution so it can be resumed by anyone (or by a
fresh context) without losing work or acting on stale assumptions. Update it after
**every** commit. The plan itself is `plans/030-role-fail-closed.md`.

## Fixed facts

| | |
|---|---|
| Branch | `plan/030` (created from `feature/multi-tenant @ cfd8809`) |
| Base commit | `cfd8809` |
| Baseline | **424 tests, 0 failures, 0 errors, 0 skipped** (verified 2026-09-12) |
| Test count rule | must only ever **go up**. A drop means tests were deleted — treat as failure |
| Verify commands | `./gradlew :app:compileDebugKotlin` · `:app:testDebugUnitTest` · `:app:assembleDebug` |
| Exact count | `python3 -c "import glob,xml.etree.ElementTree as ET;t=f=0;[ (globals().__setitem__('t',t+int(ET.parse(p).getroot().get('tests',0))), globals().__setitem__('f',f+int(ET.parse(p).getroot().get('failures',0)))) for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml')];print(t,f)"` |

## Hard constraints (do not violate while resuming)

- Never modify `domain/PermissionService.kt` or `domain/Role.kt`.
- Never modify or delete an **existing** test file. Only add new ones.
- `DefaultPermissionService` stays permissive (legacy single-user installs depend on it).
- JUnit4 + `org.junit.Assert.*` only. No kotlin.test, no Mockito, no Robolectric.
- No new/bumped dependencies. No `runBlocking` under `app/src/main/`.
- Do not merge into `feature/multi-tenant` — that is a human gate (Gate B).

## Prior failed attempt (do not repeat)

A `flow_run` MCP dispatch (branch `flow/030-role-fail-closed`, since deleted) produced
**none** of the plan's files and instead deleted 11 of the 12 tests in
`domain/RoleTest.kt` — the whole SELLER/OWNER/ADMIN/SUPERUSER permission matrix — leaving
one fabricated test that misuses `Role.fromStorage` (it takes a role name like `"SELLER"`,
not JSON). The suite went green **because the tests were removed**. Branch and worktree
discarded 2026-09-12; nothing was merged.

**Lesson encoded above**: the test count must rise, and existing test files are immutable.

## Step status

| Step | What | Status | Commit | Tests after |
|---|---|---|---|---|
| 0 | Baseline recorded | ✅ DONE | — | 424 |
| 1 | `access/MemberCache.kt` (pure `MemberCacheJson` + `JsonMemberCacheRepository`) + `MemberCacheTest.kt` | ✅ DONE | `d923bd3` | 433 |
| 2 | `isMemberRowMissing` + guarded catch in `AppwriteMembershipRepository` + `MembershipErrorPolicyTest.kt` | ✅ DONE | `0e750bf` | 438 |
| 3 | `AuthViewModel`: seed from cache, persist member, clear on sign-out | ✅ DONE | `38aefc1` | 438 |
| 4 | `AuthenticationGate`: construct and inject the cache | ✅ DONE | `4ccb1f4` | 438 |
| 5 | `retainRole` + `setSellerContext` never downgrades + `RoleRetentionTest.kt` | ✅ DONE | `9506733` | 444 |
| 6 | `MainActivity`: `LaunchedEffect(profile?.uid, member?.role)` | ✅ DONE | `ff98b3f` | 444 |

**All steps complete.** Final: 444 tests / 0 failures (+20 vs baseline), `assembleDebug` OK
(APK 18.6 MB). Awaiting Gate B — not merged into `feature/multi-tenant`.

Status values: TODO · IN PROGRESS · ✅ DONE · ⚠️ BLOCKED (with reason)

## How to resume

1. `git branch --show-current` → must be `plan/030`.
2. Read this table; find the first non-DONE step.
3. Read that step in `plans/030-role-fail-closed.md` and implement **only** it.
4. Run compile + tests; confirm the count is ≥ the last recorded value.
5. Commit, then update this file's row **in the same commit or immediately after**.

## Notes / deviations from the plan

- **Test file paths differ from the plan.** The plan listed flat paths
  (`app/src/test/java/com/moneycounter/MemberCacheTest.kt`). The repo's actual convention is
  that tests mirror the source package, so they were placed as:
  - `app/src/test/java/com/moneycounter/access/MemberCacheTest.kt` (pkg `com.moneycounter.access`)
  - `app/src/test/java/com/moneycounter/appwrite/MembershipErrorPolicyTest.kt` (pkg `com.moneycounter.appwrite`)
  - `app/src/test/java/com/moneycounter/viewmodel/RoleRetentionTest.kt` (pkg `com.moneycounter.viewmodel`)
  Repo convention beats the plan's guess. No other deviation.

## Review findings (self-review after all steps)

Verified by inspection, not by trusting the diff summary:
- The only cache load that feeds `_member` is uid-gated (`AuthViewModel.kt:139`,
  `.takeIf { it.uid == uid }`), so one account's role cannot carry into another's session.
- `signOut()` clears the cache (`AuthViewModel.kt:228`).
- Only three test files were **added**; no existing test file was modified or deleted.
- `domain/PermissionService.kt` and `domain/Role.kt` are byte-identical to `feature/multi-tenant`.

**Subtle behavior worth keeping — do NOT "fix" it the other way.** When the server returns a
genuine 404 *and* a role was previously cached, the cached role is kept rather than cleared
(`AuthViewModel.kt:146`). Clearing it would set the role to null, which resolves to
`DefaultPermissionService` — i.e. it would hand out **every** permission, reintroducing exactly
the bug this plan fixes. Keeping the last known role is the safer failure direction (a SELLER
stays a SELLER). Membership revocation is not enforced here anyway: it runs through the `users`
table's access status (`PENDING`/`APPROVED`) and the `AppAccessState` gate in `checkAccess()`,
which still works normally. If revocation-by-row-deletion ever becomes a real requirement, it
must downgrade to a *restrictive* role, never to null.
