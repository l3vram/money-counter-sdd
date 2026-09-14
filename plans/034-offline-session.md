# Plan 034: An offline session that survives, and a 401 that ejects

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 948fc9f..HEAD -- app/src/main/java/com/moneycounter/appwrite/AppwriteAuthRepository.kt app/src/main/java/com/moneycounter/appwrite/AppwriteAccessRepository.kt app/src/main/java/com/moneycounter/ui/AuthViewModel.kt app/src/main/java/com/moneycounter/access/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — it changes what happens at startup, the one path every session goes through
- **Depends on**: `plans/033-no-membership-no-access.md` (steps 1–6, on `plan/033`)
- **Category**: feature + security
- **Planned at**: commit `908af91`, 2026-09-14
- **Rebased at**: commit `948fc9f`, 2026-09-14 — drift reconciliado tras el trabajo de login:
  `currentUser()` sigue igual (401 ⇒ null, el resto se re-lanza) y `checkAccess()` sigue
  poniendo `AppAccessState.Error`, ahora con el mensaje traducido por `mapAuthError`. La
  premisa del plan se mantiene. Novedad a tener en cuenta: el ViewModel ahora guarda
  `sessionPassword` en memoria para el cambio forzado de contraseña — **el caché de sesión de
  este plan NO debe persistir la contraseña**, sólo identidad y estado de acceso.

## Why this matters

The app is offline-first for its data and online-only for its session. Today, launching without
connectivity does not open the app:

```kotlin
// AppwriteAuthRepository.kt:24 — account.get() is a network call
override suspend fun currentUser(): AuthUser? {
    return try { account.get().toAuthUser() }
    catch (e: AppwriteException) { if (e.code == 401) null else throw e }
    catch (e: IOException) { throw e }          // ← offline lands here
}
```

```kotlin
// AuthViewModel.kt:107 — and any throwable becomes a dead end
} catch (e: Exception) {
    _uiState.value = AppAccessState.Error(e.localizedMessage ?: "Error de conexión")
}
```

So a seller who opens the app in a shop with no signal gets the connection-error screen and
cannot even look at what they already have. `ensureUserDocument()` has the same problem: it
reads the `users` table over the network.

## Owner decisions (2026-09-14)

1. **An offline session has no expiry.** No max-age on the cached session. The reasoning is
   the owner's: offline, the user only sees what they already had, nothing refreshes, the
   screen says it is disconnected, and their operations stay pending. There is nothing to
   gain by locking them out of reading stale data they already hold.
2. **A 401 ejects.** If the server is reachable and answers 401 — session revoked, account
   deleted, access removed — the session is signed out and the user leaves the app. This is
   the difference that makes decision 1 acceptable: revocation takes effect the moment the
   device sees the network again.
3. **The disconnected state is always visible.** Not a toast at startup: a persistent
   indicator, on every screen, that clears by itself when connectivity returns.

## The policy, in one sentence

**A transport failure keeps the last known session; a 401 destroys it.** This is plan 030's
rule (`isMemberRowMissing`: only a 404 means "no membership") applied to identity instead of
role. Both live in pure functions with tests, because both are security decisions.

## Current state

| Piece | Where | Today |
|---|---|---|
| Identity | `AppwriteAuthRepository.currentUser()` | network call; 401 ⇒ null, anything else rethrown |
| Access status | `AppwriteAccessRepository.ensureUserDocument()` | reads `users/{uid}`; no local copy |
| Startup | `AuthViewModel.checkAccess()` | any exception ⇒ `AppAccessState.Error` |
| Role | `MemberCache` + `AppwriteMembershipRepository` | **already cached** (plan 030); keeps the last known role on failure |
| Membership 401 | `AppwriteMembershipRepository.observeMember` | swallowed — keeps the last known member. Contradicts decision 2 |
| Connectivity probe | `AppwriteHealth.ping()` | exists, used only by the login screen's "verify connection" button |
| Connectivity indicator | — | **none anywhere in the UI** |

Plan 030 already solved this shape for the role. This plan extends the same pattern to the
identity and the access status, and adds the piece plan 030 deliberately left out: reacting to
an explicit revocation.

## Scope

**In scope**
- A local session cache: uid, email, displayName, photoUrl, last known `AccessStatus`.
  Pure JSON (de)serialization mirroring `MemberCacheJson`, plus its own tests.
- A pure policy function deciding, from an auth failure, whether to use the cache, sign out,
  or fail.
