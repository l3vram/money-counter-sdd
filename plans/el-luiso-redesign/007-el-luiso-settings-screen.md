# Plan 007: Reskin the configuration screen (DenominationManagementScreen)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt app/src/main/java/com/moneycounter/ui/theme`
> On a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW (settings screen, all CRUD wiring is callbacks-only)
- **Depends on**: plans/el-luiso-redesign/001-*.md, plans/el-luiso-redesign/002-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

"Configuración" (Ajustes) manages currencies, products, units and
denominations — the admin surface. Kit §11/§25 and §icons want Settings icon
navigation, strong financial hierarchy, and radio-chips for selection states.
This screen currently mixes custom headers and inline lists; restyling it with
the Luiso kit makes the whole app feel like one product.

## Current state

- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt`:
  - Top-level screen with back navigation (uses `Icons.Filled.ArrowBack`,
    deprecated warning noted in builds) and a `ConfigScreen`-style layout.
  - Sections (in order): **MONEDA** (currency radio + edit/delete, and an add
    dialog with Código/Nombre/Símbolo), **PRODUCTOS** (Nombre, Unidad, Precio,
    Recargo fields/item rows), **UNIDADES DE MEDIDA**, then **DENOMINACIONES**.
  - Currently mixes several `Color(0x...)`/`Color.White` (run the grep).
  - CRUD actions (`onSaveCurrency`, `onDeleteCurrency`, product/unit CRUD,
    denomination save) are passed in as callbacks; the screen holds no logic.
- Money display uses `formatMoneyBigDecimal`; currencies list uses
  `currencies` from the ViewModel state.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Grep | `grep -n 'Color(0x\|Color.White' app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt` | 0 after step 3 |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/DenominationManagementScreen.kt`

**Out of scope** (do NOT touch):
- `ui/theme/*`, repositories/ViewModel, other screens
- The CRUD callback signatures, validation, or currency/product/unit/denomination domain models
- `MainActivity.kt` (nav wiring for "settings" screen — unchanged)

## Git workflow

- Worktree branch created by orchestrator: `exec/007-rpt`.
- Commit conventional lowercase, e.g. `feat(settings): apply El Luiso theme`.
  Do NOT push.

## Steps

### Step 1: Header and section structure

- Top bar → `LuisoTopBar(title = "Configuración", navigationIcon = <existing
  back callback>)`. Keep the back arrow (`Icons.Filled.ArrowBack`) wiring.
- Section headings ("MONEDA", "PRODUCTOS", "UNIDADES DE MEDIDA",
  "DENOMINACIONES") → `LuisoSectionHeader` with `accent = false` (green bar),
  or `accent = true` on the first/last section for the sparse yellow detail.
  Keep the exact section ordering.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Selections, rows, dialogs

- Currency radio selection → `LuisoCard` rows with a `RadioButton`-equivalent
  selected state highlighted by `primaryContainer` background; keep radio
  semantics and the same `onChange` callback.
- Edit/delete icon buttons: keep small `IconButton` affordances, colorize
  delete via `error`; edit stays `onSurfaceVariant`.
- Add/Edit dialogs (currency Código/Nombre/Símbolo; product fields; unit;
  denomination) → `LuisoTextField` instances; save →
  `LuisoButton`; cancel → `LuisoOutlineButton`. Keep all field labels and
  parse/decimal keyboard types unchanged.
- Replace any `Color(0x...)`/`Color.White` with roles/tokens; keep `Color.White`
  ONLY for `onPrimary`/`onError` button text.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Full regression

**Verify**:
- `./gradlew test --console=plain` → exit 0
- `grep -n 'Color(0x\|Color.White' DenominationManagementScreen.kt` → 0
- `git status --short` → only the in-scope file modified.

## Test plan

No new tests (settings UI). Regression: full suite green + `assembleDebug` in
final verify.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin` exit 0; `./gradlew test` exit 0
- [ ] `grep 'Color(0x\|Color.White'` on the screen → 0
- [ ] Sections headed by `LuisoSectionHeader`; dialogs and save/cancel use Luiso components
- [ ] All CRUD callbacks, validation, model types and labels untouched
- [ ] `plans/el-luiso-redesign/README.md` row 007 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Screen structure differs from "Current state".
- You find yourself changing callback signatures, adding ViewModel surface,
  or touching domain models (Money, Currency, Product, MeasurementUnit).
- A needed color/component doesn't exist — STOP, do not invent literals.

## Maintenance notes

- This screen is the admin CRUD reference; future configuration features should
  reuse `LuisoCard` rows + `LuisoTextField` + `LuisoButton` patterns set here.
- The currency radio pattern must visually pair with the counter screen's
  currency selector (004) so users recognize the same control family.