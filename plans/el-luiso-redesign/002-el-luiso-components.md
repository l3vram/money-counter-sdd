# Plan 002: Build the Luiso component kit

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/components app/src/main/java/com/moneycounter/ui/theme`
> If anything changed since commit `999dec4`, compare the excerpts below
> against live code; on a mismatch treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED (new public composables, additive; nothing consumes them yet)
- **Depends on**: plans/el-luiso-redesign/001-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

The reskin plans (004–007) must NOT hand-roll buttons, cards and headers per
screen — the design kit's `AGENT_IMPLEMENTATION.md` demands a reusable
component system ("Construye un Design System de El Luiso y úsalo de forma
consistente"). This plan creates the component kit after plan 001's tokens, so
screens can be restyled by composition instead of by re-declaring shapes and
styles. `DenominationRow.kt` (the one existing shared component) also migrates
to semantic theme colors.

## Current state

- `app/src/main/java/com/moneycounter/ui/components/` contains a single file:
  `DenominationRow.kt` (denomination chip row with `formatMoney` /
  `formatMoneyBigDecimal` helpers and money formatting). It uses
  `MaterialTheme.colorScheme.*` and `Color.White` in places.
- Tokens from plan 001: brand colors live in
  `app/src/main/java/com/moneycounter/ui/theme/Color.kt`, shapes in
  `Shape.kt` (`LuisoShapes`), spacing in `Dimens.kt`
  (`Dimen4…Dimen48`), typography in `Type.kt` (`LuisoTypography`).
- Screens currently build custom `Button`, `OutlinedButton`, `Card` (
  note: ReportsScreen uses plain rows, no cards), `TextField`, `TopAppBar`
  call sites directly — they will switch to these components in 004–007.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Grep | `grep -rn 'MaterialTheme.colorScheme' app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` | only theme-derived colors after step 3 |

## Scope

**In scope** (the files to create/modify):
- `app/src/main/java/com/moneycounter/ui/components/Components.kt` (create — all new composables)
- `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` (modify — token migration)

**Out of scope** (do NOT touch):
- Any file under `app/src/main/java/com/moneycounter/ui/screens/`
- `app/src/main/java/com/moneycounter/MainActivity.kt` — the bottom nav is
  restyled in plan 003; do not touch it here even though the kit defines a
  `LuisoBottomNavigation`.
- Material icons beyond what the repo already uses (no new dependencies).

## Git workflow

- Worktree branch created by orchestrator: `exec/002-rpt`.
- Commit per logical unit, conventional lowercase
  (`feat(components): add Luiso component kit`), pure additive; do NOT push.

## Steps

### Step 1: Create `Components.kt`

Create `app/src/main/java/com/moneycounter/ui/components/Components.kt`
containing these composables, all reading colors from
`MaterialTheme.colorScheme` (which plan 001 already rebased onto the El Luiso
palette) and spacing/shape from the `Dimens.kt` / `LuisoShapes` tokens:

1. `LuisoButton(text, onClick, modifier, enabled = true, leadingIcon = null)`
   — primary button: `Button` default `primary` container, rounded `12.dp`
   corners, `Modifier.height(48.dp)` (kit touch target), body spacing `Dimen16`
   horizontal. Optional leading `ImageVector`.
2. `LuisoOutlineButton(text, onClick, modifier, enabled = true)` —
   `OutlinedButton` with `primary` content/border color.
3. `LuisoCard(modifier, onClick = null, content) — optional Surface clickable,
   `LuisoShapes.medium` (12.dp) corners, `surfaceContainerLow`/`surface`
   background, padding `Dimen16`, no shadow by default (kit: "sombras sutiles
   o ninguna").
4. `LuisoTextField(value, onValueChange, label, modifier, hint = null,
   keyboardOptions = KeyboardOptions.Default)` — wraps `OutlinedTextField`
   with `colors = TextFieldDefaults.colors(focusedContainerColor =
   surfaceContainerLow, unfocusedContainerColor = surfaceContainerLow,
   focusedIndicatorColor = MaterialTheme.colorScheme.primary, ...)`, corner
   `12.dp`.
5. `LuisoTopBar(title, modifier, navigationIcon = null, actions = {})` —
   a `Surface` bar, `primary` background, `onPrimary` title text, height
   `Dimen56 = 56.dp` (add to Dimens if missing), `titleMedium` typography.
   NOT Material's `TopAppBar` (avoids `@ExperimentalMaterial3Api`); use
   `Row`/`Surface` + optional `IconButton` navigationIcon.
6. `LuisoEmptyState(message, modifier, icon = null, accentColor = null)` —
   centered `Column`: optional icon in a `LuisoCircle` (48dp circle in a
   `primaryContainer`-shaded `Surface`, containing the icon or a single
   character), message in `bodyLarge` `onSurfaceVariant`, centered.
7. `LuisoStatCard(label, value, modifier, valueColor = MaterialTheme.colorScheme.onSurface,
   icon = null)` — `LuisoCard` with a small `labelSmall` `onSurfaceVariant`
   caption, the value in `titleLarge` in `valueColor`, optional leading icon.
   Purpose: dashboard-style figures (totals per currency, TODAY amounts).
8. `LuisoSectionHeader(text, modifier, accent = false)` — `Row` with a
   `8.dp × 20.dp` accent bar in `LuisoYellow` (import from `ui.theme`) rounded
   `4.dp`, then `titleSmall`/`titleMedium` text in `onSurface`. When `accent`
   is false, use `primary` bar instead of yellow.
9. `LuisoCircle(content, modifier, size = 48.dp, background = ...)` — private
   helper used by `LuisoEmptyState` for the mascot placeholder ("L" inside a
   green circle), brand placeholder while no mascot asset exists.

All components take `modifier` parameters so screens can pad/weight them.
Parameter names must match the above exactly — screen plans reference them.

Also add any missing spacing constant to `Dimens.kt` you rely on (e.g.
`Dimen56 = 56.dp`) — that file is in scope this plan only for additions.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Migrate `DenominationRow.kt` to theme colors

Replace any literal whites/grays in `DenominationRow.kt` with
`MaterialTheme.colorScheme` roles (primary for selected/preview, surfaceVariant
for container backgrounds, onSurface for text). Keep `formatMoney` /
`formatMoneyBigDecimal` signatures and behavior byte-identical — those are the
money formatters every screen and future plan depends on.

**Verify**: `grep -rn 'Color(0x\|Color.White' app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` → no matches; then
`./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Compile + regression

**Verify**:
- `./gradlew compileDebugKotlin --console=plain` → exit 0
- `./gradlew test --console=plain` → exit 0
- `git status --short` → only `Components.kt`, `DenominationRow.kt`,
  `Dimens.kt` touched.

## Test plan

No new unit tests (UI components, no Compose harness in repo).

Manual acceptance (operator): components are visible once a screen plan (004+)
uses them — clicking any Luiso main-screen button triggers its action.

## Done criteria

All must hold:

- [ ] `Components.kt` defines `LuisoButton`, `LuisoOutlineButton`, `LuisoCard`,
      `LuisoTextField`, `LuisoTopBar`, `LuisoEmptyState`, `LuisoStatCard`,
      `LuisoSectionHeader`
- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] `grep -rn 'Color(0x\|Color.White' app/src/main/java/com/moneycounter/ui/components` returns nothing
- [ ] No files outside the in-scope list modified
- [ ] `plans/el-luiso-redesign/README.md` status row for 002 → DONE

## STOP conditions

Stop and report back (do not improvise) if:

- Plan 001's tokens do not exist (no `Color.kt` in `ui/theme/`).
- A step's verification fails twice after a reasonable fix attempt.
- Any component appears to require a Compose dependency not already used
  (e.g. a new icon outside `androidx.compose.material.icons.Icons.*` already
  present in the repo).

## Maintenance notes

- Keep components free of business logic: no repository calls, no ViewModel
  access, no state hoisted outside parameters.
- `LuisoCircle`/`LuisoEmptyState` stand in for the brand mascot. When a real
  mascot vector arrives, replace only the `LuisoCircle` internals.
- If a later screen needs a pattern (e.g. a currency chip), extend an existing
  component instead of hand-building a new one inline.