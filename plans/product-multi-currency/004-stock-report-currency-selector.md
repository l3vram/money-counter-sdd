# Plan 004: Stock report currency selector + per-currency export labels

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/product-multi-currency/README.md`.
>
> **Worktree**: Execute in an isolated worktree from `feature/product-multi-currency` (after 001 landed). Do NOT work on `main`.
>
> **Drift check (run first)**: `git diff --stat HEAD..origin/feature/product-multi-currency -- app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt app/src/main/java/com/moneycounter/util/PdfExporter.kt app/src/main/java/com/moneycounter/util/ExcelExporter.kt`
> If any in-scope file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: 001
- **Category**: feature
- **Planned at**: commit `e8665ca`, 2026-09-07

## Why this matters

With multi-currency products, the stock report (Existencias) total value is ambiguous — it depends on which currency's prices you use. This plan adds a **currency selector** at the top of the Existencias screen, prominently labeled, with the total computed in that currency. Exported PDF/CSV also show "Moneda: US$ (USD)" with code+symbol.

## Current state (post-001)

### Key files

| File | Role |
|------|------|
| `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` | Existencias — `totalValue` uses per-currency via `stockValueFor` (post-001), `StockLine` per-currency (post-001) |
| `app/src/main/java/com/moneycounter/util/PdfExporter.kt` | PDF — `exportStockReport(products, currencyId, currencySymbol, currencyCode, generatedAt)` (post-001 signature) |
| `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` | CSV — same signature (post-001) |
| `app/src/main/java/com/moneycounter/ui/components/Components.kt` | Kit: `LuisoButton`, `LuisoCard`, `LuisoSectionHeader`, `LuisoTopBar` |
| `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` | `formatMoneyBigDecimal(x, symbol)` |

### StockReportScreen post-001

- `totalValue` = fold of `stockValueFor(selectedCurrencyId)` across inStock products
- `StockLine(product, selectedCurrencyId, symbol)` — renders per-currency unit price and stock value
- Export buttons pass `(uiState.selectedCurrencyId, curSymbol, curCode, ...)`
- No visible currency selector — implicitly uses `uiState.selectedCurrencyId`

### StockReportScreen currency selector design

Add a currency dropdown (same pattern as `ReportsScreen.kt`'s `ReportsCurrencySelector`) at the top of the screen. The selected currency for the report becomes a **local state** (not the global `selectedCurrencyId` from the counter), so the user can view the report in any currency without affecting the counter.

Pattern from `ReportsScreen.kt` (line 454–484):

```kotlin
@Composable
private fun ReportsCurrencySelector(
    currencies: List<Currency>,
    selectedCurrencyId: String,
    onSelectCurrency: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = currencies.firstOrNull { it.id == selectedCurrencyId }
    Box {
        LuisoOutlineButton(
            text = selected?.let { "${it.symbol} ${it.code}" } ?: "—",
            onClick = { expanded = true },
            modifier = Modifier.width(96.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                    onClick = { onSelectCurrency(currency.id); expanded = false }
                )
            }
        }
    }
}
```

### Conventions to follow

- `formatMoneyBigDecimal(x, symbol)` — never prefix `$` manually
- Currency selector pattern: `ReportsCurrencySelector` from `ReportsScreen.kt`
- PdfExporter title paint: `Color.parseColor("#1B6B3A")`
- PdfExporter label paint: `Color.parseColor("#666666")`, textSize 11f

## Commands you will need

| Purpose   | Command                              | Expected on success |
|-----------|--------------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin`       | exit 0              |
| Tests     | `./gradlew test`                     | all pass            |
| Full build| `./gradlew assembleDebug`            | exit 0              |

## Scope

**In scope** (modify these files):
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt`
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt`

**Out of scope** (do NOT touch):
- MoneyCounterScreen / StockScreen — other plans
- ReportsScreen / HistoryDetailScreen / UnifiedReportScreen — plan 005
- Product.kt / JsonProductRepository — done in 001

## Git workflow

- Branch: `exec/004-stock-report` (worktree, from `feature/product-multi-currency`)
- Commit: `feat(reports): stock report currency selector + visible labels`

## Steps

### Step 1: Add local currency state to StockReportScreen

In `StockReportScreen.kt`, add a local `reportCurrencyId` state that defaults to `uiState.selectedCurrencyId`:

```kotlin
@Composable
fun StockReportScreen(viewModel: MoneyCounterViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var reportCurrencyId by remember { mutableStateOf(uiState.selectedCurrencyId) }
    val reportCurrency = uiState.currencies.firstOrNull { it.id == reportCurrencyId }
    val currencySymbol = reportCurrency?.symbol ?: "$"
    val currencyCode = reportCurrency?.code ?: ""

    val inStock = uiState.products.filter { it.stock.signum() != 0 }
    val totalValue = inStock.fold(Money.ZERO) { acc, product ->
        acc.add(product.stockValueFor(reportCurrencyId) ?: Money.ZERO)
    }
    // ...
```

### Step 2: Add currency selector at top of Scaffold

Inside the `Scaffold` content (before the LazyColumn), add a Row with the currency selector and the screen title. Actually the `LuisoTopBar` is the top bar; add a currency selector Row just inside the LazyColumn as the first item:

