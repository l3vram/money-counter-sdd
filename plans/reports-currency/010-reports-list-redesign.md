# Plan 010: Reportes list redesign (Diseño 1) + fix USD filter bug

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md` (create that
> README from the `plans/stock-screen/README.md` shape if the plans run has not created it
> yet).
>
> **Drift check (run first)**:
> `git diff --stat f8a517d..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/domain/ReportKeys.kt app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt app/src/main/java/com/moneycounter/domain/SavedCount.kt app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt`
> Baseline for this plan is commit `f8a517d`. Any diff vs. that commit is a STOP signal
> (your worktree branch must be exactly at `f8a517d`).

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — rewrites the ReportsScreen UI (selection on demand, sort toggle, day
  totals, hierarchy, export moved to top) and touches one ViewModel line (the USD bug).
  No persistence changes, no JSON changes, no navigation changes, no new dependencies.
- **Depends on**: plans/reports-currency/008-reports-screen.md (the ReportsScreen +
  ReportKeys this plan evolves) and 009 (nothing changes in the summary flow — the
  selection fed to `onOpenSummary` still works).
- **Category**: feature + bugfix
- **Planned at**: commit `f8a517d`, 2026-09-07

## Why this matters

User feedback on the Reportes list (verbatim complaints):
1. "los dias estan intercabiados" → days appear in the wrong order; they want Desc OR Asc.
   Decision: **Asc is the default** (oldest first), a toggle inverts to Desc.
2. "el listado debe ser descriptivo no se ve nada excepto la fecha ... no tiene jerarquia
   todo se ve a la misma altura" → collapsed days must show a **day total**; month/day/
   count levels need distinct visual weight.
3. "con el check box deberia mostrarse el check cuando le demos exportar no antes" →
   checkboxes only appear in an explicit **selection mode** ("Seleccionar"/"Listo"); in
   browse mode rows are clean and tap to open detail.
4. "el exportar deberia estar arriba ... si la lista es muy larga hay que navegar hasta
   abajo" → **GENERAR RESUMEN moves to the top** (sticky first items), footer removed.
5. "es muy grande la vista u con muchos registros habria que scrollear mucho" → every
   collapsed day is ONE compact line (day header with total); only the current month and
   today stay expanded. No pagination needed.
6. **Bug**: "si filtro por usd no sale nada y justo tengo una operacion en usd y no se ve
   en los resumenes" → root cause: `saveCount()` in `MoneyCounterViewModel.kt` builds
   `SavedCount` WITHOUT `currencyId` (line 483–490), so every new save defaults
   `currencyId` to `"cup"` even when the selected/flown currency is USD. The Reports filter
   (`filtered = uiState.history.filter { it.currencyId == filterCurrencyId }`) then finds
   nothing for `"usd"`. Fix: pass `currencyId = state.selectedCurrencyId`.

## Current state

- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (599 lines),
  `saveCount()` at lines 483–504:
  ```kotlin
  val saved = SavedCount(
      id = UUID.randomUUID().toString(),
      savedAt = System.currentTimeMillis(),
      targetAmount = target,
      items = items,
      currency = currencySymbol(),
      products = savedProducts
  )
  ```
  `currencySymbol()` returns the SELECTED currency's symbol (`"$"` / `"US$"`). The
  selected currency id is already in state: `_uiState.value.selectedCurrencyId`
  (`MoneyCounterUiState.selectedCurrencyId: String`). This plan passes it as
  `currencyId = state.selectedCurrencyId`.
- `app/src/main/java/com/moneycounter/domain/ReportKeys.kt` (41 lines). `groupByMonthDay`
  is hard-wired newest-first (months, then days, then counts). Signature:
  `fun groupByMonthDay(history: List<SavedCount>): List<MonthGroup>`.
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (406 lines). Key state
  (lines 70–72): `filterCurrencyId`, `expandedKeys`, `selectedIds`. Layout: currency
  selector as first `item`, then LazyColumn over `rows` (MonthItem/DayItem/CountItem),
  a BOTTOM footer with `"N seleccionados · CUP"` + `FilledTonalButton "GENERAR RESUMEN"`
  (lines 197–212). Rows ALWAYS render a `Checkbox`; report row card click = toggle;
  trailing `Visibility` IconButton = detail. Default expansion (lines 78–83) opens current
  month + today. Currency change clears selection + expansion (commit `fae663d`).
- `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt` (118 lines, 8 tests) —
  covers grouping/ordering/keys/stability for the default (descending) behavior.
- `SavedCount.currency` = SYMBOL (`"$"`)/`"US$"`); `SavedCount.currencyId` = id
  (`"cup"`/`"usd"`), default `DefaultCurrencies.CUP.id` = `"cup"`.
- `formatMoneyBigDecimal(value, symbol)` embeds the symbol (no manual `$` prefix).

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat f8a517d..HEAD -- <paths in drift block>` | empty (baseline) |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (ONE line:
  add `currencyId = state.selectedCurrencyId` to the `SavedCount` constructor in
  `saveCount()`)
