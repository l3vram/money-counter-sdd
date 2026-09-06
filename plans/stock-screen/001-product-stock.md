# Plan 001: Add `stock` to Product — model, products.json v2, ViewModel, tests

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/stock-screen/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 1c516bb..HEAD -- app/src/main/java/com/moneycounter/domain/Product.kt app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt app/src/test/java/com/moneycounter/domain/`
> If any of those files changed since this plan was written, compare the "Current state"
> excerpts below against the live code; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — changes a persisted JSON format (additive, with read-compat) and public
  ViewModel function signatures.
- **Depends on**: none
- **Category**: migration / feature foundation
- **Planned at**: commit `1c516bb`, 2026-09-06

## Why this matters

Products become inventory. Each product needs an available quantity (stock) that the new
Stock screen edits and the sale flow deducts. Today `Product` has no quantity field and
`products.json` (version 1) cannot store one. This plan adds the `stock` field, bumps the
JSON format to version 2 while still reading version 1 (existing products load with
stock = 0, mirroring how `count_history.json` v2 reads v1), threads `stock` through the
ViewModel's `addProduct`/`editProduct`, and adds a `ProductJson` codec object so the
logic is unit-testable without an Android Context. Every later plan builds on this.

## Current state

- `app/src/main/java/com/moneycounter/domain/Product.kt` (whole file, 22 lines):
  ```kotlin
  data class Product(
      val id: String,
      val name: String,
      val unit: String,
      val unitPrice: BigDecimal,
      val surcharge: BigDecimal = Money.ZERO
  ) {
      init {
          require(id.isNotBlank()) { "Product ID must not be blank" }
          require(name.isNotBlank()) { "Product name must not be blank" }
          require(unit.isNotBlank()) { "Product unit must not be blank" }
          require(unitPrice.signum() >= 0) { "Unit price must be non-negative" }
          require(surcharge.signum() >= 0) { "Surcharge must be non-negative" }
      }

      val effectiveUnitPrice: BigDecimal
          get() = unitPrice.add(surcharge).setScale(Money.SCALE)
  }
  ```
- `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` (81 lines):
  `class JsonProductRepository(private val context: Context) : ProductRepository`.
  `load()` reads `products.json`, rejects `version != 1`, parses each product
  (`unitPrice` via `BigDecimal(unitPriceStr).setScale(Money.SCALE)`), skips entries with
  blank id/name/unit or negative unitPrice; `save()` writes version 1 atomically. The
  version check is at line 24: `if (root.optInt("version", 1) != 1) return emptyList()`.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:
  - `fun addProduct(name: String, unit: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean` (lines 345–358). Validates non-blank name/unit, non-negative price/surcharge, and rejects when both price and surcharge are zero.
  - `fun editProduct(id: String, name: String, unit: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean` (lines 360–374).
  - `private fun persistProducts(products: List<Product>)` (lines 519–521).
  - `MoneyCounterUiState` (lines 38–54) holds `products: List<Product>` — its shape does not change.
- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` — the PRODUCTOS section calls `viewModel.addProduct(name, unit, price, surcharge)` at line 441 and `viewModel.editProduct(product.id, name, unit, price, surcharge)` at line 466. These two call sites MUST be updated in this plan so the tree compiles after the signature change (the richer Cantidad dialog replaces them in plan 002).

## Pattern to follow (persistence codec)

`JsonSavedCountRepository.kt:42-160` defines `object SavedCountJson` with
`VERSION = 2`, `toJson(...)`, and a `fromJson(...)` that accepts `version != VERSION && version != 1` → empty list. Mirror this shape for products: `object ProductJson` in the same file as `JsonProductRepository` (next to the repository class, like `SavedCountJson`).

Repository testability pattern: `app/src/test/java/com/moneycounter/domain/SavedCountRepositoryTest.kt` tests `SavedCountJson.toJson/fromJson` directly (no Context). Unit tests at `app/src/test/java/com/moneycounter/domain/` use JUnit4 + `org.json` (already a test dep).

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Drift     | `git diff --stat 1c516bb..HEAD -- <paths above>` | empty or only planned edits |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/domain/Product.kt`
- `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt`
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` — ONLY the two `onConfirm` lambdas at lines 441 / 466
- `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` (create)

**Out of scope** (do NOT touch, even though they look related):
- The PRODUCTOS section / ProductDialog / ProductManagementRow in `DenominationManagementScreen.kt` — plan 002 moves them and adds the Cantidad field.
- `MoneyCounterScreen.kt` (main screen display) — plan 003.
- `saveCount()` stock deduction — plan 004.
- Stock report + exporters — plan 005.
- Units, currencies, denominations, history, any other file.

## Git workflow

- Branch: `feature/stock-screen` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (`git log` shows e.g. "Add multi-currency, product-based objective, configurable units, and CSV export"). Example: `Add stock quantity to product model and persistence`.
- Do NOT push or open a PR.

## Steps

### Step 1: Add `stock` to the Product model

In `app/src/main/java/com/moneycounter/domain/Product.kt`:
- Add `val stock: BigDecimal = Money.ZERO` as the last field (default keeps existing constructions compiling).
- Add to the `init` block: `require(stock.signum() >= 0) { "Stock must be non-negative" }`.
- Add a derived property (used by the report in plan 005):
  ```kotlin
  val stockValue: BigDecimal
      get() = effectiveUnitPrice.multiply(stock).setScale(Money.SCALE)
  ```
  Keep `effectiveUnitPrice` exactly as is.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Bump products.json to version 2 via a `ProductJson` codec

In `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt`:
- Add `object ProductJson` with `private const val VERSION = 2`, modeled on `SavedCountJson`
  (`JsonSavedCountRepository.kt:42-160`). It must:
  - `toJson(products: List<Product>): String` — write `version = 2` and, per product:
    `id`, `name`, `unit`, `unitPrice` (`.toPlainString()`), `surcharge` (`.toPlainString()`),
    and **`stock` (`.toPlainString()`)**.
  - `fromJson(json: String): List<Product>` — blank input → empty list; parse as JSONObject;
    `val version = root.optInt("version", 1)`; if `version != VERSION && version != 1`
    return empty list (mirror `JsonSavedCountRepository.kt:90-91`); read the `products`
    array and per entry:
    - `id`, `name`, `unit` must be non-blank or skip the entry; skip duplicate ids.
    - `unitPrice = runCatching { BigDecimal(optString("unitPrice", "")).setScale(Money.SCALE) }.getOrNull()`; skip if null or negative.
    - `surcharge = runCatching { BigDecimal(optString("surcharge", "0")).setScale(Money.SCALE) }.getOrDefault(Money.ZERO)`; skip if negative.
    - `stock = runCatching { BigDecimal(optString("stock", "0")).setScale(Money.SCALE) }.getOrDefault(Money.ZERO)`; **skip the entry if negative**.
  - Keep the whole parse defensive (wrap in try/catch returning `emptyList()`), matching the current repository behavior.
- Rewire the repository to delegate:
  - `load()` → `ProductJson.fromJson(jsonString)` (keep the existing file-exists / blank guard).
  - `save(products)` → write `ProductJson.toJson(products)` atomically (keep the `.tmp` + `renameTo` pattern, lines 73–76).
- `ProductRepository` interface does not change.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Thread `stock` through the ViewModel

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:

- `addProduct` — new signature and behavior:
  ```kotlin
  fun addProduct(name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  ```
  Keep the existing validations and add `if (stock.signum() < 0) return false`. Build the
  product as `Product(generateProductId(state.products), cleanName, cleanUnit, unitPrice, surcharge, stock)`.
- `editProduct` — new signature and behavior:
  ```kotlin
  fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  ```
  Same pattern: add `stock.signum() < 0` guard; map `it.copy(name = cleanName, unit = cleanUnit, unitPrice = unitPrice, surcharge = surcharge, stock = stock)`.
- Do NOT change `saveCount`, `productsTotal`, `productLineTotal`, or `persistProducts` in this plan.

In `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt`, update
the two existing call sites so the tree still compiles:
- Line 441 `onConfirm`: `viewModel.addProduct(name, unit, BigDecimal.ZERO, price, surcharge)`.
- Line 466 `onConfirm`: `viewModel.editProduct(product.id, name, unit, BigDecimal.ZERO, price, surcharge)`.
(`BigDecimal` is already imported in that file.)

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 4: Unit tests for the codec

Create `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt`, modeled on
`SavedCountRepositoryTest.kt` (JUnit4, plain strings → `ProductJson.fromJson`). Cover:

1. Round trip v2 preserves all fields including `stock` (assert `stock == BigDecimal("40.00")`).
2. v1 JSON without a `stock` field loads with `stock == Money.ZERO`; other fields intact.
   Use a hand-written v1 string like the one at `SavedCountRepositoryTest.kt:41-56`.
3. `version: 3` returns an empty list.
4. A product entry with negative `stock` is skipped; other entries survive.
5. Blank `json`/empty input returns empty list.

Use `com.moneycounter.domain.Money` and `java.math.BigDecimal` where needed.

**Verify**:
`./gradlew test --console=plain` → exit 0 with the 5 new tests passing
(appears in the test report under `app/build/reports/tests/` or the console summary).

## Test plan

Listed in Step 4. The structural pattern is `SavedCountRepositoryTest.kt`. The migration
case (test 2) is the regression guard: real devices already have a v1 `products.json`.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0, and the 5 `ProductJsonTest` tests exist and pass
- [ ] `git grep -n "optInt(\"version\", 1)" app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` finds nothing (version gate now inside `ProductJson`)
- [ ] `git grep -n "stock" app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` shows `stock` read and written
- [ ] `git grep -n "addProduct(" app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` shows a 5-arg call passing `BigDecimal.ZERO`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/stock-screen/README.md` status row for 001 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- Wrapping the version gate/storage in `ProductJson` forces changes to `SavedCountJson`
  or `count_history.json` (them must stay untouched).
- You discover that a v1 `products.json` on disk must be migrated server-side or renamed.

## Maintenance notes

- When other plans branch on `Product.stock` remember: a product with stock 0 is valid
  (it is simply out of stock); stock may legitimately go **negative** after a sale that
  exceeded inventory (warn-and-allow was the decided behavior, plan 004).
- If a product's `unit` is renamed via `editUnit` (ViewModel lines 135–152), only the unit
  string changes; stock quantity is unaffected.
- Reviewer should scrutinize: that `fromJson` skips (not clamps) negative stock, and that
  v2-format files written by Step 2 still have the fields a future v3 read can rely on.