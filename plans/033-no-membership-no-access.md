# Plan 033: No membership means no access — fail closed, and close the tenant tables

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat ff98b3f..HEAD -- app/src/main/java/com/moneycounter/domain/PermissionService.kt app/src/main/java/com/moneycounter/domain/Role.kt app/src/main/java/com/moneycounter/access/ app/src/main/java/com/moneycounter/ui/ webadmin/function/src/index.js`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P0 (a live fail-open, and the DB half is already applied in production)
- **Effort**: M
- **Risk**: MED — flipping a permission default is small in code and wide in behavior;
  every screen gate and 30+ tests depend on it
- **Depends on**: `plans/030-role-fail-closed.md` (landed on `plan/030` @ `ff98b3f`)
- **Category**: security
- **Planned at**: commit `ff98b3f`, 2026-09-12
- **Owner decision (2026-09-12)**: reverses 030's "`DefaultPermissionService` stays
  permissive for legacy single-user installs". There are no legacy installs — the app has
  never shipped. An unassigned account must not be able to operate, and **not** as SELLER
  either: it is locked out until the SUPERUSER assigns business, branch and role.

## Why this matters

Plan 030 closed the *transient* hole: a network failure no longer degrades a known role.
The *permanent* hole is still open. With `users.access = APPROVED` and **no `members` row**,
the session resolves to `role = null`, and:

```kotlin
// domain/PermissionService.kt:33 — every operational permission granted
object DefaultPermissionService : PermissionService {
    override fun canSell() = true
    override fun canRegisterExpense() = true
    override fun canAddStock() = true
    ...
}
```

```kotlin
// domain/Role.kt:120 — the UI gates default to "allowed" too
fun Role?.mayRegisterWriteoff(): Boolean = this?.canRegisterWriteoff() ?: true
```

So "no membership" means **full OWNER-grade powers**. This is not hypothetical: the account
`marvelalvarez89@gmail.com` has `users.access = APPROVED` and no `members` row right now, so
it currently operates with every permission.

Second, the tenant tables are readable across tenants. As of 2026-09-12, table permissions
were:

| Table | Permissions | rowSecurity | Consequence |
|---|---|---|---|
| `members` | `read("users")` | false | **FIXED 2026-09-12**: now `[]` + rowSecurity true |
| `users` | `read("users")`, `create("users")` | false | any signed-in user reads every email / displayName / access |
| `orgs` | `read("users")` | false | any signed-in user reads every business and its WhatsApp |
| `branches` | `read("users")` | false | any signed-in user reads every branch of every business |
| `signups` | `create("users")` | false | already closed for reads — leave as is |
| `settings` | `read("users")` | false | the superuser WhatsApp is readable by any user — **accepted**, the app needs it to send the signup message |

This contradicts plan 024, whose isolation tests assert cross-tenant reads are impossible —
they pass because they exercise the *domain* filters, not Appwrite's permissions.

### The trap that binds the two halves

`members` was closed already (rowSecurity on, no table read), so each row must carry its own
`read("user:<uid>")`. If a row lacks it, Appwrite answers the app's read of **its own** row
with 404 — and `isMemberRowMissing(404)` (`AppwriteMembershipRepository.kt:24`) spells 404 as
"no membership", which today means *every permission*. **Tightening permissions can reopen
030's fail-open through a different door.** Fixing the default (Part A) is what makes the
lockdown (Part B) safe, so Part A lands first.

## Current state

### Already applied outside this plan (do not redo)

- `members` table: `$permissions: []`, `rowSecurity: true` (Appwrite console, 2026-09-12).
- `members/6aa352520004960be987` (Luis, SUPERUSER): `read("user:6aa352520004960be987")`.
- `webadmin/function/src/index.js` — `approveSignup` now writes the members row with
  `permissions: [sdk.Permission.read(sdk.Role.user(signupId))]`. **Committed but NOT
  deployed.** Until it is deployed, approving a signup creates an unreadable members row.

### What the app reads as the signed-in user

- `members/{uid}` — own row only, polled every 10 s (`AppwriteMembershipRepository.kt:44`).
- `users/{uid}` — own row, plus a profile observer (`AccessRepository`).
- `orgs/{orgId}` and a `branches` list filtered by `orgId`
  (`AppwriteCloudOrgRepository.kt:76-95`), used by plan 028's post-approval sync.

The admin Function reads and writes everything with a per-execution API key
(scopes `rows.read`/`rows.write`), so it is **unaffected** by row permissions.

### Conventions

- The permission matrix lives in `domain/Role.kt`; `domain/PermissionService.kt` only
  delegates to it. Add no new policy to the ViewModel.
- Policy decisions go in pure functions with unit tests (as `retainRole` and
  `isMemberRowMissing` did in plan 030).
- Spanish for user-facing copy, English for code and comments.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0, 0 failures |
| Build APK | `./gradlew :app:assembleDebug` | exit 0 |

**Baseline: 444 tests on `plan/030` @ `ff98b3f`.** This plan *adds* tests and *changes*
expectations in the permission suites; record the new count in the status row.

## Scope

**In scope**

- `domain/PermissionService.kt` — the permissive default becomes a deny-all default.
- `domain/Role.kt` — the `Role?.may*()` gates default to `false`.
- `access/AppAccessState.kt` + `ui/AuthViewModel.kt` + `ui/AuthenticationGate.kt` — a
  membership that is incomplete or absent lands on a locked screen, not on the app.
- `domain/Member.kt` (or wherever `Member` lives) — a pure `isOperable()` predicate.
- A locked-screen composable with Spanish copy.
- `webadmin/function/src/index.js` — grant `read` on `orgs`/`branches` rows to approved
  members; keep the members grant already added.
- Appwrite table permissions for `users`, `orgs`, `branches` (console/MCP, not code).
- Tests for every one of the above.

**Out of scope** (do NOT touch)

- `settings` table permissions — the app legitimately needs `superuserWhatsapp`.
- `signups` permissions — already closed for reads.
- The SUPERUSER model: it stays a `members` row with `orgId: "platform"`. Auth labels were
  considered and **rejected** by the owner on 2026-09-12; Luis uses a separate account for
  business-role testing.
- `Role` enum values — no `PENDING` member is introduced. Absence of a usable membership is
  the signal; adding a second representation of "no access" invites them to disagree.
- Any operational repository or `MoneyCounterViewModel` business logic beyond the
  permission wiring. Plan 032 owns the repository refactor.
- Deploying the site or the Function — the orchestrator does that (see `webadmin/README.md`).

## Git workflow

- Branch: `plan/033` off `plan/030` (030 is not merged yet; this depends on its cache).
- One commit per step, in Spanish, suffixed `(plan 033, paso N)`.
- Do NOT push, do NOT open a PR, do NOT merge.

## Steps

### Step 0: Record the baseline

Run the three commands above on a clean `plan/033` branch. Write down the test count. If it
is not 444, STOP and report.

### Step 1: A pure predicate for "this membership can operate"

Beside the `Member` model, add:

```kotlin
/**
 * A membership is operable only when the SUPERUSER has fully assigned it: a business, at
 * least one branch, and an operational role. Anything else — no row, a half-written row, a
 * SUPERUSER (whose place is the admin panel, not the app) — cannot operate.
 */