- `AuthViewModel.checkAccess()` applying it, and exposing an `isOffline` flow.
- `AppwriteMembershipRepository`: a 401 must surface as revocation, not be swallowed.
- A persistent "sin conexión" indicator in the app shell, visible on every screen.
- Clearing the session cache on sign-out, next to the member cache (shared-device hygiene —
  `AuthViewModel` already does this for the member cache).

**Out of scope** (do NOT touch)
- The outbox / pending-operations sync. Operations already queue locally; making them upload
  is `advisor-plans/008-shared-inventory-DESIGN.md` step 5, not this plan.
- What an offline session is *allowed to do*. Permissions come from the cached role exactly as
  plan 030 left them. This plan changes reachability, not authority.
- Stock freshness. Today stock is local JSON, so it displays offline. Once F2 makes stock
  server-authoritative it will be stale or absent offline, and *that* plan owns the messaging.
- `AppwriteHealth.ping()`'s implementation.
- Any change to the login flow itself: signing in still requires connectivity, which is
  unavoidable and correct.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0, 0 failures |
| Build APK | `./gradlew :app:assembleDebug` | exit 0 |

**Baseline: 471 tests on `main` @ `948fc9f`.** Record the new count in the status row.

## Git workflow

- Branch: `plan/034` off `main`.
- One commit per step, in Spanish, suffixed `(plan 034, paso N)`.
- Do NOT push, do NOT merge.

## Steps

### Step 0: Record the baseline

Run the three commands. If the test count is not 471, STOP and report.

### Step 1: The session cache (pure)

Beside `access/MemberCache.kt`, add `access/SessionCache.kt` following it exactly: a pure
`SessionCacheJson` object with `encode`/`decode`, a `CachedSession` data class
(`uid`, `email`, `displayName`, `photoUrl`, `access: AccessStatus`, `savedAtMs`), and a
`SessionCacheRepository` interface with a JSON-file implementation.

`savedAtMs` is recorded but **not** used to expire anything (owner decision 1). It exists so
the UI can say how stale the data is, and so a future plan can add a policy without a
migration.

Decoding must fail closed: any malformed field yields `null`, never a partially built session.
`MemberCacheTest` is the model for the cases to cover.

**Verify**: tests pass; count = baseline + ~8.

### Step 2: The policy function (pure)

In the `access` package:

```kotlin
enum class SessionOutcome { USE_CACHE, SIGN_OUT, FAIL }

/**
 * @param code HTTP status from the failed auth call, or null for a transport failure.
 */
fun sessionOutcomeFor(code: Int?, hasCachedSession: Boolean): SessionOutcome
```

Rules:
- `code == 401` → `SIGN_OUT`, whether or not there is a cache. Revocation wins (decision 2).
- `code == null` (transport) and a cache exists → `USE_CACHE`.
- any other code (5xx, 429, …) and a cache exists → `USE_CACHE`. The server is reachable but
  not answering usefully; that is not a revocation.
- no cache, any failure → `FAIL`. There is nothing to fall back to, so the honest answer is
  the connection screen.

Tests: one per rule, plus 403 explicitly asserted as `USE_CACHE` **not** `SIGN_OUT` — only a
401 means "this session is gone".

**Verify**: tests pass; count = baseline + ~16 cumulative.

### Step 3: Apply it at startup

In `AuthViewModel.checkAccess()`:

- Wrap the `currentUser()` + `ensureUserDocument()` pair. On failure, read the HTTP code
  (`(e as? AppwriteException)?.code`, null for `IOException`) and consult
  `sessionOutcomeFor`.
- `USE_CACHE`: build the state from the cached session (`toAppAccessState(cachedUser,
  cachedAccess)`), set `isOffline = true`, and still start `observeMember(uid)` so the
  membership poll recovers on its own when the network returns.
- `SIGN_OUT`: call the existing sign-out path — it already clears the member cache — and also
  clear the session cache.
- `FAIL`: today's behavior, `AppAccessState.Error`.
- On success: save the session to the cache and set `isOffline = false`.

Expose `val isOffline: StateFlow<Boolean>`.

**Interaction with plan 033 — get this right:** offline with a cached *session* but no cached
*membership* must NOT open the app. Plan 033's rule stands: an undetermined membership grants
nothing. Show the connection screen with retry, which is the honest answer, rather than
`AwaitingAssignment`, which would blame the administrator for a network problem.

