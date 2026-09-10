# Refined Spec — Phase 2b: Unified Movement Ledger, Fiado fix, Expenses, Closings

> Builds on branch `feature/accounting-ops-phase2` (tip `8b8285e`). Negotiated with the owner
> 2026-09-09. This iteration refactors the four separate Phase-2 stores into ONE journal and
> adds expenses + closings. Executor plans live beside this file (008+). English is the contract;
> the owner reads the Spanish summary in chat.

## 0. The bug that triggered this (root cause, owned by the planner)

`registerCreditSale` (plan 004) required `CounterStatus.COMPLETED`, and the fiado button was
rendered only inside the `COMPLETED` branch of `MoneyCounterScreen` (`:846`). A credit sale has NO
cash counted, so the count never reaches COMPLETED → the button is hidden and the function returns
null → **nothing is saved**, and since no receivable is created, collection has nothing to settle.
The plan-004 spec ("same guards as saveCount: COMPLETED") was wrong for a no-cash sale. This
iteration fixes it and hardens against "a movement silently not saving" by routing every event
through one persisted journal and testing the actual VM entry points (not just pure mappers).

## 1. Confirmed decisions (owner, 2026-09-09)

1. **Unified Movement ledger** (refactor + migration), not bolt-on.
2. **Alta** = new product / initial stock; **Entrada** = restock of an existing product. Both are
   stock-in with a cost.
3. **Cierre**: default one-tap **"Cerrar el día"** (all open movements) + **optional manual selection**.
4. **Cobro UX**: popup of OPEN debts → selecting one **loads its product/amount into the counter** so
   the user enters the denominations (the cash actually received) → records the collection.

## 2. The unified model — a journal (libro diario)

One persisted store replaces `SavedCount`(sales), `Receivable`, `Payment`, `InventoryWriteoff`.

```kotlin
enum class MovementType { ALTA, ENTRADA, GASTO, MERMA, VENTA, VENTA_FIADO, COBRO }

data class MovementProductLine(name, unit, quantity: BigDecimal, unitPrice: BigDecimal, subtotal: BigDecimal)
data class MovementDenomination(value: Long, quantity: Long, subtotal: BigDecimal)  // arqueo

data class Movement(
    id: String, at: Long, type: MovementType, currencyId: String,
    concept: String?,                       // required for GASTO; debtor name for VENTA_FIADO/COBRO
    products: List<MovementProductLine>,    // empty for GASTO
    denominations: List<MovementDenomination>, // ONLY for VENTA and COBRO (the cash arqueo)
    amount: BigDecimal,                     // monetary value of the movement (see matrix)
    linkId: String?,                        // COBRO → the VENTA_FIADO it settles
    closingId: String?                      // null = open; set when included in a cierre
)
```

Effect matrix (drives stock, cash sign in a cierre, and whether denominations apply):

| Type | Stock | Cash | Denominations | Concept / notes |
|---|---|---|---|---|
| ALTA | +qty | −cost | no | new product / initial stock |
| ENTRADA | +qty | −cost | no | restock existing product |
| GASTO | — | −amount | no | service/expense description (required) |
| MERMA | −qty | loss | no | product write-off |
| VENTA | −qty | +cash | **yes (arqueo)** | cash sale |
| VENTA_FIADO | −qty | +receivable | **no** | credit sale; carries debtor name; `settled` derived from a linked COBRO |
| COBRO | — | +cash | **yes (arqueo)** | settles a VENTA_FIADO (`linkId`) |

A VENTA_FIADO is OPEN while no COBRO links to it; SETTLED once a COBRO does.

## 3. Behavior

- **Fiado (fixed)**: button **always visible**; registering needs products (total>0) + debtor name,
  **no denominations, no COMPLETED requirement**; always writes a VENTA_FIADO movement (−stock).
- **Cobro**: popup lists OPEN VENTA_FIADO; selecting one loads its product lines/amount into the
  counter; the user enters denominations; confirming writes a COBRO (arqueo, +cash, `linkId`=fiado).