- `app/src/main/java/com/moneycounter/domain/ReportKeys.kt` (parameterize sort)
- `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt` (ascending tests)
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (redesign)

**Out of scope** (do NOT touch, even though they look related):
- `UnifiedReportScreen.kt`, `PdfExporter.kt`, `ExcelExporter.kt`, `ReportAggregation.kt`,
  `MainActivity.kt`, `HistoryDetailScreen.kt`, `HistoryScreen.kt` — untouched.
- No JSON/persistence changes: old USD operations saved before this fix keep
  `currencyId = "cup"` (they were migrated from v1/v2 by symbol; if a pre-fix save has a
  `US$` symbol but `cup` id because it was saved between 006 and 010, it stays `cup`).
  This plan fixes NEW saves only; the user can re-enter the handful of affected USD ops or
  a future backfill plan can re-derive ids from symbols.
- `DefaultCurrencies`, `Currency`, `Product`, `SavedCount` data classes — untouched.
- Developer-portal/AndroidX Chrome Custom Tabs — nothing to do.

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo (`git log` shows e.g.
  "Clear report selection when currency filter changes"). Example:
  `Redesign reports list with selection mode, sort toggle and day totals`. Do NOT push or
  open a PR.

## Steps

### Step 1: Fix the USD bug in `saveCount()`

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`, the `SavedCount`
constructor in `saveCount()` (lines 483–490) gains one field:

```kotlin
val saved = SavedCount(
    id = UUID.randomUUID().toString(),
    savedAt = System.currentTimeMillis(),
    targetAmount = target,
    items = items,
    currency = currencySymbol(),
    products = savedProducts,
    currencyId = state.selectedCurrencyId
)
```

`state` is already the local `val state = uiState.value` used throughout `saveCount()`.
Do NOT touch anything else in this file.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Parameterize `groupByMonthDay` with an `ascending` flag

In `app/src/main/java/com/moneycounter/domain/ReportKeys.kt`, replace the fixed
newest-first ordering with an explicit direction. Keep the existing signature usable
(existing callers/tests must still compile): add a defaulted parameter.

```kotlin
/** Groups history into months, each with days, each day's counts sorted by savedAt.
 *  ascending=false → newest first (months, days, counts). ascending=true → oldest first. */
fun groupByMonthDay(history: List<SavedCount>, ascending: Boolean = false): List<MonthGroup> {
    val dir = if (ascending) 1 else -1
    val dayGroups = history
        .groupBy { dayKeyOf(it.savedAt) }
        .map { (key, counts) ->
            DayGroup(key, if (ascending) counts.sortedBy { it.savedAt } else counts.sortedByDescending { it.savedAt })
        }
        .sortedWith(compareBy { dir * (it.key.year * 10_000 + it.key.month * 100 + it.key.day) })
    return dayGroups
        .groupBy { MonthKey(it.key.year, it.key.month) }
        .map { (key, days) -> MonthGroup(key, days) }
        .sortedWith(compareBy { dir * (it.key.year * 100 + it.key.month) })
}
```

Key ints are positive and the multiplier `dir` flips the total order without breaking
determinism. `sortedWith`/`sortedBy` are stable, so equal keys keep input order.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew test --console=plain` → exit 0 (the 8 existing ReportKeys tests still pass —
they call the default `ascending = false`).

