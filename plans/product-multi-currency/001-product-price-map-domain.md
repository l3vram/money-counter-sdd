# Plan 001: Convert Product to per-currency price map + JSON v4 + auto-merge migration

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/product-multi-currency/README.md`.
>
> **Worktree**: Execute in an isolated worktree. Do NOT work on `main`.
> ```bash
> git worktree add -b exec/001-product-price-map /var/folders/bz/6r30lrbn7_3fj5wz16jlbv1c0000gn/T/opencode/wt-001 main
> ```
> cd into that worktree for ALL commands.
>
> **Drift check (run first)**: `git diff --stat e8665ca..HEAD -- app/src/main/java/com/moneycounter/domain/Product.kt app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt`
> If any file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: none
- **Category**: migration
- **Planned at**: commit `e8665ca`, 2026-09-07

## Why this matters

Products today carry a single `(unitPrice, surcharge, currencyId)`. Users who sell the same
physical product (e.g. "Petróleo") in two currencies must create two separate product entries
("Petróleo CUP" / "Petróleo USD"), each with its own stock count. Stock drifts out of sync.
The fix: one product with a `prices: Map<currencyId, ProductPrice>` and a single `stock`.
Existing duplicates are auto-merged by `(name, unit)` on migration.

## Current state

### Key files

| File | Role |
|------|------|
| `app/src/main/java/com/moneycounter/domain/Product.kt` | Domain model — currently: `id, name, unit, unitPrice, surcharge, stock, currencyId` |
| `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt` | Persistence — JSON version 3, reads v1/v2/v3 |
| `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` | VM — `addProduct`, `editProduct`, `productsForCurrency`, `productsTotal`, `productLineTotal`, `saveCount`, `applyStockDeduction` |
| `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` | Counter — `ProductsSection` filters by `currencyId`, `ProductRow` reads `unitPrice/surcharge/effectiveUnitPrice/stock` |
| `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` | Inventario — `ProductRow`, `ProductDialog` (single currency dropdown) |
| `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` | Existencias — sums `stockValue` across ALL products (latent bug) |
| `app/src/main/java/com/moneycounter/util/PdfExporter.kt` | PDF — `exportStockReport` takes `currencySymbol: String` |
| `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` | CSV — same signature |

### Product.kt current (lines 5–26)

```kotlin
data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal = Money.ZERO,
    val stock: BigDecimal = Money.ZERO,
    val currencyId: String = DefaultCurrencies.CUP.id
) {
    init {
        require(id.isNotBlank()) { "Product ID must not be blank" }
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(unit.isNotBlank()) { "Product unit must not be blank" }
        require(unitPrice.signum() >= 0) { "Unit price must be non-negative" }
        require(surcharge.signum() >= 0) { "Surcharge must be non-negative" }
    }
    val effectiveUnitPrice: BigDecimal
        get() = unitPrice.add(surcharge).setScale(Money.SCALE)
    val stockValue: BigDecimal
        get() = effectiveUnitPrice.multiply(stock).setScale(Money.SCALE)
}
```

### JsonProductRepository.kt version + write/read

- `VERSION = 3` (line 14)
- `toJson`: writes `unitPrice`, `surcharge`, `stock`, `currencyId` per product (lines 22–34)
- `fromJson`: reads version 1/2/3; currencyId defaults to CUP (line 64)

### VM: addProduct signature (line 345)

```kotlin
fun addProduct(name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
```

### VM: productsForCurrency (companion, line 586)

```kotlin
fun productsForCurrency(products: List<Product>, currencyId: String): List<Product> =
    products.filter { it.currencyId == currencyId }
```

### VM: productLineTotal (line 442)

```kotlin
fun productLineTotal(selection: ProductSelection): BigDecimal {
    val product = _uiState.value.products.firstOrNull { it.id == selection.productId } ?: return Money.ZERO
    val quantity = selection.quantity()
    if (quantity.signum() < 0) return Money.ZERO
    return product.effectiveUnitPrice.multiply(quantity).setScale(Money.SCALE)
}
```

### MoneyCounterScreen: ProductsSection filter (line 247)

```kotlin
val productsForCurrency = products.filter { it.currencyId == selectedCurrencyId }
```

### StockScreen: ProductRow reads (line 234–238)

```kotlin
Text("${product.stock.stripTrailingZeros().toPlainString()} ${product.unit} · " +
    "${symbol}${product.unitPrice.stripTrailingZeros().toPlainString()} por ${product.unit}" +
    " · ${currencyCodeOf(product.currencyId)}" +
    if (product.surcharge.signum() > 0) " · + recargo ${symbol}${product.surcharge.stripTrailingZeros().toPlainString()}" else "")
```

### StockReportScreen: totalValue (line 47)

```kotlin
val totalValue = inStock.fold(Money.ZERO) { acc, product -> acc.add(product.stockValue) }
```

### StockReportScreen: StockLine (lines 217, 226)

```kotlin
formatMoneyBigDecimal(product.effectiveUnitPrice, symbol)
formatMoneyBigDecimal(product.stockValue, symbol)
```

### PdfExporter: exportStockReport signature (line 59)

```kotlin
fun exportStockReport(products: List<Product>, currency: String, generatedAt: Long)
```

### ExcelExporter: exportStockReport signature (line 25)

```kotlin
fun exportStockReport(products: List<Product>, currency: String, generatedAt: Long)
```

### Test constructors — ProductJsonCurrencyTest (line 12)

```kotlin
private fun product(id: String, name: String, currencyId: String) = Product(
    id = id, name = name, unit = "Lb",
    unitPrice = BigDecimal("25.00"), surcharge = BigDecimal("2.00"),
    stock = BigDecimal("40.00"), currencyId = currencyId
)
```

### Test — StockDeductionTest (line 10)

```kotlin
private fun product(id: String, stock: String) =
    Product(id, "Name $id", "Lb", BigDecimal("2.00"), surcharge = BigDecimal("0.50"), stock = BigDecimal(stock).setScale(Money.SCALE))
```

### Conventions to follow

- Money: `Money.SCALE = 2`, `Money.ZERO = BigDecimal("0.00")`
- `formatMoneyBigDecimal(x: BigDecimal, symbol: String)` — never prefix `$` manually at call sites
- Denominations are global (not per-currency); denominations list unchanged
- `ProductSelection` unchanged (productId + quantityText)
- Tests: JUnit 4, `org.junit.Assert.*`, file `app/src/test/java/com/moneycounter/domain/`
- `./gradlew assembleDebug` and `./gradlew test` are the verification commands

## Commands you will need

| Purpose   | Command                              | Expected on success |
|-----------|--------------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin`       | exit 0              |
| Tests     | `./gradlew test`                     | 72+ tests pass      |
| Full build| `./gradlew assembleDebug`            | exit 0              |

## Scope

**In scope** (modify these files):
- `app/src/main/java/com/moneycounter/domain/Product.kt`
- `app/src/main/java/com/moneycounter/repository/JsonProductRepository.kt`
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (mechanical: construct Product with prices map; productLineTotal/productsTotal use priceFor; saveCount snapshot)
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` (filter + ProductRow reads)
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` (ProductRow reads, ProductDialog still passes single price — 003 refines dialog)
- `app/src/main/java/com/moneycounter/ui/screens/StockReportScreen.kt` (totalValue + StockLine per-currency)
- `app/src/main/java/com/moneycounter/util/PdfExporter.kt` (stock export signature + per-currency computation)
- `app/src/main/java/com/moneycounter/util/ExcelExporter.kt` (same)
- `app/src/test/java/com/moneycounter/domain/ProductJsonCurrencyTest.kt`
- `app/src/test/java/com/moneycounter/domain/StockDeductionTest.kt`
- `app/src/test/java/com/moneycounter/domain/ProductJsonTest.kt` (if it exists and constructs Product)

**Out of scope** (do NOT touch — other plans handle these):
- ProductDialog UI layout (per-currency price fields) — plan 003
- StockReportScreen currency selector UI — plan 004
- ReportsScreen / HistoryDetailScreen / UnifiedReportScreen — plan 005
- DenominationManagementScreen — unchanged

## Git workflow

- Branch: `exec/001-product-price-map` (worktree)
- Commit with message: `feat(product): price-per-currency map + JSON v4 + auto-merge`
- Message style matches repo (imperative, scope-prefixed)

## Steps

### Step 1: Create `ProductPrice` data class

Create a new file `app/src/main/java/com/moneycounter/domain/ProductPrice.kt`:

```kotlin
package com.moneycounter.domain

import java.math.BigDecimal

data class ProductPrice(
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal = Money.ZERO
) {
    init {
        require(unitPrice.signum() >= 0) { "Unit price must be non-negative" }
        require(surcharge.signum() >= 0) { "Surcharge must be non-negative" }
    }

    val effectiveUnitPrice: BigDecimal
        get() = unitPrice.add(surcharge).setScale(Money.SCALE)
}
```

**Verify**: File compiles (next step verifies the full module).

### Step 2: Rewrite `Product.kt` to use price map

Replace the entire file with:

```kotlin
package com.moneycounter.domain

import java.math.BigDecimal

data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val stock: BigDecimal = Money.ZERO,
    val prices: Map<String, ProductPrice> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Product ID must not be blank" }
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(unit.isNotBlank()) { "Product unit must not be blank" }
    }

    fun priceFor(currencyId: String): ProductPrice? = prices[currencyId]

    fun effectiveUnitPriceFor(currencyId: String): BigDecimal? =
        prices[currencyId]?.effectiveUnitPrice?.setScale(Money.SCALE)

    fun stockValueFor(currencyId: String): BigDecimal? =
        prices[currencyId]?.effectiveUnitPrice?.multiply(stock)?.setScale(Money.SCALE)

    fun hasPriceIn(currencyId: String): Boolean = prices.containsKey(currencyId)
}
```

Key changes from old model:
- Removed: `unitPrice`, `surcharge`, `currencyId`, `effectiveUnitPrice`, `stockValue`
- Added: `prices: Map<String, ProductPrice>`, `priceFor`, `effectiveUnitPriceFor`, `stockValueFor`, `hasPriceIn`

**Verify**: compilation will break in next steps; expected.

### Step 3: Rewrite `JsonProductRepository.kt` — JSON v4 + auto-merge

Replace the entire `ProductJson` object and `JsonProductRepository` class:

```kotlin
package com.moneycounter.repository

import android.content.Context
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductPrice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

object ProductJson {

    private const val VERSION = 4

    fun toJson(products: List<Product>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        for (product in products) {
            val item = JSONObject()
            item.put("id", product.id)
            item.put("name", product.name)
            item.put("unit", product.unit)
            item.put("stock", product.stock.toPlainString())
            val pricesObj = JSONObject()
            for ((curId, price) in product.prices) {
                val priceObj = JSONObject()
                priceObj.put("unitPrice", price.unitPrice.toPlainString())
                priceObj.put("surcharge", price.surcharge.toPlainString())
                pricesObj.put(curId, priceObj)
            }
            item.put("prices", pricesObj)
            array.put(item)
        }
        root.put("products", array)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Product> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrElse { return emptyList() }
        val version = root.optInt("version", 1)
        if (version !in setOf(1, 2, 3, 4)) return emptyList()
        val array = root.optJSONArray("products") ?: return emptyList()

        val raw = mutableListOf<Product>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val name = item.optString("name", "")
            val unit = item.optString("unit", "")
            if (id.isBlank() || name.isBlank() || unit.isBlank()) continue
            if (!seenIds.add(id)) continue

            val stock = runCatching {
                BigDecimal(item.optString("stock", "0")).setScale(Money.SCALE)
            }.getOrDefault(Money.ZERO)

            val prices = mutableMapOf<String, ProductPrice>()

            if (version == 4) {
                val pricesObj = item.optJSONObject("prices")
                if (pricesObj != null) {
                    for (curId in pricesObj.keys()) {
                        val pObj = pricesObj.optJSONObject(curId) ?: continue
                        val up = runCatching {
                            BigDecimal(pObj.optString("unitPrice", "0")).setScale(Money.SCALE)
                        }.getOrNull() ?: continue
                        val sc = runCatching {
                            BigDecimal(pObj.optString("surcharge", "0")).setScale(Money.SCALE)
                        }.getOrDefault(Money.ZERO)
                        if (up.signum() >= 0 && sc.signum() >= 0) {
                            prices[curId] = ProductPrice(up, sc)
                        }
                    }
                }
            } else {
                // v1/v2/v3 legacy
                val curId = item.optString("currencyId", DefaultCurrencies.CUP.id)
                val up = runCatching {
                    BigDecimal(item.optString("unitPrice", "0")).setScale(Money.SCALE)
                }.getOrNull() ?: continue
                val sc = runCatching {
                    BigDecimal(item.optString("surcharge", "0")).setScale(Money.SCALE)
                }.getOrDefault(Money.ZERO)
                if (up.signum() >= 0 && sc.signum() >= 0) {
                    prices[curId] = ProductPrice(up, sc)
                }
            }

            raw.add(Product(id, name, unit, stock, prices))
        }

        return mergeDuplicates(raw)
    }

    /**
     * Auto-merge products with identical (normalized name, normalized unit).
     * - prices: union across all entries in group (last write wins per currencyId)
     * - stock: max across group (never sum — duplicates represent the same physical stock)
     * - id: first entry in group wins
     * - name/unit: first entry in group wins
     */
    private fun mergeDuplicates(products: List<Product>): List<Product> {
        return products
            .groupBy { it.name.trim().lowercase() to it.unit.trim().lowercase() }
            .map { (_, group) ->
                val first = group.first()
                val mergedPrices = mutableMapOf<String, ProductPrice>()
                for (p in group) {
                    mergedPrices.putAll(p.prices)
                }
                val mergedStock = group.maxOfOrNull { it.stock } ?: Money.ZERO
                first.copy(stock = mergedStock, prices = mergedPrices)
            }
    }
}

class JsonProductRepository(private val context: Context) : ProductRepository {

    private val fileName = "products.json"

    override fun load(): List<Product> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()
            ProductJson.fromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun save(products: List<Product>) {
        try {
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(ProductJson.toJson(products))
            tempFile.renameTo(file)
        } catch (e: Exception) {
            // silently ignore persistence errors
        }
    }
}
```

**Verify**: `./gradlew compileDebugKotlin` will fail at this point — expected; call sites break in next steps.

### Step 4: Fix `MoneyCounterViewModel.kt` — minimal compile + per-currency totals

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:

**4a. `productsForCurrency` companion (line 586)** — change body to `hasPriceIn`:

```kotlin
fun productsForCurrency(products: List<Product>, currencyId: String): List<Product> =
    products.filter { it.hasPriceIn(currencyId) }
```

**4b. `productLineTotal` (line 442–447)** — use `effectiveUnitPriceFor`:

```kotlin
fun productLineTotal(selection: ProductSelection): BigDecimal {
    val product = _uiState.value.products.firstOrNull { it.id == selection.productId } ?: return Money.ZERO
    val quantity = selection.quantity()
    if (quantity.signum() < 0) return Money.ZERO
    return product.effectiveUnitPriceFor(_uiState.value.selectedCurrencyId)
        ?.multiply(quantity)?.setScale(Money.SCALE) ?: Money.ZERO
}
```

**4c. `productsTotal` (line 430–439)** — use `effectiveUnitPriceFor`:

```kotlin
fun productsTotal(): BigDecimal {
    val state = _uiState.value
    var total = Money.ZERO
    for (selection in state.productSelections) {
        val product = state.products.firstOrNull { it.id == selection.productId } ?: continue
        val quantity = selection.quantity()
        if (quantity.signum() < 0) continue
        total = total.add(
            product.effectiveUnitPriceFor(state.selectedCurrencyId)
                ?.multiply(quantity) ?: Money.ZERO
        )
    }
    return total.setScale(Money.SCALE)
}
```

**4d. `addProduct` (line 345)** — keep same signature; build `prices` internally:

```kotlin
fun addProduct(name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean {
    val cleanName = name.trim()
    val cleanUnit = unit.trim()
    if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
    if (unitPrice.signum() < 0 || surcharge.signum() < 0 || stock.signum() < 0) return false
    if (unitPrice.signum() == 0 && surcharge.signum() == 0) return false
    val state = _uiState.value
    val prices = mapOf(currencyId to ProductPrice(unitPrice, surcharge))
    val new = Product(generateProductId(state.products), cleanName, cleanUnit, stock, prices)
    val newProducts = state.products + new
    _uiState.update { it.copy(products = newProducts) }
    persistProducts(newProducts)
    return true
}
```

**4e. `editProduct` (line 360)** — build prices map from the single currency params:

```kotlin
fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean {
    val cleanName = name.trim()
    val cleanUnit = unit.trim()
    if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
    if (unitPrice.signum() < 0 || surcharge.signum() < 0 || stock.signum() < 0) return false
    val state = _uiState.value
    if (state.products.none { it.id == id }) return false
    val prices = mapOf(currencyId to ProductPrice(unitPrice, surcharge))
    val newProducts = state.products.map {
        if (it.id == id) it.copy(name = cleanName, unit = cleanUnit, stock = stock, prices = prices) else it
    }
    _uiState.update { it.copy(products = newProducts) }
    persistProducts(newProducts)
    return true
}
```

**4f. `saveCount` (line 456–504)** — snapshot price from selected currency:

At line ~477 (inside the `savedProducts` block), replace the line that reads `product.unitPrice` and `product.surcharge`:

```kotlin
val savedProducts = state.productSelections.mapNotNull { selection ->
    val product = state.products.firstOrNull { it.id == selection.productId } ?: return@mapNotNull null
    val quantity = selection.quantity()
    if (quantity.signum() <= 0) return@mapNotNull null
    val pp = product.priceFor(state.selectedCurrencyId) ?: return@mapNotNull null
    SavedProductItem(
        name = product.name,
        unit = product.unit,
        quantity = quantity,
        unitPrice = pp.unitPrice,
        surcharge = pp.surcharge,
        subtotal = pp.effectiveUnitPrice.multiply(quantity).setScale(Money.SCALE)
    )
}
```

**4g. `applyStockDeduction` companion (line 589–605)** — no change needed (it already does `product.copy(stock = ...)` and the `prices` field is carried via `copy`).

**Verify**: `./gradlew compileDebugKotlin` should now succeed for the VM. Continue to screens.

### Step 5: Fix `MoneyCounterScreen.kt` — filter + ProductRow

**5a. ProductsSection filter (line 247)**: Already calls `products.filter { it.currencyId == selectedCurrencyId }` — change to:

```kotlin
val productsForCurrency = products.filter { it.hasPriceIn(selectedCurrencyId) }
```

**5b. ProductRow (line 420–435)** — reads `selectedProduct.effectiveUnitPrice`, `selectedProduct.stock`, `selectedProduct.unit`, `selectedProduct.surcharge`:

Replace the `if (selectedProduct != null)` block at line 420:

```kotlin
if (selectedProduct != null) {
    val pp = selectedProduct.priceFor(selectedCurrencyId)
    val unitPrice = pp?.effectiveUnitPrice
    val outOfRange = selection.quantity() > selectedProduct.stock
    Text(
        text = if (pp != null)
            "${symbol}${pp.unitPrice.stripTrailingZeros().toPlainString()} por ${selectedProduct.unit}" +
            " · disponible: ${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit}"
        else "Sin precio en esta moneda",
        style = MaterialTheme.typography.labelSmall,
        color = if (pp == null) MaterialTheme.colorScheme.error
                else if (outOfRange) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (pp != null && pp.surcharge.signum() > 0 && lineTotal.signum() > 0) {
        Text(
            text = "incluye recargo ${pp.surcharge.stripTrailingZeros().toPlainString()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
    if (outOfRange) {
        Text(
            text = "⚠ Cantidad mayor que el stock disponible (${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
```

Note: `selectedCurrencyId` is passed into `ProductsSection` as a param (currently named `selectedCurrencyId`). The `ProductRow` composable at line 322 does not currently receive `selectedCurrencyId`. Add it as a parameter:

Change `ProductRow` signature (line 322) to add `selectedCurrencyId: String`. Update the call site (line 293) to pass it.

**Verify**: `./gradlew compileDebugKotlin` — should succeed.

### Step 6: Fix `StockScreen.kt` — ProductRow reads

In `StockScreen.kt`, the `ProductRow` composable (line 210–263) reads `product.unitPrice`, `product.surcharge`, `product.currencyId`. Update the info text (line 234–238):

Replace with a helper that renders prices from the map. The simplest: iterate `product.prices` and render each entry:

```kotlin
Column(modifier = Modifier.weight(1f)) {
    Text(
        text = product.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = "${product.stock.stripTrailingZeros().toPlainString()} ${product.unit}" +
                product.prices.entries.joinToString(" · ") { (curId, pp) ->
                    val sym = currencyCodeOf(curId)
                    "${sym}${pp.unitPrice.stripTrailingZeros().toPlainString()}" +
                    if (pp.surcharge.signum() > 0) " +${sym}${pp.surcharge.stripTrailingZeros().toPlainString()}" else ""
                },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

Note: `currencyCodeOf` is already passed as a lambda (line 213). Also need a `symbolOf` or `currencySymbolOf` to get the display symbol (not code). Actually `currencyCodeOf` returns the code (e.g. "CUP"). For the display the user wants symbol. Add a new param `symbolOf: (String) -> String` to `ProductRow` or reuse currencies list. The cleanest: pass `currencies: List<Currency>` to ProductRow and resolve symbol per currencyId. But to keep change minimal in 001: add `symbolOf: (String) -> String` param, pass from StockScreen: `symbolOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.symbol ?: id }`.

Update `ProductRow` signature to include `symbolOf: (String) -> String = { it }`.

**Verify**: `./gradlew compileDebugKotlin`.

### Step 7: Fix `StockReportScreen.kt` — per-currency total

In `StockReportScreen.kt`:

**7a.** Add a `selectedCurrencyId` (already derived from `uiState.selectedCurrencyId` — it's used implicitly for `currencySymbol`).

**7b.** Change `totalValue` (line 47) to use per-currency:

```kotlin
val totalValue = inStock.fold(Money.ZERO) { acc, product ->
    acc.add(product.stockValueFor(uiState.selectedCurrencyId) ?: Money.ZERO)
}
```

**7c.** In `StockLine` composable (line 194), the `symbol` param is the display symbol. But `product.effectiveUnitPrice` is gone. Change `StockLine` signature to accept `selectedCurrencyId: String`:

```kotlin
@Composable
private fun StockLine(product: Product, selectedCurrencyId: String, symbol: String) {
```

Inside, replace lines 217 and 226:

```kotlin
// Line 217 (unit price)
formatMoneyBigDecimal(product.effectiveUnitPriceFor(selectedCurrencyId) ?: Money.ZERO, symbol)
// Line 226 (stock value)
formatMoneyBigDecimal(product.stockValueFor(selectedCurrencyId) ?: Money.ZERO, symbol)
```

Update the call site (line 131) to pass `selectedCurrencyId = uiState.selectedCurrencyId`.

**7d.** Filter `inStock` — keep all products with stock != 0 (don't filter by price availability — a product with stock but no price in selected currency shows "0" for that column, which is honest).

**Verify**: `./gradlew compileDebugKotlin`.

### Step 8: Fix `PdfExporter.kt` — stock export per-currency

Change `exportStockReport` and `buildStockPdf` signatures to accept `currencyId` instead of just `currencySymbol`:

```kotlin
fun exportStockReport(products: List<Product>, currencyId: String, currencySymbol: String, currencyCode: String, generatedAt: Long) {
    val file = buildStockPdf(products, currencyId, currencySymbol, currencyCode, generatedAt)
    share(file)
}
```

Inside `buildStockPdf`:
- Line 318: `canvas.drawText("Moneda: $currencySymbol ($currencyCode)", margin, y, headerPaint)`
- Line 339: `formatMoneyBigDecimal(product.effectiveUnitPriceFor(currencyId) ?: Money.ZERO, currencySymbol)`
- Line 340: `formatMoneyBigDecimal(product.stockValueFor(currencyId) ?: Money.ZERO, currencySymbol)`
- Line 345: total uses `product.stockValueFor(currencyId) ?: Money.ZERO`
- Line 347: total label `formatMoneyBigDecimal(total, currencySymbol)`

**Verify**: `./gradlew compileDebugKotlin`.

### Step 9: Fix `ExcelExporter.kt` — same signature change

Apply the same pattern as PdfExporter: `exportStockReport` takes `currencyId, currencySymbol, currencyCode`. Inside `buildStockCsv`:

- Line 159: `lines += csvRow("Moneda", "$currencySymbol ($currencyCode)")`
- Line 168: `formatMoneyBigDecimal(p.effectiveUnitPriceFor(currencyId) ?: Money.ZERO, currencySymbol)`
- Line 170: `formatMoneyBigDecimal(p.stockValueFor(currencyId) ?: Money.ZERO, currencySymbol)`
- Line 175–176: total uses `p.stockValueFor(currencyId) ?: Money.ZERO`

**Verify**: `./gradlew compileDebugKotlin`.

### Step 10: Update call sites in screens that invoke exporters

In `StockReportScreen.kt`, the export buttons (lines 139, 154) currently call:

```kotlin
PdfExporter(context).exportStockReport(uiState.products, currencySymbol, System.currentTimeMillis())
ExcelExporter(context).exportStockReport(uiState.products, currencySymbol, System.currentTimeMillis())
```

Update to:

```kotlin
val cur = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }
val curSymbol = cur?.symbol ?: "$"
val curCode = cur?.code ?: ""

