# Plan 001: Build the El Luiso design system tokens and rework Theme.kt

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/theme`
> If anything under `ui/theme` changed since commit `999dec4`, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (theme swap affects every screen visually; code is additive)
- **Depends on**: none
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

The app currently uses a generic Material 3 green palette in `Theme.kt` and
hardcoded `Color(0x...)` literals scattered through screens. The user approved
a new brand identity ("El Luiso", from the design kit at
`/Users/marvel/Downloads/El_Luiso_Design_Kit/`): dark green `#0F5132`, growth
green `#22C55E`, yellow accent `#FACC15`, cream background `#F8FAE5`, ink text
`#1F2937`. This plan centralizes those colors plus typography, shapes and
spacing into `ui/theme/` tokens so every later reskin plan (004–007) consumes
a single source of truth. After this plan, screens still compile unchanged;
only the look changes.

## Current state

- `app/src/main/java/com/moneycounter/ui/theme/Theme.kt` — the only theme file.
  Lines 15–36 define `LightColorScheme` (green-primary, near-white surfaces),
  lines 38–59 `DarkColorScheme`, lines 61–81 `MoneyCounterTheme` composable
  exposing `MaterialTheme(colorScheme = …)` with a status-bar side effect.
  It uses `isSystemInDarkTheme()` as default.
- No `Type.kt`, `Shape.kt`, `Color.kt` or `Dimens.kt` exist.
- Hardcoded colors elsewhere: `grep -rn 'Color(0x' app/src/main/java` →
  39 matches total: 1 in `Theme.kt` (test fixture helpers outside `ui/`) and
  38 in `ui/screens/MoneyCounterScreen.kt`. `MoneyCounterScreen` hardcodes the
  same old green (`0xFF1B6B3A`) in its header/summary. Those are rewritten by
  plan 004, NOT here.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Grep | `grep -rn '0xFF1B6B3A' app/src/main/java` | no matches after step 3 |

## Scope

**In scope** (the only files you should modify or create):
- `app/src/main/java/com/moneycounter/ui/theme/Color.kt` (create)
- `app/src/main/java/com/moneycounter/ui/theme/Type.kt` (create)
- `app/src/main/java/com/moneycounter/ui/theme/Shape.kt` (create)
- `app/src/main/java/com/moneycounter/ui/theme/Dimens.kt` (create)
- `app/src/main/java/com/moneycounter/ui/theme/Theme.kt` (modify)

**Out of scope** (do NOT touch, even though they look related):
- Any screen file under `ui/screens/` — reskins happen in plans 004–007.
- `app/src/test/` — no behavior changes.
- `AndroidManifest.xml`, `strings.xml`, launcher icons — plan 003.

## Git workflow

- Branch name in the executor worktree: `exec/001-rpt` (recreated by the orchestrator).
- Commit per logical unit (tokens file, then theme rework), message style:
  conventional, lowercase, e.g. `feat(theme): add El Luiso color tokens`.
  Match the repo style from `git log --oneline -10` (short, imperative).
- Do NOT push or open a PR.

## Steps

### Step 1: Create `Color.kt`

Create `app/src/main/java/com/moneycounter/ui/theme/Color.kt` with the brand
palette. Exact values (from the approved design kit):

```kotlin
package com.moneycounter.ui.theme

import androidx.compose.ui.graphics.Color

// El Luiso brand palette (design kit: DESIGN_SYSTEM.md)
val LuisoGreen       = Color(0xFF0F5132) // primary — navigation, primary buttons, branding
val LuisoGreenBright = Color(0xFF22C55E) // secondary — growth, positive indicators
val LuisoYellow      = Color(0xFFFACC15) // accent — highlights, small details only
val LuisoCream       = Color(0xFFF8FAE5) // background / surface
val LuisoInk         = Color(0xFF1F2937) // text and financial figures
val LuisoError       = Color(0xFFBA1A1A) // destructive actions, stock out
```

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Create `Type.kt`, `Shape.kt`, `Dimens.kt`

