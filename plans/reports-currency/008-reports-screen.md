# Plan 008: Reportes — 3rd bottom-nav tab, ReportsScreen with month/day groups, checkbox selection, currency filter

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md` (create that
> README from the `plans/stock-screen/README.md` shape if the plans run has not created it
> yet).
>
> **Drift check (run first)**:
> `git diff --stat 8f77ad2..HEAD -- app/src/main/java/com/moneycounter/MainActivity.kt app/src/main/java/com/moneycounter/ui/screens/HistoryScreen.kt app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt app/src/main/java/com/moneycounter/domain/SavedCount.kt app/src/main/java/com/moneycounter/domain/ReportKeys.kt app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt`
> Two deltas are EXPECTED and REQUIRED here — they come from plans 006/007 and must be
> present before you start (if they are not, STOP):
> 1. `SavedCount` has a `currencyId` field (plan 006), so `uiState.history` rows know their
>    currency.
> 2. `MoneyCounterScreen` no longer takes `onNavigateToHistory` (plan 007), so the
>    `"counter"` branch in MainActivity is a 2-arg call and `HistoryScreen` is only reachable
>    through the old `"history"` branch.
> Any other drift vs. the excerpts below is a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED — rewires app navigation (adds a bottom tab, removes the `"history"` branch,
  retargets `"detail"`); new screen with group/selection state. No persistence changes, no
  ViewModel changes, no new dependencies.
- **Depends on**: plans/reports-currency/006-product-currency-model.md (adds
  `SavedCount.currencyId`, which the currency filter reads) and
  plans/reports-currency/007-product-currency-ui.md (removes the History top-bar icon and
  the `onNavigateToHistory` param this plan no longer passes)
- **Category**: feature (report browser + selection)
- **Planned at**: commit `8f77ad2`, 2026-09-06

## Why this matters

History is moving from an idle screen reached by a top-bar icon into a first-class
**Reportes** tab in the bottom navigation, matching Stock. The new screen groups saved
counts by month then by day (expand/collapse), lets the operator select whole days/months or
individual counts per currency, and feeds the selection into a unified report screen that
plan 009 fills in. Plan 008 ships all of that EXCEPT the unified merge/export; prediction:
- the `"summary"` branch wired in MainActivity gets a **minimal compiling placeholder** now
  and its real body in plan 009;
- `HistoryScreen.kt` becomes dead code (left on disk, no imports) rather than risking a
  big-file rewrite alongside the navigation change.

## Current state

- `app/src/main/java/com/moneycounter/MainActivity.kt` (113 lines, file as planned against;
  lines re-cited at commit `8f77ad2` — the 007 delta below is expected):
  - `var currentScreen by remember { mutableStateOf("counter") }` (line 49) and
    `var selectedHistoryId by remember { mutableStateOf<String?>(null) }` (line 50).
  - `val showBottomBar = currentScreen == "counter" || currentScreen == "stock"` (line 52).
  - One `NavigationBar` with `Contador` (`Icons.Filled.Paid`, selected when
    `currentScreen == "counter"`, lines 58–63) and `Stock` (`Icons.Filled.Inventory2`, lines
    64–69). Imports at lines 10–32; `Paid`/`Inventory2` come from
    `androidx.compose.material.icons.filled.*`.
  - `when (currentScreen)` over `"counter" / "stock" / "report" / "settings" / "history" /
    "detail"` (lines 79–110). The `"counter"` branch (80–84) currently passes
    `onNavigateToHistory = { currentScreen = "history" }` — AFTER plan 007 it is the 2-arg
    form:
    ```kotlin
    "counter" -> MoneyCounterScreen(
        viewModel = viewModel,
        onNavigateToSettings = { currentScreen = "settings" }
    )
    ```
    The `"history"` branch (97–104) reaches `HistoryScreen` and sets `selectedHistoryId` for
    `"detail"` (105–109, back to `"history"`).
  - `material-icons-extended` is already a dependency (`app/build.gradle.kts:94`), so
    `Icons.Filled.Receipt / ChevronRight / ExpandMore / Visibility` are available with no
    new deps.
- `app/src/main/java/com/moneycounter/ui/screens/HistoryScreen.kt` (182 lines) — the screen
  being replaced (left as dead code). top `TopAppBar` "Historial" + back arrow (lines
  57–75), empty state (77–92), `LazyColumn` (93–110) of `HistoryItemCard`s, delete
  `AlertDialog` (112–131). `formatDate(millis)` is a public top-level fun at line 180 used
  by `HistoryDetailScreen` — keep it.
- `app/src/main/java/com/moneycounter/domain/SavedCount.kt` — AFTER plan 006:
  ```kotlin
  data class SavedCount(
      val id: String,
      val savedAt: Long,
      val targetAmount: BigDecimal,
      val items: List<SavedCountItem>,
      val currency: String = "$",
      val products: List<SavedProductItem> = emptyList(),
      val currencyId: String = DefaultCurrencies.CUP.id
  )
  ```
  `currency` is the SYMBOL (`"$"` / `"US$"`); `currencyId` is the stable id (`"cup"` /
  `"usd"` / `"c1"` …). `uiState.history` is new-first (ViewModel `saveCount()` prepends,
  `MoneyCounterViewModel.kt:491`).
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` (700 lines) — the
  currency dropdown to copy is `CurrencySelector` (lines 476–510):
  `Box` + `OutlinedButton(width 96.dp, "selected.symbol selected.code")` + `DropdownMenu` of
  `"${currency.symbol} ${currency.code} — ${currency.name}"` items.
