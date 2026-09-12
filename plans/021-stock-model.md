# Plan 021: Modelo de stock — StockItem + StockRepository + backfill de Product.stock (compat)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/repository app/src/main/java/com/moneycounter/viewmodel app/src/test`
> Compare excerpts; on mismatch STOP.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED — new persistence entity; read path must not change what screens show during the transition.
- **Depends on**: plans/020-ledger-scoping.md
- **Category**: migration
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

Master plan FASE 3/4 + sections 19–26: `Product.stock` must stop being the single
stock source of truth; stock becomes branch-scoped (`StockItem`), shared by every
authorized user of a branch. The master plan explicitly permits keeping
`Product.stock` as a temporary compatibility field while `StockItem` is introduced
and consumers migrate progressively. This plan builds the model, the branch-scoped
persistence, the idempotent backfill from `Product.stock`, and the merged read
path (StockItem for the current branch, falling back to `Product.stock` until the
row exists). It does NOT change any write; plan 022 makes every stock mutation
write both places.

## Current state

`domain/Product.kt`:
```kotlin
data class Product(
    val id: String, val name: String, val unit: String,
    val stock: BigDecimal = Money.ZERO,
    val prices: Map<String, ProductPrice> = emptyMap()) { ... }
```

`repository/JsonProductRepository.kt` — `ProductJson.VERSION = 4` (accepts 1–4 on read), `products.json`; `load()`/`save()`.

`viewmodel/MoneyCounterViewModel.kt`:
- uiState has `products: List<Product>`.
- Stock is mutated ONLY through: `saveCount` (`applyStockDeduction`, line 661), `registerCreditSale` (line 710), `registerWriteoff` (`applyWriteoff`, line 566), `addStock` (line 461), `addProduct` (line 381, ALTA movement when stock>0), `editProduct` (line 419, ENTRADA when delta>0), `deleteProduct` (445).
- Pure helpers in companion: `applyStockDeduction` (949), `applyWriteoff` (968); `MoneyCounterCalculator` and stock summaries read `Product.stock`.
- Plan 020 established `currentOrgId`/`currentBranchId` on the VM; that is what scopes stock here.

Repo existing pattern: synchronous `load()`/`save(All)`, `org.json` serialization objects, atomic write, tolerant read. Test pattern: `ProductJsonTest.kt` for serialization.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, green |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/domain/StockItem.kt` (create)
- `app/src/main/java/com/moneycounter/domain/Product.kt`
- `app/src/main/java/com/moneycounter/repository/StockRepository.kt` (create)
- `app/src/main/java/com/moneycounter/repository/JsonStockRepository.kt` (create, with a pure `StockJson`)
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/test/java/com/moneycounter/domain/StockItemTest.kt` (create)
- `app/src/test/java/com/moneycounter/domain/StockJsonTest.kt` (create)
- extend `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` (orgId tolerance)

**Out of scope**:
- Any stock WRITE going into both Product.stock and StockItem (plan 022).
- Stock screen UI changes beyond what the merged read provides.
- Removing `Product.stock` (explicitly deferred — cleanup plan will come after all consumers migrate).

## Steps

### Step 1: Create `StockItem.kt` (domain + pure ops)

```kotlin
data class StockItem(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val productId: String,
    val quantity: BigDecimal,
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank())
        require(organizationId.isNotBlank())
        require(branchId.isNotBlank())
        require(productId.isNotBlank())
        require(quantity.signum() >= 0)
    }
}
```
Top-level pure operations (semantic, NOT a generic updateStock — matches master section 26):
```kotlin
fun increaseStock(items: List<StockItem>, productId: String, amount: BigDecimal): List<StockItem>
fun decreaseStock(items: List<StockItem>, productId: String, amount: BigDecimal): List<StockItem>
fun adjustStock(items: List<StockItem>, productId: String, absoluteQuantity: BigDecimal): List<StockItem>
```
Each finds the item for `(currentBranchId=context, productId)`; `increase`/`decrease` return matched item with quantity ± amount (may go negative — warn-and-allow, consistent with `applyStockDeduction`); `adjust` sets absolute; keeps `updatedAt` bumped to `System.currentTimeMillis()` only for the touched item; missing productId → no-op (decrease/increase of a product with no row returns the list unchanged — callers create the row first).

