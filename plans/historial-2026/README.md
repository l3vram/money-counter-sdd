# Implementation Plans — Historial feature run

Generated 2026-09-03 for the "historial" run. Objective: allow saving a completed count
(target + denominations with quantities), browsing history, viewing detail, and exporting
as PDF.

This run edits the tree in place (no worktrees), matching the prior run's convention.

## Execution order & status

| Plan | Title | Priority | Effort | Depends on | Status |
|------|-------|----------|--------|------------|--------|
| 001  | SavedCount domain model + JSON repository | P1 | M | — | DONE |
| 002  | History state + save button in ViewModel/screen | P1 | M | 001 | DONE |
| 003  | History list, detail screen, PDF export | P1 | L | 001, 002 | DONE |

Status values: TODO | IN PROGRESS | DONE | BLOCKED (reason) | REJECTED (reason)

## Dependency notes

- 002 requires 001 (uses `SavedCount`/repository).
- 003 requires 001 + 002 (uses ViewModel history state + `SavedCount`).

## Recon facts (inline for executors)

- Project root: `/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd`
- Build: `./gradlew assembleDebug --console=plain`
- Compile: `./gradlew compileDebugKotlin --console=plain`
- Tests: `./gradlew test --console=plain`
- Tech: Kotlin, Jetpack Compose, Material 3, minSdk 26, targetSdk 34.
- Money is `BigDecimal`; currency formatting lives in `DenominationRow.kt`
  (`formatMoney`, `formatMoneyBigDecimal`).
- Persistence mirrors `JsonDenominationRepository` (org.json, atomic temp write).
- Navigation is string-based in `MainActivity.kt` (`currentScreen` state, no nav library).
- `material-icons-extended` is a dependency (History, Share, ArrowBack icons available).
- `androidx.core` is a dependency (FileProvider available).

## Findings considered and rejected

- Capturing a screenshot instead of PDF: rejected by user decision — chose native PDF
  (`android.graphics.pdf.PdfDocument`), no extra dependency.
