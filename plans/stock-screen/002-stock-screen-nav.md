# Plan 002: Stock tab screen + bottom navigation + remove PRODUCTOS from Ajustes

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/stock-screen/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 1c516bb..HEAD -- app/src/main/java/com/moneycounter/MainActivity.kt app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt app/src/main/java/com/moneycounter/ui/screens/`
> If anything changed since this plan was written, compare the excerpts below against the
> live code; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED — rewires app navigation; touches the largest existing screen.
- **Depends on**: plans/stock-screen/001-product-stock.md
- **Category**: feature
- **Planned at**: commit `1c516bb`, 2026-09-06

## Why this matters

The products section currently lives inside Ajustes (settings). Products become
inventory, so they get their own screen reached through a new bottom navigation bar with
two tabs: `Contador` (the existing main screen) and `Stock`. The Stock tab shows
product – quantity (stock) – price – surcharge with add/edit/delete (same UX as today),
plus a button that opens the Existence report (screen + exporters land in plan 005; this
plan wires the navigation placeholder). Ajustes keeps only MONEDA, UNIDADES DE MEDIDA and
DENOMINACIONES.

## Current state

- `app/src/main/java/com/moneycounter/MainActivity.kt` (62 lines) — `MoneyCounterApp`
  holds `var currentScreen by remember { mutableStateOf("counter") }` and a
  `when (currentScreen)` over `"counter" / "settings" / "history" / "detail"` (lines
  38–61). Each screen is a full-screen composable; no bottom bar exists.
- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` (1134
  lines) — the PRODUCTOS section spans lines 146–189 (section title "PRODUCTOS" at 148–150,
  product rows loop at 160–172, "AGREGAR PRODUCTO" button at 174–189). Product state vars
  at lines 75–77; delete dialog at 481–500; add dialog 431–454; edit dialog 456–479;
  `ProductManagementRow` 649–705; `ProductDialog` 1013–1128; `parseDecimalInput`
  1130–1134. After plan 001, the two `onConfirm` lambdas pass `BigDecimal.ZERO` as stock.
- `MoneyCounterViewModel` (from plan 001) — `addProduct(name, unit, stock, unitPrice,
  surcharge)`, `editProduct(id, name, unit, stock, unitPrice, surcharge)`, and
  `deleteProduct(id)` exist. `uiState.units` gives the measurement units for the dialog
  dropdown. `uiState.currencies`/`selectedCurrencyId` give the symbol
  (`firstOrNull { it.id == selectedCurrencyId }?.symbol ?: "$"`).

Persisted product row today (from `DenominationManagementScreen.ProductManagementRow`,
lines 650–705): a Card row with name (bodyLarge, ellipsis) over a subtitle
`"${symbol}${unitPrice} por ${unit}"` + `" + recargo ${symbol}${surcharge}"` when
surcharge > 0, and Edit/Delete icon buttons at the right.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0 (no regressions)  |
| Build APK | `./gradlew assembleDebug --console=plain` | exit 0, APK produced |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/MainActivity.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` (create)
- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt`

**Out of scope** (do NOT touch, even though they look related):
- `MoneyCounterScreen.kt` — the bottom tab BAR is added in MainActivity, but the main
  screen's content and its top bar stay unchanged (plan 003 adds the "disp" line there).
- `viewmodel/MoneyCounterViewModel.kt` — no new ViewModel code needed here; plan 001's
  signatures are enough. (Stock deduction = plan 004.)
- Report screen + exporters — plan 005; this plan only wires a nav placeholder for it.
- Any other file.

## Git workflow

- Branch: `feature/stock-screen` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (e.g. "Extract products into a dedicated Stock screen with bottom navigation").
- Do NOT push or open a PR.

## Steps

### Step 1: Add bottom navigation to `MainActivity.kt`

Restructure `MoneyCounterApp` so:
- `currentScreen` gains two values: `"stock"` and `"report"` (existing values unchanged).
- The app gets an outer `Scaffold` whose `bottomBar` is a Material 3 `NavigationBar`
  with two `NavigationBarItem`s: `Contador` and `Stock`.
  - Icons (from the already-included `material-icons-extended`): `Icons.Filled.Paid` for
    Contador, `Icons.Filled.Inventory2` for Stock. Use `NavigationBarItem` with
    `selected` = whether `currentScreen == "counter"` / `"stock"`, `onClick` sets
    `currentScreen = ...`, `icon = { Icon(...) }`, `label = { Text("Contador") }` /
    `Text("Stock")`.
