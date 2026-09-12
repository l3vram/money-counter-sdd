# Plan 023: Cierres por rol — ClosingScope SELLER/BRANCH + creación gateada

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/viewmodel app/src/main/java/com/moneycounter/ui app/src/test`
> Compare excerpts; on mismatch STOP.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — the cierre is the money-cut; scope changes how a SELLER closes vs an ADMIN.
- **Depends on**: plans/022-stock-dual-write.md
- **Category**: migration
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

Master plan sections 30–34 + FASE 7: a closing belongs to an org+branch and has a
**scope** — SELLER (own open movements only) vs BRANCH (all open movements of the
branch). A SELLER must never close the whole branch. Legacy closings (no scope in
today's JSON) default to SELLER per the master's migration rule. Today's
`createClosing` records tenant fields (plan 020) but has no scope concept and
`computeClosing` stamps only seller identity.

## Current state

`domain/Closing.kt` — fields (plan 020 added `organizationId`/`branchId`):
```kotlin
data class Closing(
    val id: String, val at: Long, val currencyId: String,
    val movementIds: List<String>,
    val totalsByType: Map<MovementType, BigDecimal>,
    val netCash: BigDecimal,
    val stockSnapshot: List<ClosingStockLine>,
    val organizationId: String = "", val branchId: String = "",
    val sellerUid: String = "", val sellerName: String = "") { ... }
