# Plan 020: Scoping de ledger — Movement/Closing con orgId/branchId + filtros de historial por rol

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/repository app/src/main/java/com/moneycounter/viewmodel app/src/main/java/com/moneycounter/ui app/src/test`
> Compare excerpts; on mismatch STOP.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED — touches the money journal serialization (v1→v2) and what the Historial/Cierres screens show per role.
- **Depends on**: plans/019-tenant-context-bootstrap.md
- **Category**: migration
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

Master plan FASE 57: every new movement carries `organizationId`/`branchId`; legacy movements get stamped with the migrated org/branch. FASE 6: history filtering by role — SELLER sees only own (branch + sellerUid), ADMIN sees branch, OWNER sees org. Without branch ids on the ledger, a seller would still see everyone's movements — the core tenant-isolation leak. This plan adds the two fields to Movement and Closing, migrates JSON v1→v2 with a compatibility layer (never lose history), and wires the role-scoped `visibleMovements` into Historial/Cierres/detail.

## Current state

`domain/Movement.kt`:
```kotlin
data class Movement(
    val id: String, val at: Long, val type: MovementType,
    val currencyId: String, val concept: String? = null,
    val products: List<MovementProductLine> = emptyList(),
    val denominations: List<MovementDenomination> = emptyList(),
    val amount: BigDecimal, val linkId: String? = null,
    val closingId: String? = null,
    val sellerUid: String = "", val sellerName: String = "") { ... }
```

`repository/JsonMovementRepository.kt` — `MovementJson.VERSION = 1`; `toJson` writes every field; `fromJson` line 132–133 `if (version != VERSION) return emptyList()`; builds `Movement(...)` at lines 198–213.

`domain/Closing.kt`:
```kotlin
data class Closing(
    val id: String, val at: Long, val currencyId: String,
    val movementIds: List<String>,
    val totalsByType: Map<MovementType, BigDecimal>,
    val netCash: BigDecimal,
    val stockSnapshot: List<ClosingStockLine>,
    val sellerUid: String = "", val sellerName: String = "") { ... }
