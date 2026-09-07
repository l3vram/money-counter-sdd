# Plan 009: Unified report — merge selected counts into `UnitedCount`, exported as PDF and CSV

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 8f77ad2..HEAD -- app/src/main/java/com/moneycounter/domain/ReportAggregation.kt app/src/main/java/com/moneycounter/util/PdfExporter.kt app/src/main/java/com/moneycounter/util/ExcelExporter.kt app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt app/src/main/java/com/moneycounter/domain/SavedCount.kt app/src/main/java/com/moneycounter/domain/SavedProductItem.kt`
> Expected delta from plan 006: `SavedCount` carries `currencyId`. Expected from plan 008:
> `UnifiedReportScreen.kt` is a placeholder whose body this plan replaces. Any other drift
> vs. the excerpts below is a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW-MED — one new pure aggregation file + tests, two ADDITIVE exporter
  overloads (existing `export(SavedCount)` must stay untouched), and one screen body that
  replaces the 008 placeholder. No persistence changes, no ViewModel changes, no new
  dependencies.
- **Depends on**: plans/reports-currency/008-reports-screen.md (wires the `"summary"`
  branch to the `UnifiedReportScreen` placeholder skipped here; also provides
  `SavedCount.currencyId` via plan 006 for the same-currency guard)
- **Category**: feature (unified report aggregation + export)
- **Planned at**: commit `8f77ad2`, 2026-09-06

## Why this matters

Plan 008 lets the operator multi-select saved counts per currency. This plan makes that
selection useful: it collapses the selected counts into ONE `UnitedCount` (denominations
merged by value, products merged by name+unit, totals summed) and exports it as a single
PDF or Excel (CSV) file — the same report shape the app already produces for one count,
but across many. The merge helper is pure so the math is unit-testable without Android.

## Current state

- `app/src/main/java/com/moneycounter/MainActivity.kt` — AFTER plan 008 the `"summary"`
  branch already exists and needs NO edits here:
  ```kotlin
  "summary" -> UnifiedReportScreen(
      viewModel = viewModel,
      selectedCountIds = selectedReportIds,
      onNavigateBack = { currentScreen = "reports" }
  )
  ```
  `currentScreen` state, `selectedReportIds` and the `"reports"` tab all live (008).
- `app/src/main/java/com/moneycounter/domain/SavedCount.kt` (from plan 006) — the merge
  input; `items: List<SavedCountItem>`, `products: List<SavedProductItem>`,
  `targetAmount: BigDecimal`, `currency: String` (SYMBOL `$`/`US$`),
  `currencyId: String` (e.g. `"cup"`/`"usd"`). Note there is NO code (3-letter) field on
  `SavedCount` — the code lives in `uiState.currencies` (id → `Currency.code`).
- `app/src/main/java/com/moneycounter/domain/SavedCountItem.kt` (14 lines) —
  `SavedCountItem(denominationValue: Long, quantity: Long, subtotal: BigDecimal)`.
- `app/src/main/java/com/moneycounter/domain/SavedProductItem.kt` (17 lines) —
  `SavedProductItem(name, unit, quantity: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal, subtotal: BigDecimal)`.
- `app/src/main/java/com/moneycounter/domain/Money.kt` — `Money.SCALE = 2`, `Money.ZERO`,
  `Money.fromLong(value)` (BigDecimal at SCALE 2). All merged money stays `BigDecimal` at
  `Money.SCALE`.
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` — the 008
  placeholder (this plan replaces only its body):
  ```kotlin
  @Composable
  fun UnifiedReportScreen(
      viewModel: MoneyCounterViewModel,
      selectedCountIds: List<String>,
      onNavigateBack: () -> Unit
  ) {
      // Placeholder; full unified report screen lands in plan 009.
  }
  ```
