# Implementation Plans

Generated on 2026-09-03 for the Money Counter run "quantity step buttons write to field".

This run is a single focused bug fix (not a git repo — no branches/worktrees; executors
edit the tree in place under `app/src/main/java/`).

## Execution order & status

| Plan | Title | Priority | Effort | Depends on | Status |
|------|-------|----------|--------|------------|--------|
| 001  | Fix +/− so it writes the quantity into the field and stays in sync | P1 | S | — | DONE |

Status values: TODO | IN PROGRESS | DONE | BLOCKED (with one-line reason) | REJECTED (with one-line rationale)

## Dependency notes

- Single-plan run; no dependencies.

## Recon facts (inline for executors — not a git repo)

- Test command: `./gradlew test --console=plain` (currently green — baseline verified).
- Compile command: `./gradlew compileDebugKotlin --console=plain`.
- Release build: `./gradlew assembleRelease --console=plain` (minify disabled; proves compilation).
- Project root: `/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd`.
- All source under `app/src/main/java/com/moneycounter/`.
- Tech: Kotlin, Jetpack Compose, Material 3. Money is `BigDecimal` in the domain/UI layer.
- Quantity floor-at-zero logic lives in `MoneyCounterViewModel.incrementQuantity` /
  `decrementQuantity` and is correct; do not modify.

## Findings considered and rejected

- "Summary shows CONTADO twice" — rejected on read: the two `CONTADO` blocks are in mutually
  exclusive branches of `if (targetAmount != null && targetAmount > zero)` in
  `MoneyCounterScreen.kt`; only one renders at a time. Not a bug.
- "Long overflow in value × quantity" — rejected for MVP: `999_999_999²` stays within
  `Long.MAX_VALUE`; an overflow needs absurd manual input, and the calculator already guards
  negative quantities. Leaving a note in the plan's maintenance section instead of a fix.
- "Keyboard does not appear" — rejected as a code bug on code review: both quantity and target
  fields set `KeyboardType.Number`/`Decimal`; the numpad is correctly requested. The operator
  simply could not run an emulator; no defect found.