- `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt` (341 lines) — the
  detail screen keeps working from the `"detail"` branch; `ProductLine` (309–341),
  `ItemLine` (273–306), summary `Card` (101–124) and `DetailRow` (250–271) are the visual
  patterns the ReportsScreen rows mirror. `formatMoneyBigDecimal(value, symbol)` /
  `formatMoney(value, symbol)` are in `ui/components/DenominationRow.kt:162-174` — they
  EMBED the symbol, never prefix `$` manually.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` — `uiState`
  (`MoneyCounterUiState`, lines 38–54) exposes `history: List<SavedCount>`,
  `currencies: List<Currency>`, `selectedCurrencyId: String`. `deleteSavedCount(id)` (503)
  still exists but ReportsScreen (per this plan) does not call it.

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat 8f77ad2..HEAD -- <paths in drift block>` | only plan 006/007 deltas + this plan's files |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/domain/ReportKeys.kt` (create — pure helpers)
- `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt` (create)
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (create)
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` (create —
  compiling placeholder; the full screen is plan 009)
- `app/src/main/java/com/moneycounter/MainActivity.kt` (bottom nav + `when` wiring only)

**Out of scope** (do NOT touch, even though they look related):
- `HistoryScreen.kt` — left ON DISK as dead code (unused but still compiles; it was
  deliberately not imported by anything after MainActivity stops referencing it).
  `formatDate` stays (HistoryDetailScreen uses it).
- `viewmodel/MoneyCounterViewModel.kt`, `domain/*` (except the new `ReportKeys.kt`),
  `repository/*`, `util/*` — no logic changes; `deleteSavedCount` is simply not called by
  the new screen. Per-report delete from the reports list is intentionally DROPPED (the old
  HistoryScreen delete UI dies with the branch; bring it back in a later plan if wanted —
  it does NOT belong to 008).
- `HistoryDetailScreen.kt` — untouched; only its back target in MainActivity changes.
- The unified merge + export work — the ENTIRE body of plan 009. 008 only wires the
  `"summary"` branch to a placeholder.
- `MoneyCounterScreen.kt`, `StockScreen.kt`, `StockReportScreen.kt`,
  `DenominationManagementScreen.kt` — untouched.
- No new dependencies.

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (`git log` shows e.g.
  "Add existence report screen with PDF and CSV export"). Example:
  `Add Reportes tab with grouped, selectable reports screen`. Do NOT push or open a PR.

## Steps

### Step 1: Pure grouping/key helpers in `domain/ReportKeys.kt` + `ReportKeysTest.kt`

Create `app/src/main/java/com/moneycounter/domain/ReportKeys.kt`. It must be pure
JVM Kotlin — imports only `com.moneycounter.domain.SavedCount` and `java.util.Calendar`
(no Android imports), so it is unit-testable like `ProductSelection.kt`.

```kotlin
package com.moneycounter.domain

import java.util.Calendar

data class MonthKey(val year: Int, val month: Int)          // month 1..12
data class DayKey(val year: Int, val month: Int, val day: Int)

data class DayGroup(val key: DayKey, val counts: List<SavedCount>)
data class MonthGroup(val key: MonthKey, val days: List<DayGroup>)

fun monthKeyOf(millis: Long): MonthKey {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return MonthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

fun dayKeyOf(millis: Long): DayKey {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return DayKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun isCurrentMonth(key: MonthKey): Boolean = monthKeyOf(System.currentTimeMillis()) == key
fun isToday(key: DayKey): Boolean = dayKeyOf(System.currentTimeMillis()) == key

/** "2026-09" — month expansion key (matches Collapse-key strings). */
fun MonthKey.keyString(): String = "%04d-%02d".format(year, month)

