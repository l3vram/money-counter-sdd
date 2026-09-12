# Plan 030: Make the session role survive offline instead of granting full privileges

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat cfd8809..HEAD -- app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt app/src/main/java/com/moneycounter/ui/AuthViewModel.kt app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/MainActivity.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (touches the authentication/permission path used by every screen)
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `cfd8809`, 2026-09-12

## Why this matters

The app resolves the user's role from an Appwrite `members` row over the
network. Any failure to read that row — including **being offline** — currently
degrades the session to "no member", and "no member" means
`DefaultPermissionService`, which grants **every** permission: create and delete
products, stock alta, merma, branch closings.

This app is explicitly offline-first, so losing connectivity is a normal
operating mode, not an edge case. The practical result is that a SELLER who
works without signal operates the entire session with OWNER-level powers. This
silently voids the permission model built by plans 018–024 — the unit tests pass
because they exercise `PermissionService` in isolation, and nothing covers this
wiring.

After this plan: the last known role is cached locally and reused when the
network fails, a connectivity error never downgrades an established session, and
genuinely member-less legacy installs keep working exactly as they do today.

## Current state

### The three defects, inlined

**1 — `app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt:26-41`**
Every exception collapses into "no member":

```kotlin
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
```

A missing row (HTTP 404) and a dropped connection are treated identically. Only
the first genuinely means "this install has no membership".

**2 — `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt:63-64, 127-135`**
`_member` starts `null` and is only ever populated by the network poll, so the
first moments of every session — and any fully offline session — have no role:

```kotlin
private val _member = MutableStateFlow<Member?>(null)
val member: StateFlow<Member?> = _member.asStateFlow()
```

```kotlin
private fun observeMember(uid: String) {
    if (memberJob?.isActive == true) return
    memberJob = viewModelScope.launch {
        membershipRepository.observeMember(uid).collect { member ->
            _member.value = member
            seedCloudTenantIfNeeded(uid, member)
        }
    }
}
```

**3 — `app/src/main/java/com/moneycounter/MainActivity.kt:91-94`**
The role is read once, keyed only on the uid, so a role that arrives later never
reaches the ViewModel:

```kotlin
val viewModel: MoneyCounterViewModel = viewModel()
LaunchedEffect(profile?.uid) {
    viewModel.setSellerContext(profile?.uid, profile?.displayName, member?.role)
}
```

And in `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt:177-189`,
a `null` role installs the permissive service:

```kotlin
fun setSellerContext(uid: String?, name: String?, role: Role?, cloudOrgId: String? = null) {
    sellerUid = uid.orEmpty()
    sellerName = name.orEmpty()
    memberOrgId = cloudOrgId?.takeIf { it.isNotBlank() }
    currentRole = role
    permissionService = DefaultPermissionService.forRole(role)
    ...
}
```

`DefaultPermissionService` (`app/src/main/java/com/moneycounter/domain/PermissionService.kt:33-52`)
returns `true` for `canAddStock`, `canDeleteProduct`, `canCreateBranchClosing`,
`canManageCatalog` and the rest.

### Conventions you must match

**Pure decision object + thin Android I/O class.** This repo consistently splits
serialization/decisions (unit-tested on the JVM, no Android imports) from file
I/O. The exemplar is `app/src/main/java/com/moneycounter/repository/JsonTenantRepository.kt`:
`object TenantJson` holds `orgToJson` / `orgFromJson` / `ensureSeeded`, and
`class JsonTenantRepository(private val context: Context)` does the file work:

```kotlin
class JsonTenantRepository(private val context: Context) : TenantRepository {

    private val orgFile = File(context.filesDir, "org.json")

    override fun loadOrganization(): Organization? {
        return try {
            if (!orgFile.exists()) return null
            TenantJson.orgFromJson(orgFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override fun saveOrganization(organization: Organization) {
        try {
            val temp = File(context.filesDir, "org.json.tmp")
            temp.writeText(TenantJson.orgToJson(organization))
            temp.renameTo(orgFile)
        } catch (e: Exception) {
            // silently ignore persistence errors, matching the other Json repos
        }
    }
}
```

Note the conventions to copy exactly: a `version` field in the JSON, `runCatching`
/ try-catch returning a null-or-empty fallback on any parse failure, and
**atomic writes via a `.tmp` file + `renameTo`**.

**Domain types you will use** (do not redefine them):

```kotlin
// app/src/main/java/com/moneycounter/domain/Member.kt
data class Member(
    val uid: String,
    val orgId: String,
    val role: Role,
    val branchIds: List<String> = emptyList()
)
```

