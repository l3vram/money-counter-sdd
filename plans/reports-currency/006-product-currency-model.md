# Plan 006: Add `currencyId` to `Product` and `SavedCount` — model, products.json v3, count_history.json v3, ViewModel, tests

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 8f77ad2..HEAD -- app/src/main/java/com/moneycounter/domain/Product.kt app/src/main/java/com/moneycounter/domain/SavedCount.kt app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/test/java/com/moneycounter/domain/`
> If any of those files changed since this plan was written, compare the "Current state"
> excerpts below against the live code; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — changes two persisted JSON formats (additive, with read-compat) and
  public ViewModel function signatures; the SavedCount migration depends on a symbol→id
  heuristic for v1/v2 history.
- **Depends on**: none (builds on the stock work already merged at `8f77ad2`)
- **Category**: migration / feature foundation (per-currency products + reportability)
- **Planned at**: commit `8f77ad2`, 2026-09-06

## Why this matters

Two pieces of the reports/currency story need a durable currency identifier today:

1. **Products belong to a currency.** When the counter screen switches to CUP and back to
   USD, it should show only the products priced in that currency. Today `Product` has no
   `currencyId`, so a product has no way to say "I am a CUP product". This plan adds the
   field and bumps `products.json` to version 3 (v2 files still load, currencyId defaults
   to CUP).
2. **Every saved count must say which currency it was counted in.** Today
   `SavedCount.currency` stores only a *symbol* (`"$"` / `"US$"`); a later symbol edit
   would silently re-tag history. This plan adds `currencyId` to `SavedCount`, bumps
   `count_history.json` to version 3, and migrates v1/v2 entries by reverse-looking-up the
   symbol against the default currencies (fallback: CUP).

Everything here is read-compatible with the data already on devices, mirroring how
`count_history.json` v2 reads v1 and how `products.json` v2 reads v1.

## Current state

- `app/src/main/java/com/moneycounter/domain/Product.kt` (whole file, 26 lines):
  ```kotlin
  data class Product(
      val id: String,
      val name: String,
      val unit: String,
      val unitPrice: BigDecimal,
      val surcharge: BigDecimal = Money.ZERO,
      val stock: BigDecimal = Money.ZERO
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

      val stockValue: BigDecimal
          get() = effectiveUnitPrice.multiply(stock).setScale(Money.SCALE)
  }
  ```
  `stockValue` and `effectiveUnitPrice` stay untouched in this plan.
- `app/src/main/java/com/moneycounter/domain/SavedCount.kt` (whole file, 17 lines):
  ```kotlin
  data class SavedCount(
      val id: String,
      val savedAt: Long,
      val targetAmount: BigDecimal,
      val items: List<SavedCountItem>,
      val currency: String = "$",
      val products: List<SavedProductItem> = emptyList()
  ) {
      init {
          require(id.isNotBlank()) { "id must not be blank" }
          require(targetAmount.signum() > 0) { "targetAmount must be positive" }
      }
  }
  ```
- `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` (99 lines):
  `object ProductJson` at the top of `JsonProductRepository.kt` with
  `private const val VERSION = 2` (line 13). `toJson` (lines 15–32) writes `id`, `name`,
  `unit`, `unitPrice`, `surcharge`, `stock` per product. `fromJson` (lines 34–68) gates
  `if (version != VERSION && version != 1) return emptyList()` (line 38) and builds each
  product positionally: `Product(id, name, unit, unitPrice, surcharge, stock)` (line 64).
- `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt` (161 lines):
  `object SavedCountJson` with `private const val VERSION = 2` (line 44). `toJson`
  (lines 46–85) writes `currency` as the symbol only. `fromJson` (lines 87–160) gates
  `if (version != VERSION && version != 1) return emptyList()` (line 91), reads
  `val currency = entry.optString("currency", "$")` (line 148) and builds
  `SavedCount(id, savedAt, target, items, currency, products)` (lines 149–156).
- `app/src/main/java/com/moneycounter/domain/DefaultCurrencies.kt` (9 lines):
  `CUP = Currency("cup", "CUP", "Peso Cubano", "$")`, `USD = Currency("usd", "USD", "Dólar Americano", "US$")`,
  `fun get(): List<Currency> = listOf(CUP, USD)`. This is the source of truth for the
  v1/v2 symbol→id reverse lookup.
- `app/src/main/java/com/moneycounter/domain/Currency.kt` — `id` is a stable string
  (`"cup"` / `"usd"` for the defaults; `"c1"`, `"c2"`, … for user-added ones, generated
  by `generateCurrencyId`).
- `app/src/main/java/com/moneycounter/domain/SavedProductItem.kt` — persisted product
  line (name, unit, quantity, unitPrice, surcharge, subtotal). NOT changed in this plan:
  history payloads keep what they have; plan 007+ decides whether a saved product line
  needs its own currency.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:
  - `fun addProduct(name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean` (line 345). Builds `Product(generateProductId(state.products), cleanName, cleanUnit, unitPrice, surcharge, stock)` (line 353).
  - `fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean` (line 360). Maps `it.copy(name = cleanName, unit = cleanUnit, unitPrice = unitPrice, surcharge = surcharge, stock = stock)` (line 369).
  - `saveCount()` (lines 453–501) builds `SavedCount(..., currency = currencySymbol(), products = savedProducts)` — intentionally NOT touched here (see scope).
  - `companion object` (lines 573–591) already hosts a pure static helper,
    `applyStockDeduction(products, selections)`, as the tested home for products logic.
  - `MoneyCounterUiState` exposes `selectedCurrencyId: String` (line 50) and
    `products: List<Product>` (line 51).
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` — the two `ProductDialog`
  `onConfirm` lambdas are the only product add/edit call sites in the app:
  - Line 159 (add): `if (viewModel.addProduct(name, unit, stock, price, surcharge)) { ... }`.
  - Line 185 (edit): `if (viewModel.editProduct(product.id, name, unit, stock, price, surcharge)) { ... }`.
  `val uiState by viewModel.uiState.collectAsState()` is in scope at line 60, so
  `uiState.selectedCurrencyId` is usable inside both lambdas.
- `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` (90 lines) — existing
  codec tests. NOTE: the test `unsupported version returns empty list` (lines 53–65)
  currently uses `"version": 3` as the "unsupported" version. Once this plan bumps the
  real VERSION to 3, that fixture means `version: 3` IS supported, so the test MUST be
  rewritten to use `"version": 4` — otherwise `./gradlew test` fails at Step 5.

## Pattern to follow (persistence codec)

`JsonSavedCountRepository.kt:42-160` defines `object SavedCountJson` with a private
`VERSION`, `toJson(...)`, and `fromJson(...)` that accepts older versions and returns an
empty list for unknown ones. `JsonProductRepository.kt:11-69` mirrors this as
`object ProductJson`. Both bump VERSION +1 (2 → 3) in this plan while keeping the older
versions readable, exactly as the stock plan (001) did for version 1 → 2.

Repository testability pattern: `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt`
and `SavedCountRepositoryTest.kt` test `ProductJson.toJson/fromJson` and
`SavedCountJson.toJson/fromJson` directly (no Context). Unit tests at
`app/src/test/java/com/moneycounter/domain/` use JUnit4 + `org.json` (already a test dep).
Backtick test names are allowed. Money stays `BigDecimal` at `Money.SCALE = 2`.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat 8f77ad2..HEAD -- <paths above>` | empty or only planned edits |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/domain/Product.kt` — add `currencyId` as the LAST field.
- `app/src/main/java/com/moneycounter/domain/SavedCount.kt` — add `currencyId` as the LAST field.
- `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` — `ProductJson` VERSION 2 → 3 + currencyId read/write.
- `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt` — `SavedCountJson` VERSION 2 → 3 + currencyId read/write + symbol→id migration.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` — `addProduct`/`editProduct` signatures, `productsForCurrency` instance + companion helpers.
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` — ONLY the two `onConfirm` lambdas (lines 159 and 185).
- `app/src/test/java/com/moneycounter/domain/ProductJsonCurrencyTest.kt` (create).
- `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` — ONLY the `unsupported version` fixture: `"version": 3` → `"version": 4`.

**Out of scope** (do NOT touch, even though they look related):
- `MoneyCounterScreen.kt` (main counter screen) — plan 007 filters the product dropdown
  through `productsForCurrency` and adds the product-dialog currency dropdown.
- A currency dropdown inside `ProductDialog` — plan 007.
- Wiring `saveCount()` to write a real `currencyId` — the currency-aware save flow is
  plan 008; the migration's symbol reverse-lookup keeps v1/v2 history correct meanwhile.
- `SavedProductItem` and the history payloads — they keep the symbol-only `currency`.
- Reports (PDF/CSV), `HistoryScreen`, `HistoryDetailScreen`, `PdfExporter.kt`, `ExcelExporter.kt`.
- Units, denominations, `currencies.json`, `DenominationManagementScreen.kt`, any other file.
- No new dependencies.

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (`git log` shows e.g.
  "Add existence report screen with PDF and CSV export"). Example:
  `Add currencyId to products and saved counts, bump JSON formats to v3`.
- Do NOT push or open a PR.

## Steps

### Step 1: Add `currencyId` to the `Product` and `SavedCount` models

In `app/src/main/java/com/moneycounter/domain/Product.kt`:
- Add `val currencyId: String = DefaultCurrencies.CUP.id` as the **LAST** field (after
  `stock`). Position matters: the current positional constructor call
  `Product(generateProductId(state.products), cleanName, cleanUnit, unitPrice, surcharge, stock)`
  keeps compiling and simply defaults the new field to CUP until Step 4 passes it.
- Do NOT add an init requirement for `currencyId`, and do NOT touch `stockValue` or
  `effectiveUnitPrice`.
- `DefaultCurrencies` (same package) is already importable without a new import.

In `app/src/main/java/com/moneycounter/domain/SavedCount.kt`:
- Add `val currencyId: String = DefaultCurrencies.CUP.id` as the **LAST** field (after
  `products`). Existing positional constructions (`SavedCount(id, savedAt, target, items)`
  and `SavedCount(..., currency = ..., products = ...)`) keep compiling via the default.
- No new init requirements.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Bump products.json to version 3 (ProductJson)

In `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt`, inside
`object ProductJson`:
- Change `private const val VERSION = 2` to `private const val VERSION = 3`.
- `toJson`: after `item.put("stock", product.stock.toPlainString())`, add
  `item.put("currencyId", product.currencyId)`.
- `fromJson`:
  - Change the gate to keep reading all older formats:
    `if (version != VERSION && version != 1 && version != 2) return emptyList()` —
    accepts v1 (pre-stock), v2 (stock, no currencyId), v3.
  - After reading `stock`, add
    `val currencyId = item.optString("currencyId", DefaultCurrencies.CUP.id)`.
  - Pass `currencyId` positionally last in the constructor:
    `products.add(Product(id, name, unit, unitPrice, surcharge, stock, currencyId))`.
- Keep the whole parse defensive (blank → empty list, bad entries skipped), matching the
  current repository behavior. `DefaultCurrencies` must be imported in this file
  (`com.moneycounter.domain.DefaultCurrencies`).
- `JsonProductRepository` class body and `ProductRepository` interface do not change.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Bump count_history.json to version 3 (SavedCountJson) with symbol migration

In `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt`, inside
`object SavedCountJson`:
- Change `private const val VERSION = 2` to `private const val VERSION = 3`.
- `toJson`: after `item.put("currency", saved.currency)`, add
  `item.put("currencyId", saved.currencyId)`.
- `fromJson`:
  - Change the gate to keep reading all older formats:
    `if (version != VERSION && version != 1 && version != 2) return emptyList()` —
    accepts v1 (no products), v2 (products, no currencyId), v3.
  - After `val currency = entry.optString("currency", "$")` (line 148), resolve the id:
    ```kotlin
    val currencyId = entry.optString("currencyId", "")
        .ifBlank {
            DefaultCurrencies.get().associate { it.symbol to it.id }[currency]
                ?: DefaultCurrencies.CUP.id
        }
    ```
    i.e. a blank/absent `currencyId` means v1/v2 data → reverse-lookup the symbol against
    the default currencies (`DefaultCurrencies.get()` currently covers CUP `"$"` and USD
    `"US$"`). Symbols not in the default map fall back to `DefaultCurrencies.CUP.id`.
  - Pass it positionally last in the constructor:
    `SavedCount(id, savedAt, target, items, currency, products, currencyId)`.
- Add the import `com.moneycounter.domain.DefaultCurrencies`.
- `JsonSavedCountRepository` class body and `SavedCountRepository` interface do not change.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 4: Thread `currencyId` through the ViewModel and StockScreen

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:
- `addProduct` — new signature (currencyId inserted after stock):
  ```kotlin
  fun addProduct(name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  ```
  Keep the existing validations unchanged. Build the product as
  `Product(generateProductId(state.products), cleanName, cleanUnit, unitPrice, surcharge, stock, currencyId)`.
- `editProduct` — new signature:
  ```kotlin
  fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  ```
  Keep the existing validations unchanged. Map `it.copy(name = cleanName, unit = cleanUnit, unitPrice = unitPrice, surcharge = surcharge, stock = stock, currencyId = currencyId)`.
- Add a `productsForCurrency` helper as an instance function that delegates to a pure
  static helper in the companion object (so it is unit-testable without a ViewModel):
  ```kotlin
  fun productsForCurrency(currencyId: String): List<Product> =
      productsForCurrency(_uiState.value.products, currencyId)
  ```
  And, inside the existing `companion object` (next to `applyStockDeduction`, which is the
  established pattern for tested static helpers):
  ```kotlin
  fun productsForCurrency(products: List<Product>, currencyId: String): List<Product> =
      products.filter { it.currencyId == currencyId }
  ```
- `updateProductSelection`: NO logic change (the UI is expected to pass product IDs
  already filtered to the current currency via `productsForCurrency`; plan 007 wires that).

In `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`, update the two `onConfirm`
lambdas so the tree compiles and new products are tagged with the current currency:
- Line 159 (add):
  `viewModel.addProduct(name, unit, stock, uiState.selectedCurrencyId, price, surcharge)`.
- Line 185 (edit):
  `viewModel.editProduct(product.id, name, unit, stock, uiState.selectedCurrencyId, price, surcharge)`.
Do NOT add a currency dropdown to `ProductDialog` in this plan (that is plan 007); the
current currency is captured at the moment of save/edit.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 5: Unit tests

Create `app/src/test/java/com/moneycounter/domain/ProductJsonCurrencyTest.kt`, modeled on
`ProductJsonTest.kt` and `SavedCountRepositoryTest.kt` (JUnit4, plain strings, no Context).
Cover:

1. `ProductJson` v3 round-trip preserves `currencyId` (assert equals `"cup"` / `"usd"` after load).
2. `ProductJson` v2 migration: hand-written `"version": 2` product WITHOUT `currencyId`
   loads with `currencyId == DefaultCurrencies.CUP.id`; other fields intact.
3. `SavedCountJson` v3 round-trip preserves `currencyId` (build a `SavedCount` with an
   explicit `currencyId`, e.g. `"usd"`, save then load, assert the id and the symbol survive).
4. `SavedCountJson` v2 migration: hand-written `"version": 2` entry with `"currency": "$"`
   loads with `currencyId == "cup"`; a second fixture with `"currency": "US$"` loads with
   `currencyId == "usd"`.
5. `SavedCountJson` unknown symbol: `"version": 2` entry with `"currency": "€"` loads with
   `currencyId == DefaultCurrencies.CUP.id` (fallback).
6. `productsForCurrency` filtering: call the companion static
   `MoneyCounterViewModel.productsForCurrency(products, "cup")` with a mixed list and assert
   only products whose `currencyId == "cup"` are returned (add at least one CUP and one USD
   product). This keeps the helper tested without instantiating a ViewModel.

Also update `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt`:
- The `unsupported version returns empty list` fixture must change `"version": 3` →
  `"version": 4` (v3 is now supported). No other edits to that file.

Use `com.moneycounter.domain.Money`, `java.math.BigDecimal`, and
`com.moneycounter.repository.ProductJson` / `SavedCountJson` where needed.

**Verify**:
`./gradlew test --console=plain` → exit 0 with the 6 new tests passing and the pre-existing
`ProductJsonTest` suite still green (including the rewritten v4 fixture).

## Test plan

Listed in Step 5. The migration cases (tests 2, 4, 5 and the existing v1-load tests) are the
regression guards: real devices already have a v2 `products.json` and a v1/v2
`count_history.json`, and all must keep loading after the VERSION bump. The
`productsForCurrency` test (6) pins the filtering the counter screen will rely on in plan 007.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0, and the 6 `ProductJsonCurrencyTest` tests exist and pass
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "currencyId" app/src/main/java/com/moneycounter/domain/Product.kt` shows the field (last in the constructor)
- [ ] `git grep -n "currencyId" app/src/main/java/com/moneycounter/domain/SavedCount.kt` shows the field (last in the constructor)
- [ ] `git grep -n "VERSION = 3" app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` finds exactly one result
- [ ] `git grep -n "VERSION = 3" app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt` finds exactly one result
- [ ] `git grep -n "productsForCurrency" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` shows both the instance helper and the companion static
- [ ] `git grep -n "addProduct(" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` shows the 6-arg call passing `uiState.selectedCurrencyId`
- [ ] `git grep -n "version.*4" app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` — the unsupported-version fixture uses v4
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 006 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- The v1/v2 → v3 migrations require touching `count_history.json` or `products.json`
  server-side, renaming, or rewriting existing files.
- You find any other call site of `Product(...)` or `SavedCount(...)` using positional
  construction that breaks because `currencyId` could not be added last (e.g. a call that
  already fills every positional slot).
- Implementing the symbol reverse-lookup for `SavedCountJson` reveals default-currency
  symbols that collide (two defaults with the same symbol); report instead of guessing.
- The currency-aware UI work (product-filtered dropdown, dialog currency selector,
  `saveCount()` writing `currencyId`) is needed for `./gradlew test` to pass — that work
  belongs to plans 007/008, not here.

## Maintenance notes

- When plan 007 wires the counter screen, remember `productsForCurrency(products, currencyId)`
  is a pure static helper precisely so it stays testable without an Android context —
  keep any new filtering logic in the companion, not inline in a composable.
- `currencyId` on a product is set **at creation/edit time** to the then-current currency
  (`uiState.selectedCurrencyId`). Changing the selected currency later does NOT retag
  existing products; that is deliberate until plan 007 adds an explicit per-product choice.
- `SavedCount.currencyId` is currently written by `SavedCountJson.toJson` from whatever
  the domain object carries, and today `saveCount()` still leaves it at the CUP default.
  v1/v2 history therefore resolves via the symbol reverse-lookup — do not "clean up" that
  heuristic later without re-migrating `count_history.json`.
- Currency symbols are editable (`editCurrency`); the symbol→id lookup only runs for
  v1/v2 entries, so editing a symbol never resurfaces on already-migrated v3 data.
- Reviewer should scrutinize: that both VERSION bumps kept the older-version gates open
  (v1 AND v2 for both files), that the existing `ProductJsonTest` v1-load tests still pass
  after the gate change, and that `currencyId` was added last in both constructors so no
  positional call site shifts.