### Step 3: Add ascending tests to `ReportKeysTest.kt`

Append new tests to `app/src/test/java/com/moneycounter/domain/ReportKeysTest.kt`
(reuse the existing `millisFor(year, month, day, hour, minute)` helper). Cover:

- `groupByMonthDayAscending`: with `ascending = true`, months come oldest-first
  (assert the `MonthGroup.key` order), days within a month oldest-first, and a day's
  counts oldest-first (by `savedAt`). Use ≥ 2 months and ≥ 2 days total so each level is
  actually exercised.
- `groupByMonthDayAscendingStable`: feeding the same counts in REVERSED input order
  yields the same group structure and the same ascending order.
- `groupByMonthDayDefaultStillDescending`: the no-arg call still returns months
  newest-first (guards the default).

**Verify**:
`./gradlew test --console=plain` → exit 0, new tests green
(expect ≥ 11 tests total in `ReportKeysTest`).

### Step 4: Redesign `ReportsScreen.kt` (Diseño 1)

Rewrite `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`. Signature
UNCHANGED:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MoneyCounterViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenSummary: (List<String>) -> Unit
)
```

State (replaces lines 70–76):

```kotlin
val uiState by viewModel.uiState.collectAsState()
var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
var ascending by remember { mutableStateOf(true) }          // Asc is the default
var selectionMode by remember { mutableStateOf(false) }     // "Seleccionar" / "Listo"
var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
```

Data + grouping (grouping now depends on the sort direction):

```kotlin
val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
val filtered = uiState.history.filter { it.currencyId == filterCurrencyId }
val groups = remember(filtered, ascending) { groupByMonthDay(filtered, ascending) }
```

Keep the default-expansion block (lines 78–83, current month + today) EXACTLY as is.

Layout (all inside the existing `LazyColumn`):

1. **Header items (sticky by being the first items — no `stickyHeader`, LazyColumn is
   fine)**, in order:
   - **Row A**: `ReportsCurrencySelector` (existing composable, unchanged) on the LEFT and
     a sort **toggle button** on the RIGHT (`OutlinedButton`):
     `Text(if (ascending) "Antiguos ↑" else "Recientes ↓")`; `onClick` flips
     `ascending = !ascending`. Do NOT clear `selectedIds` or `expandedKeys` on sort
     change — the row SET is identical, only its order changes.
   - **Row B**: `FilledTonalButton` **"Seleccionar"** (toggles `selectionMode`; label
     becomes **"Listo"** while active) on the LEFT and `FilledTonalButton`
     **"GENERAR RESUMEN (N)"** on the RIGHT, where
     `enabled = selectionMode && selectedIds.isNotEmpty()` and the label shows
     `("GENERAR RESUMEN")` when count is 0, `("GENERAR RESUMEN ($n)")` when `n > 0`.
     `onClick = { onOpenSummary(selectedIds.toList()) }`.
   - Keep the spacing style: `Arrangement.spacedBy(8.dp)` on the LazyColumn already covers
     it; give Row B `padding(bottom = 4.dp)`.
2. **Empty state** — unchanged (lines 141–157).
3. **Month row** (`__DESIGN__`: full-width band, `primaryContainer`) — visually the
   strongest level:
   - `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)`.
   - Label: month NAME + year in Spanish via
     `SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(calendarForMonth))` — build
     the `Calendar` with `set(year, month - 1, 1, 12, 0, 0)` so the date is a fixed midday
     (timezone-safe). Style `titleMedium`, Bold, `onPrimaryContainer`. Example rendered:
     `Septiembre 2026`.
   - Subtitle same line, `bodySmall` `onPrimaryContainer` (alpha ~0.7): `(N operaciones)`
     where N = sum of counts across the month's days.
   - `Checkbox` (month select-all) RENDERED ONLY when `selectionMode`; trailing
     expand/collapse `IconButton` (ExpandMore/ChevronRight) always visible.
4. **Day row** (secondary level, inset) — a compact single line that carries the day TOTAL
   so collapsed days are descriptive:
   - `Card`, `surfaceContainer`, `Modifier.padding(start = 4.dp)` for a slight indent.
   - Label: weekday short + date in Spanish via
     `SimpleDateFormat("EEE dd/MM/yyyy", Locale("es"))` — `bodyMedium`, Medium weight.
   - Right of label (same line): `"TOTAL "` + `formatMoneyBigDecimal(dayTotal, symbol)`
     in `titleSmall` Bold `primary`, where
     `dayTotal = day.counts.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.targetAmount) }`
     and `symbol = filterCurrency?.symbol ?: "$"`. Then `"· N"` (op count) `bodySmall`
     `onSurfaceVariant`.
   - `Checkbox` (day select-all) rendered ONLY when `selectionMode`; trailing
     expand/collapse `IconButton` always visible.
5. **Count row** (tertiary level, most compact) — browse vs selection mode differ:
   - Browse mode (`!selectionMode`): NO checkbox. Leading decorative bullet
     `Text("•", bodyMedium, onSurfaceVariant)`. Middle (weight 1): amount
     `formatMoneyBigDecimal(saved.targetAmount, saved.currency)` `titleSmall` Bold
     `primary`, then `· HH:mm` (`formatTime(saved.savedAt)`) `bodySmall`
     `onSurfaceVariant`. Code badge (existing chip). Trailing chevron
     `Icons.Filled.ChevronRight`. **Card click = `onOpenDetail(saved.id)`**.
   - Selection mode: leading `Checkbox(checked = saved.id in selectedIds)`; same middle
     amount/time; code badge; trailing `IconButton` `Icons.Filled.Visibility`
     `contentDescription = "Ver detalle"` → `onOpenDetail` (keep the icon — operator still
     wants detail mid-selection). **Card click = toggle selection**
     (`toggleSingle(saved.id)`).
6. **REMOVE the bottom footer** (old lines 197–212: the `"N seleccionados · CUP"` row,
   the bottom `GENERAR RESUMEN` button, and the trailing 16.dp Spacer). Also keep any
   trailing `item { Spacer(Modifier.height(16.dp)) }` for scroll bottom padding.

Existing helpers to keep: `formatTime`, `toggleKey`, `toggleSingle`, `toggleAll`,
`MonthHeaderRow`/`DayHeaderRow`/`ReportRow` (RENAMED/REPURPOSED to match the new
visual/behavior contract), `ReportItem`/`MonthItem`/`DayItem`/`CountItem`,
`ReportsCurrencySelector`. Strings all Spanish. New imports needed: `java.util.Calendar`,
`java.util.Locale`, `java.math.BigDecimal` (for the day total fold), `FontWeight`,
`TextAlign` already present. Month/day labels use `Locale("es")` — spell-check typical
outputs ("Septiembre", "Octubre", "lun", "mar"...). Do not add a manual `$` anywhere.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 5: Full verification

Run all three commands from the Commands table plus the greps in Done criteria.

**Verify**:
`./gradlew test --console=plain` → exit 0, no failures.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

`ReportKeysTest.kt` grows by 3 tests (ascending order, ascending stability, default still
descending) — pure JVM, deterministic millis. The USD fix has no unit test (it writes
`currencyId` inside `saveCount()`, which build the JSON via `persistHistory()`); it is
covered by the done-criteria grep + manual acceptance. `ReportsScreen` has no Compose UI
test harness; correctness is structural (grep criteria) + manual.

Manual acceptance (operator, on device): a USD operation saved AFTER this build now shows
under the USD filter AND in the USD summary; the list defaults Asc (antiguos arriba) and
the toggle flips to Desc; collapsed days show their TOTAL; checking does not exist in
browse mode (tap row = detail); "Seleccionar" reveals checkboxes and "GENERAR RESUMEN (N)"
at the top enables; month/day/count hierarchy is visually distinct; only current month +
today start expanded.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (ReportKeysTest ≥ 11 tests, all green)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "currencyId = state.selectedCurrencyId" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` → the `saveCount()` constructor line
- [ ] `git grep -n "fun groupByMonthDay" app/src/main/java/com/moneycounter/domain/ReportKeys.kt` → signature has `ascending: Boolean = false`
- [ ] `git grep -n "ascending" app/src/main/java/com/moneycounter/domain/ReportKeys.kt` → the parameter is used in all three orderings (counts, days, months)
- [ ] `git grep -n "ascending" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → state var + wiring into `groupByMonthDay`
- [ ] `git grep -n "remember(selected" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → nothing (grouping key now `(filtered, ascending)`; the OLD `filtered`-only remember is gone)
- [ ] `git grep -n "selectionMode" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → the toggle + checkboxes gated on it
- [ ] `git grep -n "GENERAR RESUMEN" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → appears in the TOP action bar only; NOT at the bottom (old footer grep `seleccionados ·` returns nothing)
- [ ] `git grep -n "seleccionados" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → returns nothing (footer removed)
- [ ] `git grep -n "TOTAL" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → day header total line present
- [ ] `git grep -n "MMMM yyyy" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → Spanish month name label; `git grep -n '"es"' app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → `Locale("es")` present
- [ ] `git grep -n "formatMoneyBigDecimal(saved.targetAmount, saved.currency)" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → count rows use the saved symbol (no manual prefix)
- [ ] `git diff f8a517d..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt | git grep -n "currencyId"` → the ONLY ViewModel delta is the `currencyId` line (no other logic drift)
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 010 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts (e.g. `saveCount()`
  moved, `groupByMonthDay` already has a sort parameter, `ReportsScreen` was already
  redesigned).
- `git diff f8a517d..HEAD` is not empty at drift check (your worktree branch is not at the
  baseline).
- A step's verification fails twice after a reasonable fix attempt.
- You need to touch any file OUTSIDE the four in-scope files to make the change compile
  (e.g. `MainActivity` wiring, `UnifiedReportScreen` signature, `DefaultCurrencies`, a
  JSON repository, `PdfExporter`/`ExcelExporter`).
- You are tempted to restore per-report DELETE, add pagination, or lift
  `selectedIds`/`expandedKeys`/`selectionMode` into MainActivity — deliberately out of scope.
- The currency change handler (commit `fae663d` behavior: clear `selectedIds` +
  `expandedKeys` on filter switch) is lost during the rewrite — it must survive.
- `Locale("es")` month/weekday rendering needs manual string tables (it must draw from the
  system locale data; Spanish Android locales are always present).

## Maintenance notes

- Ordering is single-sourced in pure `ReportKeys.kt`; the screen just passes a flag. If the
  default sort ever changes, change the DEFAULT of `ascending` (and the initial
  `remember { mutableStateOf(...) }` value happens to be the user-facing default that
  `groupByMonthDay` mirrors) — keep both aligned.
- Selection mode is a LOCAL boolean; leaving to detail/summary and coming back resets it
  (screen-level `remember`), same as `selectedIds`/`expandedKeys` today. Accepted.
- The day total sums `targetAmount` within a single-currency filtered group, so it never
  mixes symbols. It does NOT re-derive per-product subtotals; products/denominations stay
  in the detail and unified summary.
- Reviewers: verify the month/day/weekday labels render Spanish with `Locale("es")`, the
  `(filtered, ascending)` remember key (not a stale `filtered`-only one), checkboxes are
  strictly gated on `selectionMode`, GENERAR RESUMEN is top-of-list only, and
  `currencyId = state.selectedCurrencyId` is the sole ViewModel change.