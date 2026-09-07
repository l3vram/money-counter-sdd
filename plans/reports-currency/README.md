# Implementation Plans — Reports & product currency

Run "reports-currency" for the Money Counter Android app (continues the stock feature on
`feature/stock-screen`). Objective (approved by user on 2026-09-06, in the user's language):

- Products live in a currency: each product has a `currencyId`. The counter shows only
  products of the selected currency (no `(USD)`/`(CUP)` suffixes — filtering replaces
  labeling). Existing products migrate to CUP.
- **Reportes** replaces Historial: it becomes the 3rd bottom-nav tab; the counter's top-bar
  history icon is removed.
- Reports list is grouped by month → day (collapsible; current month + today expanded by
  default), each report shows its currency (code + symbol + amount).
- Reports are filterable by currency (default CUP) and **never mixed**: a unified summary
  ("GENERAR RESUMEN") can only include one currency.
- Unified summary merges the selected counts (products by name+unit, denominations by
  value) into a single PDF or Excel (CSV) export with a format chooser after generation.

Branch: `feature/stock-screen`. Planned at commit `8f77ad2` (2026-09-06). Not merged to
`main` until human Gate B.

## Execution order & status

Wave 1 = 006. Wave 2 = 007. Wave 3 = 008. Wave 4 = 009. Sequential: each builds on the
previous (006 → 007 → 008 → 009).

| Plan | Title | Priority | Effort | Depends on | Wave | Status |
|------|-------|----------|--------|------------|------|--------|
| 006  | Add `currencyId` to Product + SavedCount: model, JSON v3, migration, ViewModel, tests | P1 | M | — | 1 | DONE |
| 007  | Product currency in UI: Stock dialog + counter filter + remove History icon | P1 | M | 006 | 2 | DONE |
| 008  | Reportes: 3rd tab, grouped/selectable reports screen, currency filter | P1 | L | 006, 007 | 3 | DONE |
| 009  | Unified report: merge selected counts, PDF/CSV export with format chooser | P1 | M | 006, 008 | 4 | DONE |

Status values: TODO | IN PROGRESS | DONE | BLOCKED (with one-line reason) | REJECTED (with one-line rationale).

## Dependency notes

- 006 introduces `currencyId` on `Product` and `SavedCount`, bumps `products.json` and
  `count_history.json` to v3 (read-compatible with v1/v2), and rewrites
  `addProduct`/`editProduct` signatures + the StockScreen call sites.
- 007 consumes the new signatures: currency dropdown in the Stock product dialog, currency
  filter in the counter selector, and removes the history top-bar icon (so MainActivity
  drops `onNavigateToHistory`).
- 008 rewires navigation (3rd tab, retargets detail) and builds Reportes; it requires the
  007 MainActivity delta and 006 `SavedCount.currencyId`.
- 009 fills the `"summary"` placeholder 008 creates; needs `SavedCount` (006) + the wired
  branch (008). Exporter additions are strictly additive (existing `export(SavedCount)`
  untouched). `SavedProductItem` does NOT get a currency field — the unified report is
  single-currency; product lines inherit the parent count's currency.

## Findings considered and rejected

- "Same product multi-currency" — rejected: user wants distinct products per currency with
  their own quantity/price; filtering by currency removes the need for name suffixes.
- "Unified report mixing currencies" — rejected: user wants reports always in a single
  currency (filter, default CUP); mixing is blocked in the UI and the merge helper throws
  on mixed input.
- "Report format chosen before generating" — rejected: user chose the format AFTER
  generating, via a menu on the generated summary.
- Collapse default: user wants everything collapsed EXCEPT the current month and today
  (expanded).