```

`repository/JsonClosingRepository.kt` — `ClosingJson.VERSION = 1`; same version-gate pattern (lines 95–96); builds `Closing(...)` at lines 146–158.

`viewmodel/MoneyCounterViewModel.kt`:
- uiState has `movements: List<Movement>` (full journal) and `closings: List<Closing>`.
- Builders in companion: `buildVentaMovement` (991), `buildFiadoMovement` (1012), `buildMermaMovement` (1033), `buildCobroMovement` (1054), `buildExpenseMovement` (1080), `buildStockInMovement` (1100) — all take `sellerUid`/`sellerName`, none take org/branch.
- `recordMovement(m)` (746) prepends to `movements` and persists. `createClosing` (785) stamps chosen movements' `closingId` and persists both. `persistMovements` (751), `persistClosings` (763).
- `_uiState` current fields also include `products`, `closings`, `collectingFiado`, `lastFiadoId`.

Screens read the journal directly:
- `ReportsScreen.kt`: line 83 `val filtered = uiState.movements.filter { it.currencyId == filterCurrencyId }`; day totals via `netCashTotal(day.movements)` (364), `receivableTotal` (365).
- `CierresScreen.kt`: line 87–88 `val open = remember(uiState.movements, filterCurrencyId) { viewModel.openMovements(filterCurrencyId) }`.
- `MovementDetailScreen.kt`: line 58 `val movement = uiState.movements.firstOrNull { it.id == movementId }`.
- `VM.openMovements` (770) and `openFiadoMovements` (775) filter `_uiState.value.movements`; pure helpers `openMovementsPure` (1129) / `openFiadoMovementsPure` (1139).

Repo convention: versioned JSON object per entity (`MovementJson`, `ClosingJson`), tolerant skip of malformed entries, atomic write. Pure logic lives in the companion / top-level and is unit-tested (see `MovementTest.kt`, `ClosingComputeTest.kt`, `ClosingJsonTest.kt`).

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `domain/Movement.kt`
- `domain/Closing.kt`
- `repository/JsonMovementRepository.kt`
- `repository/JsonClosingRepository.kt`
- `viewmodel/MoneyCounterViewModel.kt`
- `ui/screens/ReportsScreen.kt`
- `ui/screens/CierresScreen.kt`
- `ui/screens/MovementDetailScreen.kt`
- `app/src/test/java/com/moneycounter/domain/MovementJsonMigrationTest.kt` (create)
- `app/src/test/java/com/moneycounter/domain/MovementScopingTest.kt` (create)
- extend `app/src/test/java/com/moneycounter/domain/ClosingJsonTest.kt`
- extend `app/src/test/java/com/moneycounter/viewmodel/MoneyCounterViewModelPureTest.kt`

**Out of scope**:
- Product/Stock scoping (plans 021/022).
- ClosingScope SELLER/BRANCH (plan 023).
- `SavedCount`/`Receivable`/`Payment`/`Writeoff` legacy stores (they are read-only legacy seeds for the journal; the journal is the read model).

## Steps

### Step 1: Domain — Movement and Closing gain tenant fields

Add to `Movement` (before `sellerUid`): `val organizationId: String = "", val branchId: String = ""`.
Add to `Closing` (before `sellerUid`): `val organizationId: String = "", val branchId: String = ""`.
Defaults `""` preserve all existing constructor call sites (compile-safe).

**Verify**: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.

### Step 2: JSON v2 — write, read tolerantly, never drop legacy

`MovementJson`:
- `VERSION = 2`; in `toJson` add `item.put("organizationId", m.organizationId); item.put("branchId", m.branchId)`.
- `fromJson`: change the version gate to `if (version != 1 && version != 2) return emptyList()`. Read `organizationId`/`branchId` with `entry.optString("organizationId","")` (absent → `""`). Pass into the `Movement(...)` constructor.
- Add top-level pure helper in the object:
  ```kotlin
  fun stampTenant(movements: List<Movement>, organizationId: String, branchId: String): List<Movement>
  ```
  returns movements with blanks filled (only where the field is currently blank). This is the migration path for v1/early-v2 data.

`ClosingJson`: same exact pattern — `VERSION = 2`, accept `version != 1 && version != 2` → empty, read/write both fields, add `stampTenant` for closings.

**Tests now** (before VM wiring): `MovementJsonMigrationTest.kt` — (a) v1 JSON (no tenant keys) parses and yields `""` fields (history survives), (b) v2 round-trip preserves tenant fields, (c) `stampTenant` fills only blanks (already-stamped untouched), (d) version 3 rejected. Same for `ClosingJson` via extended `ClosingJsonTest.kt`.

**Verify**: `./gradlew testDebugUnitTest` green with the new tests.

### Step 3: VM — stamp on build and on load (legacy migration)

- All six `build*Movement` builders gain `organizationId: String = ""` and `branchId: String = ""` params, passed into the constructed `Movement`.
- Every call site (in VM) passes `currentOrgId`/`currentBranchId` from plan 019. Call sites outside VM (tests, migration objects) keep using defaults.
- `recordMovement(m)`: stamp blanks with `stampTenant(listOf(m), currentOrgId, currentBranchId).first()` before prepending (defensive; builders already stamp).
- Add `private fun refreshTenantScope()`:
  - `stampTenant(_uiState.value.movements, currentOrgId, currentBranchId)` → update `movements` (and persist once if any changed).
  - same for `closings`.
  - then call `recomputeScopedMovements()`.
- Call `refreshTenantScope()` at the end of `setSellerContext` (from plan 018) and after `resolveTenantContext` in `init{}` (plan 019). Because `currentOrgId`/`currentBranchId` may resolve after movements load (async), stamping lazily here — not in the repo — is the correct design.

**Verify**: `./gradlew compileDebugKotlin` green.

### Step 4: VM — role-scoped `visibleMovements`

Add to `MoneyCounterUiState`: `val visibleMovements: List<Movement> = emptyList()`.

Pure helpers (top-level or companion — testable; **blank-tolerant**: a movement with blank org/branch is visible to everyone, preserving legacy records):
```kotlin
fun visibleForRole(
    movements: List<Movement>,
    role: Role?,
    orgId: String,
    branchId: String,
    uid: String
): List<Movement>
```
Semantics:
- `role == SELLER` → `movements.filter { (it.branchId.isBlank() || it.branchId == branchId) && (it.sellerUid.isBlank() || it.sellerUid == uid) }`
- `role == ADMIN` → branch scope (branchId blank OR == branchId)
- `role == OWNER` → org scope (orgId blank OR == orgId)
- `role == null` (single-user) → all movements
- `role == SUPERUSER` → empty (no operational view; platform only)

`recomputeScopedMovements()` (private): sets `visibleMovements` from `_uiState.value` + current role/org/branch/uid. Call it:
- inside `setSellerContext` (after role known),
- inside `refreshTenantScope`,
- inside `recordMovement` and `createClosing` (movement set changes).

**Verify**: compile.

### Step 5: VM — openMovements/openFiadoMovements and createClosing honor scope

- `openMovements(currencyId)` and `openFiadoMovements(currencyId)` should filter over `visibleMovements` instead of all movements (so a SELLER only closes/collects their own; ADMIN their branch). Keep the pure helpers `openMovementsPure`/`openFiadoMovementsPure` operating on whatever list is passed.
- `createClosing(movementIds)`: additionally stamp each selected movement's tenant from current context if blank; the computed `Closing` gets `organizationId = currentOrgId`, `branchId = currentBranchId`, seller fields as today. (ClosingScope is plan 023.)

**Verify**: compile.

### Step 6: Screens read `visibleMovements`

- `ReportsScreen.kt` line 83: `uiState.movements` → `uiState.visibleMovements`.
- `CierresScreen.kt` line 87: `remember(uiState.visibleMovements, ...)` and `viewModel.openMovements(filterCurrencyId)` (unchanged call; VM now scopes internally).
- `MovementDetailScreen.kt` line 58: keep `uiState.movements.firstOrNull` OR switch to `visibleMovements` for consistency — use `visibleMovements` (a detail reachable from the scoped list must resolve in the scoped list). Note `UnifiedReportScreen` (line 64) reads selected ids from full list; leave it, it operates on explicitly selected ids.

**Verify**: `./gradlew compileDebugKotlin` green.

### Step 7: VM pure tests

Extend `MoneyCounterViewModelPureTest.kt` (or the new scoping test file):
- SELLER: sees own + branch only; another seller's VENTA in same branch is hidden; blank-org legacy movements still visible.
- ADMIN: sees whole branch, not other branches, not other orgs.
- OWNER: sees whole org.
- null role: sees everything.
- SUPERUSER: sees nothing.

## Test plan

New files + extensions per Scope. Pattern: follow `MovementTest.kt`/`ClosingJsonTest.kt` (pure object-level assertions). Critical regression: v1 JSON must still load (no history loss).

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green (246 + new)
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] MovementJson/ClosingJson accept v1 AND v2 (tests prove v1 loads with `""` tenant fields)
- [ ] `visibleMovements` is role-correct per Step 4 (tests)
- [ ] ReportsScreen/CierresScreen read `visibleMovements`; `openMovements` scopes internally
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- v1 JSON test fails (history dropped) — do not "fix" by deleting legacy.
- `openMovements` scope breaks the "Cerrar el día" flow for the current single-user install (null-role must still see all open movements).
- `MovementJson`/`ClosingJson` need a VERSION > 2 for something plan 023 requires (report; do not bump twice).

## Maintenance notes

- `closingId` stamping is untouched; plan 023 adds ClosingScope on top of the tenant fields.
- Stock snapshots in closings still come from `Product.stock`; plan 022 keeps them consistent during the StockItem transition.
- The `visibleMovements` design (blank-tolerant) is what keeps the migration invisible to existing installs — preserve the blank-tolerance in any future filter.