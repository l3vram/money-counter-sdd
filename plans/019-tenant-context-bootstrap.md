# Plan 019: Contexto de tenant — org/branch bootstrap local + resolución de contexto

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/repository app/src/main/java/com/moneycounter/viewmodel app/src/test`
> If any in-scope file changed, compare excerpts; on mismatch STOP.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW — new files plus additive VM wiring; nothing existing changes shape.
- **Depends on**: plans/018-permission-service.md
- **Category**: migration
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

The master plan makes Organization -> Branch the unit of tenant isolation, and
every operational record must carry `organizationId`/`branchId`. There is no
web admin and no Appwrite `organizations`/`branches` table yet — so this slice
bootstraps local master data (default org + one branch) to (a) keep the app
fully working offline and single-tenant, and (b) give every future record a
tenant to be stamped with (plan 020). The cloud-provided `members/{uid}.orgId`
(already in the `Member` model from Appwrite) takes precedence over the local
bootstrap when present — "evolve, don't replace".

## Current state

- `domain/Organization.kt`: `data class Organization(val id, val name, val ownerUid, val whatsappNumber?=null, val createdAt)` — no `active` flag, no JSON serialization, no repository.
- `domain/Branch.kt`: `data class Branch(val id, val orgId, val name)` — no address/active/createdAt, no repository.
- `domain/Member.kt` (from plan 018 session context): carries `orgId`/`role`/`branchIds`; the Appwrite membership repo already resolves it.
- `domain/AppContext.kt` — created in plan 018 with `uid`, `organizationId?`, `branchId?`, `role?`.
- `viewmodel/MoneyCounterViewModel.kt`: repositories constructed in field init (pattern: `private val repo: XRepository = JsonXRepository(application)`), loads kicked in `init{}`, all writes funnel to `persist*` helpers using a `StateFlow<MoneyCounterUiState>` (`_uiState.update { ... }`).

Repo convenciones: repositories serialize with `org.json`, write atomically (temp file + rename), silently ignore persistence errors, and expose `load()`/`save()`/`saveAll()`. Tests are pure JUnit4; see `app/src/test/java/com/moneycounter/domain/SavedCountRepositoryTest.kt` for a repo round-trip test pattern.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/domain/Organization.kt`
- `app/src/main/java/com/moneycounter/domain/Branch.kt`
- `app/src/main/java/com/moneycounter/repository/TenantRepository.kt` (create)
- `app/src/main/java/com/moneycounter/repository/JsonTenantRepository.kt` (create)
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/test/java/com/moneycounter/domain/TenantRepositoryTest.kt` (create)
- `app/src/test/java/com/moneycounter/domain/TenantContextTest.kt` (create)

**Out of scope**:
- Org/Branch selector UI (deferred — only one branch/day for real use).
- Appwrite `organizations`/`branches` tables (deferred; the local repo is the seam a future `AppwriteTenantRepository` will slot into; keep the interface).
- Any stamping of existing records (that is plan 020).

## Steps

### Step 1: Extend the domain models (additive, safe)

`Organization` add `val active: Boolean = true`.
`Branch` add `val address: String? = null`, `val active: Boolean = true`, `val createdAt: Long = 0L`. Keep the existing field name `orgId` (do NOT rename to `organizationId` — `FirestoreMappers.kt` and `Member` depend on the name).

**Verify**: compile.

### Step 2: Create `TenantRepository.kt` (interface)

```kotlin
interface TenantRepository {
    fun loadOrganization(): Organization?
    fun loadBranches(): List<Branch>
    fun saveOrganization(organization: Organization)
    fun saveBranches(branches: List<Branch>)
}
```

### Step 3: Create `JsonTenantRepository.kt`

- Files `org.json` (single object, `{"version":1,"id","name","ownerUid","whatsappNumber","createdAt","active"}`) and `branches.json` (`{"version":1,"branches":[{...}]}`).
- Tolerant reading: any parse error / missing file → `null` / `emptyList()` (never throw).
- Write helper: atomic temp-file + rename, matches `JsonProductRepository`.
- Seed logic (the heart of this plan):
  ```kotlin
  fun ensureDefaultBootstrap(uid: String): Pair<Organization, Branch>
  ```
  If no org exists, create `Organization(id="org-1", name="Mi Negocio", ownerUid=uid, createdAt=now)`; if no branches, create `Branch(id="branch-1", orgId=org.id, name="Principal", createdAt=now)`. Persist both only when created. Idempotent: second call returns existing. If `uid` is blank, use `"local-owner"` as fallback so offline boot works.

**Verify**: compile.

### Step 4: Wire into the ViewModel

- Add `private val tenantRepository: TenantRepository = JsonTenantRepository(application)`.
- Fields `private var currentOrgId: String = ""` and `private var currentBranchId: String = ""`, plus uiState additions: `organization: Organization? = null`, `branches: List<Branch> = emptyList()`.
- In `setSellerContext` (from plan 018), after setting uid/role, call a private `resolveTenantContext()`:
  1. Load org + branches from `tenantRepository`.
  2. Decide context org: **membership wins** — if the `Member` orgId is present (non-blank) use it; else use the bootstrap org id (seeds via `ensureDefaultBootstrap(uid)` if absent).
  3. Pick the branch: for now the first bootstrap branch's id (multi-branch selection is a later milestone); store in `currentBranchId`.
  4. `selectBranch(branchId: String)` public helper: validates the branch belongs to the context org, sets `currentBranchId` and `uiState.selectedBranchId` (add `selectedBranchId: String?` to uiState). This is the seam the future selector UI will call.
  5. Pure resolution helper (top-level, testable): `fun resolveOrganizationId(memberOrgId: String?, bootstrapOrgId: String): String` → `memberOrgId?.takeIf { it.isNotBlank() } ?: bootstrapOrgId`.
- Also call `resolveTenantContext()` from `init{}` so an already-signed user on app start resolves even before re-login.

**Verify**: compile.

### Step 5: Tests

- `TenantRepositoryTest.kt`: (a) empty state returns null/empty; (b) `ensureDefaultBootstrap` seeds org+branch, idempotent on second call; (c) save/load round-trip preserves fields incl. the new optional ones; (d) malformed JSON degrades to null/empty without throwing.
- `TenantContextTest.kt`: `resolveOrganizationId` (blank member→bootstrap, present member→member), branch-same-org validation helper if you extract one.

**Verify**: `./gradlew testDebugUnitTest` → green, 246 + ~8 new tests.

## Test plan

As in Step 5. Pattern reference: `SavedCountRepositoryTest.kt` (JSON round-trip on a fake `Context`). For constructibility with no Android context, keep `JsonTenantRepository` unit-testable the same way the other Json repos are (they take an `Application`/`Context`; the JSON objects themselves are pure — put the (de)serialization logic in a `TenantJson` object, pure, and unit-test THAT; repo class then only does I/O).

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] `JsonTenantRepository` has tolerant read (malformed → null/empty) and idempotent seed, proven by tests
- [ ] VM resolves `currentOrgId`/`currentBranchId` (membership org wins over bootstrap)
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- The repo interface cannot stay pure (io-free) — do not add Android I/O inside testable JSON functions.
- `Member`'s orgId semantics differ from "cloud-authoritative tenant".

## Maintenance notes

- Plan 020 stamps Movement/Closing with `currentOrgId`/`currentBranchId`; it depends on this context existing.
- Future `AppwriteTenantRepository` must keep the same interface so the VM never changes.
- `selectedBranchId` is the hook for the future branch selector; do not build selector UI now.