// PDF button:
PdfExporter(context).exportStockReport(
    uiState.products, uiState.selectedCurrencyId, curSymbol, curCode, System.currentTimeMillis()
)
// CSV button:
ExcelExporter(context).exportStockReport(
    uiState.products, uiState.selectedCurrencyId, curSymbol, curCode, System.currentTimeMillis()
)
```

Also update `LuisoTopBar(title = "Existencias")` — keep as-is; currency visibility is plan 005.

**Verify**: `./gradlew compileDebugKotlin` — full compile success expected.

### Step 11: Update tests

**11a. `ProductJsonCurrencyTest.kt`** — update `product()` helper to use `prices` map:

```kotlin
private fun product(id: String, name: String, currencyId: String) = Product(
    id = id,
    name = name,
    unit = "Lb",
    stock = BigDecimal("40.00"),
    prices = mapOf(currencyId to ProductPrice(
        unitPrice = BigDecimal("25.00"),
        surcharge = BigDecimal("2.00")
    ))
)
```

Update the `ProductJson v3 round trip` test — it now writes v4; add a test that v3 JSON input merges correctly. Update assertions:
- `loaded[0].currencyId` → removed; assert `loaded[0].hasPriceIn("cup")` is true and `loaded[0].priceFor("cup")?.unitPrice == BigDecimal("25.00")`.
- `loaded[0].stock` remains `BigDecimal("40.00")`.

Add a new test for auto-merge:

```kotlin
@Test
fun `fromJson auto-merges products with same name and unit`() {
    val json = """
        {
          "version": 3,
          "products": [
            {"id": "p1", "name": "Petroleo", "unit": "L", "unitPrice": "120.00", "surcharge": "0.00", "stock": "500.00", "currencyId": "cup"},
            {"id": "p2", "name": "Petroleo", "unit": "L", "unitPrice": "0.30", "surcharge": "0.00", "stock": "500.00", "currencyId": "usd"}
          ]
        }
    """.trimIndent()
    val loaded = ProductJson.fromJson(json)
    assertEquals(1, loaded.size)
    assertEquals("p1", loaded[0].id)
    assertEquals(BigDecimal("500.00"), loaded[0].stock)
    assertEquals(BigDecimal("120.00"), loaded[0].priceFor("cup")?.unitPrice)
    assertEquals(BigDecimal("0.30"), loaded[0].priceFor("usd")?.unitPrice)
}

