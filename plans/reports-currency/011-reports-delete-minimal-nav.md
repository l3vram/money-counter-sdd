# Plan 011: Reports cleanup (delete + minimal list) + compact bottom nav + font-scale cap

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 4b26c29..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt app/src/main/java/com/moneycounter/MainActivity.kt`
> Baseline for this plan is commit `4b26c29`. Any diff vs. that commit is a STOP signal
> (your worktree branch must be exactly at `4b26c29`).

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED — rewrites the ReportsScreen list rendering again (delete support,
  minimal flat rows, all collapsed), adds a bulk delete to the ViewModel, and compacts
  the bottom navigation + caps font scaling in MainActivity. No persistence/JSON changes,
  no navigation-route changes, no new dependencies.
- **Depends on**: plans/reports-currency/010-reports-list-redesign.md (the current
  ReportsScreen + compact band design being simplified here).
- **Category**: feature + UI polish
- **Planned at**: commit `4b26c29`, 2026-09-07

## Why this matters

User feedback on the 010 design (verbatim requests):
1. "necesito que los reportes se puedan borrar, ya que ahora hay registros creados con usd
   y dicen cup" → the USD-id bug left bogus `cup`-labeled records; the operator must be
   able to DELETE reports (select a set and delete them) to clean up and re-enter them.
2. "se pueden hacer mas chicos los encabezados de mes? tiene mucha altura ... sin borde o
   background ya que ocupa mucho espacio" → month headers lose the `primaryContainer`
   band and the Card entirely; small plain text rows.
3. "que todo este colapsado" → default is ALL collapsed (remove the current-month/today
   auto-expand); "2 reportes toman casi toda la pantalla" confirms it.
4. "delante de los reportes por horas y el total hay algo que no se ve solo se ven puntos"
   → the browse-mode bullet `•` (and squeezed row) renders as stray dots; remove bullets
   and any per-row visual clutter, give amount + time a clean single line.
5. "la opcion de seleccionar debe ser para exportar o para eliminar" → in selection mode
   the action bar offers BOTH `ELIMINAR` and `GENERAR RESUMEN (N)` (export). Delete needs a
   confirm dialog.
6. "simplificar esa vista" → flat, borderless, low-height rows; the per-row currency code
   badge is dropped (the list is already filtered to ONE currency — the badge was
   redundant). No Cards in the list at all.
7. "el menu de abajo y toda la app es muy grande ... dame opciones" — user chose:
   **compact bottom nav** (64dp, icon 22dp, `labelSmall` labels) and a **font-scale cap
   of 1.2** app-wide (so a big system font does not blow up the whole UI).

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (412 lines, from 010):
  - State: `filterCurrencyId`, `ascending` (default true), `selectionMode`, `expandedKeys`,
    `selectedIds`. Groups = `remember(filtered, ascending) { groupByMonthDay(...) }`.
  - Auto-expand block (lines ~78–83): current month + today expanded on empty
    `expandedKeys`. THIS PLAN REMOVES IT.
  - Month row: `Card` `primaryContainer` band, `MMMM yyyy` label, `(N operaciones)`,
    select-all `Checkbox` when selectionMode, trailing `IconButton` chevron.
  - Day row: `Card` `surfaceVariant`, `EEE dd/MM/yyyy` label, `TOTAL ` + day fold +
    `· N`, select-all `Checkbox` when selectionMode.
  - Count row: leading `Checkbox` (selection) or bullet `•` (browse), amount +
    `· HH:mm` in a Column, currency code badge chip, trailing `Visibility` (selection) /
    `ChevronRight` (browse). Card click = toggle (selection) or detail (browse).
  - Top items: Row A = `ReportsCurrencySelector` + sort `OutlinedButton` ("Antiguos ↑" /
    "Recientes ↓"); Row B = `FilledTonalButton` "Seleccionar"/"Listo" + `FilledTonalButton`
    `GENERAR RESUMEN (N)` (enabled only when selectionMode && non-empty).
  - Helpers kept from 010: `ReportsCurrencySelector`, `formatTime`, `toggleKey`,
    `toggleSingle`, `toggleAll`, `ReportItem`/`MonthItem`/`DayItem`/`CountItem`,
    month/day label `remember` blocks with `Locale("es")` and midday `Calendar`.
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` line 507:
  `fun deleteSavedCount(id: String)` (updates `history`, single `persistHistory()`). It is
  only called by the dead `HistoryScreen`; keep it.
