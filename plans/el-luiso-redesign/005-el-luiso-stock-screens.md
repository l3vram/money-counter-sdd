# Plan 005: Reskin the stock screens (StockScreen + StockReportScreen)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`
> On a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (visual-only)
- **Depends on**: plans/el-luiso-redesign/001-*.md, plans/el-luiso-redesign/002-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

The stock screens are the "Inventario" tab. The El Luiso kit (`DESIGN_SYSTEM.md`
§9 Inventario) demands: name, price, stock, state, with state colors — normal →
green `#22C55E`, low → yellow `#FACC15`, out → red; a search, filters, and a
prominent "+ Agregar producto" primary action. This plan restyles both stock
screens to the brand's component system without touching stock logic,
repository access, stock-report PDF/CSV behavior or number formats.

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`:
  - `StockScreen(:56)` — top-level content; hosts header/actions and product list.
  - `SectionTitle(text)(:229)` — section heading helper.
  - `ProductRow(:239)` — name, unit price, stock count, stock-state hint
    (colors currently custom hardcoded).
  - `ProductDialog(:300)` — add/edit dialog with fields.
  - `parseDecimalInput(input)(:471)` — decimal parse helper (do not touch).
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` — stock
  PDF/CSV report screen (export buttons, summary). Inspect its current colors:
  `grep -n 'Color(0x\|Color.White' app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`.
- The app already exports via icons `Icons.Filled.Description` / `Share`
  (repo uses `Icons.Default.*`/`Icons.Filled.*` only).
- Tokens/components: plans 001–002 (see their files).

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Greps | `grep -n 'Color(0x\|Color.White' <the two stock screens>` | 0 matches after step 3 |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`

**Out of scope** (do NOT touch):
- `ui/theme/*`, any other screen, `StockRepository`/`JsonStockRepository`
  (repository naming: verify), `.pdf`/`.csv` exporters, ViewModel logic.
- Adding real search/filter controls — the current screen may not have them;
  if it lacks a stock-search control, STOP and report (it may be a follow-up,
  NOT part of this plan).

## Git workflow

- Worktree branch created by orchestrator: `exec/005-rpt`.
- Commit(s) conventional lowercase, e.g. `feat(stock): apply El Luiso theme`.
  Do NOT push.

## Steps

### Step 1: StockScreen header + actions

Restyle the top of `StockScreen` with `LuisoTopBar(title = "Inventario", …)`
(keep any existing back/settings icon wired to the current callback), the
brand accent stripe, and the primary FAB/button "+ Agregar producto" →
`LuisoButton`. Keep `ProductDialog` opening behavior identical. Use the exact
existing label of the add button (currently likely "Agregar producto" or
"+ Agregar producto") — preserve business-facing strings; copy polish is plan
008.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Stock-state colors + product rows

- `SectionTitle` → `LuisoSectionHeader`.
- `ProductRow`: restyle with `LuisoCard`, name in `titleSmall`, unit price via
  `formatMoneyBigDecimal(price, currencySymbol)` from `DenominationRow.kt`
  (match existing format calls), stock count in `bodyMedium`.
- Stock-state chip colors exactly per kit: **normal → `secondary`
  (`LuisoGreenBright`, green)**, **low → `LuisoYellow` (amber, dark text
  `LuisoInk`)**, **out → `error`**. Use `Color` literals ONLY via the brand
  constants (`LuisoGreenBright`, `LuisoYellow`, `LuisoError` imported from
  `ui.theme`), or `MaterialTheme.colorScheme` roles where the scheme already
  carries them (`secondary`, `error`). Do not leave the old custom hex.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Dialogs + stock report

- `ProductDialog` fields → `LuisoTextField`; confirm/save button →
  `LuisoButton`; cancel → `LuisoOutlineButton`; destructive deletes keep
  `error` colors.
- `StockReportScreen` → `LuisoTopBar` header, export buttons as
  `LuisoButton`/`LuisoOutlineButton`, summary figures via `LuisoStatCard`,
  replace any hardcoded colors with tokens/roles.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 4: Full regression

**Verify**:
- `./gradlew compileDebugKotlin` → exit 0; `./gradlew test` → exit 0
- `grep -n 'Color(0x\|Color.White' app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` → 0 matches
- `git status --short` → only the two in-scope files modified.

## Test plan

No new unit tests (UI only). Regression: full `test` suite + `assembleDebug` in
final verify.

## Done criteria

All must hold:

- [ ] Both stock screens compile and `test` exits 0
- [ ] `grep -n 'Color(0x\|Color.White'` on both files returns 0
- [ ] Stock states mapped: normal=green (`secondary`), low=yellow (`LuisoYellow`/`tertiaryContainer`), out=red (`error`)
- [ ] Header + add-product action use Luiso components
- [ ] No logic/export/ViewModel/format changes (diff is UI-only)
- [ ] `plans/el-luiso-redesign/README.md` status row for 005 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- The stock screens' code doesn't match "Current state" (drifted).
- The feature has no stock-search/filter control today and adding one would
  require new repository/ViewModel surface — that's OUT of scope; report it as a
  finding instead.
- You need colors not present in tokens/roles.

## Maintenance notes

- Stock states rely on color + text simultaneously (kit §Accessibility and the
  established chip labels) — keep the label ("Normal"/"Bajo"/"Agotado") next to
  the colored chip; never color-only.
- The add/product dialog pattern established here is the template the
  DenominationManagement dialogs (007) will follow.