fun Member?.isOperable(): Boolean
```

Rules: `null` → false. `role == null` → false. `role == SUPERUSER` → false. `orgId` blank →
false. `branchIds` empty → false. Otherwise true.

Tests: one per rule, plus the happy path for SELLER, OWNER and ADMIN.

**Verify**: tests pass; count = baseline + ~8.

### Step 2: Flip the permission default to deny-all

In `domain/PermissionService.kt`, rename `DefaultPermissionService` to
`NoAccessPermissionService` and make **every** method return `false`. Keep it as the
`forRole(null)` result and as the ViewModel's initial value. The rename is deliberate: a
thing named "Default" that denies everything is a trap for the next reader.

In `domain/Role.kt`, every `Role?.may*()` helper defaults to `false`
(`this?.canX() ?: false`), including `mayViewBranchHistory` and `mayManageCatalog`.

Update the KDoc on both files: no role means no access, and why (the offline fail-open).

**Verify**: `compileDebugKotlin` passes. The suite will have failures — expected, Step 3
fixes them. Do not commit a red suite; do Steps 2 and 3 in one commit.

### Step 3: Update the tests that encoded the permissive default

~30 references to `DefaultPermissionService` exist, most in tests asserting that a null role
grants everything. Each must be re-read and re-decided, not mechanically flipped:

- Tests asserting "null role can sell / add stock / manage catalog" now assert it **cannot**.
- Tests using a null role as a shorthand for "any user" must be given an explicit role
  instead — otherwise they now assert nothing.
- Keep `canViewAllSellersDashboard()` false for a null role (it already was).
- `RoleRetentionTest` (plan 030) must stay green: `retainRole` keeps the last known role, so
  a transient null still resolves to the cached role, not to deny-all.

**Verify**: full suite green. Commit Steps 2+3 together.

### Step 4: Lock the app behind a complete membership

Add `AppAccessState.AwaitingAssignment(user: AuthUser)` and route to it from
`AuthViewModel` when access is APPROVED but `member.isOperable()` is false — **except** while
the membership is still unknown and a cached role exists (plan 030's cache), which must
continue to open the app offline.

Distinguish the two cases in `AuthenticationGate` with Spanish copy:

- No usable membership: "Tu cuenta está esperando que el administrador te asigne negocio,
  sucursal y rol." Offer sign-out and a retry.
- `role == SUPERUSER`: "Esta cuenta administra la plataforma desde el panel web." Offer
  sign-out only.

**Verify**: compile + suite green; add a ViewModel test per branch (approved+operable →
`Approved`; approved+not operable → `AwaitingAssignment`; approved+unknown+cached role →
`Approved`; SUPERUSER → the superuser screen).

### Step 5: Grant org/branch reads at approval time (Function)

In `approveSignup`, after the org and branches are resolved, add
`read("user:<uid>")` to the `orgs/{orgId}` row and to every `branches/{id}` row in
`branchIds` — preserving existing permissions, since several members share one org. Use
`getRow` → merge → `updateRow({ permissions })`; never overwrite blindly.

Guard the merge with a helper (`withUserRead(existing, uid)`) so a repeated approval is
idempotent and does not accumulate duplicates.

**Verify**: no Kotlin change, so the Gradle suite is unaffected. Reason about the code and
state plainly in the report that this step is unverified until deployed (Step 7).

### Step 6: Close `users`, `orgs` and `branches` in Appwrite

Only after Steps 1-5 are green (the deny-all default must be in place first):

| Table | Set permissions to | rowSecurity | Note |
|---|---|---|---|
| `users` | `create("users")` | true | existing rows already carry `read/update/delete("user:<uid>")`; client-created rows get them by default |
| `orgs` | `[]` | true | the Function grants per-member reads (Step 5) |
| `branches` | `[]` | true | the `branches` list query then returns only the caller's branches |

Before flipping each one, list its rows and confirm every row carries the read grant its
reader needs. A row without it produces a 404, which the app reads as "missing".

**Verify**: after each flip, re-read the table and confirm `$permissions` and `rowSecurity`.

### Step 7: End-to-end on a real device (owner-gated)

Deploy the Function (`webadmin/README.md` documents the tarball route), then run the flow in
`docs/onboarding-e2e.md`: register a DUEÑO from the app → approve in the panel → confirm the
app opens with OWNER, org and branches synced → register a SELLER against the same org →
approve with one branch → confirm the SELLER restrictions → airplane mode + restart and
confirm the role holds (plan 030's regression).

## Test plan

| Case | Expectation |
|---|---|
| `Member?.isOperable()` — null, null role, SUPERUSER, blank orgId, empty branchIds | false in all five |
| `isOperable()` — SELLER/OWNER/ADMIN with org + one branch | true |
| `NoAccessPermissionService` — every method | false |
| `forRole(null)` | returns `NoAccessPermissionService` |
| `Role?.may*()` with a null receiver | false for every helper |
| `retainRole` with a cached role and a transient null | still the cached role (030 must not regress) |
| Approved + operable membership | `AppAccessState.Approved` |
| Approved + non-operable membership | `AwaitingAssignment` |
| Approved + membership unknown + cached role | `Approved` (offline start still works) |
| SUPERUSER signing into the app | the panel-only screen, zero operational permissions |

## Done criteria

1. `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all exit 0, 0 failures.
2. No code path grants a permission when the role is unknown and no cached role exists.
3. `grep -rn "?: true" app/src/main/java/com/moneycounter/domain/Role.kt` returns nothing.
4. `DefaultPermissionService` no longer exists under `app/src/main`.
5. `users`, `orgs`, `branches` and `members` all have `rowSecurity: true` and no
   `read("users")`; `settings` is unchanged and documented as an accepted exception.
6. Step 7's device walkthrough is signed off by the owner.
7. `plans/README.md` status row updated with the new test count.

## STOP conditions

- Baseline is not 444 tests, or the drift check shows in-scope files changed.
- A test asserting the *old* permissive default cannot be re-decided without changing
  product behavior beyond this plan's scope — report it instead of guessing.
- Flipping a table's permissions makes the app read its own row as 404. Revert that table
  immediately (`read("users")` restored) and report: it means a row is missing its grant.
- Any change would be needed in `MoneyCounterViewModel`'s operational logic, or in an
  operational repository. That is plan 032's territory.
- The Function cannot be deployed. Land Steps 1-4 anyway (they are the security fix) and
  report Steps 5-6 as blocked — **do not flip `orgs`/`branches` with an undeployed Function**.

## Maintenance notes

- Every new table readable by the app needs the same treatment: rowSecurity on, no
  `read("users")`, and whoever creates a row grants its reader explicitly.
- `settings` stays broadly readable on purpose. If it ever holds anything but the support
  number, revisit it.
- If the shared-inventory work (`advisor-plans/008-shared-inventory-DESIGN.md`) adds
  `movements` and `stock` tables, they are server-written only: the app must never hold
  write permission on them.