- `app/src/main/java/com/moneycounter/MainActivity.kt` (130 lines): `MoneyCounterApp()`
  wraps everything in `Scaffold(bottomBar = { if (showBottomBar) NavigationBar { … } })`
  (lines 58–82). Three `NavigationBarItem`s: Contador (`Paid`), Stock (`Inventory2`),
  Reportes (`Receipt`). Default `NavigationBar` height is 80dp with 24dp icons.
  `setContent { MoneyCounterTheme { MoneyCounterApp() } }` (lines 40–43).
- No `Divider`/`HorizontalDivider` used anywhere in the codebase (avoid `Divider`; use a
  plain thin `Box` background separator instead — no API-version risk).

## Commands you will need

| Purpose   | Command (run at project root)      | Expected on success      |
|-----------|------------------------------------|--------------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`   | exit 0, no failures      |
| Assemble  | `./gradlew assembleDebug --console=plain` | exit 0, APK produced         |
| Drift     | `git diff --stat 4b26c29..HEAD -- <paths in drift block>` | empty (baseline) |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
  (ADD `deleteSavedCounts(ids: List<String>)` — additive; do not modify existing methods)
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (rewrite the list UI)
- `app/src/main/java/com/moneycounter/MainActivity.kt` (compact nav + font-scale cap)

**Out of scope** (do NOT touch):
- `ReportKeys.kt`, `ReportKeysTest.kt`, `UnifiedReportScreen.kt`, exporters,
  `HistoryScreen.kt` / `HistoryDetailScreen.kt`, repositories, JSON, other screens.
- Per-row single delete buttons in browsed mode — delete flows through selection mode only
  (select one report and ELIMINAR handles a single delete too).
- No pagination, no undo history, no "mover a papelera".

## Git workflow

- Branch: `feature/reports-currency` (this run's branch; you work in an isolated worktree).
- Commit per logical step; message style matches the repo. Examples: `Fix saveCount to
  persist selected currencyId`-style short lowercase verbs: "Add bulk delete for saved
  counts", "Show report delete and export actions in selection mode", "Simplify reports
  list to flat collapsed rows", "Make bottom navigation compact and cap font scale".
  Do NOT push or open a PR.

## Steps

### Step 1: Bulk delete in the ViewModel

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`, right after
`deleteSavedCount` (line 507), add:

```kotlin
fun deleteSavedCounts(ids: List<String>) {
    if (ids.isEmpty()) return
    val idSet = ids.toSet()
    _uiState.update { st -> st.copy(history = st.history.filterNot { it.id in idSet }) }
    persistHistory()
}
```

Do NOT modify `deleteSavedCount` or anything else.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Rewrite the ReportsScreen list (minimal, flat, all-collapsed, delete)

Rewrite the list part of `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`.
Keep the signature, the `ReportItem` sealed hierarchy, `ReportsCurrencySelector`,
`formatTime`, the currency-change handler (clear `selectedIds` + `expandedKeys`), the
sort toggle (does NOT clear selection/expansion), and the `Locale("es")` month label
(`MMMM yyyy`) / day label (`EEE dd/MM/yyyy`) `remember(...)` blocks.

Changes:

1. **Remove the auto-expand block** entirely (the `if (expandedKeys.isEmpty()) { … }`
   that opened current month + today). `expandedKeys` stays `emptySet()` — EVERYTHING is
   collapsed by default; only the operator expands what they need.

