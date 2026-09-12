# Plan 022: Escrituras de stock duales — venta/alta/entrada/merma/ajuste sincronizan Product.stock + StockItem

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
- **Risk**: MED — every stock-mutating money path now writes two stores; a divergence would break the "regla de caja" (libro contable: montos correctos).
- **Depends on**: plans/021-stock-model.md
- **Category**: migration
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

Plan 021 created the branch-scoped `StockItem` store but left writes untouched:
`Product.stock` is still the only store mutations touch, so the two stores would
drift after the first sale. This plan makes every stock mutation write BOTH
`Product.stock` (compat copy — consumers: StockScreen, reports totals, closing
stock snapshots) and the branch-scoped `StockItem` (the new source of truth for
branch-scoped shared inventory). After this plan the stores are always consistent,
and the future removal of `Product.stock` becomes safe. Shared inventory per branch
(section 23 of the master plan) is achieved: all sellers of a branch read the same
`StockItem` rows.

## Current state

`viewmodel/MoneyCounterViewModel.kt` — every stock change (verify each before editing):

| Function | Line | Today's behavior |
|---|---|---|
| `addProduct` | 368 | creates `Product`; if `stock>0` records ALTA movement; `Product.stock` set |
| `editProduct` | 405 | replaces product; ENTRADA movement when `delta>0`; `Product.stock` set; negative delta = silent reduction |
| `deleteProduct` | 445 | removes product from list |
| `addStock` | 461 | adds `qty` to `Product.stock`, ALTA movement |
| `saveCount` | 615 | `applyStockDeduction(products, selections)` on `Product.stock`, VENTA movement |
| `registerCreditSale` | 686 | same deduction, VENTA_FIADO movement |
| `registerWriteoff` | 557 | `applyWriteoff(...)`, MERMA movement |

Plan 021 added: `stockItems` in uiState, `branchStock(productId)` merged read, `loadStock` + idempotent backfill, `updateStockState`, pure `increaseStock/decreaseStock/adjustStock`, `stockForBranch(items, branchId)`.
Plan 020 added: `currentOrgId`/`currentBranchId`, builders with tenant fields, `visibleMovements`.

Context for this plan's writes: all dual-writes target the CURRENT branch (`currentBranchId`) and org (`currentOrgId`).

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`
- `app/src/test/java/com/moneycounter/viewmodel/MoneyCounterViewModelPureTest.kt` (extend)
- `app/src/test/java/com/moneycounter/domain/StockDeductionTest.kt` (extend)

**Out of scope**:
- JSON schema bumps (plan 021 owns those).
- Removing `Product.stock` or its consumers (future cleanup).
- Movement/journal behavior — only the stock stores change here.

## Steps

### Step 1: Add a private dual-write primitive

In the VM add:
```kotlin
private fun updateStockAndItems(
    newProducts: List<Product>,
    transform: (List<StockItem>) -> List<StockItem>
) {
    val newItems = transform(_uiState.value.stockItems)
    _uiState.update { it.copy(products = newProducts, stockItems = newItems) }
    persistProducts(newProducts)
    persistStockItems(newItems)
}
```
`persistStockItems` = `viewModelScope.launch { stockRepository.saveAll(items) }` (mirror `persistProducts`). All mutation paths below route through this — a single funnel guarantees the two stores never diverge on a write.

### Step 2: Reroute each mutation through the dual-write

For each of the seven functions in "Current state":

- `addStock(productId, qtyText)`:
  - newProducts = product with stock+ qty (as today).
  - transform: ensure a StockItem row exists for (org, branch, productId); if absent create one with `quantity = product.stock + qty`; else apply `increaseStock(items, productId, qty)`.
- `editProduct(id, name, unit, stock, prices)`:
  - newProducts as today. transform: row exists → set `quantity = product.stock` via `adjustStock`; if the row was absent, create with `quantity = stock`. (Covers both positive and negative deltas.)
- `addProduct(...)` with `stock>0`:
  - newProducts include the new product. transform: create the row `quantity = stock` (new product → new row).
- `deleteProduct(id)`:
  - transform: drop any StockItem row for productId (any branch? no — only current branch; keep other branches' rows, as a deleted product may still exist in others).
- `saveCount` / `registerCreditSale`:
  - after `applyStockDeduction`, transform: `decreaseStock(items, productId, soldQty)` per product with `soldQty>0`; ensure rows exist (backfill guarantees, but be defensive: missing row → create with `0 − sold` result NOT possible; instead create with `product.stock − sold`).
- `registerWriteoff(productId, qtyText)`:
  - after `applyWriteoff`, transform: `decreaseStock(items, productId, qty)`.

Order guarantee: compute `newProducts` and `newItems` from the SAME base state inside the same `_uiState.value` read (no interleaved updates), then call `updateStockAndItems` once.

### Step 3: StockScreen shows branch-scoped merged stock

- Where rows display quantity, prefer the merged read semantics of plan 021 (stock item row for current branch when present, else `Product.stock`). Since `Product.stock` is kept in lockstep by this plan, the displayed value is unchanged — but the screen must read `viewModel` data that stays consistent with `stockItems` (verify by test in Step 4, not by visual judgment).
- No new UI; no buttons added or removed here (plan 018 owns visibility gates).

**Verify**: `./gradlew compileDebugKotlin` green.

### Step 4: Consistency tests (the money guarantee)

Extend `MoneyCounterViewModelPureTest.kt` (pure operations, no Android):
- After a sale, for each sold product: `Product.stock` delta == `StockItem` row delta == sold quantity (same sign).
- After writeoff: both stores decrease by the same amount.
- After addStock (alta): both increase by the same amount.
- After editProduct with positive delta (entrada): both equal the new absolute stock; with negative delta: both equal the reduced absolute stock.
- After addProduct with initial stock: product stock == new row quantity.
- After deleteProduct: no StockItem row for productId in current branch; OTHER branches keep their rows.
- Branch isolation: two branches with independent rows — mutation in branch A never touches branch B rows.
- Functional-style: tests call pure versions of the transforms (extract the transform lambdas as pure companion functions, e.g. `fun applySaleToStock(products, items, selections, orgId, branchId): Pair<List<Product>, List<StockItem>>`), so they run on JVM without a repository.

**Verify**: `./gradlew testDebugUnitTest` → green.

## Test plan

As in Step 4. Structural pattern: `StockDeductionTest.kt` (pure) + existing VM pure tests. The extraction of pure `applyXtoStockAndItems(...)` companion functions is REQUIRED so the regla-de-caja can be proven deterministically.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green (246 + new consistency tests)
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] Every mutation in the Current-state table writes both stores through ONE funnel (`updateStockAndItems`) — code review + compile
- [ ] Consistency tests prove `Product.stock == StockItem` after each mutation type
- [ ] Branch isolation test proves rows in other branches untouched
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- A mutation must interleave two `_uiState.update` calls (breaks the single-funnel guarantee).
- `Product.stock` and `StockItem` cannot be kept in lockstep for a path (design must change — report, do not silently drop one store).
- `StockScreen` needs structural changes beyond the merged read to stay consistent.

## Maintenance notes

- After this, `Product.stock` is pure duplication. A future cleanup plan removes it once Cierres snapshot, StockReportScreen, and StockScreen read only StockItem.
- Cross-device atomicity (two sellers selling concurrently in the cloud) is explicitly deferred to the sync/cloud milestone — local JSON is single-writer.
- Keep using the merged read `branchStock()`; do not special-case reads.