- `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt` (341 lines) — the
  layout this screen mirrors: summary `Card` (101–124), PRODUCTOS header row (126–164),
  DENOMINACIONES header row (166–198), `ProductLine` (309–341), `ItemLine` (273–306),
  `DetailRow` (250–271), export-button pattern (216–242). `formatMoneyBigDecimal(value, symbol)`
  (`ui/components/DenominationRow.kt:166`) EMBEDS the symbol — never prefix `$` manually.
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt` (265 lines):
  - `fun export(saved: SavedCount)` (line 53) → `buildPdf(saved)` (63–171) → private `share(file)`
    (252–264, `Intent.ACTION_SEND`, `application/pdf`, FileProvider, chooser "Exportar reporte").
  - Paint objects (title/header/body/label/line, lines 24–51) are private `val`s shared by
    builders; page setup 595×842, margin 48, `newPageIfNeeded(needed)` helper (74–83), page
    break pattern at line 116. PRODUCTOS section 110–130; DENOMINACIONES 132–156.
  - Filename scheme: `File(context.cacheDir, "reporte_$stamp.pdf")` where
    `stamp = dd-MM-yyyy_HH-mm-ss` (160–162); stock variant uses `existencias_` (plan 005).
  - `formatMoney` / `formatMoneyBigDecimal` are already imported (lines 14–15).
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` (153 lines):
  - `fun export(saved: SavedCount)` (line 19) → `buildCsv(saved)` (29–85) → private `share(file)`
    (140–152, `text/csv`, UTF-8 BOM writing at buildCsv 81–82). `csvRow`/`escapeCsvField`
    (129–138). Row patterns 32–72. Stock variant filenames `existencias_` (plan 005).
- Unit-test pattern: `app/src/test/java/com/moneycounter/domain/` — JUnit4, backtick names,
  `java.math.BigDecimal` + `com.moneycounter.domain.Money`. See `ProductSelectionTest.kt` (38 lines).

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat 8f77ad2..HEAD -- <paths in drift block>` | expected deltas only   |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/domain/ReportAggregation.kt` (create — pure helper)
- `app/src/test/java/com/moneycounter/domain/ReportAggregationTest.kt` (create)
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt` (ADD `exportUnited` +
  `buildUnitedPdf`; do not touch anything else)
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` (ADD `exportUnited` +
  `buildUnitedCsv`; do not touch anything else)
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` (replace the
  placeholder body; signature stays exactly as in 008)

**Out of scope** (do NOT touch, even though they look related):
- `MainActivity.kt` — the `"summary"` wiring already exists from 008; nothing to change.
- The existing `export(SavedCount)`, `buildPdf(SavedCount)`, `buildCsv(SavedCount)`,
  `exportStockReport`, `buildStockPdf`, `buildStockCsv`, and `share` implementations —
  keep them byte-for-byte behaviorally identical (this includes the `reporte_` /
  `existencias_` filenames).
- `MoneyCounterViewModel.kt`, `HistoryScreen`, `HistoryDetailScreen`, `ReportsScreen`,
  `SavedCount`/`SavedProductItem` models, repositories — untouched.
- Grouping/selection state (008) — not reworked here.
- No new dependencies.

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo. Example:
  `Add unified report aggregation with PDF and CSV export`. Do NOT push or open a PR.

## Steps

### Step 1: `domain/ReportAggregation.kt` (pure) + `ReportAggregationTest.kt`

Create `app/src/main/java/com/moneycounter/domain/ReportAggregation.kt`. Pure JVM Kotlin —
imports only `com.moneycounter.domain.*` data classes and `java.math.BigDecimal`.

```kotlin
package com.moneycounter.domain

import java.math.BigDecimal

data class UnitedDenomination(
    val denominationValue: Long,
    val quantity: Long,
    val subtotal: BigDecimal
)

data class UnitedProduct(
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal,
    val subtotal: BigDecimal
)