/** "2026-09-07" — day expansion key. */
fun DayKey.keyString(): String = "%04d-%02d-%02d".format(year, month, day)

/** Groups history into months (newest month first), each with days (newest day first),
 *  each day's counts sorted by savedAt descending. Deterministic and de-duplicated. */
fun groupByMonthDay(history: List<SavedCount>): List<MonthGroup> {
    val dayGroups = history
        .groupBy { dayKeyOf(it.savedAt) }
        .map { (key, counts) -> DayGroup(key, counts.sortedByDescending { it.savedAt }) }
        .sortedByDescending { d -> d.key.year * 10_000 + d.key.month * 100 + d.key.day }
    return dayGroups
        .groupBy { MonthKey(it.key.year, it.key.month) }
        .map { (key, days) -> MonthGroup(key, days) }
        .sortedByDescending { m -> m.key.year * 100 + m.key.month }
}
```

Create `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt` (JUnit4, backtick test
names allowed, mirror `ProductSelectionTest.kt`). Add a tiny local helper that builds a
deterministic millis value so timezone never flips a boundary:

```kotlin
private fun millisFor(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
```

Cover at minimum:
1. `monthKeyOf`/`dayKeyOf` extract the right fields (e.g. `millisFor(2026, 9, 7)` →
   `MonthKey(2026, 9)` / `DayKey(2026, 9, 7)`).
2. Month/day boundary: `millisFor(2026, 1, 1, 0, 0)` stays in January/2026 and
   `millisFor(2026, 12, 31, 23, 59)` stays in December/2026 (year rollover safe).
3. `keyString` zero-padding: `MonthKey(2026, 9).keyString() == "2026-09"`,
   `DayKey(2026, 9, 7).keyString() == "2026-09-07"`.
4. `isCurrentMonth`/`isToday` return true for the current month/today and false for a
   fixed past key (offset by −32 days).
5. `groupByMonthDay` grouping/de-dup: a history list with 3 counts on the same day, 2 on
   another, and 1 in a different month → 2 `MonthGroup`s, correct `DayGroup` counts, each
   day sorted `savedAt` descending.
6. `groupByMonthDay` stability: feeding the same counts in REVERSED order yields the same
   group structure and the same sorted order (month newest-first, day newest-first).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew test --console=plain` → exit 0 with the new tests green.

### Step 2: `ui/screens/ReportsScreen.kt`

Create `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`. Signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MoneyCounterViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenSummary: (List<String>) -> Unit
)
```

State (top of the function, after `val uiState by viewModel.uiState.collectAsState()`):

```kotlin
var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
```

Layout and behavior, in order:

1. **Scaffold + TopAppBar "Reportes"** — NO back arrow (it is a bottom-nav tab). Copy the
   TopAppBar from `HistoryScreen.kt:58-75` but with title `"Reportes"` and no
   `navigationIcon`.
2. **Currency filter** — a `CurrencySelector` copy (from `MoneyCounterScreen.kt:476-510`,
   it is private → copy it as a private composable here, rename to `ReportsCurrencySelector`
   to avoid clashes). Uses `uiState.currencies` and `filterCurrencyId`; selecting sets
   `filterCurrencyId`. Default `DefaultCurrencies.CUP.id` is the literal `"cup"`.
   Render it as the first `item {}` of the LazyColumn, left-aligned.
   - Resolve the live filter currency defensively:
     `val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }`.
     If a currency was deleted, this is null → fall back: show the selector label `"—"` and
     filter with `filterCurrencyId` anyway (an id with no rows yields the empty state; do
     not "repair" the id silently).
3. **Data + grouping**:
   ```kotlin
   val filtered = uiState.history.filter { it.currencyId == filterCurrencyId }
   val groups = remember(filtered) { groupByMonthDay(filtered) }
   ```
4. **Expand/collapse defaults** — computed once from the groups that match today so the
   current month AND the current day both start expanded (uses the tested helpers):
   ```kotlin
   if (expandedKeys.isEmpty()) {
       expandedKeys = buildSet {
           groups.forEach { g -> if (isCurrentMonth(g.key)) add(g.key.keyString()) }
           groups.forEach { g -> g.days.forEach { d -> if (isToday(d.key)) add(d.key.keyString()) } }
       }
   }
   ```
   (Guard with `if (expandedKeys.isEmpty())` so a deliberate user collapse of the last key
   does not auto-re-expand.) Header rows toggle their key:
   `expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key`.
5. **Flat, deterministic item list** for the LazyColumn (an "ArrayDeque-style" render: one
   flat list of month headers, day headers, and report rows, newest first). Define a local
   sealed interface, e.g. `sealed interface ReportItem { val key: String }` with
   `data class MonthItem(val group: MonthGroup)`, `data class DayItem(val day: DayGroup)`,
   `data class CountItem(val saved: SavedCount)`. Build the list with `buildList { ... }`:
   for each `MonthGroup` (list already newest-first from `groupByMonthDay`):
   - `add(MonthItem(group))`;
   - if `group.key.keyString() in expandedKeys`: for each `DayGroup` add `DayItem(day)` and,
     if `day.key.keyString() in expandedKeys`, `CountItem` per count (keep count order).
   `items(items, key = { it.key })` where month key = `group.key.keyString()`, day key =
   `day.key.keyString()`, count key = `saved.id`.
6. **Empty state** — if `filtered.isEmpty()`: centered text
   `"No hay registros guardados para ${filterCurrency?.code ?: filterCurrencyId}"`
   (style copied from `HistoryScreen.kt:86-91`), skip the footer.
7. **Month row** (one `item`-card, `Card` + `CardDefaults.cardColors(surface)`):
   - `Checkbox(checked = allSelectedInGroup, onCheckedChange = { toggleAll(monthIds) })`
     where `allSelectedInGroup = monthIds.all { it in selectedIds }` (indeterminate state
     optional — boolean-only is fine and lower-risk).
   - Label `Text("${mm}/${yyyy}")` — e.g. `"09/2026"` from `group.key.month` zero-padded +
     `group.key.year` (matches the app's dd/MM/yyyy look).
   - Trailing chevron `Icon(Icons.Filled.ExpandMore)` when expanded, `Icon(Icons.Filled.ChevronRight)`
     when collapsed, inside an `IconButton` that toggles the month key.
   - `toggleAll(ids)` = if every id in `ids` is selected remove them all, else add them all
     (`selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids else selectedIds + ids`).
8. **Day row** (same card pattern): `Checkbox` for the day's `DayGroup.counts` ids,
   label `"${dd}/${mm}/${yyyy}"` (zero-padded), chevron toggles the day key.
9. **Report row** (`Card`, clickable toggles selection — the spec's "tap card = toggle"):
   - Left: `Checkbox(checked = saved.id in selectedIds, onCheckedChange = { toggle -> toggleSingle(saved.id) })`.
   - Middle (clickable, `Modifier.weight(1f)`): time `formatTime(saved.savedAt)` in
     `bodySmall` onSurfaceVariant + amount `formatMoneyBigDecimal(saved.targetAmount, saved.currency)`
     in `titleMedium` Bold primary (match `HistoryScreen.kt:156-161`).
   - Code badge: a small rounded `Surface` (primaryContainer, `shape = MaterialTheme.shapes.small`,
     padding 4–6 dp) with `Text(code, style = labelSmall)` where
     `val code = uiState.currencies.firstOrNull { it.id == saved.currencyId }?.code ?: saved.currencyId`.
   - Trailing detail: `IconButton(onClick = { onOpenDetail(saved.id) })` with
     `Icon(Icons.Filled.Visibility, contentDescription = "Ver detalle")`.
   - `formatTime(millis)` is a local private fun: `SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))`.
   - Row click toggles: card `Modifier.clickable { toggleSingle(saved.id) }`; the
     Checkbox and the Visibility IconButton sit INSIDE the card and consume their own clicks
     (nested clickables are fine — the Checkbox reports onCheckedChange, the IconButton is
     its own click target).
10. **Footer summary bar** (last `item {}` when `filtered` is non-empty):
    ```kotlin
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${selectedIds.size} seleccionados · ${filterCurrency?.code ?: filterCurrencyId}")
        FilledTonalButton(
            onClick = { onOpenSummary(selectedIds.toList()) },
            enabled = selectedIds.isNotEmpty()
        ) { Text("GENERAR RESUMEN") }
    }
    ```
    plus `item { Spacer(Modifier.height(16.dp)) }`.

Strings: all Spanish. Icons: `Checkbox` is Material-built-in; no new icon imports beyond
`ExpandMore`, `ChevronRight`, `Visibility`.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
(The screen is not referenced yet — it compiles standalone.)

### Step 3: `UnifiedReportScreen.kt` — compiling placeholder

Create `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` so the
MainActivity wiring in Step 4 has something to compile against. Only:

```kotlin
package com.moneycounter.ui.screens

