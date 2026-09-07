# Plan 004: Reskin the counter screen (MoneyCounterScreen) to El Luiso

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`
> On a mismatch, treat it as a STOP condition. (ui/theme drift is EXPECTED —
> plan 001 added the design tokens; do not include it in the diff.)

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED (visual-only refactor of the busiest screen; no behavior)
- **Depends on**: plans/el-luiso-redesign/001-*.md, plans/el-luiso-redesign/002-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

The counter is the app's landing screen and its busiest — 38 hardcoded
`Color(0x...)` literals repeat the old green. The El Luiso brand starts here:
a green `LuisoTopBar` with the app name + tagline, cream background, brand
green primary buttons, yellow-led summary cards. No behavior, state, money
formatting or navigation changes.

## Current state

- Composable layout in `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`:
  - `MoneyCounterScreen(:70)` — top-level scaffold content; hosts the header,
    `ProductsSection`, `CurrencySelector`, `SummarySection`, `DenominationRow`
    stream and the "Guardar conteo" action.
  - `ProductsSection(:212)`, `ProductRow(:302)`, `ProductSelector(:423)` —
    product lines + selector dialog.
  - `CurrencySelector(:468)` — currency dropdown/chips.
  - `SummarySection(:504)`, `SummaryRow(:666)` — per-currency totals (the
    "target" summary).
  - Money formatting MUST keep using `formatMoney` / `formatMoneyBigDecimal`
    from `ui/components/DenominationRow.kt` (no `$` prefixes in call sites).
- `grep -n 'Color(0x' app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` → 38 matches (all old-green literals).
- Tokens (plans 001–002): brand colors in `ui/theme/Color.kt`; components
  `LuisoTopBar`, `LuisoButton`, `LuisoOutlineButton`, `LuisoCard`,
  `LuisoTextField`, `LuisoEmptyState`, `LuisoStatCard`, `LuisoSectionHeader`,
  `LuisoCircle` in `ui/components/Components.kt`.
- Header today: an inline colored banner with the app name + tagline-like
  subtitle; the save button is a `Button` with `containerColor = primary`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Grep | `grep -n 'Color(0x' app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` | 0 matches after step 5 |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`

**Out of scope** (do NOT touch):
- `ui/theme/*` (tokens — if a needed color is missing, STOP and report, do not hardcode)
- Any other screen file
- Business logic (`viewModel.*` calls), money formatting helpers, navigation callbacks

## Git workflow

- Worktree branch created by orchestrator: `exec/004-rpt`.
- Commit(s) conventional lowercase, e.g. `feat(counter): apply El Luiso theme`.
  Do NOT push.

## Steps

### Step 1: Swap the header for `LuisoTopBar`

Replace the inline header banner with `LuisoTopBar(title = "El Luiso", ...)`
with the tagline as a subtitle line under the title (`bodySmall`,
`onPrimary`/`onPrimaryContainer`), plus the brand-yellow accent 4dp stripe
under the bar. Keep the existing layout params and any content descriptions
the header had. Title text uses the hardcoded string "El Luiso" (resources
migration is plan 008's scope).

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Replace every hardcoded color with theme roles and tokens

Go through all 38 `Color(0x...)` literals and replace with the semantic value
they represent:

- old header green `0xFF1B6B3A` and its variants → `MaterialTheme.colorScheme.primary` / `onPrimary`
- light containers → `surfaceContainerLow` / `surfaceVariant`
- near-white surfaces → `surface` / `surfaceContainer`
- text greens → `secondary` (`LuisoGreenBright`) where it signals positive/total
- text ink → `onSurface` / `onSurfaceVariant`
- error rojos → `error` / `onErrorContainer`
- any white literal → `Color.White` only where `onPrimary`/`onError` must stay
  white (buttons); import `Color` as needed.

Keep the file's drift discipline: after every few replacements compile.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Adopt Luiso components where they map 1:1

- Primary action ("Guardar conteo" / save) → `LuisoButton(text = "Guardar conteo", onClick = { viewModel.saveCount() }, leadingIcon = Icons.Filled.Check)`. Match exact label to whatever the current string is (check the file; it may be `Guardar conteo`).
- Secondary/tertiary actions in `ProductsSection`/`ProductSelector`/`CurrencySelector` → `LuisoOutlineButton` where they are outlined today.
- Summary rows in `SummarySection`/`SummaryRow` → migrate to `LuisoStatCard(label = currency name, value = formatted total, valueColor = MaterialTheme.colorScheme.primary)` layout; keep the exact `formatMoneyBigDecimal(money, symbol)` calls and per-currency grouping. Do NOT change the visible numbers.
- Product/Dialog `OutlinedTextField`s → `LuisoTextField` (labels, hints, keyboard options preserved from current fields).

Every swap must keep the same `Modifier` params (weights, fills, padding).

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 4: Empty/feedback copy

The kit requires human copy. Where the screen shows a "no products for this
currency" preview or an empty denominations state, use `LuisoEmptyState` with
on-brand text; feedback toasts/strings must stay as they are (copy pass is 008).

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 5: Full regression

**Verify**:
- `./gradlew compileDebugKotlin --console=plain` → exit 0
- `./gradlew test --console=plain` → exit 0
- `grep -n 'Color(0x' app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` → no matches
- `git status --short` → only `MoneyCounterScreen.kt` modified.

## Test plan

No new unit tests (pure UI). Regression: `test` suite (Repositories/Calculator/
Keys/Summary/Counter tests all green) + `assembleDebug` in the final verify.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `grep -n 'Color(0x' app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` returns 0 matches
- [ ] Header banner replaced by `LuisoTopBar("El Luiso" …)` with tagline
- [ ] Save action and summary use `LuisoButton` / `LuisoStatCard`
- [ ] Money values use `formatMoney`/`formatMoneyBigDecimal` unchanged
- [ ] No behavior/navigation/ViewModel changes beyond restyling (reviewer diff shows only UI)
- [ ] `plans/el-luiso-redesign/README.md` status row for 004 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- The file structure differs materially from "Current state" (another plan
  already touched it).
- You need a color/component that doesn't exist (missing token) — STOP; do not
  invent a hex literal.
- You find yourself changing `.save()`, money formatting, or ViewModel wiring —
  STOP.

## Maintenance notes

- `MoneyCounterScreen` keeps being the app's identity touchpoint: future brand
  updates start here. Yellow accent = highlights only (sparse), green =
  authority/trust.
- The "CUP/USD selector" and summary are per-currency; keep the selected-currency
  highlight in `primaryContainer` so the active currency visibly stands apart on
  cream surfaces.