data class UnitedCount(
    val currencyId: String,
    val currencySymbol: String,
    val currencyCode: String,
    val targetAmountTotal: BigDecimal,
    val items: List<UnitedDenomination>,
    val products: List<UnitedProduct>,
    val count: Int
) {
    fun total(): BigDecimal = targetAmountTotal
}
```

**Signature note (deliberate, documented)**: the requested `uniteCounts(counts)` cannot
derive the 3-letter code — `SavedCount` carries only `currencyId` + symbol, and the code
lives in `uiState.currencies`. So the function takes the code as a SECOND parameter with an
empty default, keeping tests callable with one arg while the UI passes the resolved code:

```kotlin
fun uniteCounts(counts: List<SavedCount>, currencyCode: String = ""): UnitedCount {
    if (counts.isEmpty()) throw IllegalArgumentException("La lista de ventas está vacía")
    val ids = counts.map { it.currencyId }.distinct()
    if (ids.size != 1) throw IllegalArgumentException(
        "Las ventas seleccionadas son de monedas distintas (${ids.joinToString(", ")})"
    )

    val first = counts.first()
    val total = counts.fold(Money.ZERO) { acc, c -> acc.add(c.targetAmount) }

    val denominations = counts
        .flatMap { it.items }
        .groupBy { it.denominationValue }
        .map { (value, lines) ->
            val qty = lines.sumOf { it.quantity }
            UnitedDenomination(value, qty, Money.fromLong(value * qty))
        }
        .sortedByDescending { it.denominationValue }

    val products = counts
        .flatMap { it.products }
        .groupBy { it.name to it.unit }
        .map { (key, lines) ->
            val firstLine = lines.first()
            val effective = firstLine.unitPrice.add(firstLine.surcharge)
            val qty = lines.fold(Money.ZERO) { acc, line -> acc.add(line.quantity) }
            UnitedProduct(
                name = key.first,
                unit = key.second,
                quantity = qty,
                unitPrice = firstLine.unitPrice,
                surcharge = firstLine.surcharge,
                subtotal = effective.multiply(qty).setScale(Money.SCALE)
            )
        }
        .sortedWith(compareBy({ it.name }, { it.unit }))

    return UnitedCount(
        currencyId = ids.first(),
        currencySymbol = first.currency,
        currencyCode = currencyCode,
        targetAmountTotal = total.setScale(Money.SCALE),
        items = denominations,
        products = products,
        count = counts.size
    )
}
```

Merging policy (documented, matches the spec):
- Denominations merge by `denominationValue`; quantity sums; subtotal recomputed
  `value × quantity` (mirrors `saveCount()`'s `Money.fromLong(den.value * qty)`,
  `MoneyCounterViewModel.kt:463`).
- Products merge by `name + unit`; quantity sums; subtotal recomputed
  `(unitPrice + surcharge) × totalQuantity` — the FIRST-seen price/surcharge wins. Different
  units keep separate rows even for the same name. If the same name+unit ever appears with
  different prices across counts, the first-seen price produces the merged subtotal
  (acceptable: product pricing is stable per name; noted for the reviewer).
- Outputs are deterministic: denominations value-descending, products sorted by name+unit.
- `total()` == `targetAmountTotal` == sum of each count's `targetAmount` (scale `Money.SCALE`).

Create `app/src/test/java/com/moneycounter/domain/ReportAggregationTest.kt` (JUnit4,
backtick names, mirror `ProductSelectionTest.kt`). Build fixtures with a local helper that
constructs `SavedCount`s (id, savedAt, targetAmount, items, currency, products, currencyId).
Cover:

1. **Mixing currencies throws**: two counts (`currencyId` `"cup"` and `"usd"`) →
   `assertThrows(IllegalArgumentException::class.java) { uniteCounts(list, "CUP") }`.
2. **Empty list throws**: `uniteCounts(emptyList())` throws `IllegalArgumentException`.
3. **Denominations sum by value**: two CUP counts both containing value 100 (qty 3 and
   qty 2) → one `UnitedDenomination(100, 5, 500.00)`. A value present in only one count
   stays, quantity preserved.
4. **Same product name+unit merges across counts**: same name `"Arroz"`, unit `"Lb"`,
   unitPrice `"500"`, surcharge `"0"`, qty `1.5` + `2.5` → one row, quantity `4.00`,
   subtotal `2000.00` (4.00 × 500).
5. **Different units stay separate**: `"Arroz" "Lb"` and `"Arroz" "Kg"` → two `UnitedProduct`
   rows.
6. **Totals match**: `united.total()` == `targetAmountTotal` ==
   `counts.sumOf { it.targetAmount }` (scale 2); `united.count == counts.size`.
7. **Same currencyId, different symbols still merge** (symbol is cosmetic): two counts with
   `currencyId = "cup"`, currencies `"$"` and `"$"` → no throw; `united.currencySymbol`
   equals the FIRST count's symbol (documents first-wins for display).

Use `assertThrows` from `org.junit.Assert` (JUnit 4.13 ships it).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew test --console=plain` → exit 0 with the new tests green.

