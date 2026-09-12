# Plan 024: Aislamiento de tenants — tests de seguridad (cross-tenant / cross-branch / escalada de privilegios)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/viewmodel app/src/test`
> Compare excerpts; on mismatch STOP.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: LOW — tests (+ pokes a couple of still-ungated VM mutators to close real holes).
- **Depends on**: plans/023-closing-scope.md
- **Category**: security
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

Master plan FASE 12 (security hardening) requires proof that a tenant/branch
cannot read another's data and that a SELLER cannot escalate to stock
modification. Most of the mechanics now exist (PermissionService in 018, scoped
ledger in 020, dual-write stock in 022, scoped closings in 023). This plan locks
the guarantees in as tests and does a final audit of remaining VM mutators to
make sure no money/data path is left unguarded. It is the security net that lets
the branch be merged and released with confidence — the codebase's own "regla de
caja" demands it before any merge.

## Current state

After plans 018–023 the following should already hold (verify, do not assume):

- `RoleView` matrix in `Role.kt` / `PermissionServiceTest.kt` — per-role permissions.
- `visibleForRole(...)` in the VM companion — blank-tolerant scoped journal.
- `stampTenant` for Movement/Closing legacy migration.
- `StockJson` + `branchStock()` merged read; dual-write funnel `updateStockAndItems`.
- `resolveOrganizationId` (membership org wins), `resetTenantScope`.
- `Closing` with `ClosingScope`.

Public VM mutators that could still mutate **catalog/config** and are NOT in plan 018's gate list — audit each for a permission guard in this plan: `loadDenominations`/`loadCurrencySettings`/`loadProducts`/`loadUnits`/`loadMovements`/`loadClosings` are `private` (fine); `addDenomination`/`editDenomination`/`deleteDenomination`/`moveDenominationUp`/`moveDenominationDown`, `addCurrency`/`editCurrency`/`deleteCurrency`, `addUnit`/`editUnit`/`deleteUnit` — these were to be gated by plan 018 (`canManageCatalog`); VERIFY they are. Any mutator found ungated must be gated here.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| Audit     | see Step 1 | passes |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (only if step 1 finds ungated mutators)
- `app/src/test/java/com/moneycounter/domain/TenantIsolationTest.kt` (create)
- `app/src/test/java/com/moneycounter/viewmodel/MoneyCounterViewModelPureTest.kt` (extend if needed)

**Out of scope**:
- Real remote backend security (Appwrite rules/permissions) — noted for the web-admin run.
- Performance/pagination (later run).
- Any new feature.

## Steps

### Step 1: Audit gate coverage (grep-driven)

Run:
`rg -n "fun (add|edit|delete|move|record|register|save)[A-Za-z]*\(" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
List every public mutator. For each, confirm it either (a) is permission-gated (`if (!permissionService.canX()) return false/expected`) OR (b) is inherently tenant-safe (sale/fiado/cobro allowed for SELLER). If any catalog/config mutator lacks `canManageCatalog` (or the money ones lack their gate), gate it now with the matching permission from plan 018 and add a test.

**Verify**: the command's output list is fully accounted for in the audit table below.

### Step 2: Write `TenantIsolationTest.kt` (pure JVM)

Cover, with concrete two-org/two-branch/two-seller fixtures:

- **Cross-tenant**: org A movements never appear in org B's `visibleForRole(OWNER, orgB, …)` output.
- **Cross-branch**: branch A movements never appear in branch B's `visibleForRole(ADMIN, …)`; `closingsVisibleForRole` similarly isolates branches.
- **Role escalation (SELLER → stock)**: a SELLER's `PermissionService` denies `canAddStock/canEditStock/canRegisterWriteoff/canCreateProduct/canEditProduct/canDeleteProduct/canRegisterExpense`; plus `addStock`-equivalent pure function refuses when gated (verify the VM returns false — if the guard exists, test asserts `false`).
- **Membership escalation (ADMIN → org)**: `canViewOrganizationHistory()` false for ADMIN; `visibleForRole(ADMIN, …)` never crosses to another org.
- **OWNER cross-organization**: OWNER sees org A, not org B (fixture with both).
- **Null-role fallback**: null role sees everything (documented single-user migration behavior) — assert this explicitly so nobody "fixes" it later.
- **SELLER own-history**: SELLER sees only own movements+branch, even when other sellers exist in the same branch.
- **Stamp idempotency**: `stampTenant` never overwrites a filled org/branch.
- **Stock isolation**: `increaseStock/decreaseStock/adjustStock` on branch A left branch B rows untouched (draws on plan 022 helpers).

### Step 3: Run + record

`./gradlew testDebugUnitTest` → green; total count recorded (246 + new).

## Test plan

New: `TenantIsolationTest.kt` per Step 2 (pure top-level function tests). Extend `MoneyCounterViewModelPureTest.kt` only if a step-1 fix needs a behavioral assertion. Patterns: `MovementTest.kt`, `RoleTest.kt`.

## Done criteria

ALL must hold:

- [ ] Every public VM mutator is accounted for; ungated ones are now gated (audit table in plan finished)
- [ ] `TenantIsolationTest.kt` covers the 9 highlighted cases, all passing
- [ ] `./gradlew testDebugUnitTest` green; `./gradlew compileDebugKotlin assembleDebug` green
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- An ungated mutator is a **money** path (VENTA/COBRO/GASTO) that affects the caja and belongs to SELLER — do not gate; flag instead.
- Filtering semantics differ from blank-tolerant legacy behavior (blank org/branch must be visible to the null-role install).
- A test requires a real Appwrite backend (keep everything pure/fixture-based).

## Maintenance notes

- This is the last plan of the run — treat it as the release gate. If any failure here involves the "regla de caja", stop the run and escalate to the owner before merge.
- Keep the security posture: UI hiding is UX; `PermissionService` at the VM is the boundary; real remote rule enforcement belongs to the future Appwrite tables/web-admin milestone.