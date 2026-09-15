# Plan 036: The initial state stops claiming what it does not know

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat b2b0858..HEAD -- app/src/main/java/com/moneycounter/repository/ app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S/M
- **Risk**: MED — it changes what the first frame shows, and one of the three items touches the
  permission gates
- **Depends on**: `plans/032-suspend-repositories.md` (merged into `main` at `b2b0858`). The
  `suspend` modifier is what makes item 1 possible at all.
- **Category**: correctness + UX
- **Planned at**: commit `b2b0858`, 2026-09-15

## Why this matters

Three findings that are the same mistake in three places: **`MoneyCounterUiState`'s initial
values assert things the app has not learned yet.**

### 1. `suspend` did not move the I/O off the main thread

Plan 032 added the modifier and deliberately nothing else. Verified after it landed: there is
**no `withContext` and no `Dispatchers` reference** in any of the four `Json*Repository`
implementations, and `viewModelScope` runs on `Dispatchers.Main.immediate`. So every load still
blocks the main thread while it reads a file — exactly as before.

Not a regression, but the word "suspend" now invites the wrong assumption, and the blocking is
real: it is the reason the first frame can jank on a device with a large journal.

### 2. There is no loading state, so "empty" and "not loaded yet" look identical

`MoneyCounterUiState` has no loading flag. Every list starts `emptyList()`, so a user opening
the app cannot tell "you have no movements" from "your movements have not arrived". Once item 1
moves the reads off Main, that window becomes visible instead of merely brief.

**Owner decision (2026-09-14)**: show a loading state rather than seeding the initial state
from a second source. Less code, less coupling, and when F2 makes stock server-authoritative
the "loading" becomes real anyway.

### 3. The permission flags default to `true` — the plan 033 hole, surviving in a default

```kotlin
data class MoneyCounterUiState(
    ...
    val canAddStock: Boolean = true,
    val canRegisterWriteoff: Boolean = true,
    val canEditStock: Boolean = true,
    val canCreateProduct: Boolean = true,
    ...
)
```

`refreshPermissions()` is called from **`setSellerContext(...)` only** — never from `init { }`.
MainActivity calls `setSeller*` from a `LaunchedEffect`, so between ViewModel construction and
that effect the UI state claims **every operational permission granted**, and a SELLER sees the
stock and write-off buttons.

**Severity, stated honestly**: this is defence-in-depth and UX, **not** an exploitable
escalation. `permissionService` is `NoAccessPermissionService` from construction, and it is the
real boundary — every mutation consults it before acting, so a button tapped in that window
does nothing. But it directly contradicts the principle plan 033 established: the UI gates
mirror the permission service and **both** fail closed. Plan 033 flipped the `Role?.may*()`
helpers to `false` and missed these defaults.

## Current state

| Piece | Where | Today |
|---|---|---|
| The four suspend implementations | `repository/Json{Stock,Movement,Closing,Product}Repository.kt` | `suspend`, blocking I/O, no dispatcher switch |
| Load functions | `MoneyCounterViewModel.loadProducts/loadStock/loadMovements/loadClosings` | each inside `viewModelScope.launch` |
| Loading flag | — | does not exist |
| Permission defaults | `MoneyCounterUiState` | `canAddStock`, `canRegisterWriteoff`, `canEditStock`, `canCreateProduct`, `canEditProduct`, `canDeleteProduct`, `canRegisterExpense`, `canViewReports`, `canCreateSellerClosing`, `canCreateBranchClosing`, `canManageCatalog` all default `true`; `canViewAllSellersDashboard` already `false` |
| `refreshPermissions()` | `MoneyCounterViewModel` | called only from `setSellerContext` |

### The dispatcher idiom that already exists

`sync/SyncManager.kt` already imports `kotlinx.coroutines.Dispatchers` and uses
`CoroutineScope(Dispatchers.IO)`. **Follow that**; do not introduce a second convention or an
injected-dispatcher abstraction. The four implementations take a `Context` and read files —
`withContext(Dispatchers.IO) { … }` around the existing body is the whole change.

## Scope

**In scope**
- `repository/JsonStockRepository.kt`, `JsonMovementRepository.kt`, `JsonClosingRepository.kt`,
  `JsonProductRepository.kt` — wrap the bodies of the four `suspend` methods in
  `withContext(Dispatchers.IO)`.
- `MoneyCounterUiState` — add one loading flag; flip the eleven permission defaults to `false`.
- `MoneyCounterViewModel` — set and clear the flag around the initial loads.
- The screen that shows the counter, to render the loading state.
- Any test that asserts the old defaults, re-decided case by case.

**Out of scope** (do NOT touch)
- The `*Json` pure objects (`StockJson`, `MovementJson`, …). No I/O, nothing to dispatch.
- `TenantRepository` / `JsonTenantRepository`, `MemberCache`, `SessionCache`. They are not
  `suspend` and converting them forces `init` async — plan 032's exclusion stands, and §9.12 of
  `docs/ESTADO-Y-PASOS.md` explains what `init` does when pushed.
- `refreshPermissions()`'s logic, and `PermissionService`. Item 3 changes **defaults only**.
- Adding an injected-dispatcher seam "for testability". The tests here do not need it; it is
  coupling bought with no buyer.
- `webadmin/`.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0, 0 failures |
| Build APK | `./gradlew :app:assembleDebug` | exit 0 |

**Baseline: 507 tests on `main` @ `b2b0858`.** Items 1 and 2 add tests; item 3 changes
expectations in whatever asserts the defaults. Record the final count.

## Git workflow