### Step 2: `PdfExporter.exportUnited` (ADDITIVE)

In `PdfExporter.kt` add, next to the existing `export`/`exportStockReport`:

```kotlin
fun exportUnited(count: UnitedCount, currencySymbol: String) {
    val file = buildUnitedPdf(count, currencySymbol)
    share(file)
}
```

Add a private `buildUnitedPdf(count: UnitedCount, currencySymbol: String): File` modeled
byte-for-byte on `buildPdf`'s skeleton (paints, pageWidth 595, pageHeight 842, margin 48,
`var y = 80f`, `newPageIfNeeded(needed)` — copy the exact helper from lines 74–83).
Content, in order:

- Title `"Reporte de conteo unificado"` (titlePaint), `y += 30f`.
- `"Fecha: <dd/MM/yyyy HH:mm>"` of `System.currentTimeMillis()` (headerPaint), `y += 24f`.
- `"Moneda: $currencySymbol (${count.currencyCode})"` (headerPaint), `y += 20f`.
- `"Nº de ventas: ${count.count}"` (headerPaint), `y += 20f`.
- `"TOTAL GENERAL: ${formatMoneyBigDecimal(count.total(), currencySymbol)}"` (headerPaint),
  `y += 36f`.
- PRODUCTOS section, only if `count.products.isNotEmpty()` — copy the exact section shape
  from `buildPdf:111-130`: `"PRODUCTOS"` header, separator `canvas.drawLine` pairs,
  column labels `PRODUCTO`/`CANT.`/`TOTAL` at margin/240f/380f (labelPaint), then one row
  per product with `newPageIfNeeded(20f)` before each: name | `"${quantity.stripTrailingZeros().toPlainString()} ${unit}"`
  | `formatMoneyBigDecimal(product.subtotal, currencySymbol)` (bodyPaint). `y += 16f` after.
- DENOMINACIONES section — copy `buildPdf:132-156`: header, separator, labels
  `DENOMINACIÓN`/`CANTIDAD`/`TOTAL`, then if `count.items.isEmpty()` print
  `"No hay denominaciones registradas."` else one row per denomination with
  `newPageIfNeeded(20f)`: `formatMoney(value, currencySymbol)` |
  `quantity.toString()` | `formatMoneyBigDecimal(subtotal, currencySymbol)`.
- `document.finishPage(page)`; then the write block copied from `buildPdf:159-170` with the
  stamp from `System.currentTimeMillis()` and filename `resumen_$stamp.pdf`:
  ```kotlin
  val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
      .format(Date(System.currentTimeMillis()))
  val file = File(context.cacheDir, "resumen_$stamp.pdf")
  ```
- `share(file)` is untouched and shared.

Imports to add: `com.moneycounter.domain.UnitedCount`. (`formatMoney`/`formatMoneyBigDecimal`
already imported.)

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`git grep -n "resumen_" app/src/main/java/com/moneycounter/util/PdfExporter.kt` → 1 hit.

### Step 3: `ExcelExporter.exportUnited` (ADDITIVE)

In `ExcelExporter.kt` add:

```kotlin
fun exportUnited(count: UnitedCount, currencySymbol: String) {
    val file = buildUnitedCsv(count, currencySymbol)
    share(file)
}
```