**Verify**: compile.

### Step 2: Create `StockRepository.kt`

```kotlin
interface StockRepository {
    fun load(): List<StockItem>
    fun saveAll(items: List<StockItem>)
}
```
(All rows in one store, each row carrying org/branch — filtering is pure in the VM. Deliberately minimal like the other repos; business ops live in the VM + pure functions.)

### Step 3: Create `JsonStockRepository.kt` (+ pure `StockJson`)

- File `stock.json`, `StockJson.VERSION = 1`, tolerant read (skip malformed rows), atomic write.
- `StockJson.toJson(items)` / `StockJson.fromJson(json)` pure object — all JSON logic testable without Android.

**Verify**: compile + the new pure JSON tests in Step 5 pass.

### Step 4: VM — load, backfill (idempotent), merged read

- `private val stockRepository: StockRepository = JsonStockRepository(application)`; uiState add `stockItems: List<StockItem> = emptyList()`.
- `loadStock()` in init and re-run on tenant context change: load all items; for the context org+branch only, **backfill**: for every product with no StockItem row in that branch, create `StockItem(id = "si-$productId", org, branch, productId, quantity = product.stock, updatedAt = now)`. Persist only if rows were created. Idempotent (second run finds rows).
- `Product` gains `organizationId: String = ""` (copy-style, default blank like Movement). `ProductJson` VERSION→5 writing `organizationId`, `fromJson` accepting 1–5, reading blank when absent (tests: existing v4 docs still load, blank orgId). Stamp products with org on load via a pure helper `Product.withOrgId(orgId)` (fill only blank).
- Merged read: `fun branchStock(productId: String): BigDecimal` → the StockItem quantity for context branch if a row exists, else `product.stock` (compat fallback). Add `fun stockForBranch(items: List<StockItem>, branchId: String): Map<String,BigDecimal>` pure helper.
- `updateStockState()` helper: when `stockItems` changes, recompute nothing in products (Product.stock stays the compat copy for now) — the merged read resolves per call.
- `_uiState.update` for stockItems happens after every stock repo write; current consumers (`applyStockDeduction` results on `products`) remain the working set until plan 022 reconciles on reads too.

**Verify**: compile; `./gradlew testDebugUnitTest` green.

### Step 5: Tests

- `StockItemTest.kt`: validation (blank ids throw, negative qty throws), `increase/decrease/adjust` correctness, no-op for unknown productId, warn-and-allow negative.
- `StockJsonTest.kt`: round-trip, malformed row skipped, version mismatch → empty.
- `ProductJsonTest.kt` extension: v4 without `organizationId` reads with `""`; v5 round trip.
- Backfill test (pure, in StockItemTest or VM pure test): given products with stock and empty branch rows → all get rows `quantity == product.stock`; second pass creates none (idempotent).

**Verify**: `./gradlew testDebugUnitTest` → green, 246 + ~10 new.

## Test plan

As in Step 5, JUnit4 pure tests. Pattern: `ProductJsonTest.kt`, `StockDeductionTest.kt`.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] `StockJson` tolerant (malformed skipped) and versioned, proven by tests
- [ ] Backfill is idempotent and uses `Product.stock` as source (test), `Product.orgId` stamps blank-only (test)
- [ ] `branchStock()` falls back to `Product.stock` when no StockItem row exists (test)
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- Backfill would overwrite an existing StockItem row (must be missing-only).
- Stock items for a UNKNOWN branch would leak into the merged read (branch filter is mandatory).
- `ProductJson` version handling breaks existing `products.json` (must accept current v4 data unchanged).

## Maintenance notes

- Plan 022 makes writes dual (Product.stock + StockItem) so reports/closings snapshots and the Stock screen stay identical until `Product.stock` removal.
- `Product.stock` removal is a separate future cleanup plan; do not start it here.