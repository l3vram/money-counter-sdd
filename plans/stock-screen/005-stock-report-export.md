# Plan 005: Existence report screen + PDF/CSV export

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/stock-screen/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 1c516bb..HEAD -- app/src/main/java/com/moneycounter/util/ app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt`
> If anything changed, compare the excerpts below against the live code; on a mismatch,
> treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW-MED — new screen + additive methods on the two exporters; the existing
  `SavedCount` exports must stay untouched.
- **Depends on**: plans/stock-screen/001-product-stock.md (and compiles against the
  placeholder created in 002)
- **Category**: feature
- **Planned at**: commit `1c516bb`, 2026-09-06

## Why this matters

The user wants to know what exists in inventory and be able to share it, exactly like the
current sale/count report: a screen listing stock with **PDF and Excel (CSV)** export
buttons. Plan 002 wires the navigation placeholder `StockReportScreen`; this plan fills it
in and adds a stock export overload to `PdfExporter` and `ExcelExporter` (their existing
`SavedCount` exports must not change).

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` — placeholder from plan 002:
  ```kotlin
  @Composable
  fun StockReportScreen(viewModel: MoneyCounterViewModel, onNavigateBack: () -> Unit) {
      // Placeholder; full report screen lands in plan 005.
  }
  ```
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt` (179 lines) — `fun export(saved: SavedCount)` → `buildPdf(saved)` → private `share(file: File)` (lines 166–178; `Intent.ACTION_SEND`, `application/pdf`, `FileProvider`, chooser "Exportar reporte"). Paint setup at lines 49–76; page handling (595×842, margin 48, page breaks via `newPageIfNeeded`) at lines 28–47.
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` (104 lines) — `fun export(saved: SavedCount)` → `buildCsv(saved)` → private `share(file: File)` (lines 91–103; `text/csv`, UTF-8 BOM write in `buildCsv` lines 72–76). `csvRow`/`escapeCsvField` at lines 80–89.
- After plan 001: `Product` has `stock` and `stockValue` (`effectiveUnitPrice × stock`, scale `Money.SCALE`).
- `formatDate(millis)` is a public top-level fun at `ui/screens/HistoryScreen.kt:180`.
- Button pattern to copy: `HistoryDetailScreen.kt:216-242` — two `FilledTonalButton`s with a `Share` icon and labels "EXPORTAR PDF" / "EXPORTAR EXCEL (CSV)", calling the exporters with `LocalContext.current`.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0 (no regressions)  |
| Build APK | `./gradlew assembleDebug --console=plain` | exit 0 |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt`
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` (replaces the placeholder body)

**Out of scope** (do NOT touch):
- `MainActivity.kt`, `MoneyCounterViewModel.kt`, `HistoryDetailScreen.kt`
- The existing `export(SavedCount)`, `buildPdf(SavedCount)`, `buildCsv(SavedCount)`,
  `share` implementations — keep them byte-for-byte behaviorally identical.
- Any new domain file (use `Product` + `product.stockValue` directly).

## Git workflow