In the `LazyColumn`, after `item { Spacer(modifier = Modifier.height(2.dp)) }`, add:

```kotlin
item {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Moneda:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        StockReportCurrencySelector(
            currencies = uiState.currencies,
            selectedCurrencyId = reportCurrencyId,
            onSelectCurrency = { reportCurrencyId = it }
        )
    }
}
```

Add the `StockReportCurrencySelector` composable (same pattern as `ReportsCurrencySelector`):

```kotlin
@Composable
private fun StockReportCurrencySelector(
    currencies: List<Currency>,
    selectedCurrencyId: String,
    onSelectCurrency: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = currencies.firstOrNull { it.id == selectedCurrencyId }

    Box {
        LuisoOutlineButton(
            text = selected?.let { "${it.symbol} ${it.code}" } ?: "—",
            onClick = { expanded = true },
            modifier = Modifier.width(120.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                    onClick = {
                        onSelectCurrency(currency.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

Add the necessary imports:

```kotlin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.moneycounter.domain.Currency
import com.moneycounter.ui.components.LuisoOutlineButton
```

### Step 3: Update StockReportScreen summary card — visible currency label

In the summary card (line 75–88), update the "TOTAL EN EXISTENCIA" label to include the currency code:

```kotlin
item {
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            DetailRow(
                "TOTAL EN EXISTENCIA ($currencyCode)",
                formatMoneyBigDecimal(totalValue, currencySymbol),
                emphasize = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            DetailRow(
                "Productos con existencias",
                "${inStock.size}"
            )
        }
    }
}
```

### Step 4: Update StockLine — pass reportCurrencyId

The `StockLine` composable (line 194) already receives `selectedCurrencyId` and `symbol` (post-001). Update the call site (line 131) to pass `reportCurrencyId` instead of `uiState.selectedCurrencyId`:

```kotlin
items(inStock, key = { it.id }) { product ->
    StockLine(
        product = product,
        selectedCurrencyId = reportCurrencyId,
        symbol = currencySymbol
    )
}
```

### Step 5: Update export buttons — pass reportCurrencyId + currencyCode

The export buttons (lines 139, 154) currently use `uiState.selectedCurrencyId`. Update to use `reportCurrencyId` + `currencyCode`:

```kotlin
item {
    LuisoButton(
        text = "EXPORTAR PDF",
        onClick = {
            PdfExporter(context).exportStockReport(
                uiState.products, reportCurrencyId, currencySymbol, currencyCode, System.currentTimeMillis()
            )
        },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = Icons.Default.Share
    )
}

item {
    LuisoButton(
        text = "EXPORTAR EXCEL (CSV)",
        onClick = {
            ExcelExporter(context).exportStockReport(
                uiState.products, reportCurrencyId, currencySymbol, currencyCode, System.currentTimeMillis()
            )
        },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = Icons.Default.Share
    )
}
```

### Step 6: Update PdfExporter — visible "Moneda: SYMBOL (CODE)" label

In `PdfExporter.kt`, `buildStockPdf` (line 286), the "Moneda:" line (line 318) should already show `currencySymbol ($currencyCode)` after 001. Verify and ensure:

```kotlin
canvas.drawText("Moneda: $currencySymbol ($currencyCode)", margin, y, headerPaint)
```

If 001 didn't add the code, add it now.

### Step 7: Update ExcelExporter — visible "Moneda: SYMBOL (CODE)" label

In `ExcelExporter.kt`, `buildStockCsv` (line 150), the "Moneda" row (line 159) should already show `"$currencySymbol ($currencyCode)"` after 001. Verify and ensure:

```kotlin
lines += csvRow("Moneda", "$currencySymbol ($currencyCode)")
```

If 001 didn't add the code, add it now.

**Verify**: `./gradlew compileDebugKotlin` → exit 0.
**Verify**: `./gradlew test` → all tests pass.
**Verify**: `./gradlew assembleDebug` → full build success.

## Test plan

- No new unit tests (this is UI + export label changes)
- Verify existing tests pass
- Manual verification: build, open Existencias, switch currency selector → total recalculates, export PDF → shows "Moneda: US$ (USD)"

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `StockReportScreen.kt` contains a `reportCurrencyId` local state variable
- [ ] `StockReportScreen.kt` contains a `StockReportCurrencySelector` composable
- [ ] Summary card label includes currency code (e.g. "TOTAL EN EXISTENCIA (USD)")
- [ ] PDF export header includes "Moneda: SYMBOL (CODE)"
- [ ] CSV export includes "Moneda" row with "SYMBOL (CODE)"

## STOP conditions

- The code at the locations in "Current state" doesn't match the excerpts (drifted).
- A step's verification fails twice after a reasonable fix attempt.
- `./gradlew assembleDebug` fails after all steps.
- `DropdownMenu` imports conflict with existing ones — check and merge.

## Maintenance notes

- The stock report uses a **local** `reportCurrencyId` that does not affect the counter's global `selectedCurrencyId`. This is intentional — the user can preview existencias in any currency without changing the counter context.
- If the user has products with no price in the selected report currency, those products show "0" for value — honest behavior.
- Future: if a "stock report" tab is added to the bottom nav, this currency selector pattern is reusable.
