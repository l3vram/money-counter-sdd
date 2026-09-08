# Plan 002: ViewModel productsWithPrice + counter per-currency product display

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/product-multi-currency/README.md`.
>
> **Worktree**: Execute in an isolated worktree from `feature/product-multi-currency` (after 001 merged). Do NOT work on `main`.
>
> **Drift check (run first)**: `git diff --stat HEAD..origin/feature/product-multi-currency -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`
> If any in-scope file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: 001
- **Category**: feature
- **Planned at**: commit `e8665ca`, 2026-09-07

## Why this matters

Plan 001 rewrote the Product domain to `prices: Map<currencyId, ProductPrice>` with a single `stock`. The ViewModel still uses the old method name `productsForCurrency` (which is now just a filter by `hasPriceIn`), and the counter screen's `ProductsSection` already filters by `hasPriceIn`. This plan cleans up the naming, adds per-currency display helpers, and ensures the counter's product selector and line totals are per-currency correct.

## Current state (post-001)

### Key files

| File | Role |
|------|------|
| `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` | VM — `productsForCurrency` (renamed to `productsWithPrice`), `productLineTotal`, `productsTotal`, `saveCount` already updated by 001 to use `effectiveUnitPriceFor`/`priceFor` |
| `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` | Counter — `ProductsSection` filters by `hasPriceIn`, `ProductRow` uses `priceFor` (updated by 001) |
| `app/src/main/java/com/moneycounter/domain/Product.kt` | Domain — `hasPriceIn`, `priceFor`, `effectiveUnitPriceFor`, `stockValueFor` (post-001) |
| `app/src/main/java/com/moneycounter/ui/components/Components.kt` | Kit — `LuisoButton`, `LuisoOutlineButton`, `LuisoCard`, `LuisoTextField`, `LuisoSectionHeader`, `LuisoStatCard` |
| `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` | `formatMoneyBigDecimal(x, symbol)` |

### VM post-001 (after 001 steps)

- `productsForCurrency(products, currencyId)` = `products.filter { it.hasPriceIn(currencyId) }` (companion)
- `productLineTotal(selection)` = `effectiveUnitPriceFor(selectedCurrencyId)?.multiply(quantity)`
- `productsTotal()` = sum of `effectiveUnitPriceFor(state.selectedCurrencyId) * quantity`
- `addProduct(name, unit, stock, currencyId, unitPrice, surcharge)` = builds `prices = mapOf(currencyId to ProductPrice(...))`
- `editProduct(...)` = same signature, builds `prices` map
- `saveCount()` snapshots `priceFor(selectedCurrencyId)?.unitPrice/surcharge`

### MoneyCounterScreen post-001

- `ProductsSection` (line 247): `val productsForCurrency = products.filter { it.hasPriceIn(selectedCurrencyId) }`
- `ProductRow` (line 322): receives `selectedCurrencyId: String` (added by 001), uses `selectedProduct?.priceFor(selectedCurrencyId)` for display

### Conventions to follow

- `formatMoneyBigDecimal(x, symbol)` — never prefix `$` manually at call sites
- `LuisoOutlineButton(text, onClick, modifier, enabled, contentColor)` for selectable controls
- `LuisoSectionHeader(text, accent, modifier)` for section labels
- Tests: JUnit 4, `org.junit.Assert.*`, file `app/src/test/java/com/moneycounter/domain/`
- `./gradlew assembleDebug` and `./gradlew test` are the verification commands

## Commands you will need

| Purpose   | Command                              | Expected on success |
|-----------|--------------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin`       | exit 0              |
| Tests     | `./gradlew test`                     | all pass            |
| Full build| `./gradlew assembleDebug`            | exit 0              |

## Scope