@Test
fun `fromJson auto-merge stock takes max`() {
    val json = """
        {
          "version": 3,
          "products": [
            {"id": "p1", "name": "Petroleo", "unit": "L", "unitPrice": "120.00", "surcharge": "0.00", "stock": "300.00", "currencyId": "cup"},
            {"id": "p2", "name": "Petroleo", "unit": "L", "unitPrice": "0.30", "surcharge": "0.00", "stock": "500.00", "currencyId": "usd"}
          ]
        }
    """.trimIndent()
    val loaded = ProductJson.fromJson(json)
    assertEquals(1, loaded.size)
    assertEquals(BigDecimal("500.00"), loaded[0].stock)
}

@Test
fun `ProductJson v4 round trip preserves prices map`() {
    val products = listOf(
        Product("p1", "Petroleo", "L", BigDecimal("500.00"), mapOf(
            "cup" to ProductPrice(BigDecimal("120.00")),
            "usd" to ProductPrice(BigDecimal("0.30"))
        ))
    )
    val loaded = ProductJson.fromJson(ProductJson.toJson(products))
    assertEquals(1, loaded.size)
    assertEquals(2, loaded[0].prices.size)
    assertEquals(BigDecimal("120.00"), loaded[0].priceFor("cup")?.unitPrice)
    assertEquals(BigDecimal("0.30"), loaded[0].priceFor("usd")?.unitPrice)
}
```

**11b. `StockDeductionTest.kt`** — update `product()` helper:

```kotlin
private fun product(id: String, stock: String) = Product(
    id, "Name $id", "Lb", BigDecimal(stock).setScale(Money.SCALE),
    prices = mapOf("cup" to ProductPrice(BigDecimal("2.00"), BigDecimal("0.50")))
)
```

**11c. `ProductJsonTest.kt`** — if it exists and constructs Product with old params, update similarly.

**Verify**: `./gradlew test` → all tests pass (including new merge tests).
**Verify**: `./gradlew assembleDebug` → full build success.

## Test plan

- Existing tests updated to new Product constructor with `prices` map
- New test: `fromJson auto-merges products with same name and unit`
- New test: `fromJson auto-merge stock takes max`
- New test: `ProductJson v4 round trip preserves prices map`
- Pattern: follow `ProductJsonCurrencyTest.kt` style

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0; new auto-merge tests exist and pass
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `grep -rn "\.currencyId" app/src/main/java/com/moneycounter/domain/Product.kt` returns no matches
- [ ] `grep -rn "product\.unitPrice" app/src/main/java/com/moneycounter/ui/screens/` returns no matches
- [ ] `grep -rn "product\.surcharge" app/src/main/java/com/moneycounter/ui/screens/` returns no matches
- [ ] `grep -rn "product\.stockValue" app/src/main/java/com/moneycounter/ui/screens/` returns no matches
- [ ] `grep -rn "product\.effectiveUnitPrice" app/src/main/java/com/moneycounter/ui/screens/` returns no matches
- [ ] No files outside the in-scope list are modified (`git diff --name-only`)

## STOP conditions

Stop and report back if:

- The code at the locations in "Current state" doesn't match the excerpts (the codebase has drifted since this plan was written).
- A step's verification fails twice after a reasonable fix attempt.
- `./gradlew assembleDebug` fails after all steps.
- You discover a `Product` constructor call site not listed in the scope (there may be one in `HistoryDetailScreen.kt` — it reads `SavedProductItem`, which is unchanged; if it reads `Product` directly, add to scope).

## Maintenance notes

- Products with no prices in any currency are allowed (empty map). The counter will hide them (no price for selected currency). Stock report shows them with "0" for value.
- Products with stock but no price in a selected report currency show "0" total — this is honest (price unknown in that currency, stock is still physical).
- The auto-merge groups by `(name.trim().lowercase(), unit.trim().lowercase())`. If a user has two genuinely different products with identical name+unit, they'll be wrongly merged. This is an accepted tradeoff per user's request; the user can manually split after upgrade by re-creating the merged product's duplicate.
- Future: if the app adds a 3rd currency, existing products automatically support it — just add a price entry. No schema change needed.