import androidx.compose.runtime.Composable
import com.moneycounter.viewmodel.MoneyCounterViewModel

@Composable
fun UnifiedReportScreen(
    viewModel: MoneyCounterViewModel,
    selectedCountIds: List<String>,
    onNavigateBack: () -> Unit
) {
    // Placeholder; full unified report screen lands in plan 009.
}
```

Plan 009 replaces this body (it lists this file explicitly as in-scope/overwrite).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 4: Wire navigation in `MainActivity.kt`

Imports:
- Remove `import com.moneycounter.ui.screens.HistoryScreen` (line 27).
- Add `import com.moneycounter.ui.screens.ReportsScreen` and
  `import com.moneycounter.ui.screens.UnifiedReportScreen` (alphabetical, next to the other
  `com.moneycounter.ui.screens.*` imports).
- Add `import androidx.compose.material.icons.filled.Receipt` next to the `Paid`/`Inventory2`
  icon imports.

State and bottom bar:
- Keep `currentScreen`, `selectedHistoryId`. Add:
  ```kotlin
  var selectedReportIds by remember { mutableStateOf<List<String>>(emptyList()) }
  ```
- `showBottomBar` (line 52) becomes:
  ```kotlin
  val showBottomBar =
      currentScreen == "counter" || currentScreen == "stock" || currentScreen == "reports"
  ```
- Add a third `NavigationBarItem` (after Stock, lines 64–69):
  ```kotlin
  NavigationBarItem(
      selected = currentScreen == "reports",
      onClick = { currentScreen = "reports" },
      icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
      label = { Text("Reportes") }
  )
  ```

`when (currentScreen)` changes:
- DELETE the `"history"` branch (lines 97–104) entirely — it is the only place that used
  `HistoryScreen`, so the import removal is safe.
- `"detail"` (105–109) keeps the `HistoryDetailScreen` call but its back target changes:
  `onNavigateBack = { currentScreen = "reports" }`.
- Add:
  ```kotlin
  "reports" -> ReportsScreen(
      viewModel = viewModel,
      onOpenDetail = { id ->
          selectedHistoryId = id
          currentScreen = "detail"
      },
      onOpenSummary = { ids ->
          selectedReportIds = ids
          currentScreen = "summary"
      }
  )
  "summary" -> UnifiedReportScreen(
      viewModel = viewModel,
      selectedCountIds = selectedReportIds,
      onNavigateBack = { currentScreen = "reports" }
  )
  ```
- Leave `"counter"` (2-arg post-007), `"stock"`, `"report"`, `"settings"` untouched.

Note on state: `ReportsScreen`'s `selectedIds`/`expandedKeys` are `remember`-local, so
leaving to `"detail"`/`"summary"` and coming back recomposes a fresh screen (selection
reset). That is accepted behavior for 008.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew assembleDebug --console=plain` → exit 0, APK produced.