```

`domain/ClosingCompute.kt` — `computeClosing(id, at, movements, products, currencyId, sellerUid, sellerName): Closing` (caller at VM line 793).

`repository/JsonClosingRepository.kt` — `ClosingJson.VERSION = 2` (plan 020), writes/reads org+branch.

`viewmodel/MoneyCounterViewModel.kt`:
- `createClosing(movementIds)` (line 785): selects open movements (closingId==null) of ONE currency, computes, stamps `closingId` on selected, persists both.
- `openMovements(currencyId)` now scopes to `visibleMovements` (plan 020): SELLER already sees only own open movements. But NOTHING currently prevents a seller from creating a "branch-wide" close — every close is implicitly the whole pool.
- Plan 018 booleans in uiState: `canCreateSellerClosing`, `canCreateBranchClosing`.
- `CierresScreen.kt` reads `viewModel.openMovements(filterCurrencyId)` (line 88) and lists all closings from `uiState.closings`.

Scope rule to implement (from master FASE 7):
- SELLER → can create SELLER closing (own movements) only; cannot create BRANCH closing.
- ADMIN → can create BRANCH closing (all open movements of the branch).
- OWNER → can create BRANCH closing; can VIEW all of their org's closings (read).
- null role (single user) → behaves like OWNER/ADMIN today (branch close over everything) — always true.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/domain/Closing.kt`
- `app/src/main/java/com/moneycounter/domain/ClosingCompute.kt`
- `app/src/main/java/com/moneycounter/repository/JsonClosingRepository.kt`
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/main/java/com/moneycounter/ui/screens/CierresScreen.kt`
- `app/src/test/java/com/moneycounter/domain/ClosingComputeTest.kt` (extend)
- `app/src/test/java/com/moneycounter/domain/ClosingJsonTest.kt` (extend)

**Out of scope**:
- Owner Dashboard/rollups (later run).
- ReportsScreen changes (plan 020 already scopes history).
- Web admin / superuser.

## Steps

### Step 1: Domain — ClosingScope + scope field

In `Closing.kt` add:
```kotlin
enum class ClosingScope { SELLER, BRANCH }
```
Add `val scope: ClosingScope = ClosingScope.BRANCH` to `Closing` (after `branchId`). Default BRANCH keeps today's behavior for constructor sites; legacy JSON migration overrides to SELLER on read.

**Verify**: compile.

### Step 2: `computeClosing` gains scope

`ClosingCompute.kt` `computeClosing(...)` add param `scope: ClosingScope = ClosingScope.BRANCH`. Keep the netCash/totals/snapshot logic untouched. Pure default keeps tests compiling.

**Verify**: compile.

### Step 3: `ClosingJson` v3 — scope + legacy default SELLER

`ClosingJson`: `VERSION = 3`; write `scope` (`.name`); read accepting versions 2 and 3 (and 1-3), blank/missing scope → `ClosingScope.SELLER` (master FASE 58: legacy default SELLER). Propagate tenant fields as plan 020. Tests: v2 doc (no scope) reads with `scope == SELLER`; v3 round-trip preserves scope; netCash/totals unchanged.

**Verify**: `./gradlew testDebugUnitTest` green with extended `ClosingJsonTest.kt`.

### Step 4: VM — guard + scope at creation

In `createClosing(movementIds)`:
- Determine the exact pool:
  - If the caller CAN create a branch closing (`canCreateBranchClosing`) and the selection contains only movements of the current branch (visibleMovements already ensures this) → `scope = BRANCH`.
  - Else (`canCreateSellerClosing`) → force the pool to the seller's OWN open movements of the current branch: `visibleMovements.filter { it.sellerUid.isBlank() || it.sellerUid == sellerUid }` intersect the requested selection → `scope = SELLER`.
  - Guard at the top: if the effective scope is SELLER but the caller is not allowed seller closings (can never happen with a null default), return null.
- Bump max currency check (unchanged single-currency contract).
- Pass `scope`, `organizationId = currentOrgId`, `branchId = currentBranchId` into `computeClosing`.
- Stamp each selected movement's `closingId` as today.
- Do NOT let a SELLER request a BRANCH scope: enforce by filtering the pool and setting scope accordingly (defense even if UI misbehaves).

**Verify**: compile.

### Step 5: CierresScreen — scope display + creation path

- Each closing entry shows a badge `SELLER`/`BRANCH` (small label, e.g. "Cierre de vendedor" vs "Cierre de sucursal").
- The closing list is already derived from `uiState.closings` — additionally filter by visible org/branch scope via `uiState.visibleMovements`? No: closings are independent records; closure list should show closings whose org/branch matches the context (org for OWNER, branch for ADMIN/SELLER, all for null). Add a pure filter `closingsVisibleForRole(closings, role, orgId, branchId)` in the VM companion matching `visibleForRole` semantics (blank-tolerant). Apply in CierresScreen (`remember`).
- Keep "Cerrar el día" and manual selection working; the VM now scopes internally, so a SELLER's button effectively closes only their own movements — that is the intended behavior (seller closes their shift).

**Verify**: `./gradlew compileDebugKotlin` green.

### Step 6: Tests

- `ClosingComputeTest.kt`: `computeClosing` with `scope=SELLER` includes only own movements (pass a mixed list, assert movementIds subset and netCash); `scope=BRANCH` includes all; totals semantics unchanged.
- `ClosingJsonTest.kt`: v2→v3 upgrade default SELLER; v3 round-trip.
- VM pure: `createClosing` scoping — seller request yields only own movements and scope SELLER; admin request yields scope BRANCH over all branch movements; guards denied.

**Verify**: `./gradlew testDebugUnitTest` → green, 246 + ~8 new.

## Test plan

As in Step 6, pattern `ClosingComputeTest.kt`/`ClosingJsonTest.kt`. The critical regression: a legacy v2 `closings.json` must still load (default SELLER) — verify by test, not by deleting data.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] Legacy closings (no scope) read as `SELLER` (test)
- [ ] SELLER's `createClosing` never produces BRANCH scope (VM guard + test)
- [ ] CierresScreen displays scope badge and filters by role
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- Filtering closings by role hides closings the current single-user install (null role) should see — null role must see ALL.
- A v2 JSON fails to parse (data loss).
- `computeClosing` signature change ripples beyond `ClosingCompute.kt`+VM (shouldn't).

## Maintenance notes

- OWNER closing creation stayed as read/consult plus branch-close capability per master section 33 (creation by OWNER is a product decision; not enabled now).
- Future Owner Dashboard will aggregate closings across branches using `organizationId` + `scope`.