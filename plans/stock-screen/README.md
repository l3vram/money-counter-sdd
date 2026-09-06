# Implementation Plans — Stock / Inventory screen

Run "stock screen" for the Money Counter Android app. Objective (refined, approved by
user on 2026-09-06, in the user's language):

- Move PRODUCTOS out of the Ajustes (settings) screen into its own **Stock** tab reached
  via a new bottom navigation bar (`Contador` | `Stock`).
- Products become inventory: each product gains an available-quantity **stock** (decimal).
- The Stock list shows product – quantity – price – surcharge, with add/edit/delete
  (same UX as today).
- The product list still feeds the main (Contador) screen; the main screen shows the
  available stock and warns (but allows) when a sale quantity exceeds stock.
- Stock is deducted **only when a count is saved to history** (GUARDAR EN HISTORIAL,
  status COMPLETED). Deleting a saved sale does **not** restore stock.
- New **Existence report** screen: what is in stock, with PDF + Excel (CSV) export,
  same pattern as the existing sale/count report.

Branch: `feature/stock-screen`. Planned at commit `1c516bb` (2026-09-06). Not merged to
`main` until human Gate B.

## Recon facts (inline for executors — do not rediscover)

- Project root: `/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd`
- All app sources: `app/src/main/java/com/moneycounter/`
- Local unit tests: `app/src/test/java/com/moneycounter/domain/` (JUnit4, no Android
  runtime). `org.json:json:20240303` is a `testImplementation` dep — repositories are
  tested through extracted `toJson`/`fromJson` objects, never through the `Context`.
- Commands (run from project root):
  - Compile check: `./gradlew compileDebugKotlin --console=plain` → exit 0, BUILD SUCCESSFUL
  - Tests: `./gradlew test --console=plain` → exit 0
  - Release assembly (proves full build): `./gradlew assembleDebug --console=plain`
- Tech / conventions:
  - Kotlin + Jetpack Compose + Material 3. Money is `BigDecimal`, scale `Money.SCALE` (=2).
  - No navigation library. `MainActivity.kt` holds a single `currentScreen: String` state.
  - Screens: `Scaffold` + `TopAppBar` (primary colors), content in `LazyColumn`,
    rows as `Card`s, edit/delete as `IconButton`s on the row.
  - All user-facing strings are **Spanish** (this app's UI language). Keep them Spanish.
  - JSON repos are below `repository/`, atomic write via `*.tmp` + `renameTo`, silent
    `catch (e: Exception)`. Versioned readers accept current and previous version.
  - Reusable formatters: `formatMoney` / `formatMoneyBigDecimal` in
    `ui/components/DenominationRow.kt` — both take a `symbol` param and embed it; never
    prefix `$` manually. `formatDate(millis)` is a top-level fun in
    `ui/screens/HistoryScreen.kt:180`.
- FileProvider is already configured (`AndroidManifest.xml` → `${applicationId}.fileprovider`)
  and used by `util/PdfExporter.kt` and `util/ExcelExporter.kt` to share files.

## Execution order & status

Wave 1 = plan 001 (foundation). Wave 2 = 002–005 in parallel (all depend only on 001).

| Plan | Title | Priority | Effort | Depends on | Wave | Status |
|------|-------|----------|--------|------------|------|--------|
| 001  | Add `stock` to Product: model, products.json v2, ViewModel, tests | P1 | M | — | 1 | TODO |
| 002  | Stock tab screen + bottom navigation + remove PRODUCTOS from Ajustes | P1 | L | 001 | 2 | TODO |
| 003  | Main screen shows available stock + over-stock warning | P1 | S | 001 | 2 | TODO |
| 004  | Deduct stock when a count is saved to history | P1 | S | 001 | 2 | TODO |
| 005  | Existence report screen + PDF/CSV export | P1 | M | 001 | 2 | TODO |

Status values: TODO | IN PROGRESS | DONE | BLOCKED (with one-line reason) | REJECTED (with one-line rationale).

## Dependency notes

- 002, 003, 004, 005 each require 001 because 001 introduces `Product.stock`, the
  products.json v2 format, and the new `addProduct`/`editProduct` signatures they call.
- 002 alone touches `MainActivity.kt` (navigation). 003 touches `MoneyCounterScreen.kt`.
  004 touches `MoneyCounterViewModel.kt`. 005 touches `util/` exporters + a new screen.
  No two Wave-2 plans edit the same file, so they run in parallel worktrees without
  merge conflicts.
- 001 does NOT edit the Ajustes ProductDialog UI; it only adapts the two `onConfirm`
  lambdas there to pass `stock = BigDecimal.ZERO` so the tree keeps compiling until 002
  replaces that dialog (with a real Cantidad field) inside the new Stock screen.

## Findings considered and rejected

- "Deduct stock in real time as the user types quantity" — rejected: user chose
  deduct-on-save (stable; editing/removing sale rows must not touch inventory).
- "Block sales that exceed stock" — rejected: user chose warn-and-allow (stock may go
  negative).
- "Restore stock when a saved sale is deleted" — rejected: user chose no restore
  (inventory is physical; history is a record).