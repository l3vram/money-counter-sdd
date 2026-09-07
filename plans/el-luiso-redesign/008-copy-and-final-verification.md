# Plan 008: Copy consistency, empty/feedback states and final verification

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui app/src/main/java/com/moneycounter/MainActivity.kt app/src/main/res/values/strings.xml`
> On a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED (touches every screen's strings; keep functional labels exact)
- **Depends on**: plans 004, 005, 006, 007 (all reskins)
- **Category**: design-system (copy + a11y)
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

The kit (`UI_GUIDELINES.md`) requires human, direct copy: "¡Venta registrada!",
"El Luiso está listo. Registra tu primera venta.", empty states with the
brand, feedback that isn't system-speak. After the reskins (004–007), this
plan sweeps the remaining copy, adds branded empty states + mascot placeholder,
reviews accessibility (touch targets ≥ 48dp, contrast, dark mode), and runs the
FINAL full verification of the branch (compile + full suite + APK).

## Current state

- Copy today is hardcoded inline Spanish in each screen; `strings.xml` only has
  `app_name` ("El Luiso", from plan 003) + `tagline`.
- Empty states: the counter shows a "no products for this currency" preview
  hint; Stock/Reports have minimal or no brand-shaped empty states.
- The repo convention is hardcoded string literals (0 `stringResource` uses).
  → KEEP that convention EXCEPT the already-injected `strings.xml` values; do
  NOT migrate all literals to resources in this plan (large refactor, separate
  concern).
- Mascot: none exists; plan 002 built `LuisoCircle`/`LuisoEmptyState` with an
  "L" placeholder.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Full tests | `./gradlew test --console=plain` | exit 0 |
| Assemble | `./gradlew assembleDebug --console=plain` | exit 0 |
| Greps | below per-step | expected counts in each step |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/*.kt` (copy + empty-state composables)
- `app/src/main/java/com/moneycounter/ui/components/Components.kt` (only if a
  helper is missing — otherwise not)
- `app/src/main/java/com/moneycounter/MainActivity.kt` (only: labels "Contador",
  "Stock", "Reportes"; commented fontScale 1.2 cap already present)
- `app/src/main/res/values/strings.xml` (tagline already added in 003)

**Out of scope** (do NOT touch):
- `ui/theme/*`, domain, repos, ViewModel, exporters, `app/build.gradle.kts`
- Any migration of literals to `strings.xml` beyond the existing two entries
- Dark-mode palette values (done in 001)

## Git workflow

- Worktree branch created by orchestrator: `exec/008-rpt`.
- Commit conventional lowercase, e.g. `feat(copy): El Luiso voice and empty states`. Do NOT push.

## Steps

### Step 1: Voice — feedback and labels

Every **user-facing feedback string** goes through the kit's voice; factual
labels (money, dates, section names) stay exact:

- Success feedback: "¡Conteo registrado!" (counter save), "Producto agregado",
  "Guardado" — where a Toast/notification exists, use
  `"¡<resultado>!"` per `UI_GUIDELINES` examples.
- Errors: human phrasing, no system-speak — e.g. current counter error strings
  that contain technical words like "COMPLETED"/status codes get rewritten to
  "No pudimos guardar el conteo. Revisa los datos e intenta nuevamente."
- Neutral: prefer "Ventas de hoy", "Stock bajo" over raw technical captions.
- Keep counts, money formatting, currency codes, sort labels as-is (they are
  factual).

**Verify**: `grep -rn 'COMPLETED\|status\|Error:' app/src/main/java/com/moneycounter/ui/screens` → no UI-facing matches (domain-use in VM strings is out of scope).

### Step 2: Stock-critical empty/state screens

Where data can be empty and today shows a bare list, add a `LuisoEmptyState`:
- Counter, no products for the selected currency:
  `"El Luiso está listo. Registra tu primer conteo."` with the mascot
  placeholder (`LuisoCircle` with "L").
- Stock: "Todavía no hay productos. Agrega el primero."
- Reports (a month with no counts visible in selection-filtered view): keep
  TOTAL row semantics; only when the ENTIRE list is empty add
  "Todavía no hay reportes."

Do NOT add empty states that would hide functional zeros (e.g. a currency with
0 total still shows its SummarySection numbers).

**Verify**: `grep -rn 'Todavía no hay\|El Luiso está listo' app/src/main/java/com/moneycounter/ui/screens` → at least 2 matches; compile exit 0.

### Step 3: Navigation labels + touch/contrast review

- `MainActivity.kt` bottom-nav labels: "Contador", "Stock", "Reportes" stay as
  user-selected (3 tabs). If any label differs from these today, standardize to
  the three. Toggle labels ("Seleccionar"/"Listo") unchanged.
- Touch review: primary actions are ≥48dp tall (Luiso components enforce it);
  icon-only buttons (`IconButton`) use default 48dp. Do NOT add padding hacks.
- Contrast audit (quick): text that sits on `LuisoCream` with `onSurfaceVariant`
  `#5A5A43` is fine; if any screen forces `onOnSurfaceVariant`-on-yellow text
  under `LuisoYellow`, switch ON-YELLOW text to `LuisoInk`. Purely compositional
  color-only states in Reports already carry text (TOTAL labels) — keep them.

**Verify**: `grep -rn 'onTertiary\|LuisoInk' app/src/main/java/com/moneycounter/ui/screens` shows on-yellow text using ink; compile exit 0.

### Step 4: FINAL full verification (branch-wide)

**Verify**:
- `./gradlew compileDebugKotlin --console=plain` → exit 0
- `./gradlew test --console=plain` → exit 0 (full suite: 71 unit tests + any
  added by plans 001–007 → expect 71 unless a plan added tests early)
- `./gradlew assembleDebug --console=plain` → exit 0 (APK produced)
- `grep -rn 'Color(0x' app/src/main/java/com/moneycounter/ui/screens` → 0 matches (verifies no screen hardcodes a color — only `ui/theme` may)
- `git status --short` → only intended files modified.
- Chain of the last three merges intact: `git log --oneline -5`.

## Test plan

No new unit tests (copy/a11y). The hard gate is the full suite + assemble on
the final branch state — that is this plan's verification.

## Done criteria

All must hold:

- [ ] `./gradlew compileDebugKotlin`, `./gradlew test`, `./gradlew assembleDebug` all exit 0
- [ ] `grep -rn 'Color(0x' app/src/main/java/com/moneycounter/ui/screens` → 0
- [ ] No UI-facing string contains status codes/technical words
- [ ] At least two branded empty states present (counter + stock), using `LuisoEmptyState`/`LuisoCircle`
- [ ] Nav labels are exactly Contador / Stock / Reportes
- [ ] No files outside the in-scope list modified
- [ ] `plans/el-luiso-redesign/README.md` row 008 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- A reskin plan (004–007) changed a screen in ways this plan's drift check
  shows as conflicts — reconcile by reading the live code, else STOP.
- You believe you must migrate ALL strings to `strings.xml` to satisfy "copy"
  — STOP; that is a separate refactor, out of scope.
- A test fails that passed at the branch tip before your change.

## Maintenance notes

- Copy stays hardcoded by repo convention; the strings.xml
  `app_name`+`tagline` are the two exceptions. A future i18n plan migrates
  literals to resources — it will touch every screen; do not start it here.
- `LuisoCircle` ("L") is the mascot placeholder until a real asset arrives
  (mirror note in plan 002).
- Accessibility rule to keep: stock state is always color + word; on-yellow
  text must be `LuisoInk`.