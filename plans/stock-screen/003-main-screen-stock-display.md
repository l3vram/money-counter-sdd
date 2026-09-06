# Plan 003: Main screen shows available stock + over-stock warning

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/stock-screen/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 1c516bb..HEAD -- app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`
> If the file changed since this plan was written, compare the excerpts below against the
> live code; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW — display-only change in one composable.
- **Depends on**: plans/stock-screen/001-product-stock.md
- **Category**: feature
- **Planned at**: commit `1c516bb`, 2026-09-06

## Why this matters

The main (Contador) screen selects products and quantities to sell. Now that products
carry stock, the seller needs to see how much is available per product ("disp: X") and a
clear warning when the sell quantity exceeds what is in stock. Decided behavior is
**warn and allow**: the sale is not blocked and stock may go negative on save.

## Current state

`app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`:

- `ProductRow` composable (lines 310–419). It receives `selection` and `products`, looks
  up `val selectedProduct = products.firstOrNull { it.id == selection.productId }`
  (line 320), and renders the price line at lines 410–416:
  ```kotlin
  if (selectedProduct != null) {
      Text(
          text = "${symbol}${selectedProduct.effectiveUnitPrice.stripTrailingZeros().toPlainString()} por ${selectedProduct.unit}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
  ```
- The quantity field lives in a `Row` at lines 349–408; `selection.quantityText` /
  `selection.quantity()` exist on `ProductSelection` (domain/ProductSelection.kt:9-11).
- After plan 001, `Product` has `stock: BigDecimal` (default `Money.ZERO`) and
  `effectiveUnitPrice`. `Money.SCALE` and `Money.ZERO` are already imported in this file
  (imports at lines 61-62).

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0 (no regressions)  |

## Scope

**In scope** (only this file):
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`

**Out of scope** (do NOT touch):
- `viewmodel/MoneyCounterViewModel.kt` (stock deduction on save = plan 004).
- `ui/screens/StockScreen.kt`, `MainActivity.kt`, exporters, any other file.

## Git workflow

- Branch: `feature/stock-screen` (this run's branch; you work in an isolated worktree).
- Commit message style matches the repo; example: "Show available stock and warn when sale exceeds it on the counter screen".
- Do NOT push or open a PR.

## Steps

### Step 1: Show available stock on each product row

In `MoneyCounterScreen.kt`, inside `ProductRow`, replace the price line block
(lines 410–416) so it renders the availability in the same `labelSmall` style. Target shape:

```kotlin
if (selectedProduct != null) {
    val outOfRange = selection.quantity() > selectedProduct.stock
    Text(
        text = "${symbol}${selectedProduct.effectiveUnitPrice.stripTrailingZeros().toPlainString()} por ${selectedProduct.unit}" +
                " · disp: ${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit}",
        style = MaterialTheme.typography.labelSmall,
        color = if (outOfRange) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (outOfRange) {
        Text(
            text = "⚠ Cantidad mayor que el stock disponible (${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
```

Notes:
- `selection.quantity()` is the parsed decimal quantity (`ProductSelection.kt:9`); it
  returns `Money.ZERO` for empty input, so an empty field renders normally (no warning).
- The comparison `BigDecimal > BigDecimal` works naturally in Kotlin.
- Do not change the quantity `OutlinedTextField`, the line total, the surcharge subtext
  (lines 397–406), or anything else. The warning is a new sub-line only.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Confirm no behavioral changes elsewhere

**Verify**:
`git diff --stat` shows changes only in `MoneyCounterScreen.kt`, and
`./gradlew test --console=plain` → exit 0 (no regressions).

## Test plan

No new unit tests (UI-only; repo has no Compose UI unit harness). Verification is the
compile gate plus the done-criteria greps. Manual acceptance (operator, on device):
- A product with stock 40 and sell quantity 3 shows `disp: 40 Lb` on the row.
- Setting quantity to 50 turns the row's price line red and shows the warning sub-line;
  the sale is still allowed.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] `git grep -n "disp:" app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` shows the availability line with `stock`
- [ ] `git grep -n "stock disponible" app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` shows the warning
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/stock-screen/README.md` status row for 003 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- `MoneyCounterScreen.kt` no longer matches the excerpts above.
- The change requires touching the ViewModel or blocking input (decided: never block — warn and allow).
- A verification command needs an out-of-scope file to pass.

## Maintenance notes

- When plan 004 lands, the `disp` value shown reflects the *persisted* stock; an in-flight
  (not yet saved) count does not show "reserved" quantities. That is intended.
- If stock formatting ever changes (e.g. thousands separators), both this line and the
  Stock screen row must change consistently — the `.stripTrailingZeros().toPlainString()`
  convention is shared across the app.