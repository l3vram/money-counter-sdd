# Plan 006: Reskin the reports screens (ReportsScreen + HistoryDetailScreen + UnifiedReportScreen)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt`
> On a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (reports carry the most display logic — the 11-test ReportKeys
  behaviour and 7-test ReportAggregation must remain untouched)
- **Depends on**: plans/el-luiso-redesign/001-*.md, plans/el-luiso-redesign/002-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

Reports is the branch-tab that shows the relative "today/ratio/last" and the
aggregated per-currency summaries, and its selection/export/summary actions are
the feature's power user path. The kit's Reports section wants prioritized,
simple figures and human copy; financially the per-currency totals need the
strong hierarchy the kit demands ("los números deben tener jerarquía visual
fuerte: total, variación, contexto"). This plan restyles list rows, headers,
selection action bar, detail, and unified summary — never touching the
reporting aggregation/keys/export logic underneath.

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` — flat,
  all-collapsed month/day/count rows; slim headers
  (`"MMMM yyyy"` + `(N)`, day `"EEE dd/MM/yyyy es"` indent); selection mode
  with row 1 `currency · sort · Seleccionar/Listo`; row 2 `ELIMINAR` +
  `GENERAR RESUMEN (N)` (both `weight(1f)`, used from plan 012). Uses
  `MaterialTheme.colorScheme.*`; currently no hardcoded brand colors
  (`grep -c 'Color(0x' ReportsScreen.kt` → 0).
- `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt` —
  detail of one saved count: `DetailRow`, money lines via
  `formatMoneyBigDecimal`, financial separation per currency; uses
  `surfaceVariant` rows and header with back arrow.
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt` —
  per-currency summary with export buttons (PDF/CSV) — `PdfExporter`/CSV
  already wired; colors are theme-derived with a few literals
  (`grep -n 'Color(0x\|Color.White' UnifiedReportScreen.kt`).
- Report THE STRUCTURE is load-bearing: the 11 `ReportKeysTest` and 7
  `ReportAggregationTest` unit tests must keep passing BENAVIORALLY. The
  keys (`groupByMonthDay`, ascending sort) live in `domain/ReportKeys.kt` —
  do not touch domain code.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Report tests | `./gradlew test --console=plain --tests "*Report*"` | 18 tests, all pass |
| Full tests | `./gradlew test --console=plain` | exit 0 |
| Greps | `grep -n 'Color(0x\|Color.White' <each in-scope screen>` | 0 after step 4 |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/UnifiedReportScreen.kt`
- Optionally `app/src/main/java/com/moneycounter/ui/theme/Dimens.kt` (only additive)

**Out of scope** (do NOT touch):
- `domain/ReportKeys.kt`, `domain/ReportAggregation*`, repositories, exporters
- `ui/theme/Color.kt` / `Theme.kt` semantic values
- Selection/sort/delete/summary logic and its callbacks syncs with the ViewModel (870-900 in VM)
- `plans/el-luiso-redesign/README.md` (index update is yours too, but only the row for 006)

## Git workflow

- Worktree branch created by orchestrator: `exec/006-rpt`.
- Commit conventional lowercase, e.g. `feat(reports): apply El Luiso theme`.
  Do NOT push.

## Steps

### Step 1: ReportsScreen — headers, rows, action bar

- Month/day header rows: keep the SAME text/params (`"MMMM yyyy"`/`(N)` etc.)
  and same indent logic; restyle only: use `LuisoSectionHeader` for month
  headers and theme `surfaceVariant`/`surfaceContainerHigh` only where the
  current code uses literal containers; add the yellow 4dp accent on the
  current-month header only (kit: yellow = attention/sparse).
- Count rows: `bodyMedium` on `surface`; the day totals keep `titleMedium`
  bold with `onSurface`; selection checkboxes/click behavior unchanged.
- Action bar row 1: `currency · sort · Seleccionar/Listo` — keep three
  outlines in the exact order; restyle sort and toggle to
  `LuisoOutlineButton`/`FilledTonalButton` equivalent using theme colors.
  The `Antiguos ↑`/`Recientes ↓` labels are unchanged.
- Row 2 (`if (selectionMode)`): `ELIMINAR` keeps `error` colors; `GENERAR
  RESUMEN (N)` keeps `weight(1f)` — both `LuisoButton`/`LuisoOutlineButton`.
- Confirm-delete `AlertDialog` stays as-is except text roles from theme.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0; then
`./gradlew test --console=plain --tests "*Report*"` → 18 pass.

### Step 2: HistoryDetailScreen — detail of one count

- Header via `LuisoTopBar(title = "Detalle", navigationIcon = <back>)` (keep
  the existing back callback).
- Alternate row shading from `surfaceVariant` → `surfaceContainerLow`/`surface`.
  Rows: label `labelMedium` `onSurfaceVariant`, value `bodyLarge` `onSurface`;
  the money total uses `titleLarge` + `primary`/`secondary` (positive total).
- Keep `formatDate` (added in a prior fix), `formatMoneyBigDecimal` calls and
  the PDF/CSV share affordances exactly as-is.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: UnifiedReportScreen — per-currency summary + exports

- Top bar `LuisoTopBar`; per-currency summary cards → `LuisoStatCard` (label
  = currency name/symbol, value = aggregate total, `valueColor = primary`).
  Section headers → `LuisoSectionHeader` (yellow accent on the leading
  currency, plain otherwise).
- Export buttons: "Exportar PDF" / "Exportar CSV" → `LuisoButton` /
  `LuisoOutlineButton`, `leadingIcon` = existing `Icons.Filled.Description`.
- Replace any `Color(0x...)`/`Color.White` literals with roles/tokens.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 4: Full regression

**Verify**:
- `./gradlew test --console=plain` → exit 0 (18 report-key/aggregation tests included)
- `./gradlew assembleDebug --console=plain` → exit 0
- `grep -n 'Color(0x\|Color.White' <the three screens>` → 0 matches
- `git status --short` → only the in-scope files modified.

## Test plan

No new tests (UI-only restyle). Regression guarantee is the unaffected report
unittests (`ReportKeys` 11 + `ReportAggregation` 7): their inputs (key shape,
aggregation results) are untouched because domain code is out of scope.

## Done criteria

All must hold:

- [ ] `./gradlew test` exits 0; `--tests "*Report*"` passes 18 tests
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `grep 'Color(0x\|Color.White'` on the three screens → 0
- [ ] Selection action bar layout (row 1 with toggle, row 2 weighted ELIMINAR + GENERAR RESUMEN) is preserved pixel-logic unchanged
- [ ] Report grouping/sort/aggregation behavior unchanged (no domain edits)
- [ ] `plans/el-luiso-redesign/README.md` row 006 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Any of the three screens drift from "Current state".
- You find yourself editing `ReportKeys.kt`, aggregation, exporters, or the
  ViewModel — STOP; those are out of scope and their loss is real.
- A test that passed before fails after your UI diff — STOP (never hide/ignore
  a failing report test).

## Maintenance notes

- The selection/export/summary path is the most user-visible power flow: keep
  the yellow accent strictly sparse (current month + leading currency) so the
  green authority stays dominant.
- Money hierarchy: total is `titleLarge` primary/secondary; context labels
  stay `labelMedium`; never let decoration compete with the digits.