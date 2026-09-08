# Implementation Plans — Product Multi-Currency Prices

Run "product-multi-currency" for the Money Counter Android app. Branch: `feature/product-multi-currency` from `main` at `e8665ca`. Objective:

- **Product price map**: One product with `prices: Map<currencyId, ProductPrice>` and a single `stock`. No more duplicate products for the same physical item in different currencies.
- **Auto-merge on migration**: Existing products with the same `(name, unit)` are merged on first load (JSON v3→v4 migration). Stock = max across merged entries; prices = union.
- **Stock report currency selector**: Existencias (stock report) has a visible currency selector; total computed in the selected currency.
- **Currency visible in reports**: All report screens show the currency code+symbol prominently.

## Execution order & status

| Plan | Title | Priority | Effort | Depends on | Wave | Status |
|------|-------|----------|--------|------------|------|--------|
| 001  | Product price-map domain + JSON v4 + auto-merge + compile ripple | P1 | L | — | 1 | TODO |
| 002  | ViewModel productsWithPrice + counter per-currency display | P1 | M | 001 | 2 | TODO |
| 003  | Stock screen per-currency price dialog + ProductRow multi-currency | P1 | M | 001 | 2 | TODO |
| 004  | Stock report currency selector + per-currency export labels | P1 | M | 001 | 2 | TODO |
| 005  | Reports currency visible across all screens | P1 | S | — | 1 | TODO |

Wave 1 = 001 + 005 (disjoint: domain+repo vs reports UI). Wave 2 = 002 + 003 + 004 (all depend on 001, mutually disjoint files).

Status values: TODO | IN PROGRESS | DONE | BLOCKED (with one-line reason) | REJECTED (with one-line rationale).

## Dependency notes

- 001 introduces `ProductPrice` data class, rewrites `Product` to `prices: Map<String, ProductPrice>` with single `stock`, bumps `products.json` to v4 (reads v1/v2/v3 + auto-merge), and mechanically updates all call sites (VM, screens, exporters, tests). This is the foundation.
- 002 renames `productsForCurrency` → `productsWithPrice`, adds per-currency badge in counter ProductRow. Depends on 001 because it uses `hasPriceIn`.
- 003 rewrites the StockScreen `ProductDialog` to show per-currency price fields and changes VM `addProduct`/`editProduct` to accept `prices: Map<String, ProductPrice>`. Depends on 001 for the new model.
- 004 adds a currency selector to `StockReportScreen` (local state, not global) and updates export labels. Depends on 001 for `stockValueFor`.
- 005 adds currency badges/labels to `ReportsScreen`, `HistoryDetailScreen`, `UnifiedReportScreen`. Independent of product model (uses currencies list + saved.currencyId).

## Design decisions

1. **Price map per currency** (user chose): Product has `prices: Map<currencyId, ProductPrice>` instead of single `unitPrice/surcharge/currencyId`. Handles N currencies; respects that currencies are user-configurable.
2. **Auto-merge by name+unit** (user chose): On JSON migration, products with identical normalized `(name, unit)` are merged into one. Stock = max across entries (never sum — duplicates represent the same physical product). Prices = union of all entries.
3. **Stock report uses local currency state**: The Existencias screen has its own `reportCurrencyId` (not the global `selectedCurrencyId`), so the user can preview in any currency without changing the counter context.
4. **Counter hides products without price**: `productsWithPrice(currencyId)` filters to products with a price for the selected currency. Products with no price in that currency are hidden from the selector (prevents accidental cross-currency selection).
5. **Exporters compute per-currency**: PDF/CSV stock exports now accept `currencyId, currencySymbol, currencyCode` and compute values using `priceFor(currencyId)`.

## Findings considered and rejected

- **Keep per-currency products with shared stock via group key**: Rejected in favor of the price-map model because the user explicitly wanted "one product, two prices, shared stock" and the group approach keeps duplicate entries in the UI.
- **Fixed dual-price fields (CUP/USD)**: Hardcodes two currencies; breaks if user adds a 3rd. Rejected because currencies are user-configurable in settings.
- **Auto-merge stock = sum**: Rejected because duplicates represent the same physical product; summing would double-count. Max is the safe choice.