Add a private `buildUnitedCsv(count: UnitedCount, currencySymbol: String): File` modeled on
`buildCsv` (reuse `csvRow`/`escapeCsvField`/`share` and the UTF-8 BOM write from
`buildCsv:79-83`). Content, in order:

- `csvRow("Reporte de conteo unificado")`
- `csvRow("Fecha", <dd/MM/yyyy HH:mm of now>)`
- `csvRow("Moneda", "${currencySymbol} (${count.currencyCode})")`
- `csvRow("Nº de ventas", count.count.toString())`
- `csvRow("TOTAL GENERAL", formatMoneyBigDecimal(count.total(), currencySymbol))`
- blank line
- `csvRow("PRODUCTOS")` + `csvRow("Producto", "Unidad", "Cantidad", "Precio unitario", "Recargo", "Subtotal")`
- per `UnitedProduct` (skipping not needed — merged rows are all non-empty):
  `csvRow(prod.name, prod.unit, prod.quantity.stripTrailingZeros().toPlainString(),
  formatMoneyBigDecimal(prod.unitPrice, currencySymbol),
  if (prod.surcharge.signum() > 0) formatMoneyBigDecimal(prod.surcharge, currencySymbol) else "",
  formatMoneyBigDecimal(prod.subtotal, currencySymbol))`
- blank line
- `csvRow("DENOMINACIONES")` + `csvRow("Denominación", "Cantidad", "Total")`
- if `count.items.isEmpty()`: `csvRow("No hay denominaciones registradas")`; else per item:
  `csvRow(formatMoney(item.denominationValue, currencySymbol), item.quantity.toString(),
  formatMoneyBigDecimal(item.subtotal, currencySymbol))`
- Write with BOM; filename `resumen_$stamp.csv`, stamp from `System.currentTimeMillis()`:
  ```kotlin
  val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
      .format(Date(System.currentTimeMillis()))
  val file = File(context.cacheDir, "resumen_$stamp.csv")
  ```

Import to add: `com.moneycounter.domain.UnitedCount`. (`formatMoney`/`formatMoneyBigDecimal`
already imported.)

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`git grep -n "resumen_" app/src/main/java/com/moneycounter/util/ExcelExporter.kt` → 1 hit.

### Step 4: Replace the `UnifiedReportScreen.kt` placeholder body

