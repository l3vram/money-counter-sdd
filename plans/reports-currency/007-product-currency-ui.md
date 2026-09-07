# Plan 007: Product currency in the UI — Stock product dialog currency, currency-filtered counter product selector, remove History icon

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 8f77ad2..HEAD -- app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt app/src/main/java/com/moneycounter/MainActivity.kt`
> If anything changed since this plan was written, compare the excerpts below against the
> live code; on a mismatch, treat it as a STOP condition. The ViewModel is intentionally
> NOT in the drift list — plan 006 rewrites `addProduct`/`editProduct` to take
> `currencyId` (see "Current state"); those changes ARE expected and required here.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW — pure UI-screen edits across three files; no persistence changes, no
  ViewModel signature changes, no new dependencies, no user-facing string churn besides
  the two planned messages.
- **Depends on**: plans/reports-currency/006-product-currency-model.md (adds `currencyId`
  to `Product` and threads it through `addProduct`/`editProduct`)
- **Category**: feature (UI surfacing of product currency)
- **Planned at**: commit `8f77ad2`, 2026-09-06

## Why this matters

Plan 006 gave `Product` a durable `currencyId`, written at add/edit time from
`uiState.selectedCurrencyId`. But the UI is still currency-blind: every product shows in
every currency dropdown, and the Stock dialog gives the user no way to pick which
currency a product belongs to. This plan makes currency first-class in the UI:

1. The Stock product dialog gains a **Moneda** dropdown (Unidad ÷ Cantidad); product rows
   show the product's currency code.
2. The counter screen's product selector shows **only** the products of the selected
   currency. Filtering replaces any `(CUP)`/`(USD)` suffix labeling — the current
   `"${product.name} (${product.unit})"` item text stays exactly as it is.
3. The History icon leaves the counter top bar (it moves to the bottom nav in plan 008,
   which also rewires the history entry points).

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` (700 lines):
  - Signature (lines 72–76) takes three params:
    ```kotlin
    fun MoneyCounterScreen(
        viewModel: MoneyCounterViewModel,
        onNavigateToSettings: () -> Unit,
        onNavigateToHistory: () -> Unit
    )
    ```
  - Top-bar actions (lines 91–106) hold a History `IconButton(onClick = onNavigateToHistory)`
    at 92–98 (icon `Icons.Default.History`, content description "Ver historial") and the
    Settings `IconButton(onClick = onNavigateToSettings)` at 99–105. Import
    `androidx.compose.material.icons.filled.History` is at line 23.
  - `ProductsSection` (lines 222–308) takes `products: List<Product>` (line 224) and
    `selectedCurrencyId: String` (line 226). Empty-state gate is `if (products.isEmpty())`
    (line 261) with the message `"No hay productos configurados. Ve a Ajustes para agregarlos."`
    (line 264) plus the "CONFIGURAR PRODUCTOS" `FilledTonalButton` (lines 269–279). The
    else branch passes `products = products` into each `ProductRow` (line 284) and renders
    the "AGREGAR PRODUCTO" button.
  - `ProductRow` (lines 311–429) resolves `selectedProduct = products.firstOrNull { it.id == selection.productId }`
    (line 320); an id that is not in the passed list yields `null` and the selector shows
    "Seleccionar…" (line 446). `ProductSelector` (lines 431–474) labels items
    `"${product.name} (${product.unit})"` (line 461) — unchanged by this plan.
  - `CurrencySelector` (lines 476–510) menu items display `"${currency.symbol} ${currency.code} — ${currency.name}"`
    (line 501) — the exact display pattern the Stock dialog reuses.
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` (429 lines):
  - `ProductDialog` (lines 292–423). Params at 293–303 end with
    `onConfirm: (name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal) -> Unit`
    (line 301). Field order: Nombre (321–327) → Spacer → Unidad dropdown (330–355) →
    Spacer (355) → Cantidad/stock (357–368) → Precio por unidad (371–382) → Recargo
    (385–396) → errorMessage (397–404). Confirm button (407–416) calls
    `onConfirm(name, unit, parsedStock, parsedPrice, parsedSurcharge)` (line 412).
    The Unidad dropdown (330–355) is the pattern to copy for the currency dropdown:
    `Box` + `OutlinedButton` ("Unidad: $unit") + `DropdownMenu` + `DropdownMenuItem`.
  - Add call site (149–172, onConfirm at 158–165) calls the **current pre-006**
    5-arg `viewModel.addProduct(name, unit, stock, price, surcharge)` (line 159).
  - Edit call site (174–198, onConfirm at 184–191) calls the current 6-arg
    `viewModel.editProduct(product.id, name, unit, stock, price, surcharge)` (line 185).
    After plan 006 both signatures carry `currencyId` (see next bullet).
  - `ProductRow` (lines 232–289): the unitPrice sub-line (lines 260–268) is
    `"$stock ${unit} · ${symbol}${unitPrice} por ${unit}"` plus
    `" · + recargo ${symbol}${surcharge}"` when `surcharge.signum() > 0`.
  - `uiState` is in scope (line 60) via `viewModel.uiState.collectAsState()`, so
    `uiState.currencies` and `uiState.selectedCurrencyId` are available to the dialog call
    sites and the row lambda.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` — AFTER plan 006
  the signatures are (currencyId sits AFTER stock, BEFORE unitPrice/surcharge):
  ```kotlin
  fun addProduct(name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
  ```
  `uiState.currencies: List<Currency>` and `uiState.selectedCurrencyId: String` are on
  `MoneyCounterUiState` (lines 49–50). `updateProductSelection` makes no currency checks;
  the UI is expected to only ever pass product ids already filtered to the current
  currency (this plan wires that).