- `Type.kt`: `val LuisoTypography = Typography()`. Material 3 default styles
  are acceptable (they approximate the kit's "Inter/Google Sans" direction);
  encode the usage hierarchy with a small comment block mapping
  `displayLarge`→branding/empty states, `headlineSmall`/`titleLarge`→titles,
  `bodyLarge/bodyMedium`→information, `labelMedium/labelSmall`→buttons/labels.
  Do NOT invent non-default styles — overriding sizes now breaks the layout
  budget that the reskin plans (004–007) depend on.
- `Shape.kt`: `val LuisoShapes = Shapes(...)` with rounded corners:
  small `8.dp`, medium `12.dp`, large `16.dp`, extraLarge `24.dp`.
- `Dimens.kt`: constants following the kit's 4dp scale:
  `Dimen4 = 4.dp`, `Dimen8 = 8.dp`, `Dimen12 = 12.dp`, `Dimen16 = 16.dp`,
  `Dimen20 = 20.dp`, `Dimen24 = 24.dp`, `Dimen32 = 32.dp`, `Dimen40 = 40.dp`,
  `Dimen48 = 48.dp`. (These are exported for later plans; nothing consumes
  them yet.)

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Rework `Theme.kt` to the El Luiso palette

Replace the two color schemes in `Theme.kt` with schemes derived from the brand
tokens. Light scheme (exact):

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = LuisoGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EFE3),
    onPrimaryContainer = LuisoGreen,
    secondary = LuisoGreenBright,
    onSecondary = Color(0xFF05200C),
    secondaryContainer = Color(0xFFD9F8E4),
    onSecondaryContainer = Color(0xFF0B3D1C),
    tertiary = LuisoYellow,
    onTertiary = LuisoInk,
    tertiaryContainer = Color(0xFFFFF3C4),
    onTertiaryContainer = Color(0xFF54461D),
    background = LuisoCream,
    onBackground = LuisoInk,
    surface = LuisoCream,
    onSurface = LuisoInk,
    surfaceVariant = Color(0xFFEFEFD8),
    onSurfaceVariant = Color(0xFF5A5A43),
    surfaceContainer = Color(0xFFF3F3DE),
    surfaceContainerHigh = Color(0xFFEDEED8),
    error = LuisoError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)
```

Dark scheme: use the same brand hues with dark-mode luminance lifted so text
contrast holds. Exact:

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BE3B8),
    onPrimary = Color(0xFF0A3D20),
    primaryContainer = LuisoGreen,
    onPrimaryContainer = Color(0xFFD9EFE3),
    secondary = LuisoGreenBright,
    onSecondary = Color(0xFF05200C),
    secondaryContainer = Color(0xFF0E4A22),
    onSecondaryContainer = Color(0xFFB8F5CC),
    tertiary = LuisoYellow,
    onTertiary = Color(0xFF3D3A0F),
    tertiaryContainer = Color(0xFF4C4411),
    onTertiaryContainer = Color(0xFFFFE98A),
    background = Color(0xFF161712),
    onBackground = Color(0xFFE8E9DE),
    surface = Color(0xFF161712),
    onSurface = Color(0xFFE8E9DE),
    surfaceVariant = Color(0xFF4A4A38),
    onSurfaceVariant = Color(0xFFCCCBB0),
    surfaceContainer = Color(0xFF1E1F18),
    surfaceContainerHigh = Color(0xFF25271F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)
```

Keep `MoneyCounterTheme` signature and the status-bar side effect exactly as
they are (lines 61–81 of the current file). Import the new token constants
from the same package (they live in `ui/theme`, so no import needed).

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0
(confirm the old `0xFF1B6B3A` is gone from `ui/theme/` and that
`grep -rn '0xFF1B6B3A' app/src/main/java/com/moneycounter/ui/` returns only
the 38 hits in `MoneyCounterScreen.kt`, which plan 004 removes).

### Step 4: Full regression

**Verify**:
- `./gradlew test --console=plain` → exit 0 (71 local JUnit tests: report,
  keys, aggregation, calculator, repositories — none touch UI).
- `git status --short` → only the theme files modified/created.

## Test plan

No new unit tests (pure theme constants; no Compose UI harness in this repo).
Regression safety: step-gated compile + the suite above.

Manual acceptance (operator): app renders in the new cream/green palette;
setting the system to dark mode flips to the dark variant; buttons and
navigation still readable.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] `Color.kt` exists and defines the five brand tokens with the exact hex
      values listed in Step 1
- [ ] `Type.kt`, `Shape.kt`, `Dimens.kt` exist and compile
- [ ] `grep -rn '0xFF1B6B3A' app/src/main/java/com/moneycounter/ui/theme` returns nothing
- [ ] No files outside the in-scope list modified
- [ ] `plans/el-luiso-redesign/README.md` status row for 001 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- The `Theme.kt` excerpt in "Current state" doesn't match the live file.
- A step's verification fails twice after a reasonable fix attempt.
- Changing the palette requires touching a screen file — it does not; if it
  seems to, STOP.

## Maintenance notes

- The light/dark schemes must keep M3 tonal roles filled (primary, container,
  surface variants) — the component plan (002) and screens (004–007) read
  `MaterialTheme.colorScheme.*`, not brand constants, so any future palette
  tweak belongs only in `Color.kt` + `Theme.kt`.
- `Dimens.kt` is exported for later plans; do not delete it as "unused" — the
  reskin plans consume those constants.