```kotlin
// app/src/main/java/com/moneycounter/domain/Role.kt
enum class Role { SELLER, OWNER, ADMIN, SUPERUSER;
    companion object {
        fun fromStorage(value: String?): Role? = value?.let { v -> values().find { it.name == v } }
        fun toStorage(x: Role): String = x.name
    }
}
```

Use `Role.toStorage` / `Role.fromStorage` for persistence — do not call `.name`
or `valueOf` directly.

**Design constraint from the master plan** (`PLAN.md — El Luiso Multi-Tenant`,
§41 "Regla de seguridad" and §42): *"La UI no es seguridad"* and *"Validar
siempre: currentUser, organization, membership, role, branch antes de cualquier
operación."* The client-side cache added here is defense in depth and a UX
enabler for offline work — it is **not** the authorization boundary. Do not
describe it as one in comments.

**Appwrite exceptions**: the SDK throws `io.appwrite.exceptions.AppwriteException`,
which exposes a nullable `code: Int?` carrying the HTTP status. Import it as
`io.appwrite.exceptions.AppwriteException`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0, all tests pass (424 before this plan) |
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0, no errors |
| Build APK | `./gradlew :app:assembleDebug` | exit 0, APK produced |

Run from the repo root. There is no separate lint/typecheck gate in this project;
`compileDebugKotlin` is the type gate.

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/access/MemberCache.kt` (create — pure object + repository)
- `app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt` (modify)
- `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt` (modify)
- `app/src/main/java/com/moneycounter/ui/AuthenticationGate.kt` (modify — wire the cache into the factory)
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (modify — one method)
- `app/src/main/java/com/moneycounter/MainActivity.kt` (modify — one `LaunchedEffect` key)
- `app/src/test/java/com/moneycounter/MemberCacheTest.kt` (create)
- `app/src/test/java/com/moneycounter/MembershipErrorPolicyTest.kt` (create)
- `app/src/test/java/com/moneycounter/RoleRetentionTest.kt` (create)

**Out of scope** (do NOT touch, even though they look related):
- `app/src/main/java/com/moneycounter/domain/PermissionService.kt` and `Role.kt` —
  the permission matrix itself is correct and covered by `PermissionServiceTest`.
  This plan changes *which role reaches* the matrix, never the matrix.
- `DefaultPermissionService`'s permissive behavior — it must stay as-is, because a
  legacy single-user install with genuinely no membership row still depends on it.
- `app/src/main/java/com/moneycounter/sync/SyncManager.kt` — an unrelated stub.
- Anything under `webadmin/`.
- The `AppwriteAccessRepository.observeUserProfile` catch block — same shape, but
  the profile carries no privileges; leave it alone.

## Git workflow

- Branch: `plan/030` off the current `feature/multi-tenant` HEAD.
- Conventional commits, matching `git log`. Example from this repo:
  `feat(perms): SELLER read-only inventario + rol ADMIN (plan 018)`.
  Use a `fix(perms): ...` / `feat(perms): ... (plan 030)` subject in Spanish.
- Do NOT push, merge, or open a PR.

## Steps

### Step 1: Add the pure member cache

Create `app/src/main/java/com/moneycounter/access/MemberCache.kt` with a pure
`object MemberCacheJson` and an interface + JSON-file implementation, modeled
exactly on `TenantJson` / `JsonTenantRepository`.

`MemberCacheJson` must expose:
- `fun toJson(member: Member): String` — writes `version` (=1), `uid`, `orgId`,
  `role` (via `Role.toStorage`), `branchIds` (a `JSONArray`).
- `fun fromJson(json: String): Member?` — returns `null` on blank input, a
  version mismatch, a malformed document, a blank `uid`/`orgId`, or an
  unrecognized role. Never throws.

Then:

```kotlin
interface MemberCacheRepository {
    fun load(): Member?
    fun save(member: Member)
    fun clear()
}
```

and `class JsonMemberCacheRepository(private val context: Context) : MemberCacheRepository`
backed by `File(context.filesDir, "member-cache.json")`, using the `.tmp` +
`renameTo` atomic-write pattern and swallowing I/O errors, exactly like
`JsonTenantRepository`.

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.

### Step 2: Stop treating connectivity failures as "no member"

In `AppwriteMembershipRepository.kt`, add a pure, testable decision function at
file scope (outside the class) so it can be unit-tested without the Appwrite SDK
or Android:

```kotlin
/** Only a genuinely missing row means "this install has no membership".
 *  Every other failure (connectivity, permissions, server error) must leave the
 *  last known member in place rather than degrading to full privileges. */