- `app/src/main/java/com/moneycounter/domain/Currency.kt` (13 lines) — `data class Currency(id, code, name, symbol)`.
  `code` is the 3-letter code shown on the stock row (e.g. `USD`); `id` is what
  `Product.currencyId` stores.
- `app/src/main/java/com/moneycounter/MainActivity.kt` (113 lines) — the `"counter"` branch
  (lines 80–84) passes `onNavigateToHistory = { currentScreen = "history" }` (line 83).
  The `"history"` branch (lines 97–104) stays wired — plan 008 decides how the bottom nav
  reaches it.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat 8f77ad2..HEAD -- <3 UI files above>` | empty or only planned edits |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`
- `app/src/main/java/com/moneycounter/MainActivity.kt` (only the `MoneyCounterScreen` call site)

**Out of scope** (do NOT touch, even though they look related):
- `viewmodel/MoneyCounterViewModel.kt`, `domain/*`, `repository/*` — plan 006 already
  carries `currencyId` through the model and ViewModel; plan 007 adds no VM code.
- `DenominationManagementScreen.kt`, `HistoryScreen`/`HistoryDetailScreen`, the exporters,
  any report screen.
- Adding History to the bottom navigation — plan 008.
- Adding a `currencyId` to `SavedProductItem` or history payloads — later plan.
- Any other file. No new dependencies.

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (`git log` shows e.g.
  "Add existence report screen with PDF and CSV export"). Example for this plan:
  "Surface product currency in Stock and counter screens, move History off the top bar".
- Do NOT push or open a PR.

## Steps

### Step 1: Currency dropdown in `ProductDialog` + thread `currencyId` through add/edit call sites (StockScreen.kt)

Update `ProductDialog` (lines 292–423):

1. Add `import com.moneycounter.domain.Currency` to the imports block (alphabetical, next
   to the existing `com.moneycounter.domain.MeasurementUnit` / `com.moneycounter.domain.Product`).
2. New parameters:
   - `currencies: List<Currency>` (after `units`).
   - `initialCurrencyId: String` (after `initialUnit`).
   - `onConfirm` gains `currencyId: String` LAST:
     ```kotlin
     onConfirm: (name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal, currencyId: String) -> Unit
     ```
3. New state vars, mirroring the existing `unit`/`unitMenuOpen` pattern (lines 305–314):
   ```kotlin
   var currencyId by remember {
       mutableStateOf(
           if (currencies.any { it.id == initialCurrencyId }) initialCurrencyId
           else currencies.firstOrNull()?.id.orEmpty()
       )
   }
   var currencyMenuOpen by remember { mutableStateOf(false) }
   ```
4. Insert the dropdown between the Unidad `Spacer` (line 355) and the Cantidad
   `OutlinedTextField` (line 357) — i.e. BETWEEN Unidad and Cantidad, per the spec.
   Copy the Unidad `Box`/`OutlinedButton`/`DropdownMenu` shape (lines 330–354):
   - Button text: `"Moneda: ${selected?.let { "${it.symbol} ${it.code}" } ?: ""}"`
     where `val selected = currencies.firstOrNull { it.id == currencyId }`.
   - Menu items: `Text("${c.symbol} ${c.code} — ${c.name}")` per currency (identical
     display pattern to `MoneyCounterScreen.CurrencySelector`, line 501). Selecting an
     item sets `currencyId = c.id` and closes the menu.
   - Follow with `Spacer(modifier = Modifier.height(8.dp))`.
5. Confirm button (line 412): pass the new arg last —
   `onConfirm(name, unit, parsedStock, parsedPrice, parsedSurcharge, currencyId)`.

Update the two call sites:

