# Plan 005: Reskin the stock screens (StockScreen + StockReportScreen)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`
> On a mismatch, treat it as a STOP condition. Empty (no diff) is expected.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (visual-only)
- **Depends on**: plans/el-luiso-redesign/001-*.md, plans/el-luiso-redesign/002-*.md
- **Category**: design-system
- **Planned at**: commit `999dec4`, 2026-09-07
- **Revised at**: wave-3 re-plan — removed the stock-state chip deliverable
  after the first executor correctly STOPPED (states don't exist in this
  codebase; adding them requires business logic, out of scope).

## Why this matters

The stock screens are the "Inventario" tab. The El Luiso kit demands a clean,
branded inventory list with name, price, stock, and prominent actions. **There
is no stock-state feature today** (no Normal/Bajo/Agotado chips, no search, no
filters, no low-stock threshold in the domain). This plan restyles BOTH stock
screens to the brand's component system WITHOUT touching stock logic,
repository access, stock-report PDF/CSV behavior, or number formats. State
chips / search / filters are product features — NOT this plan.

## Current state (verified against code)

- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`:
  - `StockScreen(:57)` — `Scaffold` + `TopAppBar("Stock")` tinted primary;
    `LazyColumn` with `SectionTitle("PRODUCTOS")`, product `ProductRow`s, the
    empty-state `Text` ("No hay productos en el stock…"), `AGREGAR PRODUCTO`
    and `REPORTE DE EXISTENCIA` as `FilledTonalButton`, and the add/edit/
    delete `ProductDialog`s.
  - `SectionTitle(text)(:229)` — plain `Text`, `titleMedium`, Bold, primary.
  - `ProductRow(:239)` — `Card` (surface) with name + one `bodySmall` line
    (stock · unit price · currency · optional "+ recargo"); `Edit`/`Delete`
    `IconButton`s (delete tinted `error`). **No stock-state chip exists.**
  - `ProductDialog(:300)` — `AlertDialog` with `OutlinedTextField`s (Nombre,
    Cantidad, Precio, Recargo), `OutlinedButton` dropdowns (Unidad, Moneda),
    `TextButton` confirm/cancel.
  - `parseDecimalInput(input)(:471)` — decimal parse helper (DO NOT TOUCH).
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`:
  - `StockReportScreen(:44)` — `TopAppBar("Existencias")` w/ `ArrowBack`
    nav icon, summary `Card` (TOTAL EN EXISTENCIA + products count), a
    PRODUCTO/CANTIDAD/V.UNIT/TOTAL label row, per-product `StockLine` cards,
    empty text ("No hay existencias registradas."), and `EXPORTAR PDF` /
    `EXPORTAR EXCEL (CSV)` as `FilledTonalButton`s (use `Icons.Default.Share`).
  - `StockLine(:217)` — name + stock + `effectiveUnitPrice` + `stockValue`
    (primary, Bold). Uses `formatMoneyBigDecimal` already.
  - Both files already use `MaterialTheme.colorScheme.*` roles only → `grep
    'Color(0x\|Color.White'` on both currently returns **0**.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Tests | `./gradlew test --console=plain` | exit 0 |
| Greps | `grep -n 'Color(0x\|Color.White' <the two stock screens>` | 0 matches |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt`

**Out of scope** (do NOT touch):
- `ui/theme/*`, any other screen, `StockRepository`/`JsonStockRepository`,
  `.pdf`/`.csv` exporters, ViewModel logic.
- **Do NOT invent stock states (Normal/Bajo/Agotado), thresholds, search, or
  filters** — they don't exist today and adding them needs domain/VM work.

## Git workflow

- Worktree branch created by orchestrator: `exec/005-rpt`.
- Commit(s) conventional lowercase, e.g. `feat(stock): apply El Luiso theme`.
  Do NOT push.

## Steps

### Step 1: StockScreen header + actions

- Replace `TopAppBar` with `LuisoTopBar(title = "Inventario")` (keep the
  existing import style of the file; there is no back/settings icon on this
  screen — do not add one).
- `SectionTitle` → `LuisoSectionHeader(text = "PRODUCTOS")` (keep the
  `LuisoSectionHeader` modifier default; you may drop the local `SectionTitle`
  helper if nothing else uses it).
- Empty state `Text` → `LuisoEmptyState(message = "No hay productos en el
  stock. Agrega uno con cantidad, precio y recargo.")`. Use the yellow accent
  variant: `LuisoEmptyState(message = …, accentColor = LuisoYellow)`.
- `AGREGAR PRODUCTO` and `REPORTE DE EXISTENCIA` `FilledTonalButton`s →
  `LuisoButton(text = …)` (keep identical labels, keep `fillMaxWidth()`
  modifier via the `modifier` parameter). Keep their `Icon`s (Add /
  Description) via `leadingIcon = Icons.Default.Add` / `.Description`.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Product rows

- `ProductRow` inner `Card` → `LuisoCard` (keep the existing inner `Row`
  layout and all fields/text as-is — name `bodyLarge/Medium`, secondary
  `bodySmall` line).
- Keep `Edit` tint `primary`, `Delete` tint `error` (already correct).
- **No stock-state chip**: do not add any colored state indicator; one does
  not exist and adding it is out of scope.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Dialogs + stock report

- `ProductDialog`'s `OutlinedTextField`s (Nombre, Cantidad, Precio, Recargo) →
  `LuisoTextField` (label preserved; `keyboardOptions` decimal values kept).
- Keep the Unidad/Moneda `OutlinedButton` dropdowns as-is (they are selector
  menus, not in the Luiso kit).
- Delete confirm `TextButton` stays `TextButton` with `Text("Eliminar", color =
  MaterialTheme.colorScheme.error)` (destructive, keep red).
- `StockReportScreen`: `TopAppBar` → `LuisoTopBar(title = "Existencias",
  navigationIcon = <existing ArrowBack IconButton>)`; summary `Card` →
  `LuisoCard` (keep `DetailRow` content); `StockLine` `Card`s → `LuisoCard`;
  `EXPORTAR PDF`/`EXPORTAR EXCEL (CSV)` buttons → `LuisoButton(text = …,
  leadingIcon = Icons.Default.Share, modifier = Modifier.fillMaxWidth())`.
- The label row + empty text stay as-is (already themed).

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
- [ ] Header + both action buttons use Luiso components; section title is a
      `LuisoSectionHeader`; rows/dialog-fields use `LuisoCard`/`LuisoTextField`
- [ ] No logic/export/ViewModel/format changes (diff is UI-only)
- [ ] `plans/el-luiso-redesign/README.md` status row for 005 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- The stock screens' code doesn't match "Current state" (drifted).
- A step cannot be done with existing tokens/components and would require new
  Material3 components or new dependencies.
- You believe a stock-state/search/filter feature is required — it is NOT;
  report it as a finding, do not implement it.

## Maintenance notes

- Kit §9 (Inventario) mentions stock states Normal→green/low→yellow/out→red,
  plus search+filters. None exist in this codebase. LOG this as a finding for
  a future feature plan; do not fake it here.
- The add/product dialog pattern established here mirrors 007's dialogs.