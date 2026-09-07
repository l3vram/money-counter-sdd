# Plan 012: Fix reports selection action bar (5 buttons aligned)

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything in
> the "STOP conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/reports-currency/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 7139c1e..HEAD -- app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`
> Baseline is commit `7139c1e` (feature/stock-screen tip after plan 011). Any diff vs.
> that commit is a STOP signal.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW — single-file UI layout change in ReportsScreen.kt. No logic, no
  persistence, no navigation, no new dependencies.
- **Depends on**: plans/reports-currency/011-reports-delete-minimal-nav.md (the action-bar
  layout being fixed).
- **Category**: bugfix (UI layout)
- **Planned at**: commit `7139c1e`, 2026-09-07

## Why this matters

User report: in the reports screen, when entering selection mode there are 5 controls that
appear "encimados y desalineados" (overlapping and misaligned).

Current layout when `selectionMode` is on:
- Item A: `Row(SpaceBetween)` = [currency selector · sort toggle]
- Item B: `Row(SpaceBetween)` = [Listo · (`Row(spacedBy(4))` = ELIMINAR · GENERAR RESUMEN (N))]

The nested right-side `Row` (ELIMINAR + GENERAR RESUMEN (N)) is too wide to fit beside
"Listo" on device widths, so the `<Row(SpaceBetween)>` squeezes/overflows the nested row
and the buttons misalign — 5 controls in two uneven rows.

Fix: put the mode toggle on the FIRST row (always visible, aligned with the other two
controls) and make the SECOND row exist ONLY in selection mode with two full-width
weighted buttons that can never overflow:

```
Row1 (always):  [ $ CUP ]   [Antiguos ↑]   [Seleccionar | Listo]
Row2 (selection only):  [ ELIMINAR (weight 1) ][ GENERAR RESUMEN (N) (weight 1) ]
```

## Current state

`app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` (508 lines) — the two
drop-in `item {}` blocks at the top of the LazyColumn (lines 135–194):

```kotlin
item {
    Row(fillMaxWidth, SpaceBetween) {
        ReportsCurrencySelector(… onSelectCurrency = { filterCurrencyId = it; selectedIds = emptySet(); expandedKeys = emptySet() })
        OutlinedButton(onClick = { ascending = !ascending }) { Text(if (ascending) "Antiguos ↑" else "Recientes ↓") }
    }
}
item {
    Row(fillMaxWidth, SpaceBetween, padding(bottom = 4.dp)) {
        if (selectionMode) {
            FilledTonalButton({ selectionMode = false }) { Text("Listo") }
            Row(spacedBy(4.dp)) {
                OutlinedButton({ confirmDelete = true }, enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("ELIMINAR") }
                FilledTonalButton({ onOpenSummary(selectedIds.toList()) },
                    enabled = selectionMode && selectedIds.isNotEmpty()) {
                    Text(if (selectedIds.isEmpty()) "GENERAR RESUMEN" else "GENERAR RESUMEN (${selectedIds.size})")
                }
            }
        } else {
            FilledTonalButton({ selectionMode = true }) { Text("Seleccionar") }
        }
    }
}
```

Imports already present: `Arrangement`, `Row`, `FilledTonalButton`, `OutlinedButton`,
`ButtonDefaults`, `Text`, `MaterialTheme`, `Modifier`. `weight` is a `RowScope` extension —
no new import needed.

## Scope

**In scope**: `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` ONLY.
**Out of scope**: everything else (ViewModel, other screens, no test changes).

## Git workflow

- Branch: `feature/reports-currency` (you work in an isolated worktree).
- Single commit, repo-style message, e.g. `Align report selection actions into two clean rows`.
  Do NOT push or open a PR.

## Steps

### Step 1: Restructure the action bar

Replace the two `item {}` blocks (lines 135–194) with:

```kotlin
item {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ReportsCurrencySelector(
            currencies = uiState.currencies,
            selectedCurrencyId = filterCurrencyId,
            onSelectCurrency = {
                filterCurrencyId = it
                selectedIds = emptySet()
                expandedKeys = emptySet()
            }
        )
        OutlinedButton(onClick = { ascending = !ascending }) {
            Text(if (ascending) "Antiguos ↑" else "Recientes ↓")
        }
        FilledTonalButton(onClick = { selectionMode = !selectionMode }) {
            Text(if (selectionMode) "Listo" else "Seleccionar")
        }
    }
}

if (selectionMode) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("ELIMINAR")
            }
            FilledTonalButton(
                onClick = { onOpenSummary(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (selectedIds.isEmpty()) "GENERAR RESUMEN"
                    else "GENERAR RESUMEN (${selectedIds.size})"
                )
            }
        }
    }
}
```

Notes:
- The mode toggle moves to Row 1 (right of the sort toggle); browse mode shows
  `Seleccionar`, selection mode shows `Listo`.
- Row 2 renders ONLY when `selectionMode`; the two buttons share the width via
  `Modifier.weight(1f)` so they are equal and never overflow. No third control competes
  with them.
- When `selectionMode` is toggled OFF while `selectedIds` is non-empty, leave the ids as
  are (re-entering selection keeps the selection) — do NOT auto-clear.
- Keep everything else in the file unchanged.

**Verify**:
`./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL.

### Step 2: Full verification

**Verify**:
`./gradlew test --console=plain` → exit 0, no failures.
`./gradlew assembleDebug --console=plain` → exit 0, APK produced.

## Test plan

No new tests (pure layout change; no Compose UI harness in this repo). Regression safety:
step-gated compile + greps.

Manual acceptance (operator, on device): Reportes → the first row shows `currency · sort ·
Seleccionar` all on one line and aligned; tap Seleccionar → button label becomes `Listo` in
the SAME row; a NEW second row appears with `ELIMINAR` and `GENERAR RESUMEN (N)` side by
side at equal width; nothing overlaps on a narrow screen; ELIMINAR opens the confirm dialog.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0 (full suite green)
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `git grep -n "FilledTonalButton(onClick = { selectionMode = !selectionMode })" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → the mode toggle lives in the FIRST row (no `Seleccionar`/`Listo` inside the second `item` block)
- [ ] `git grep -n "if (selectionMode) {" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → the second row (ELIMINAR + GENERAR RESUMEN) is guarded by it
- [ ] `git grep -n "Modifier.weight(1f)" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → both Row 2 buttons are weighted
- [ ] `git grep -n "ELIMINAR" app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` → appears exactly in the Row-2 OutlinedButton and the dialog confirm text (no other placement)
- [ ] `git diff 7139c1e..HEAD --stat` → ONLY `ReportsScreen.kt` changed (+ README status if committed)
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/reports-currency/README.md` status row for 012 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- The ReportsScreen action-bar code no longer matches the "Current state" excerpt.
- You need to touch any file other than `ReportsScreen.kt` (and the plan README).
- `Modifier.weight` requires a new import (it is a `RowScope` receiver extension; if the
  enclosing lambda is not a `RowScope`, STOP — the structure is wrong).
- You are tempted to also fix the USD-save behavior here (that is NOT this plan — the
  counter already shows the no-products hint; only the layout changes now).

## Maintenance notes

- Row 1 is now the single home of the three persistent controls; Row 2 is transient
  (selection-only). Any future toolbar additions should go to Row 1 (persistent) or a new
  conditional row — never nest two rows of buttons inside a `SpaceBetween` Row again.
- Reviewer: confirm the mode-toggle label flips (`Seleccionar`→`Listo`) while staying in
  Row 1, selection survives toggling off/on, and diverging `spaceBy(8.dp)` vs the old
  `spaceBy(4.dp)` for the weighted buttons keeps a clean gap.