- The bottom bar is shown ONLY when `currentScreen` is `"counter"` or `"stock"`;
  otherwise pass `bottomBar = null` for `"settings" / "history" / "detail" / "report"`.
  Implement with a `val showBottomBar = currentScreen == "counter" || currentScreen == "stock"`.
- The outer Scaffold's content lambda receives padding; apply it as
  `Modifier.padding(innerPadding)` on the `Box`/`when` wrapper so tabbed screens are not
  covered by the nav bar:
  ```kotlin
  Scaffold(bottomBar = { if (showBottomBar) { /* NavigationBar(...) */ } }) { innerPadding ->
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          when (currentScreen) { ... }
      }
  }
  ```
- Inner screens (`MoneyCounterScreen`, `StockScreen`) keep their own Scaffold/TopAppBar —
  that is fine and consistent with the existing code.
- Add two branches to the `when`:
  - `"stock" -> StockScreen(viewModel = viewModel, onNavigateToReport = { currentScreen = "report" })`
  - `"report" -> StockReportScreen(viewModel = viewModel, onNavigateBack = { currentScreen = "stock" })`
  (The `StockReportScreen` import/branch will not compile until Step 4 — see note there.
  Alternatively add both branches after Step 4; the compile gate in between accounts for it.)
- Import the icons (`androidx.compose.material.icons.automirrored.filled.*` is not needed;
  `Icons.Filled.Paid` and `Icons.Filled.Inventory2` exist under `Icons.Filled`).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL. (If you placed
both `when` branches before `StockScreen`/`StockReportScreen` exist, add the branches in
Step 4 and verify there instead.)

### Step 2: Create `ui/screens/StockScreen.kt`