Keep the EXACT 008 signature. Replace the placeholder body with the full screen:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedReportScreen(
    viewModel: MoneyCounterViewModel,
    selectedCountIds: List<String>,
    onNavigateBack: () -> Unit
)
```

Structure (mirror `HistoryDetailScreen.kt`):
- `val uiState by viewModel.uiState.collectAsState()`; `val context = LocalContext.current`.
- `val selected = uiState.history.filter { it.id in selectedCountIds.toSet() }` (keep the
  `history` list order — new-first).
- Resolve the code from the first selected count's currency:
  `val countryCode = uiState.currencies.firstOrNull { it.id == selected.firstOrNull()?.currencyId }?.code.orEmpty()`.
- Merge with a catch:
  ```kotlin
  val united = remember(selected, uiState.currencies) {
      runCatching { uniteCounts(selected, countryCode) }
  }
  ```
- Scaffold + TopAppBar `"Resumen unificado"` with back arrow (copy
  `HistoryDetailScreen.kt:56-73`).
- Empty/failure handling instead of the body when
  `selected.isEmpty()` → centered `"No hay registros seleccionados."`, and when
  `united.isFailure` → centered `"Los registros seleccionados son de monedas distintas. Selecciona ventas de una misma moneda."`
  (the UI normally prevents this; the message is the safety net for the `IllegalArgumentException`).
- On success (`val u = united.getOrThrow()`), a `LazyColumn` (copy the `HistoryDetailScreen`
  pattern with `verticalArrangement = Arrangement.spacedBy(12.dp)`, horizontal padding 16):
  - **Summary card** (surfaceVariant, padding 16 — copy 101–124), with `DetailRow` lines
    (copy the private `DetailRow` composable, 250–271):
    - `"MONEDA"` → `"${u.currencyCode} (${u.currencySymbol})"`
    - `"VENTAS"` → `"${u.count}"`
    - `"TOTAL GENERAL"` → `formatMoneyBigDecimal(u.total(), u.currencySymbol)` (emphasize)
  - **PRODUCTOS section** ONLY if `u.products.isNotEmpty()` — copy the section header row
    (126–164: `PRODUCTO`/`CANTIDAD`/`TOTAL` labels, SpaceBetween) and render one
    `ProductLine`-style card per product (`ProductLine` is private in HistoryDetailScreen →
    copy a local `private fun UnitedProductLine(item: UnitedProduct, symbol: String)` modeled
    on `HistoryDetailScreen.kt:309-341`: name | `"${quantity.toPlainString()} ${unit}"` |
    `formatMoneyBigDecimal(item.subtotal, symbol)`).
  - **DENOMINACIONES section header** (166–198), then `ItemLine`-style cards per
    denomination (local `private fun UnitedItemLine(item: UnitedDenomination, symbol: String)`
    modeled on 273–306: `formatMoney(value, symbol)` | `"x ${quantity}"` | subtotal) or the
    empty text `"No hay denominaciones registradas"` when `u.items.isEmpty()`.
  - **Format chooser** (spec: button `"Exportar ▾"` + `DropdownMenu` with `PDF` and
    `Excel (CSV)`):
    ```kotlin
    var exportMenuOpen by remember { mutableStateOf(false) }
    item {
        Box(modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { exportMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Exportar ▾")
            }
            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                DropdownMenuItem(text = { Text("PDF") }, onClick = {
                    exportMenuOpen = false
                    PdfExporter(context).exportUnited(u, u.currencySymbol)
                })
                DropdownMenuItem(text = { Text("Excel (CSV)") }, onClick = {
                    exportMenuOpen = false
                    ExcelExporter(context).exportUnited(u, u.currencySymbol)
                })
            }
        }
    }
    ```
  - `item { Spacer(Modifier.height(16.dp)) }`.

New imports needed: `androidx.compose.foundation.layout.Box`, `DropdownMenu`/`DropdownMenuItem`,
`Icons.Default.Share`, `com.moneycounter.domain.UnitedDenomination`, `com.moneycounter.domain.UnitedProduct`,
`com.moneycounter.util.PdfExporter`/`ExcelExporter`, `java.math.BigDecimal` (for
`quantity.toPlainString()` typing — `UnitedProduct.quantity` is BigDecimal).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 5: Full verification

Run all three commands from the Commands table plus the Done-criteria greps. Manually smoke
the export filenames via the greps confirming `resumen_` appears in both exporters AND that
the existing `reporte_`/`existencias_` filenames are untouched.

**Verify**:
`./gradlew test --console=plain` → exit 0, no regressions.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

`ReportAggregationTest.kt` (Step 1) is the only new test file; its seven cases pin the
merge math, the same-currency guard, the first-wins symbol policy, and the totals invariant.
The exporters are Android-bound (`Context`/`FileProvider`) and stay untested locally, exactly
like the existing exports — regression safety is the ADDITIVE diff pattern (existing method
bodies frozen) plus the `resumen_`/`reporte_`/`existencias_` filename greps in Done criteria.

Manual acceptance (operator, on device): Reportes → select 2 completed CUP counts (one with
denominations+same product, one partial) → GENERAR RESUMEN → summary card shows code/count/
total; Exportar ▾ → PDF shares `resumen_*.pdf`; Exportar ▾ → Excel (CSV) shares
`resumen_*.csv`; opening either shows merged PRODUCTOS and DENOMINACIONES with the summed
totals. Selecting counts of different currencies (should be impossible via the filter) must
surface the friendly error text instead of crashing.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (ReportAggregationTest included)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "data class UnitedCount\|data class UnitedDenomination\|data class UnitedProduct\|fun uniteCounts" app/src/main/java/com/moneycounter/domain/ReportAggregation.kt` finds them all, and the file has NO Android imports
- [ ] `app/src/test/java/com/moneycounter/domain/ReportAggregationTest.kt` exists with the 7 cases (two `assertThrows`, denominations-sum, product-merge, unit-separation, totals, first-wins symbol)
- [ ] `git grep -n "fun exportUnited\|fun buildUnitedPdf" app/src/main/java/com/moneycounter/util/PdfExporter.kt` → both defs
- [ ] `git grep -n "fun exportUnited\|fun buildUnitedCsv" app/src/main/java/com/moneycounter/util/ExcelExporter.kt` → both defs
- [ ] `git grep -n "resumen_" app/src/main/java/com/moneycounter/util/` → exactly 2 hits (one per exporter)
- [ ] `git grep -n "reporte_\|existencias_" app/src/main/java/com/moneycounter/util/` → the pre-existing `reporte_` (2) + `existencias_` (2) filenames still present — existing builders untouched
- [ ] `git grep -n "fun export(saved: SavedCount)" app/src/main/java/com/moneycounter/util/` → still 2 hits (existing exports intact)
- [ ] `git grep -n "UnifiedReportScreen(" app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` → the 008 signature unchanged (viewModel, selectedCountIds, onNavigateBack)
- [ ] `git grep -n "Resumen unificado\|TOTAL GENERAL\|Exportar" app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` → title, summary rows, format chooser present
- [ ] `git grep -n "uniteCounts" app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` → merge call inside a `runCatching`
- [ ] `git grep -n "Registro\|monedas distintas" app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` → Spanish empty/error states ("No hay registros seleccionados." / "…son de monedas distintas…") present
- [ ] `MainActivity.kt` is NOT modified by this plan (`git status`)
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 009 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Plan 008 is NOT applied: there is no `"reports"`/`"summary"` wiring, or
  `UnifiedReportScreen.kt` does not exist with the 3-arg signature.
