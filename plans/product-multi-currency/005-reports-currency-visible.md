# Plan 005: Reports currency visible across all report screens

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/product-multi-currency/README.md`.
>
> **Worktree**: Execute in an isolated worktree from `feature/product-multi-currency` (after 001 landed). Do NOT work on `main`.
>
> **Drift check (run first)**: `git diff --stat HEAD..origin/feature/product-multi-currency -- app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt`
> If any in-scope file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (independent of 001–004)
- **Category**: feature
- **Planned at**: commit `e8665ca`, 2026-09-07

## Why this matters

The user wants the currency to be clearly visible on every report screen — not just a subtle filter dropdown but a prominent label showing code+symbol. This plan adds currency badges/labels to ReportsScreen (the report list), HistoryDetailScreen, and UnifiedReportScreen.

## Current state

### Key files

| File | Role |
|------|------|
| `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` | Report list — has `ReportsCurrencySelector` (line 454–484) showing "$ CUP" in a 96dp button; history filtered by `filterCurrencyId` |
| `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt` | Single count detail — shows `saved.currency` as symbol in money formatting; no explicit currency code label |
| `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` | Unified summary — stat card label already shows `"${u.currencyCode} (${u.currencySymbol})"` (line 121) |

### ReportsScreen current (line 72–81)

```kotlin
var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
val filtered = uiState.history.filter { it.currencyId == filterCurrencyId }
```

The filter selector is a small 96dp button showing "$ CUP" — not very prominent.

### HistoryDetailScreen

Uses `saved.currency` (the symbol) for formatting. Shows total, products, denominations — all formatted with `saved.currency` symbol. No explicit "Moneda: CUP ($)" label.

### UnifiedReportScreen stat card (line 119–125)

```kotlin
LuisoStatCard(
    label = "${u.currencyCode} (${u.currencySymbol})",
    value = formatMoneyBigDecimal(u.total(), u.currencySymbol),
    valueColor = MaterialTheme.colorScheme.primary
)
```

Already shows the currency code in the stat card label — acceptable but could be more explicit.

### Conventions to follow

- `LuisoSectionHeader(text, accent, modifier)` for section labels
- `LuisoStatCard(label, value, valueColor)` for highlight stats
- `LuisoCard(modifier)` for content cards
- `DetailRow(label, value, emphasize)` is a private composable already defined in both `StockReportScreen.kt` and `UnifiedReportScreen.kt` — reuse the pattern
- `formatMoneyBigDecimal(x, symbol)` — never prefix `$` manually

## Commands you will need

| Purpose   | Command                              | Expected on success |
|-----------|--------------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin`       | exit 0              |
| Tests     | `./gradlew test`                     | all pass            |
| Full build| `./gradlew assembleDebug`            | exit 0              |

## Scope

**In scope** (modify these files):
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt`

**Out of scope** (do NOT touch):
- Product.kt / JsonProductRepository / ViewModel — other plans
- StockScreen / StockReportScreen / exporters — other plans
- MoneyCounterScreen — plan 002

## Git workflow

- Branch: `exec/005-reports-currency` (worktree, from `feature/product-multi-currency`)
- Commit: `feat(reports): visible currency labels on all report screens`

## Steps

### Step 1: ReportsScreen — prominent currency banner

In `ReportsScreen.kt`, add a visible currency banner at the top of the list (after the filter row, before the grouped list). After the `item` block containing the filter buttons (lines 133–157), add:

```kotlin
item {
    val currencyLabel = filterCurrency?.let { "${it.symbol} (${it.code}) — ${it.name}" } ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LuisoSectionHeader(
            text = "REPORTES EN $currencyLabel",
            modifier = Modifier.weight(1f)
        )
    }
}
```

Also widen the `ReportsCurrencySelector` from 96dp to 110dp for better readability:

```kotlin
modifier = Modifier.width(110.dp)
```

### Step 2: HistoryDetailScreen — add "Moneda" detail row

In `HistoryDetailScreen.kt`, find the section that displays the total (usually near the top of the detail content). Add a "MONEDA" DetailRow after the total:

```kotlin
item {
    DetailRow(
        "MONEDA",
        "${saved.currency} (${uiState.currencies.firstOrNull { it.id == saved.currencyId }?.code ?: saved.currencyId})"
    )
}
```

If the `DetailRow` private composable is not in `HistoryDetailScreen.kt`, add it (copy from `StockReportScreen.kt` lines 171–191):

```kotlin
@Composable
private fun DetailRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
```

**Verify**: `./gradlew compileDebugKotlin` after step 2.

### Step 3: UnifiedReportScreen — add explicit "MONEDA" detail row

In `UnifiedReportScreen.kt`, the stat card (line 119) already shows the currency. Add an explicit "MONEDA" DetailRow in the summary card below the stat card:

After the `LuisoStatCard` item (line 119–125), add:

```kotlin
item {
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        DetailRow(
            "MONEDA",
            "${u.currencySymbol} (${u.currencyCode})"
        )
    }
}
```

The `DetailRow` composable already exists in `UnifiedReportScreen.kt` (line 251).

### Step 4: Verify all changes

**Verify**: `./gradlew compileDebugKotlin` → exit 0.
**Verify**: `./gradlew test` → all tests pass.
**Verify**: `./gradlew assembleDebug` → full build success.

## Test plan

- No new unit tests (UI-only label additions)
- Verify existing tests pass

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `ReportsScreen.kt` contains "REPORTES EN" string (visible currency banner)
- [ ] `HistoryDetailScreen.kt` contains "MONEDA" string (detail row)
- [ ] `UnifiedReportScreen.kt` contains "MONEDA" string (detail row)
- [ ] No files outside the in-scope list are modified

## STOP conditions

- The code at the locations in "Current state" doesn't match the excerpts (drifted).
- A step's verification fails twice after a reasonable fix attempt.
- `./gradlew assembleDebug` fails after all steps.
- `ReportsCurrencySelector` is a private composable in `ReportsScreen.kt` — if it can't be accessed to widen, widen via its call site modifier instead.

## Maintenance notes

- The currency label is derived from `uiState.currencies` and `filterCurrencyId`/`saved.currencyId`/`united.currencyCode`. If currencies are deleted, the label falls back to the symbol stored in the saved count.
- The `ReportsScreen` banner uses `LuisoSectionHeader` which supports `accent` — the current month is already accented. Keep consistent.
- If the user wants to export a report, the export PDF/CSV headers already include "Moneda: ..." (handled in 001/004). This plan focuses on on-screen visibility only.