New file `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`. Reuse the visual
language of `DenominationManagementScreen` (Scaffold + primary-colored TopAppBar,
`LazyColumn`, `Card` rows, `FilledTonalButton` + Add icon, `AlertDialog` confirmations).
Signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToReport: () -> Unit
)
```

Content:
- `TopAppBar(title = { Text("Stock") })` with primary container colors (copy the
  `TopAppBarDefaults.topAppBarColors(...)` block from `DenominationManagementScreen.kt:96-99`).
  No back arrow (it is a bottom-nav tab).
- `LazyColumn` content:
  - Section header title "PRODUCTOS" (use the `SectionTitle` styling — note that
    `SectionTitle` is private in `DenominationManagementScreen.kt`; copy a local private
    `SectionTitle` composable into this file).
  - If `uiState.products.isEmpty()`: a hint text `"No hay productos en el stock. Agrega uno con cantidad, precio y recargo."`.
  - Else: a product row per `uiState.products` (key = `id`). Row = `Card` with name/unit +
    quantity + price + surcharge + Edit/Delete buttons, styled like the existing
    `ProductManagementRow` but showing the stock. Layout (matches the app's row pattern —
    subtext lines give the 4 data points):
    - Main line: product `name` (bodyLarge, Medium weight, ellipsis).
    - Sub line: `"$stock ${unit} · ${symbol}${unitPrice} por ${unit}"` plus
      `" · + recargo ${symbol}${surcharge}"` when `surcharge.signum() > 0`.
      Strip trailing zeros with `.stripTrailingZeros().toPlainString()` for price,
      surcharge and stock numbers — the existing rows do this.
    - Right side: Edit (`Icons.Filled.Edit`, primary tint) and Delete (`Icons.Filled.Delete`, error tint) `IconButton`s.
  - "AGREGAR PRODUCTO" `FilledTonalButton` (Add icon) — opens the add dialog.
  - "REPORTE DE EXISTENCIA" `FilledTonalButton` (icon `Icons.Filled.Description`) —
    calls `onNavigateToReport()`.
- Add/edit dialog: copy `ProductDialog` from `DenominationManagementScreen.kt:1013-1128`
  into this file as a private composable, and extend it with a **Cantidad** field so
  stock is editable — new parameter `initialStock: String` and a decimal
  `OutlinedTextField` labeled `"Cantidad (stock)"` (same decimal input regex/parse as
  `Precio por unidad`: `\d*\.?\d*`, comma→dot; reuse a local `parseDecimalInput` identical
  to `DenominationManagementScreen.kt:1130-1134`). The confirm lambda becomes
  `onConfirm: (name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal) -> Unit`
  and calls the plan-001 signatures:
  - Add: `viewModel.addProduct(name, unit, stock, price, surcharge)`.
  - Edit: `viewModel.editProduct(product.id, name, unit, stock, price, surcharge)`.
  - Dialog default stock for a new product is `"0"`; for edit, prefill with
    `product.stock.stripTrailingZeros().toPlainString()`.
- Delete dialog: `AlertDialog` "Eliminar producto" / `"¿Seguro que deseas eliminar el producto \"${product.name}\"?"` → `viewModel.deleteProduct(product.id)` (copy lines 481–500).
- Error-message handling identical to `DenominationManagementScreen` (`var errorMessage by remember { mutableStateOf<String?>(null) }`, shown inside the dialogs).
- Symbol: `val currencySymbol = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }?.symbol ?: "$"`.
- All user-facing strings stay Spanish.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Remove the PRODUCTOS section from `DenominationManagementScreen.kt`

Remove from `DenominationManagementScreen.kt`:
- The `PRODUCTOS` section body (lines 146–189), i.e. the `SectionTitle("PRODUCTOS")` item, the products-empty hint, the `items(uiState.products...)` loop with `ProductManagementRow`, and the "AGREGAR PRODUCTO" button. Keep the `item { Spacer(...) }` rhythm so MONEDA → UNIDADES DE MEDIDA spacing stays clean.
- Product state vars (lines 75–77: `showAddProductDialog`, `showEditProductDialog`, `showDeleteProductDialog`).
- The add/edit/delete product dialogs (lines 431–500).
- `ProductManagementRow` (649–705), `ProductDialog` (1013–1128), and `parseDecimalInput` (1130–1134) — the dialog now lives in `StockScreen.kt`.
- The now-unused `import com.moneycounter.domain.Product` (line 56). Keep every import still used elsewhere (e.g. `formatMoney`, `BigDecimal`).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL (unused-import
warnings are fine; errors are not).

### Step 4: Wire navigation to the report screen

If you deferred them, add the `"stock"` and `"report"` branches from Step 1 after
`StockScreen` exists. `"report"` references `StockReportScreen`, which is created in plan
005. So that this plan leaves the tree compiling **independently**, create a minimal
`app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` NOW with only:

```kotlin
@Composable
fun StockReportScreen(viewModel: MoneyCounterViewModel, onNavigateBack: () -> Unit) {
    // Placeholder; full report screen lands in plan 005.
}
```
(Plus the minimal imports: `androidx.compose.runtime.Composable`. Plan 005 replaces this
file's body — that plan lists it explicitly as in-scope/overwrite.)

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

No new unit tests for UI code (no Compose UI test harness in this repo's local JUnit
setup). Regression safety comes from step-gated compilation:
- After Step 3, `git grep -n "PRODUCTOS" app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt`
  → no matches (section removed from Ajustes).
- After Step 4, `./gradlew assembleDebug` succeeds — proves the whole screen graph links.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (no regressions)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `MainActivity.kt` has a 2-item bottom `NavigationBar` (Contador/Stock) and handles `"stock"` and `"report"`
- [ ] `git grep -n "PRODUCTOS" app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` returns nothing
- [ ] `StockScreen.kt` exists with add/edit/delete dialogs whose Cantidad field is wired to `addProduct`/`editProduct`, and a "REPORTE DE EXISTENCIA" button
- [ ] `StockReportScreen.kt` exists as a compiling placeholder
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/stock-screen/README.md` status row for 002 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- Removing the section breaks MONEDA/UNIDADES/DENOMINACIONES rendering or any ViewModel entry point (in that case you touched something out of scope).
- You find yourself editing `MoneyCounterScreen.kt`, the ViewModel, or any exporter.

## Maintenance notes

- The Stock tab is where product CRUD lives from now on; Ajustes is config-only
  (currency/units/denominations).
- The bottom bar is app-wide navigation; new tabs (if ever added) extend the same
  `NavigationBar` and the `showBottomBar` predicate.
- Reviewer should check: the two product dialogs (add vs edit) differ only in title/prefill
  and that delete still clears the product from any active sale selections
  (that behavior is `deleteProduct` in the ViewModel — untouched).