- Branch: `feature/stock-screen` (this run's branch; you work in an isolated worktree).
- Commit message style matches the repo; example: "Add existence report screen with PDF and CSV export".
- Do NOT push or open a PR.

## Steps

### Step 1: Add a stock-report export to `PdfExporter`

In `PdfExporter.kt`, add a public method that mirrors `export`:

```kotlin
fun exportStockReport(products: List<Product>, currency: String, generatedAt: Long) {
    val file = buildStockPdf(products, currency, generatedAt)
    share(file)
}
```

Add a private `buildStockPdf(products: List<Product>, currency: String, generatedAt: Long): File` that reuses the same paint objects and page-break helper as `buildPdf` (you may extract the paint setup into private `val`s shared by both builders, but do not change `buildPdf`'s output). Content:
- Title `"Reporte de existencias"` (titlePaint) + `"Fecha: ${formatDate(generatedAt)}"`-style date line. `formatDate` is `ui/screens/HistoryScreen.kt:180` — import `com.moneycounter.ui.screens.formatDate`. (Note `buildPdf` currently formats its own date inline; for the stock report you may use the same `SimpleDateFormat` inline instead — either is fine, do not alter `buildPdf`.)
- Header line `"Moneda: $currency"`.
- Column headers (labelPaint style): `PRODUCTO` (margin), `CANT.` (240f), `V.UNIT` (360f), `TOTAL` (450f), then a separator line (copy the line-drawing from `buildPdf` lines 107–113 pattern).
- One row per product **with `stock.signum() != 0`** — skip zero-stock products (they are not "in existence"). Each row:
  - PRODUCTO: `product.name`
  - CANT: `"${product.stock.stripTrailingZeros().toPlainString()} ${product.unit}"`
  - V.UNIT: `formatMoneyBigDecimal(product.effectiveUnitPrice, currency)`
  - TOTAL: `formatMoneyBigDecimal(product.stockValue, currency)`
  - use `newPageIfNeeded(20f)` before each row (pattern at `buildPdf:116`).
- After a spacer, a total line (headerPaint): `"TOTAL EN EXISTENCIA"` at margin and `formatMoneyBigDecimal(<sum>, currency)` right-aligned at 450f, where `<sum>` = sum of `product.stockValue` over the same non-zero-stock products.
- If no product has stock > 0, print `"No hay existencias."` (bodyPaint).
- Finish the page, write to `File(context.cacheDir, "existencias_<dd-MM-yyyy_HH-mm-ss>.pdf")` (copy the stamp logic from `buildPdf:153-155`, but use `System.currentTimeMillis()` if `generatedAt` is 0 — prefer formatting `generatedAt` when > 0), and return the file. `share(file)` stays shared.

Imports to add: `com.moneycounter.domain.Product`. `formatMoneyBigDecimal` is already imported (`PdfExporter.kt:13`).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Add a stock-report export to `ExcelExporter`

In `ExcelExporter.kt`, add:

```kotlin
fun exportStockReport(products: List<Product>, currency: String, generatedAt: Long) {
    val file = buildStockCsv(products, currency, generatedAt)
    share(file)
}
```

`buildStockCsv` mirrors `buildCsv` (UTF-8 BOM + `.csv` in cacheDir, filename `existencias_<stamp>.csv`). Content (reuse `csvRow`/`escapeCsvField`):
- `csvRow("Reporte de existencias")`
- `csvRow("Fecha", <dd/MM/yyyy HH:mm of generatedAt>)`
- `csvRow("Moneda", currency)`
- blank line
- `csvRow("Producto", "Unidad", "Cantidad", "Precio unitario", "Recargo", "Valor total")`
- per product with `stock.signum() != 0`:
  - `csvRow(product.name, product.unit, <stock plain>, formatMoneyBigDecimal(product.effectiveUnitPrice, currency), <surcharge if > 0 else "">, formatMoneyBigDecimal(product.stockValue, currency))`
- blank line + `csvRow("Total en existencia", formatMoneyBigDecimal(<sum stockValue>, currency))`

Import `com.moneycounter.domain.Product`.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Fill in `StockReportScreen.kt`

Replace the placeholder body with a full screen modeled on `HistoryDetailScreen.kt` (Scaffold + primary TopAppBar, back arrow handling `onNavigateBack`). Signature stays
`fun StockReportScreen(viewModel: MoneyCounterViewModel, onNavigateBack: () -> Unit)`.

Content:
- `val uiState by viewModel.uiState.collectAsState()`; `val context = LocalContext.current`;
  `val currencySymbol = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }?.symbol ?: "$"`.
- `val inStock = uiState.products.filter { it.stock.signum() != 0 }` (products actually in existence).
- TopAppBar title `"Existencias"`, back arrow (copy pattern from `HistoryDetailScreen.kt:59-72`).
- `LazyColumn`:
  - A summary `Card` (surfaceVariant container, like `HistoryDetailScreen.kt:101-124`) with:
    - `DetailRow`-style line `"TOTAL EN EXISTENCIA"` / `formatMoneyBigDecimal(totalValue, currencySymbol)` (reuse the local `DetailRow` pattern — copy a private `DetailRow` composable from `HistoryDetailScreen.kt:250-271`, or write an equivalent Row).
    - A second line `"Productos con existencias: ${inStock.size}"` if helpful; keep minimal.
  - A header Row with column labels (copy the label styling from `HistoryDetailScreen.kt:136-159`): `PRODUCTO` | `CANTIDAD` | `V.UNIT` | `TOTAL` (SpaceBetween, three right-side labels roughly balanced — use `Arrangement.SpaceBetween` with four `Text`s).
  - If `inStock.isEmpty()`: `Text("No hay existencias registradas.")`.
  - Else: one `Card` per product (key = id) with a `Row` (SpaceBetween): name (bodyLarge, Medium, ellipsis) | `"${stock} ${unit}"` | `formatMoneyBigDecimal(effectiveUnitPrice, symbol)` | `formatMoneyBigDecimal(stockValue, symbol)` — match the `ProductLine` card style (`HistoryDetailScreen.kt:309-341`) but with four cells.
  - Export buttons, copied verbatim in style from `HistoryDetailScreen.kt:216-242`:
    - `PdfExporter(context).exportStockReport(uiState.products, currencySymbol, System.currentTimeMillis())` → "EXPORTAR PDF"
    - `ExcelExporter(context).exportStockReport(uiState.products, currencySymbol, System.currentTimeMillis())` → "EXPORTAR EXCEL (CSV)"
- All string labels Spanish.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

No new unit tests needed: the screen is Compose UI (no UI test harness in this repo's
local JUnit), and the exporters talk to Android `Context`/`FileProvider` (not unit-testable
locally). Regressions are guarded by:
- `./gradlew compileDebugKotlin` (all call sites compile),
- `./gradlew test` (no existing tests regress),
- done-criteria greps confirming the existing `SavedCount` exports were not altered.

Manual acceptance (operator, on device): open Stock → REPORTE DE EXISTENCIA → shows rows
and total; EXPORTAR PDF and EXPORTAR EXCEL (CSV) open the system share sheet with the file.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "exportStockReport" app/src/main/java/com/moneycounter/util/` shows both exporters' methods
- [ ] `git grep -n "existencias_" app/src/main/java/com/moneycounter/util/` shows the stock filenames in both exporters
- [ ] `git grep -n "fun export(saved: SavedCount)" app/src/main/java/com/moneycounter/util/` still returns 2 hits (existing exports intact)
- [ ] `StockReportScreen.kt` is a full screen with total + rows + both export buttons
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/stock-screen/README.md` status row for 005 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the excerpts above.
- Making the stock export requires changing the existing `export(SavedCount)`/`buildPdf`/`buildCsv` behavior or filename scheme.
- `StockReportScreen` needs a new ViewModel function or a state change (it should read `uiState.products` only).
- Compilation only passes by touching `MainActivity.kt` or the ViewModel.

## Maintenance notes

- The stock filenames use the `existencias_` prefix on purpose so they never collide with
  the sale report's `reporte_` files in the cache dir.
- `stockValue` is a derived domain property (plan 001). If pricing ever becomes
  currency-bound, `stockValue` and this report change together.
- Reviewer should check: only non-zero-stock products appear in the report, totals match
  the sum of the visible rows, and the sale `/reports` exports are byte-identical to before.