**Verify**: compile + full suite; add ViewModel tests per branch.

### Step 4: A 401 from the membership poll is a revocation

`AppwriteMembershipRepository.observeMember` currently swallows every non-404 failure, keeping
the last known member. Correct for transport errors, wrong for a 401 (decision 2).

Emit revocation distinctly — a sealed result, or a callback the ViewModel can act on — and
have the ViewModel sign out when it arrives. Do not turn a 401 into `null`: plan 033 reads
`null` as "no membership assigned", a different situation with a different screen.

Extend `isMemberRowMissing`'s KDoc: 404 means no membership, 401 means no session, everything
else means keep what you have.

**Verify**: tests for the three cases (404, 401, transport).

### Step 5: The visible indicator

A persistent banner in the app shell, driven by `isOffline`: a single strip reading
"Sin conexión — estás viendo datos guardados", styled with the existing theme tokens (see
`AssignmentPendingScreen` and `AccessRequiredScreen` for the house style).

Requirements:
- Visible on **every** screen, so it belongs in the shell, not in each screen.
- It must clear by itself. The membership poll already runs every 10 s
  (`AppwriteMembershipRepository.REFRESH_INTERVAL`); a successful poll is the cheapest signal
  that connectivity is back — use it rather than adding a second timer.
- It must not cover or shift content it would hide; check the counter screen, which is the
  densest.

**Verify**: `assembleDebug`, plus the device walkthrough in step 6.

### Step 6: Device walkthrough (owner-gated)

1. Sign in online, then enable airplane mode and restart the app → it opens, shows the
   offline banner, and the role holds (plan 030's regression, now offline end-to-end).
2. Disable airplane mode → the banner clears on its own within ~10 s.
3. With the app open and online, delete that user's session from the Appwrite console → the
   app signs out and returns to login (decision 2).
4. Fresh install, no connectivity, never signed in → the connection screen. There is nothing
   cached and nothing to show.

## Test plan

| Case | Expectation |
|---|---|
| `SessionCacheJson` round-trip | identical session |
| `SessionCacheJson` with a malformed field | `null`, never partial |
| `sessionOutcomeFor(401, cache = true/false)` | `SIGN_OUT` both |
| `sessionOutcomeFor(null, cache = true)` | `USE_CACHE` |
| `sessionOutcomeFor(null, cache = false)` | `FAIL` |
| `sessionOutcomeFor(500 / 429 / 403, cache = true)` | `USE_CACHE` — not a revocation |
| Startup offline, session + membership cached and operable | app opens, `isOffline = true` |
| Startup offline, session cached, membership NOT cached | connection screen, app stays shut |
| Startup, server answers 401 | signed out, both caches cleared |
| Membership poll answers 401 | signed out |
| Membership poll answers 404 | `AwaitingAssignment` (plan 033), NOT signed out |
| Membership poll fails on transport | last known member kept (plan 030) |
| Successful startup | session saved to cache, `isOffline = false` |

## Done criteria

1. `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` all exit 0, 0 failures.
2. Launching with no connectivity opens the app for a previously signed-in, assigned user.
3. A 401 from either the identity check or the membership poll signs the session out.
4. The offline indicator shows on every screen and clears on its own.
5. Signing out clears the session cache as well as the member cache.
6. No code path opens the app with an undetermined membership (plan 033 must not regress).
7. Step 6's walkthrough signed off by the owner.
8. `plans/README.md` status row updated with the new test count.

## STOP conditions

- Baseline is not 471 tests, or the drift check shows in-scope files changed beyond the login work reconciled above.
- Making the session cache work would require changing what permissions an offline session
  has. That is plan 030's territory and this plan must not touch it.
- The offline path would open the app for a session whose membership was never determined.
  That is plan 033's hole reopening; stop and report.
- A 401 cannot be distinguished from a transport failure at some call site. Report it rather
  than guessing — guessing wrong in one direction locks out a legitimate user, and in the
  other keeps a revoked one.

## Maintenance notes

- The cached session is a **convenience, not an authorization boundary**. The real boundary is
  server-side, exactly as `MemberCache`'s KDoc says. Any new permission must be enforced where
  the data lives, not by this cache.
- `savedAtMs` is deliberately unused. If the owner ever wants a max age, it is already there.
- When F2 makes stock server-authoritative, the offline banner will need to say more: not just
  "sin conexión" but that stock figures are stale. That is F2's job, but this banner is where
  it will land.