- Branch: `plan/036` off `main`.
- One commit per step, in Spanish, suffixed `(plan 036, paso N)`.
- Do NOT push, do NOT merge.

## Steps

### Step 0: Record the baseline

Run the three commands. If the count is not 507, STOP and report.

### Step 1: Move the file I/O off the main thread

In each of the four `Json*Repository` implementations, wrap the body of each `suspend` method in
`withContext(Dispatchers.IO) { … }`. Import from `kotlinx.coroutines`, matching
`sync/SyncManager.kt`.

Do not change what the bodies do. Do not touch the `*Json` objects they delegate to.

**Verify**: compile + full suite green, count unchanged from baseline. Then
`grep -rn "Dispatchers" app/src/main/java/com/moneycounter/repository/` shows four files, and
`grep -c "withContext" ` on each shows one per suspend method.

### Step 2: A loading flag, set by the ViewModel

Add **one** field to `MoneyCounterUiState`:

```kotlin
val isLoadingData: Boolean = true,   // arranca en true: al construirse, nada se leyó todavía
```

It starts `true` on purpose — that is the honest initial value, and the point of this plan.

In the ViewModel, clear it when the initial loads have finished. The four loads are independent
coroutines, so pick one of these and say in a comment which and why:
- a small counter of pending loads, cleared when it reaches zero, **or**
- one `launch` that awaits the four and clears the flag once.

Prefer the second: it is one place to reason about, and the four loads are already independent
so joining them changes nothing else. Do not introduce a loading flag per list — one flag is
what the UI needs, and eleven booleans is how state gets out of sync.

**Verify**: a ViewModel-level assertion is not possible here (the ViewModel takes an
`Application`, see §9.12). So verify by compiling and by a test on the pure state: a fresh
`MoneyCounterUiState()` reports `isLoadingData == true`.

### Step 3: Show it

In the counter screen, when `isLoadingData` is true and the relevant list is empty, show a
loading indicator instead of the empty state. Reuse the existing style — `CircularProgressIndicator`
already appears in `LoginScreen` and `ChangePasswordScreen`; copy that idiom.

Requirements:
- Do not shift the layout when it disappears.
- Do not cover content that is already usable. If a list already has data, no spinner.
- Spanish copy if any text is added, in **tú** form (the app's register; see §9 of the handoff).

**Verify**: `assembleDebug`, plus the device walkthrough in step 5.

### Step 4: The permission defaults fail closed

In `MoneyCounterUiState`, flip these eleven to `false`: `canAddStock`, `canRegisterWriteoff`,
`canEditStock`, `canCreateProduct`, `canEditProduct`, `canDeleteProduct`, `canRegisterExpense`,
`canViewReports`, `canCreateSellerClosing`, `canCreateBranchClosing`, `canManageCatalog`.
(`canViewAllSellersDashboard` is already `false`.)

This will break any test that constructs a default `MoneyCounterUiState` and expects a
permission to be granted. **Re-decide each one**, do not flip assertions mechanically: a test
using the default state as shorthand for "any user" must be given an explicit role instead,
or it now asserts nothing.

Add a test fixing the new property: a fresh `MoneyCounterUiState()` grants **no** operational
permission.

**Verify**: full suite green. Report the final count and which tests changed and why.

### Step 5: Device walkthrough (owner-gated)

1. Open the app with a populated journal → a loading indicator appears, then the data. No jank,
   no frozen first frame.
2. Sign in as SELLER → the stock and write-off buttons are **never** visible, not even for one
   frame.
3. Open with an empty journal → the empty state, not a spinner forever.

## Test plan

| Case | Expectation |
|---|---|
| A fresh `MoneyCounterUiState()` | `isLoadingData == true` |
| A fresh `MoneyCounterUiState()` | every operational permission `false` |
| `canViewAllSellersDashboard` | still `false`, unchanged |
| Each of the four implementations | one `withContext(Dispatchers.IO)` per suspend method |
| Every existing test | still green; any changed expectation justified in the commit |

## Done criteria

1. `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` exit 0, 0 failures.
2. `grep -rn "Dispatchers.IO" app/src/main/java/com/moneycounter/repository/` shows all four
   implementations.
3. No blocking file read remains on the main thread from these four repositories.
4. A fresh `MoneyCounterUiState()` grants no operational permission and reports
   `isLoadingData == true`.
5. The loading indicator appears on a populated journal and does not appear once data is shown.
6. The `*Json` objects, `TenantRepository`, `MemberCache` and `SessionCache` are untouched.
7. Step 5's walkthrough signed off by the owner.
8. `plans/README.md` status row updated with the final test count.

## STOP conditions

- Baseline is not 507 tests, or the drift check shows in-scope files changed.
- Clearing the loading flag would require changing what `init` reads, or making
  `resolveTenantContext()` asynchronous. That is plan 032's explicit exclusion and §9.12's
  warning; report instead.
- A test asserting an old permission default cannot be re-decided without changing product
  behaviour beyond this plan. Report it.
- `withContext` on any of the four methods breaks a caller. Report which; it would mean a call
  site outside a coroutine that plan 032's reconciliation missed.

## Maintenance notes

- The initial value of any state field should be what the app actually knows at construction
  time, which is usually "nothing". Defaults that assert capability are how a fail-open comes
  back after being closed — plan 033 flipped the `Role?.may*()` helpers and still missed these.
- `Dispatchers.IO` belongs in the repository implementations, not at the call site. The
  ViewModel should not have to know that a repository touches a disk.
- If a repository ever becomes a network implementation (F2), the `withContext` stays correct
  and the loading state becomes load-bearing rather than cosmetic.