- Add (148–172):
  ```kotlin
  ProductDialog(
      title = "Nuevo producto",
      units = uiState.units,
      currencies = uiState.currencies,
      initialName = "",
      initialUnit = uiState.units.firstOrNull()?.name ?: "",
      initialCurrencyId = uiState.selectedCurrencyId,
      initialStock = "0",
      initialPrice = "",
      initialSurcharge = "0",
      confirmText = "GUARDAR",
      onConfirm = { name, unit, stock, price, surcharge, currencyId ->
          if (viewModel.addProduct(name, unit, stock, currencyId, price, surcharge)) {
              showAddProductDialog = false
              errorMessage = null
          } else {
              errorMessage = "Revisa los datos: nombre y unidad obligatorios, precio y recargo no negativos y al menos uno mayor que cero."
          }
      },
      onDismiss = { showAddProductDialog = false; errorMessage = null },
      errorMessage = errorMessage
  )
  ```
  Default currency for a new product = `uiState.selectedCurrencyId` (the Counter's current
  currency); the user can override it with the dropdown.
- Edit (174–198): the initial currency must resolve from the product, i.e.
  ```kotlin
  initialCurrencyId = uiState.currencies.firstOrNull { it.id == product.currencyId }?.id
      ?: uiState.selectedCurrencyId,
  ```
  and the lambda:
  ```kotlin
  onConfirm = { name, unit, stock, price, surcharge, currencyId ->
      if (viewModel.editProduct(product.id, name, unit, stock, currencyId, price, surcharge)) {
          showEditProductDialog = null
          errorMessage = null
      } else {
          errorMessage = "Revisa los datos: nombre y unidad obligatorios, precio y recargo no negativos."
      }
  },
  ```

Note the arg-order bridge: the dialog hands `currencyId` LAST, but the plan-006 ViewModel
wants it AFTER `stock` — the call sites above reorder it (`addProduct(name, unit, stock,
currencyId, price, surcharge)`).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Show the currency code on Stock product rows (StockScreen.kt)

`ProductRow` (lines 232–289):

1. Add a new parameter `currencyCodeOf: (String) -> String` (id → 3-letter code; place it
   after `symbol`). The call site passes a lambda over `uiState.currencies`, falling back
   to the raw id so an orphaned `currencyId` never renders blank:
   ```kotlin
   currencyCodeOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.code ?: id }
   ```
2. Append `" · ${currencyCodeOf(product.currencyId)}"` to the unitPrice sub-line (lines
   260–268), so the row reads e.g. `"50 Lb · US$5.00 por Lb · USD"` (with the recargo
   suffix still appended when applicable):
   ```kotlin
   text = "${product.stock.stripTrailingZeros().toPlainString()} ${product.unit} · " +
           "${symbol}${product.unitPrice.stripTrailingZeros().toPlainString()} por ${product.unit}" +
           " · ${currencyCodeOf(product.currencyId)}" +
           if (product.surcharge.signum() > 0)
               " · + recargo ${symbol}${product.surcharge.stripTrailingZeros().toPlainString()}"
           else "",
   ```

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Filter the counter product selector to the selected currency (MoneyCounterScreen.kt)

Inside `ProductsSection` (lines 222–308), unchanged signature — the filtering happens
internally so no caller changes:

1. Right after `val currency = currencies.firstOrNull { it.id == selectedCurrencyId } ?: currencies.firstOrNull()`
   (line 235), compute:
   ```kotlin
   val productsForCurrency = products.filter { it.currencyId == selectedCurrencyId }
   ```
2. Change the empty-state gate (line 261) from `if (products.isEmpty())` to
   `if (productsForCurrency.isEmpty())`. This covers BOTH cases with one message: zero
   products overall, and products exist but none priced in the current currency.
3. Replace the empty-state message (line 264) with:
   `"No hay productos configurados para esta moneda. Ve a Ajustes para agregarlos o cambia la moneda."`
   Keep the "CONFIGURAR PRODUCTOS" `FilledTonalButton` exactly as is.
4. In the else branch, pass the filtered list to each `ProductRow` (line 284):
   `products = productsForCurrency`. Everything downstream (`ProductRow` resolving
   `selectedProduct` at line 320, `ProductSelector` listing items at 457–471,
   "AGREGAR PRODUCTO" button) works unchanged.

Stale-selection edge (documented, NO extra code): when a selected product is not in the
current currency's filter (e.g. user switches currency after selecting), `productsForCurrency`
won't contain its id, `selectedProduct` resolves to `null` at line 320, and the row renders
"Seleccionar…" with a dash unit-subtotal. `updateProductSelection` can then only ever be
reached with an in-filter id because the dropdown only lists filtered products — all
acceptable per plan.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 4: Remove the History icon from the counter top bar (MoneyCounterScreen.kt + MainActivity.kt)