- **Venta (cash)**: unchanged UX (count to COMPLETED, save) → writes a VENTA movement (arqueo).
- **Merma**: as today (Stock screen) → writes a MERMA movement.
- **Gasto (new view)**: concept + amount + currency (+ date/time auto) → GASTO movement. No stock.
- **Alta/Entrada (new)**: recorded when stock is created/restocked on the Stock screen → ALTA/ENTRADA
  movements (product, qty, cost).

## 4. Historial de movimientos (replaces the Resúmenes/Reportes list)

One list of all movements, newest first, grouped by month/day (reuse `ReportKeys`), each row tagged
by type with a clear **identifier = colored icon + short label**:

| Type | Icon (Material) | Color token | Label |
|---|---|---|---|
| ALTA | Add/Inventory2 | primary | Alta |
| ENTRADA | MoveToInbox | primary | Entrada |
| GASTO | Payments/MoneyOff | error | Gasto |
| MERMA | Delete/Warning | error | Merma |
| VENTA | PointOfSale | green | Venta |
| VENTA_FIADO | Schedule/CreditCard | warning | Fiado |
| COBRO | Savings/Paid | green | Cobro |

Tapping a row → a detail view (reuse/adapt `HistoryDetailScreen`) showing products, denominations
(if any), amount, concept, and whether it belongs to a closing.

## 5. Cierres (new view — replaces the "reporte unificado")

A cierre is a period close, not a report. Flow:
- Default **"Cerrar el día"** button: selects ALL open movements (closingId == null) up to now.
- **Manual selection** optional: pick specific open movements.
- Computes per-type totals as **producto — cantidad — dinero**, plus net cash and **remaining stock**.
- Persists a `Closing { id, at, currencyId, movementIds[], totalsSnapshot, stockSnapshot }` and stamps
  each included movement with `closingId` → **a movement can never be in two closings**.
- Export the closing to **PDF/CSV** (reuse `PdfExporter`/`ExcelExporter`).

`Closing` is immutable once created (snapshot). Closed movements stay in the historial, marked closed.

## 6. Migration (no data loss)

On first run of the new code, `JsonMovementRepository` seeds `movements.json` once from the legacy
files if present, then treats the journal as source of truth:
- `count_history.json` (SavedCount) → VENTA movements (with denominations).
- `receivables.json` → VENTA_FIADO (OPEN); SETTLED ones also emit a linked COBRO.
- `payments.json` → COBRO (link to the receivable's movement).
- `writeoffs.json` → MERMA.
Legacy files are **kept as backup** (not deleted). Migration is idempotent (guarded by a flag/marker).

## 7. Reliability requirement (the Phase-2 lesson)

Every "record a movement" path MUST have a test that exercises the **actual VM entry point** with
realistic UI state (not only a pure mapper), asserting the journal gains exactly one correctly-typed
movement. Plus mandatory on-device verification of fiado + cobro + gasto + cierre before Gate B.

## 8. Plan decomposition (proposed DAG — serial chain on shared VM/nav/screens)

| # | Plan | Depends | Risk | Note |
|---|---|---|---|---|
| 008 | Movement domain + `JsonMovementRepository` + one-time migration from legacy stores + tests | branch tip | MED | foundation; app still behaves as today, journal seeded |
| 009 | Route all writes through the journal; **fix fiado** (always-visible, no denominations); cobro loads counter; recordExpense/recordStockIn; VM entry-point tests | 008 | HIGH | behavioral core + the bug fix |
| 010 | Historial de movimientos view (repurpose reports list) with type identifiers + detail | 009 | MED | |
| 011 | Gastos view + Alta/Entrada stock-in movements on the Stock screen + nav | 009 | MED | |
| 012 | Cierres view ("Cerrar el día" + manual selection, per-type totals + remaining stock, closingId stamping, PDF/CSV export) | 009 (,010) | HIGH | replaces reporte unificado |

Serial because 009–012 all touch `MoneyCounterViewModel`, `MainActivity` nav, and shared screens;
running them in parallel worktrees would conflict. Each plan is chained on the previous approved tip
and reviewed before the next.

## 9. Non-goals / deferred

- Roles / Firestore / multi-branch (Phase 3+, designs 006/007) — unchanged, still later.
- Nota de crédito / débito (returns) — still deferred.
- Partial collections (a fiado paid in installments) — deferred; COBRO settles in full for now.
```