fun isMemberRowMissing(code: Int?): Boolean = code == 404
```

Change the catch block so it emits `null` **only** when the row is genuinely
missing, and otherwise emits nothing (skipping this poll cycle, leaving the
collector's last value intact):

```kotlin
} catch (e: Exception) {
    val code = (e as? AppwriteException)?.code
    if (isMemberRowMissing(code)) {
        // Genuinely no membership row: legacy single-user install.
        trySend(null)
    }
    // Otherwise keep the last known member: a connectivity or server failure
    // must never widen this session's permissions.
}
```

Keep the `delay(refreshIntervalMillis)` and `awaitClose` exactly as they are.

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.

### Step 3: Seed and persist the member cache in `AuthViewModel`

Add an optional constructor parameter, placed **last** so existing call sites and
tests that use positional arguments keep compiling:

```kotlin
private val memberCacheRepository: MemberCacheRepository? = null
```

In `observeMember(uid)`, seed from the cache **before** launching the poll, and
persist every non-null member the poll produces:

```kotlin
private fun observeMember(uid: String) {
    if (memberJob?.isActive == true) return
    if (_member.value == null) {
        memberCacheRepository?.load()?.takeIf { it.uid == uid }?.let { _member.value = it }
    }
    memberJob = viewModelScope.launch {
        membershipRepository.observeMember(uid).collect { member ->
            if (member != null) {
                _member.value = member
                memberCacheRepository?.save(member)
            } else if (memberCacheRepository?.load() == null) {
                // No cached role and the row is genuinely missing:
                // legacy single-user install keeps its current behavior.
                _member.value = null
            }
            seedCloudTenantIfNeeded(uid, member)
        }
    }
}
```

The cached member is only reused when its `uid` matches the signed-in user —
never carry one account's role into another's session.

In `signOut()`, clear the cache alongside the other state, so a shared device
does not leak the previous user's role:

```kotlin
memberCacheRepository?.clear()
```

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.

### Step 4: Wire the cache in `AuthenticationGate`

In `app/src/main/java/com/moneycounter/ui/AuthenticationGate.kt`, the
`AuthViewModelFactory` already builds `AppwriteMembershipRepository()` and the
other repositories (around line 55). Construct a
`JsonMemberCacheRepository(context)` there and pass it as the new last argument
to `AuthViewModel`. Match the surrounding construction style.

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.

### Step 5: Never downgrade an established role mid-session

In `MoneyCounterViewModel.setSellerContext`, a `null` role must not replace a
role the session already knows. Change only the role-resolution lines:

```kotlin
fun setSellerContext(uid: String?, name: String?, role: Role?, cloudOrgId: String? = null) {
    sellerUid = uid.orEmpty()
    sellerName = name.orEmpty()
    memberOrgId = cloudOrgId?.takeIf { it.isNotBlank() }
    // Defense in depth: a transient null (membership not resolved yet, or a
    // failed poll) must never widen permissions for a session that already
    // knows its role. Only an explicit sign-out resets it.
    val effectiveRole = role ?: currentRole
    currentRole = effectiveRole
    permissionService = DefaultPermissionService.forRole(effectiveRole)
    uid?.takeIf { it.isNotBlank() }?.let { cleanUid ->
        context = AppContext(cleanUid, role = effectiveRole)
    }
    refreshPermissions()
    resolveTenantContext()
    refreshTenantScope()
}
```

Leave every other line of the method untouched.

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.

### Step 6: Let a later-arriving role reach the ViewModel

In `MainActivity.kt:91-94`, add the role to the `LaunchedEffect` key so the
effect re-runs when the membership poll resolves:

```kotlin
LaunchedEffect(profile?.uid, member?.role) {
    viewModel.setSellerContext(profile?.uid, profile?.displayName, member?.role)
}
```

Change nothing else in the file.

**Verify**: `./gradlew :app:assembleDebug` → exit 0, APK produced.

## Test plan

All three test files are plain JVM unit tests (no Robolectric, no Android
framework). Model their structure on
`app/src/test/java/com/moneycounter/TenantRepositoryTest.kt` and
`PermissionServiceTest.kt` — JUnit 4, `@Test fun ...`, `assertEquals` /
`assertTrue` / `assertNull`.

**`app/src/test/java/com/moneycounter/MemberCacheTest.kt`** — covers `MemberCacheJson`:
- round-trip: a `Member(uid, orgId, Role.SELLER, listOf("b1","b2"))` survives
  `toJson` → `fromJson` unchanged, branch ids and order included.
- `fromJson("")` returns `null`.
- `fromJson("{ not json")` returns `null` (does not throw).
- a document with `version = 2` returns `null`.
- a document with an unknown role string returns `null`.
- a document with a blank `uid` returns `null`.

**`app/src/test/java/com/moneycounter/MembershipErrorPolicyTest.kt`** — covers
`isMemberRowMissing`:
- `isMemberRowMissing(404)` is `true`.
- `isMemberRowMissing(null)` is `false` (an exception with no HTTP status is a
  transport failure, not a missing row).
- `isMemberRowMissing(401)`, `(403)`, `(500)`, `(503)` are all `false`.

**`app/src/test/java/com/moneycounter/RoleRetentionTest.kt`** — covers the
regression this plan exists to prevent, against `MoneyCounterViewModel`'s role
retention. `MoneyCounterViewModel` extends `AndroidViewModel` and needs an
`Application`, which JVM tests cannot build — so **do not instantiate the
ViewModel**. Instead extract the decision into a pure, file-scope function in
`MoneyCounterViewModel.kt` and have `setSellerContext` call it:

```kotlin
/** A transient null role (membership unresolved or a failed poll) must never
 *  widen permissions for a session that already knows its role. */