### Step 5: Full verification

Run all three commands from the Commands table plus the greps in Done criteria.

**Verify**:
`./gradlew test --console=plain` → exit 0, no failures.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

`ReportKeysTest.kt` (Step 1) is the only new test file — all six behaviors pinned with
deterministic `Calendar`-built millis. There are no Compose UI tests (no harness in this
repo's local JUnit). Regression safety otherwise comes from step-gated compilation: the
dead `HistoryScreen.kt` still compiles (it was left intact), the two new screens compile
standalone in Step 2/3 before MainActivity references them in Step 4, and the `"history"`
removal is proven safe by the `HistoryScreen` grep in Done criteria.

Manual acceptance (operator, on device): Contador → (bottom bar) Reportes → only the
current month/day start expanded; selecting a whole month/day toggles its counts; the row
chevrons expand/collapse; switching the currency filter hides other currencies; the footer
counts `N seleccionados · CUP`; GENERAR RESUMEN stays disabled at 0 and opens the (still
placeholder) "Resumen unificado" screen when ≥ 1 selected; the Visibility icon opens the
existing detail screen; detail's back arrow returns to Reportes.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (ReportKeysTest included)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `app/src/main/java/com/moneycounter/domain/ReportKeys.kt` exists with `MonthKey`,
      `DayKey`, `DayGroup`, `MonthGroup`, `monthKeyOf`, `dayKeyOf`, `isCurrentMonth`,
      `isToday`, `keyString`(×2), `groupByMonthDay` and NO Android imports
      (`git grep -n "android" app/src/main/java/com/moneycounter/domain/ReportKeys.kt` → nothing)
- [ ] `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt` exists with ≥ 6 tests
- [ ] `git grep -n "Receipt" app/src/main/java/com/moneycounter/MainActivity.kt` → icon import + 3rd `NavigationBarItem` labeled "Reportes"
- [ ] `git grep -n "\"reports\"" app/src/main/java/com/moneycounter/MainActivity.kt` → in `showBottomBar`, the `NavigationBarItem`, and the `when` branch
- [ ] `git grep -n "\"summary\"" app/src/main/java/com/moneycounter/MainActivity.kt` → the `UnifiedReportScreen` branch
- [ ] `git grep -n "\"history\"" app/src/main/java/com/moneycounter/MainActivity.kt` → returns nothing (branch gone)
- [ ] `git grep -n "HistoryScreen" app/src/main/java/com/moneycounter/` → returns nothing (import removed; file left as dead code only)
- [ ] `git grep -n "currentScreen = \"reports\"" app/src/main/java/com/moneycounter/MainActivity.kt` → the detail back target and the summary back target
- [ ] `git grep -n "GENERAR RESUMEN" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` + `git grep -n "seleccionados" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → footer present
- [ ] `git grep -n "Checkbox" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → month, day and count checkboxes
- [ ] `git grep -n "formatMoneyBigDecimal(saved.targetAmount, saved.currency)" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → report rows use the saved symbol (no manual `$` prefix)
- [ ] `git grep -n "groupByMonthDay" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → grouped render wired to the pure helper
- [ ] `UnifiedReportScreen.kt` exists and is still the plan-009 placeholder (no aggregation/export yet)
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 008 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Plans 006/007 are NOT applied: `SavedCount` has no `currencyId`, or
  `MoneyCounterScreen` still takes `onNavigateToHistory` (the `"counter"` branch below
  would not compile after the `"history"` branch is deleted).
- Any in-scope file no longer matches the "Current state" excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- `HistoryScreen.kt` or `HistoryDetailScreen.kt` need edits to keep compiling (dead code
  must stay untouched; `formatDate` is public and shared).
- You find yourself editing the ViewModel, an exporter, a repository, or any screen other
  than the five files in scope.
- Grouping/selection needs a ViewModel change (e.g. a new `uiState` field or a VM-side
  filter function) — this plan reads `uiState.history` / `uiState.currencies` only.
- `groupByMonthDay` requires Android time/date APIs for the tests to be deterministic
  (it must be pure `java.util.Calendar`).
- Removing the `"history"` branch or adding the tab breaks `./gradlew test` (the plan adds
  no test changes outside `ReportKeysTest.kt`; a red suite is a STOP).
- You are tempted to fill in `UnifiedReportScreen` beyond the placeholder — that is plan 009.

## Maintenance notes

- `HistoryScreen.kt` becomes dead code at 008. Despite the temptation, do NOT delete it or
  gut it: `formatDate` (line 180) is public and used by `HistoryDetailScreen.kt`, and a
  future plan may reuse pieces. A later cleanup plan can remove the file once `formatDate`
  moves to a shared home.
- The report rows format amounts with `saved.currency` (the stored SYMBOL, e.g. `$`/`US$`)
  and only SHOW the code (`CUP`/`USD`) in the badge — never mix them. `formatMoneyBigDecimal`
  embeds the symbol; do not add a manual prefix.
- `groupByMonthDay` is the single source of truth for ordering (newest month → newest day →
  newest count). Keep the UI ignorant of sorting; if the sort rules change, change the pure
  helper and its tests, not the screen.
- `selectedIds`/`expandedKeys` are screen-local; navigating to detail/summary and back
  resets them. If multi-selection should survive navigation later, lift the state up to
  MainActivity — deliberate non-goal in 008.
- Reviewer should check: the currency filter defaults to CUP (`"cup"`), only counts whose
  `currencyId == filterCurrencyId` render, default expansion opens the current month AND
  today, checkbox toggling removes-all-when-all-selected (not just additive), and the footer
  button is disabled at 0 (`enabled = selectedIds.isNotEmpty()`).