MoneyCounterScreen.kt:
- Delete the History `IconButton` (lines 92–98) and the now-unused
  `import androidx.compose.material.icons.filled.History` (line 23). Keep the Settings
  `IconButton` (lines 99–105).
- Remove the `onNavigateToHistory: () -> Unit` parameter (line 75). The signature becomes:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun MoneyCounterScreen(
      viewModel: MoneyCounterViewModel,
      onNavigateToSettings: () -> Unit
  )
  ```

MainActivity.kt:
- Remove `onNavigateToHistory = { currentScreen = "history" }` (line 83) from the call:
  ```kotlin
  "counter" -> MoneyCounterScreen(
      viewModel = viewModel,
      onNavigateToSettings = { currentScreen = "settings" }
  )
  ```
- The `"history"` branch (lines 97–104) stays untouched — it simply becomes unreachable
  from the counter top bar until plan 008 wires it again. Do NOT remove it (the user
  specified: "that branch stays for now").

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 5: Full verification + no-suffix grep

Run everything from the Commands table plus the Done-criteria greps below.

**Verify**:
`./gradlew test --console=plain` → exit 0, no failures.
`./gradlew assembleDebug --console=plain` → exit 0, APK produced.

## Test plan

No new unit tests: this is pure Compose UI wiring (dialogs, filtering, an icon). Plan 006
already unit-tests `productsForCurrency` and the `currencyId` persistence the UI reads.
Regression safety comes from step-gated compilation plus the existing suite and the greps
in "Done criteria" — in particular the `(CUP)`/`(USD)` grep proving suffix labeling was
never introduced (filtering replaced it).

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "initialCurrencyId" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` shows the dialog param plus the add/edit call sites, and the dialog's confirm passes `currencyId` last
- [ ] `git grep -n "addProduct(" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` shows the 6-arg call `(name, unit, stock, currencyId, price, surcharge)`
- [ ] `git grep -n "editProduct(" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` shows the 7-arg call `(id, name, unit, stock, currencyId, price, surcharge)`
- [ ] `git grep -n "currencyCodeOf" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` shows the `ProductRow` parameter and the `· ${currencyCodeOf(...)}` suffix on the unitPrice line
- [ ] `git grep -n "productsForCurrency" app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` shows the filter, the `if (productsForCurrency.isEmpty())` gate, and `products = productsForCurrency` in `ProductRow`
- [ ] `git grep -n "No hay productos configurados para esta moneda" app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` matches the new empty-state message
- [ ] `git grep -n "History" app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` returns nothing (icon, import, param gone)
- [ ] `git grep -n "onNavigateToHistory" app/src/main/java/com/moneycounter/` returns nothing, while `git grep -n "\"history\"" app/src/main/java/com/moneycounter/MainActivity.kt` still finds the (now unreachable) `"history"` branch
- [ ] `git grep -n "(USD)\|(CUP)" app/src/main/java/com/moneycounter/ui/screens/` returns nothing — no currency suffix was added to selector labels
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 007 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts (remember: the
  ViewModel WILL differ — it carries the plan-006 `currencyId` signatures).
- A step's verification fails twice after a reasonable fix attempt.
- Plan 006 is NOT yet applied — i.e. `addProduct(...)`/`editProduct(...)` still take the
  pre-currency signatures at StockScreen lines 159/185 or in the ViewModel. This plan
  depends on 006; do not improvise a hybrid.
- You find yourself editing the ViewModel, domain models, repositories, exporters, or any
  screen other than the three in scope.
- The `"history"`/`"detail"` branches in `MainActivity.kt` are needed to keep compiling
  after the History icon is removed (the plan deliberately leaves them in place).
- Removing the History icon or the currency filter breaks any existing test in
  `./gradlew test` (the plan adds no test changes; a red suite is a STOP).

## Maintenance notes

- Product currency is now chosen explicitly in Stock and shown on the row; the counter's
  selector is scoped by the selected currency, so a `(CUP)`/`(USD)` label would be
  redundant — keep filtering, never re-add suffixes.
- `updateProductSelection` remains currency-unaware on purpose: the filtered dropdown
  guarantees it only ever receives ids of the current currency. If the counter ever shows
  products from multiple currencies, THAT is when `updateProductSelection` needs a
  currency guard.
- With the History icon gone, the only way into `"history"` is dead code until plan 008
  moves it to the bottom nav. Do not "clean up" the branch early — plan 008 relies on it.
- The `currencyCodeOf` fallback (`?: id`) means a product whose currency was deleted (plan
  to guard `deleteCurrency`) still renders a traceable code instead of an empty line.
- Reviewer should check: the dialog onConfirm arg order bridge (dialog last → VM after
  stock), that the edit dialog prefills the product's own currency (not the app's
  selected one), and that switching currencies on the counter degrades stale selections to
  "Seleccionar…" without crashing.