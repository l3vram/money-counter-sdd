# Plan 004: Deduct stock when a count is saved to history

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/stock-screen/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 1c516bb..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/test/java/com/moneycounter/domain/`
> If anything changed, compare the excerpts below against the live code; on a mismatch,
> treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: MED — mutates persisted inventory data; must only run on a real save.
- **Depends on**: plans/stock-screen/001-product-stock.md
- **Category**: feature
- **Planned at**: commit `1c516bb`, 2026-09-06

## Why this matters

Selling means taking goods out of inventory. Decided behavior: stock is deducted
**only when a count is saved to history** (the GUARDAR EN HISTORIAL button, which requires
`CounterStatus.COMPLETED`). Editing or removing sale rows, or deleting a saved history
entry, never touches stock. Over-selling is allowed (warn-and-allow, plan 003) so the
resulting stock value may be negative and must persist as such.

## Current state

`app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:

- `fun saveCount(): String?` (lines 453–498). It:
  - returns null unless `state.result.status == CounterStatus.COMPLETED` (line 455) and
    `target = productsTotal()` has `signum() > 0` (lines 456–457);
  - builds `items` (denominations) and `savedProducts` from `state.productSelections`
    (mapNotNull, skipping selections whose product is missing or quantity <= 0, lines
    466–478);
  - creates `SavedCount`, updates `_uiState` (history prepend, `savedCountId`), and calls
    `persistHistory()` (lines 489–497), then returns `saved.id`.
- `fun productsTotal()` (lines 427–437) and `fun productLineTotal(selection)` (lines
  439–444) both use `selection.quantity()` and `product.effectiveUnitPrice`.
- `private fun persistProducts(products: List<Product>)` (lines 519–521) saves via
  `productRepository`.
- After plan 001: `Product` has `stock: BigDecimal`; `ProductSelection.quantity()` returns
  the parsed decimal.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, incl. new tests  |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/test/java/com/moneycounter/domain/StockDeductionTest.kt` (create)

**Out of scope** (do NOT touch, even though they look related):
- `saveCount()`'s history-persist behavior, the COMPLETED gate, or `CounterStatus` logic.
- Restoring stock when a saved count is deleted (`deleteSavedCount`, lines 500–505) — decided: no restore.
- Plan 002 files, plan 003 file, exporters, any other file.

## Git workflow

- Branch: `feature/stock-screen` (this run's branch; you work in an isolated worktree).
- Commit message style matches the repo; example: "Deduct product stock when a sale is saved".
- Do NOT push or open a PR.

## Steps

### Step 1: Extract a pure, testable deduction function

Add a `companion object` to `MoneyCounterViewModel` (place it after the class fields, e.g.
after `val currencySymbol` or near the bottom of the class) containing:

```kotlin
companion object {
    /** Returns products with stock reduced by the sold quantity per selection.
     *  Quantities never restore; over-selling may push stock negative (warn-and-allow). */
    fun applyStockDeduction(
        products: List<Product>,
        selections: List<ProductSelection>
    ): List<Product> {
        val soldByProduct: Map<String, BigDecimal> = selections
            .filter { !it.productId.isNullOrBlank() }
            .groupingBy { it.productId }
            .fold(BigDecimal.ZERO) { acc, s -> acc.add(s.quantity()) }

        return products.map { product ->
            val sold = soldByProduct[product.id] ?: BigDecimal.ZERO
            if (sold.signum() <= 0) product
            else product.copy(stock = product.stock.subtract(sold).setScale(Money.SCALE))
        }
    }
}
```

Notes:
- `selection.quantity()` already returns `Money.ZERO` for blank/invalid input
  (`domain/ProductSelection.kt:14-20`), so a blank sell cell deducts nothing.
- One product sold on several rows is aggregated (groupingBy + fold) and deducted once.
- `Money` and `BigDecimal` are already imported in the file.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Call the deduction inside `saveCount()`

In `saveCount()`, right before `persistHistory()` (line 496) — i.e. only on a genuine save —
deduct and persist:

```kotlin
val newProducts = applyStockDeduction(state.products, state.productSelections)
_uiState.update { it.copy(products = newProducts) }
persistProducts(newProducts)
```

- Use `state.products` (captured at the top of the method) so the deduction sees the
  pre-save inventory.
- Update `_uiState` FIRST so the UI shows the new stock immediately, then persist.
- Do not touch anything above the `if (state.result.status != CounterStatus.COMPLETED) return null`
  guard; a cancelled/non-completed save deducts nothing.
- `deleteSavedCount` stays as-is (no restore).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Unit tests

Create `app/src/test/java/com/moneycounter/domain/StockDeductionTest.kt` (JUnit4; pattern
`ProductSelectionTest.kt` / `SavedCountRepositoryTest.kt`). Import
`com.moneycounter.viewmodel.MoneyCounterViewModel.applyStockDeduction` and the domain
types. Helper to build products:

```kotlin
private fun product(id: String, stock: String) =
    Product(id, "Name $id", "Lb", BigDecimal("2.00"), surcharge = BigDecimal("0.50"), stock = BigDecimal(stock))
private fun sel(productId: String, qty: String) = ProductSelection(productId = productId, quantityText = qty)
```

Cover:
1. **Happy path**: single selection qty 3 against stock 40 → stock 37.00.
2. **Same product on two rows aggregates**: qty 1 + qty 2 against stock 5 → 2.00.
3. **Over-sell allowed, stock goes negative**: stock 2, sell 5 → stock `-3.00` (not clamped).
4. **Blank quantity deducts nothing**: quantityText `""` → stock unchanged.
5. **Selections for unknown/other products leave those products untouched**; products with
   no matching selection are returned unchanged.
6. **Decimal quantities**: sell `1.5` from stock `10` → `8.50` (scale is `Money.SCALE`, 2).

**Verify**:
`./gradlew test --console=plain` → exit 0 with the 6 new tests passing.

## Test plan

Listed in Step 3. The regression case is #6 (decimal) and #2 (dedupe) — both are real
usage in this app (a product can appear on several sale rows; quantities are decimal).

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0, and `StockDeductionTest` exists with the 6 cases passing
- [ ] `git grep -n "applyStockDeduction" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` shows the definition and the `saveCount()` call, both inside/near `saveCount` and the companion
- [ ] `git grep -n "applyStockDeduction" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt | wc -l` returns ≥ 2 (definition + call)
- [ ] `git diff --stat` touches only the two in-scope files
- [ ] `plans/stock-screen/README.md` status row for 004 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- `saveCount()`'s shape changed since this plan was written (guards above line 498).
- Deducting before `persistHistory()` is impossible without moving the COMPLETED/target
  gates (those gates must stay exactly where they are).
- The deduction must run anywhere other than `saveCount()` (e.g. real-time typing) — that
  was explicitly rejected by the user.

## Maintenance notes

- Stock is only ever reduced by saves; nothing in this plan adds stock. Adding stock is an
  edit on the Stock screen (plan 002's edit dialog), which this plan does not overlap.
- If history gains an "undo sale" feature later, restoring stock is a separate decided decision (currently: no restore).
- Reviewer should scrutinize: the deduction runs once per save even if the user taps GUARDAR repeatedly — a pre-existing quirk of `saveCount()` (it re-persists history too). Do not alter it here.