2. **Action bar (top items, order):**
   - Row A — unchanged: `ReportsCurrencySelector` left + sort `OutlinedButton`
     (`"Antiguos ↑"` / `"Recientes ↓"`) right.
   - Row B — new contract:
     - Browse mode (`!selectionMode`): left `FilledTonalButton` **"Seleccionar"**
       (`onClick = { selectionMode = true }`); right side empty. NO export/delete buttons.
     - Selection mode: left `FilledTonalButton` **"Listo"**
       (`onClick = { selectionMode = false }`); right side a `Row(horizontalArrangement =
       Arrangement.spacedBy(8.dp))` with:
       - `OutlinedButton` **"ELIMINAR"** (text color error via
         `colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)`;
         `enabled = selectedIds.isNotEmpty()`), `onClick` opens a confirm dialog.
       - `FilledTonalButton` **"GENERAR RESUMEN (N)"** (label switches to plain
         `"GENERAR RESUMEN"` when 0; `enabled = selectionMode && selectedIds.isNotEmpty()`),
         `onClick = { onOpenSummary(selectedIds.toList()) }`.

3. **Delete confirm dialog** — a `var confirmDelete by remember { mutableStateOf(false) }`.
   When true, `AlertDialog`:
   - `title = { Text("Eliminar operaciones") }`
   - `text = { Text(if (selectedIds.size == 1) "¿Eliminar esta operación?" else "¿Eliminar ${selectedIds.size} operaciones? Esta acción no se puede deshacer.") }`
   - `confirmButton = { TextButton(onClick = { viewModel.deleteSavedCounts(selectedIds.toList()); selectedIds = emptySet(); confirmDelete = false }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }`
   - `dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }`
   Render the dialog at the end of the Screen function (outside the LazyColumn).

4. **No Cards in the list.** Render month/day/count rows as flat `Row`s inside
   `Surface(color = MaterialTheme.colorScheme.surface)` `item {}`s with NO background on
   the rows themselves, separated by a thin `Box` separator:
   ```kotlin
   Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
   ```
   (only between count rows, and below the month/day headers — keep visually sparse).
   Import `androidx.compose.foundation.background`.

5. **Month row** (small, no bg): `Row(verticalAlignment = CenterVertically,
   padding(horizontal = 4.dp, vertical = 4.dp))`:
   - If `selectionMode`: `Checkbox(checked = allSelected,
     onCheckedChange = { toggleAll(monthIds) })` (the `IconButton` below gets
     `Modifier.size(32.dp)` so the row stays short).
   - `Text(monthLabel, titleSmall, FontWeight.Bold, Modifier.weight(1f))`
   - `Text("($operaciones)", bodySmall, onSurfaceVariant)` (compact — drop " operaciones")
   - `IconButton(onClick = toggleKey, Modifier.size(32.dp))` with `ExpandMore`/`ChevronRight`
     (contentDescription "Contraer mes"/"Expandir mes").
   - `monthIds`/`operaciones` computed as today.

6. **Day row** (small, no bg, light indent): `Row(…, padding(start = 20.dp, horizontal =
   4.dp, vertical = 4.dp))` (indent shows hierarchy without a background):
   - If `selectionMode`: `Checkbox(checked = allSelected, onCheckedChange = {
     toggleAll(dayIds) })`.
   - `Text(dayLabel, bodySmall, FontWeight.Medium, Modifier.weight(1f))`
   - `Text("TOTAL " + formatMoneyBigDecimal(dayTotal, symbol), titleSmall, Bold, primary)`
   - `Text("· " + day.counts.size, bodySmall, onSurfaceVariant)`
   - `IconButton(onClick = toggleKey, Modifier.size(32.dp))` chevron.
   - `dayIds` = `day.counts.map { it.id }`; `dayTotal` = `fold(BigDecimal.ZERO)` as in 010;
     `symbol` = `filterCurrency?.symbol ?: "$"`.