fun retainRole(incoming: Role?, current: Role?): Role? = incoming ?: current
```

Then test:
- `retainRole(null, Role.SELLER)` is `Role.SELLER` — **the regression guard: an
  offline poll must not turn a SELLER into a fully-privileged session.**
- `retainRole(Role.ADMIN, Role.SELLER)` is `Role.ADMIN` — a real update still wins.
- `retainRole(null, null)` is `null` — a legacy install with no membership keeps
  today's permissive behavior.
- `RolePermissionService(retainRole(null, Role.SELLER)).canAddStock()` is `false`,
  and `.canDeleteProduct()` is `false` — the retained role actually reaches the
  permission matrix.

Use `retainRole(role, currentRole)` in step 5 instead of the inline `?:`.

**Verification**: `./gradlew :app:testDebugUnitTest` → exit 0, all pass,
424 + at least 16 new tests.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `./gradlew :app:compileDebugKotlin` exits 0
- [ ] `./gradlew :app:testDebugUnitTest` exits 0, with at least 440 tests and 0 failures
- [ ] `./gradlew :app:assembleDebug` exits 0
- [ ] `grep -n "trySend(null)" app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt`
      shows the call guarded by `isMemberRowMissing` (not an unconditional emit in the catch)
- [ ] `grep -n "LaunchedEffect(profile?.uid, member?.role)" app/src/main/java/com/moneycounter/MainActivity.kt`
      returns exactly one match
- [ ] `grep -rn "retainRole" app/src/main app/src/test` shows the function defined, used in
      `setSellerContext`, and covered in `RoleRetentionTest.kt`
- [ ] The three new test files exist and every case listed in "Test plan" is present
- [ ] `git status` shows no modified files outside the in-scope list
- [ ] `plans/README.md` status row for 030 updated

## STOP conditions

Stop and report back (do not improvise) if:

- Any "Current state" excerpt does not match the live code (the branch drifted
  since `cfd8809`).
- `io.appwrite.exceptions.AppwriteException` does not exist or exposes no `code`
  property in the SDK version this project pins — report the actual exception
  shape instead of guessing an alternative.
- `AuthViewModel`'s constructor cannot take another optional parameter without
  breaking existing tests — report rather than rewriting the call sites.
- The baseline `./gradlew :app:testDebugUnitTest` is already failing **before**
  you change anything — report the pre-existing failures; do not fix them here.
- A verification fails twice after a reasonable fix attempt.
- You conclude the fix requires changing `DefaultPermissionService` or the `Role`
  permission matrix — it must not; that is an explicit out-of-scope boundary.

## Maintenance notes

- This cache is **defense in depth and an offline-UX enabler, not an
  authorization boundary**. The real boundary must live server-side (master plan
  §41–§42). When operational data moves to Appwrite (the shared-inventory work in
  `advisor-plans/008-shared-inventory-DESIGN.md`), server-side rules must
  re-validate role and branch on every write; this plan does not provide that.
- A reviewer should scrutinize: that no path can load a cached `Member` whose
  `uid` differs from the signed-in user, and that `signOut()` clears the cache.
- Deliberately deferred: making `DefaultPermissionService` strict. It stays
  permissive so genuinely member-less legacy installs keep working, per the
  owner's decision on 2026-09-12. Revisit once no legacy installs remain.
- `AppwriteAccessRepository.observeUserProfile` has the same catch-all shape.
  It carries no privileges today, so it was left alone — but if profile fields
  ever gate behavior, it needs the same treatment.