**In scope** (modify these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`

**Out of scope** (do NOT touch — other plans handle):
- StockScreen / ProductDialog — plan 003
- StockReportScreen / exporters — plan 001 + 004
- ReportsScreen / HistoryDetailScreen / UnifiedReportScreen — plan 005
- Product.kt / JsonProductRepository — done in 001

## Git workflow

- Branch: `exec/002-vm-counter` (worktree, branched from `feature/product-multi-currency` after 001 lands)
- Commit: `feat(counter): rename productsWithPrice + per-currency line totals`

## Steps

### Step 1: Rename `productsForCurrency` → `productsWithPrice`

In `MoneyCounterViewModel.kt`:

**1a.** Rename the companion function (line ~586):

```kotlin
fun productsWithPrice(products: List<Product>, currencyId: String): List<Product> =
    products.filter { it.hasPriceIn(currencyId) }
```

**1b.** Update the instance method `productsForCurrency(currencyId)` (line 404):

```kotlin
fun productsWithPrice(currencyId: String): List<Product> =
    productsWithPrice(_uiState.value.products, currencyId)
```

**1c.** Search for any call site of `productsForCurrency` in the VM and update. There should be none outside the companion + the instance method, but verify:
`grep -rn "productsForCurrency" app/src/main/java/` → should return no matches after rename.

**Verify**: `./gradlew compileDebugKotlin` → no errors.

### Step 2: Update `MoneyCounterScreen.kt` — ProductsSection

In `MoneyCounterScreen.kt`, `ProductsSection` (line 247):

```kotlin
val productsForCurrency = products.filter { it.hasPriceIn(selectedCurrencyId) }
```

Rename the local variable to `productsWithPrice` for clarity:

```kotlin
val productsWithPrice = products.filter { it.hasPriceIn(selectedCurrencyId) }
```

Update the references on lines 273 and 293 (the empty-state check and the `forEachIndexed`):

```kotlin
if (productsWithPrice.isEmpty()) {
    // ...
}
productsWithPrice.forEachIndexed { index, selection ->
    // ...
}
```

**Verify**: `./gradlew compileDebugKotlin`.

### Step 3: Add per-currency price badge to MoneyCounterScreen ProductRow

In `ProductRow` (line 322), after the existing line total display (lines 394–416), add a small per-currency price indicator if the product has multiple prices:

```kotlin
if (selectedProduct != null && selectedProduct.prices.size > 1) {
    val otherPrices = selectedProduct.prices.entries
        .filter { it.key != selectedCurrencyId }
        .joinToString(" · ") { (curId, pp) ->
            val otherSymbol = currencies.firstOrNull { it.id == curId }?.symbol ?: curId
            "$otherSymbol ${pp.effectiveUnitPrice.stripTrailingZeros().toPlainString()}"
        }
    if (otherPrices.isNotBlank()) {
        Text(
            text = "También: $otherPrices",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}
```

This adds a subtle "También: US$ 0.30" when a product has a price in another currency. The `currencies` list is already available in `ProductsSection` but not passed into `ProductRow`. Add `currencies: List<Currency>` as a parameter to `ProductRow`, passing it from the call site (line 293):

```kotlin
ProductRow(
    selection = selection,
    products = productsWithPrice,
    currencies = currencies,  // <-- new param
    symbol = currency?.symbol ?: "$",
    lineTotal = productLineTotal(selection),
    onSelectProduct = { productId -> onSelectProduct(index, productId) },
    onQuantityChange = { text -> onQuantityChange(index, text) },
    onRemove = { onRemoveRow(index) }
)
```

**Verify**: `./gradlew compileDebugKotlin`.

### Step 4: Add test for `productsWithPrice`

In `app/src/test/java/com/moneycounter/domain/ProductJsonCurrencyTest.kt`, add a new test:

```kotlin
@Test
fun `productsWithPrice filters to products with price in that currency`() {
    val cupProduct = Product("p1", "Arroz", "Lb", BigDecimal("10.00"),
        prices = mapOf("cup" to ProductPrice(BigDecimal("25.00"), BigDecimal("2.00"))))
    val usdProduct = Product("p2", "Cafe", "Kg", BigDecimal("5.00"),
        prices = mapOf("usd" to ProductPrice(BigDecimal("5.00"))))
    val both = Product("p3", "Petroleo", "L", BigDecimal("100.00"),
        prices = mapOf(
            "cup" to ProductPrice(BigDecimal("120.00")),
            "usd" to ProductPrice(BigDecimal("0.30"))
        ))

    val products = listOf(cupProduct, usdProduct, both)

    val cupOnly = MoneyCounterViewModel.productsWithPrice(products, "cup")
    assertEquals(2, cupOnly.size)
    assertEquals(setOf("p1", "p3"), cupOnly.map { it.id }.toSet())

    val usdOnly = MoneyCounterViewModel.productsWithPrice(products, "usd")
    assertEquals(2, usdOnly.size)
    assertEquals(setOf("p2", "p3"), usdOnly.map { it.id }.toSet())

    val eurOnly = MoneyCounterViewModel.productsWithPrice(products, "eur")
    assertEquals(0, eurOnly.size)
}
```

Add the necessary imports (`Product`, `ProductPrice`, `BigDecimal`, `MoneyCounterViewModel`).

**Verify**: `./gradlew test` → all tests pass.
**Verify**: `./gradlew assembleDebug` → full build success.

## Test plan

- New test: `productsWithPrice filters to products with price in that currency`
- Pattern: follow `ProductJsonCurrencyTest.kt` style
- Verify existing tests still pass with the renamed method

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0; new `productsWithPrice` test exists and passes
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `grep -rn "productsForCurrency" app/src/main/java/` returns no matches (renamed)
- [ ] No files outside the in-scope list are modified

## STOP conditions

- The code at the locations in "Current state" doesn't match the excerpts (drifted).
- A step's verification fails twice after a reasonable fix attempt.
- `./gradlew assembleDebug` fails after all steps.

## Maintenance notes

- `productsWithPrice` is the canonical filter for "which products are available in currency X". All screens that need this should use it.
- The per-currency badge in ProductRow ("También: US$ 0.30") is informational only — it doesn't affect selection or totals. The user sees the price they'll pay in the selected currency.
- If a product has no price in the selected currency, it's hidden from the selector. This prevents accidental cross-currency selections.