7. **Count row** (flat, single line, NO bullet, NO code badge, clean layout):
   `Row(verticalAlignment = CenterVertically, padding(horizontal = 4.dp + 20.dp indent,
   vertical = 5.dp))` — inside each count row:
   - Browse mode: leading `Spacer(Modifier.width(12.dp))` (keeps rows aligned in both
     modes without a bullet), then a single `Row(Modifier.weight(1f))`:
     - `Text(formatMoneyBigDecimal(saved.targetAmount, saved.currency), titleSmall, Bold,
       primary, Modifier.weight(1f))`
     - `Text(formatTime(saved.savedAt), bodySmall, onSurfaceVariant)`
     - trailing `Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle",
       tint = onSurfaceVariant)`.
     Row `Modifier.clickable { onOpenDetail(saved.id) }`.
   - Selection mode: leading `Checkbox(checked = saved.id in selectedIds,
     onCheckedChange = { toggleSingle(saved.id) })`, same amount+time `Row(weight(1f))`,
     NO trailing icon (row click toggles; there is no per-row detail affordance in
     selection mode — keep it minimal). Row `Modifier.clickable { toggleSingle(saved.id) }`.
   - Delete the old `code` badge `Surface`/chip and the `Visibility` icon imports if now
     unused (leave `ChevronRight`, `ExpandMore`).

8. **CHARACTER anything else**: keep the empty-state item and the trailing
   `item { Spacer(Modifier.height(16.dp)) }`. All strings Spanish. NO `Card` in the list
   (MonthHeaderRow/DayHeaderRow/ReportRow become plain rows — you may rename them or keep
   the names; prefer `MonthRow/DayRow/ReportRow`).

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 3: Compact bottom nav + font-scale cap in `MainActivity.kt`

1. **Compact NavigationBar** (lines 58–82): add `Modifier.height(64.dp)` to `NavigationBar`,
   `Modifier.size(22.dp)` to each `Icon(...)`, and `style = MaterialTheme.typography.labelSmall`
   to each `label = { Text(...) }`.
   Imports to add: `androidx.compose.foundation.layout.height`,
   `androidx.compose.foundation.layout.size`, `androidx.compose.material3.MaterialTheme`.
2. **Font-scale cap 1.2 app-wide** — in `MoneyCounterApp()` wrap the `Scaffold` (everything
   inside the function) so the whole UI clamps `fontScale` between 0.0 and 1.2:
   ```kotlin
   val currentDensity = LocalDensity.current
   CompositionLocalProvider(
       LocalDensity provides Density(currentDensity.density, fontScale = min(currentDensity.fontScale, 1.2f))
   ) {
       Scaffold( ... ) { ... }
   }
   ```
   Imports to add: `androidx.compose.runtime.CompositionLocalProvider`,
   `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.unit.Density`,
   `kotlin.math.min`. (Keep the `viewModel()` and screen state declarations inside the
   provider order unchanged — `viewModel()` can stay above the provider.)

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.
`./gradlew assembleDebug --console=plain` → exit 0, APK produced.

### Step 4: Full verification

Run all three commands from the Commands table plus the greps in Done criteria.

**Verify**:
`./gradlew test --console=plain` → exit 0, no failures.
`./gradlew assembleDebug --console=plain` → exit 0.

## Test plan

No new unit tests (the change is UI + one additive VM method; the repo has no Compose UI
harness). Regression safety = step-gated compilation + done-criteria greps. Existing
`ReportKeysTest` (11) and friends must stay green.