- `SavedCount` has no `currencyId` (plan 006 missing) — the same-currency guard cannot work.
- Any in-scope file no longer matches the "Current state" excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- Making `exportUnited` work requires CHANGING an existing `export`/`build*` method or its
  filename scheme — the plan forbids it.
- You find yourself editing the ViewModel, repositories, `MainActivity.kt`, `ReportsScreen`,
  `History*` screens, or the domain models.
- Merging a same-name product requires price/surcharge resolution beyond first-seen-wins
  (e.g. the executor decides weighted averages are needed) — stop and report the policy
  question rather than improvising a different merge rule.
- Adding the screen body breaks `./gradlew test` (the plan adds no test changes outside
  `ReportAggregationTest.kt`; a red suite is a STOP).

## Maintenance notes

- `resumen_` filenames are deliberately distinct from `reporte_` (single count) and
  `existencias_` (stock) so the cache dir never collides.
- `uniteCounts(counts, currencyCode = "")` exists because `SavedCount` carries the symbol
  and id but NOT the 3-letter code; the code is resolved in the screen from
  `uiState.currencies`. If a future schema adds `code` to `SavedCount`, collapse the
  parameter then (and keep the tests updated).
- First-seen price/surcharge wins when merging products. User-added currencies re-pricing a
  product between saves would surface as: merged row shows the first saved price with the
  summed quantity. Reviewer should accept this or raise a follow-up requirement for
  price-aware splitting.
- `UnitedCount.total()` currently just returns `targetAmountTotal`; it is a named method so
  the exporters/screen read intent at call sites. Do not remove it even though it looks
  trivial.
- Reviewer should check: the ADDITIVE diff of both exporters (existing method bodies
  byte-identical; only new `exportUnited`/`buildUnited*` added), that denomination subtotals
  use `Money.fromLong(value * qty)` scale 2, that product subtotal uses effective price
  (price+surcharge) × summed quantity, and that the screen's `runCatching` shows the Spanish
  message instead of crashing on a mixed-currency selection.