Manual acceptance (operator, on device): all months collapsed on open; expanding a month →
its days; a day → its reports; month/day headers are slim plain text (no colored bands);
report rows are single clean lines (no dots, no CUP badge); Seleccionar → ELIMINAR +
GENERAR RESUMEN appear; selecting reports + ELIMINAR asks a confirm and removes them
(multi and single); the USD-labeled-as-CUP records can now be selected and deleted; the
bottom nav is noticeably slimmer; increasing the system font size stops making the app
mushroom past ~1.2.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (all 11 ReportKeysTest + full suite green)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "fun deleteSavedCounts" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` → bulk delete added; `git grep -n "deleteSavedCounts" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → called after confirm
- [ ] `git grep -cn "Card" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → 0 (no list Cards). NOTE: check for `CardDefaults`/`Card(` imports removed too.
- [ ] `git grep -n "selectionMode" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → toggle + ELIMINAR + GENERAR RESUMEN gated on it
- [ ] `git grep -n "ELIMINAR" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → selection-mode action + dialog confirm/dismiss
- [ ] `git grep -n "isCurrentMonth\|isToday" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → returns NOTHING (auto-expand removed)
- [ ] `git grep -n "•" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → nothing (no bullets); `git grep -n "code" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → only the `code`/`currencyId` fallback in the count-item build, no badge UI
- [ ] `git grep -n "TOTAL " app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → day header total kept
- [ ] `git grep -n "height(64.dp)" app/src/main/java/com/moneycounter/MainActivity.kt` → compact NavigationBar; `git grep -n "size(22.dp)" app/src/main/java/com/moneycounter/MainActivity.kt` → smaller icons; `git grep -n "labelSmall" app/src/main/java/com/moneycounter/MainActivity.kt` → compact labels
- [ ] `git grep -n "fontScale = min" app/src/main/java/com/moneycounter/MainActivity.kt` → font-scale cap wired; `git grep -n "CompositionLocalProvider" app/src/main/java/com/moneycounter/MainActivity.kt` → provider present
- [ ] `git diff 4b26c29..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt | git grep -c "^+"` → only additive lines (the new function), no modified existing code
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 011 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any in-scope file no longer matches the "Current state" excerpts (drift).
- You need to touch a file OUTSIDE the three in-scope files to make it compile (e.g. a
  repository, `UnifiedReportScreen`, exporters, `HistoryDetailScreen`).
- `NavigationBar` height/size modifiers cause layout warnings that don't compile — if
  `Modifier.height(64.dp)` on `NavigationBar` is rejected by the compiler, use
  `NavigationBar(modifier = Modifier.height(64.dp), tonalElevation = 0.dp)` style ONLY
  (same shape).
- The font-scale cap needs to move outside `MoneyCounterApp()` (it must stay inside
  `setContent`; wrapping the `Scaffold` is the accepted placement).
- Removing `•`/badge/`Card` requires changing `formatMoneyBigDecimal` or the data model —
  do not.
- `Card`/`CardDefaults` are required by something else in the file (they are not — the 010
  design uses them only in the list).

## Maintenance notes

- Delete is selection-scoped by design (no per-row delete button in browse mode). Single
  reports can still be deleted: Seleccionar → select one → ELIMINAR.
- `deleteSavedCount(id)` (single, pre-existing) stays for the dead `HistoryScreen`; it is
  superseded by `deleteSavedCounts` for the reports screen.
- The list is deliberately borderless; hierarchy comes from indent (days ≥ 20.dp) +
  typography weight, not from containers. Keep it that way.
- The 1.2 font cap is a minimum-accessibility-compromising global: it floors at the user's
  chosen scale up to 1.2 and clamps above. If a future design wants per-screen density,
  keep the provider at the `MoneyCounterApp` level so all screens inherit it.
- Reviewer: verify (a) no `Card` remains in ReportsScreen, (b) `isCurrentMonth`/`isToday`
  are gone from the screen (dead imports too), (c) delete confirm actually clears
  `selectedIds` after success, (d) ELIMINAR button uses error color and is disabled at 0,
  (e) the font-scale provider wraps the whole Scaffold.