# Plan 003: Stock screen per-currency price dialog + ProductRow multi-currency display

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/product-multi-currency/README.md`.
>
> **Worktree**: Execute in an isolated worktree from `feature/product-multi-currency` (after 001 landed). Do NOT work on `main`.
>
> **Drift check (run first)**: `git diff --stat HEAD..origin/feature/product-multi-currency -- app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
> If any in-scope file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: 001
- **Category**: feature
- **Planned at**: commit `e8665ca`, 2026-09-07

## Why this matters

The product dialog in the Inventario (Stock) screen currently has a single currency dropdown + price + surcharge. With the multi-currency product model (001), users need to set a price for *each* configured currency on the same product. The dialog must dynamically show price+surcharge fields per currency (instead of a single currency dropdown). The ProductRow in the stock list must also display all prices.

## Current state (post-001)

### Key files

| File | Role |
|------|------|
| `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` | Inventario — `ProductRow` (line 210), `ProductDialog` (line 265), `parseDecimalInput` (line 432) |
| `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` | VM — `addProduct(name, unit, stock, currencyId, unitPrice, surcharge)` still has old signature from 001 (wraps into prices map internally) |
| `app/src/main/java/com/moneycounter/domain/Currency.kt` | `Currency(id, code, name, symbol)` |
| `app/src/main/java/com/moneycounter/ui/components/Components.kt` | Kit: `LuisoButton`, `LuisoCard`, `LuisoTextField`, `LuisoSectionHeader` |
| `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` | `formatMoneyBigDecimal(x, symbol)` |

### StockScreen ProductRow (line 210–263) — post-001

Already updated by 001 to read `product.prices` map and display each entry:

```kotlin
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
```

### StockScreen ProductDialog (line 265–430) — current (pre-003)

Has fields: name, unit dropdown, currency dropdown, stock, price, surcharge.
The dialog's `onConfirm` callback passes: `(name, unit, stock, price, surcharge, currencyId)`.

### VM addProduct/editProduct (post-001)

Still has old single-currency signature:

```kotlin
fun addProduct(name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, currencyId: String, unitPrice: BigDecimal, surcharge: BigDecimal): Boolean
```

Internally builds `prices = mapOf(currencyId to ProductPrice(unitPrice, surcharge))`. This plan changes the signature to accept a full prices map.

### Conventions to follow

- `formatMoneyBigDecimal(x, symbol)` — never prefix `$` manually
- Material 3: `surfaceContainer` does NOT exist (repo uses Material ~1.1.2). Use `surfaceVariant`/`surface`.
- `LuisoTextField(value, onValueChange, modifier, label, keyboardOptions)` for text inputs
- Numeric input pattern: `if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) { ... }`

## Commands you will need

| Purpose   | Command                              | Expected on success |
|-----------|--------------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin`       | exit 0              |
| Tests     | `./gradlew test`                     | all pass            |
| Full build| `./gradlew assembleDebug`            | exit 0              |

## Scope

**In scope** (modify these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (addProduct/editProduct signatures + price map params)
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` (ProductDialog, ProductRow)

**Out of scope** (do NOT touch — other plans handle these):
- MoneyCounterScreen — handled by 002
- StockReportScreen / exporters — handled by 001 + 004
- Product.kt / JsonProductRepository — done in 001
- ReportsScreen / HistoryDetail / UnifiedReport — plan 005

## Git workflow

- Branch: `exec/003-stock-dialog` (worktree, from `feature/product-multi-currency`)
- Commit: `feat(stock): per-currency price dialog + multi-currency ProductRow`

## Steps

### Step 1: Change VM `addProduct`/`editProduct` signatures to accept prices map

In `MoneyCounterViewModel.kt`, replace the two methods:

**`addProduct` (line ~345):**

```kotlin
fun addProduct(name: String, unit: String, stock: BigDecimal, prices: Map<String, ProductPrice>): Boolean {
    val cleanName = name.trim()
    val cleanUnit = unit.trim()
    if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
    if (stock.signum() < 0) return false
    if (prices.isEmpty()) return false
    if (prices.values.all { it.unitPrice.signum() == 0 && it.surcharge.signum() == 0 }) return false
    val state = _uiState.value
    val new = Product(generateProductId(state.products), cleanName, cleanUnit, stock, prices)
    val newProducts = state.products + new
    _uiState.update { it.copy(products = newProducts) }
    persistProducts(newProducts)
    return true
}
```

**`editProduct` (line ~360):**

```kotlin
fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, prices: Map<String, ProductPrice>): Boolean {
    val cleanName = name.trim()
    val cleanUnit = unit.trim()
    if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
    if (stock.signum() < 0) return false
    val state = _uiState.value
    if (state.products.none { it.id == id }) return false
    val newProducts = state.products.map {
        if (it.id == id) it.copy(name = cleanName, unit = cleanUnit, stock = stock, prices = prices) else it
    }
    _uiState.update { it.copy(products = newProducts) }
    persistProducts(newProducts)
    return true
}
```

Add the `ProductPrice` import if not already present:

```kotlin
import com.moneycounter.domain.ProductPrice
```

**Verify**: `./gradlew compileDebugKotlin` — will break at StockScreen call sites (expected, fixed next step).

### Step 2: Rewrite StockScreen `ProductDialog` — per-currency price fields

Replace the entire `ProductDialog` composable (line 265–430) with a new version that dynamically renders price+surcharge fields for each configured currency:

```kotlin
@Composable
private fun ProductDialog(
    title: String,
    units: List<MeasurementUnit>,
    currencies: List<Currency>,
    initialName: String,
    initialUnit: String,
    initialStock: String,
    initialPrices: Map<String, Pair<String, String>>,  // currencyId -> (priceText, surchargeText)
    confirmText: String,
    onConfirm: (name: String, unit: String, stock: BigDecimal, prices: Map<String, ProductPrice>) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var unit by remember {
        mutableStateOf(
            if (units.any { it.name == initialUnit }) initialUnit else units.firstOrNull()?.name ?: ""
        )
    }
    var stock by remember { mutableStateOf(initialStock) }
    // Per-currency price state: currencyId -> (price, surcharge)
    var priceStates by remember {
        mutableStateOf(
            currencies.associate { c ->
                val (p, s) = initialPrices[c.id] ?: ("0" to "0")
                c.id to mutableStateOf(p) to mutableStateOf(s)
            }
        )
    }
    var unitMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LuisoTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Nombre"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { unitMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Unidad: $unit", modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(
                        expanded = unitMenuOpen,
                        onDismissRequest = { unitMenuOpen = false }
                    ) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.name) },
                                onClick = { unit = u.name; unitMenuOpen = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LuisoTextField(
                    value = stock,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            stock = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Cantidad (stock)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Per-currency price fields
                currencies.forEach { currency ->
                    val priceState = priceStates[currency.id]?.first
                    val surchargeState = priceStates[currency.id]?.second
                    if (priceState != null && surchargeState != null) {
                        Text(
                            text = "${currency.symbol} ${currency.code}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        LuisoTextField(
                            value = priceState.value,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                                    priceState.value = newValue
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Precio por unidad",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LuisoTextField(
                            value = surchargeState.value,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                                    surchargeState.value = newValue
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Recargo fijo",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedStock = parseDecimalInput(stock)
                val prices = mutableMapOf<String, ProductPrice>()
                for (currency in currencies) {
                    val priceState = priceStates[currency.id]?.first
                    val surchargeState = priceStates[currency.id]?.second
                    if (priceState != null && surchargeState != null) {
                        val pp = parseDecimalInput(priceState.value)
                        val sc = parseDecimalInput(surchargeState.value)
                        if (pp.signum() > 0 || sc.signum() > 0) {
                            prices[currency.id] = ProductPrice(pp, sc)
                        }
                    }
                }
                onConfirm(name, unit, parsedStock, prices)
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR") }
        }
    )
}
```

Note: This uses a map of `mutableStateOf` pairs for per-currency state. This is a simplification; the key pattern is:

```kotlin
// Build state map: currencyId -> (priceState, surchargeState)
val priceStates = remember {
    currencies.associate { c ->
        val (p, s) = initialPrices[c.id] ?: ("0" to "0")
        c.id to (mutableStateOf(p) to mutableStateOf(s))
    }
}
```

**Verify**: `./gradlew compileDebugKotlin` — will fail at addProduct/editProduct call sites in StockScreen (expected).

### Step 3: Update StockScreen call sites for addProduct/editProduct

**3a. Add product dialog (line 130–156):**

Replace the `ProductDialog` call for add:

```kotlin
if (showAddProductDialog) {
    val emptyPrices = uiState.currencies.associate { it.id to ("0" to "0") }
    ProductDialog(
        title = "Nuevo producto",
        units = uiState.units,
        currencies = uiState.currencies,
        initialName = "",
        initialUnit = uiState.units.firstOrNull()?.name ?: "",
        initialStock = "0",
        initialPrices = emptyPrices,
        confirmText = "GUARDAR",
        onConfirm = { name, unit, stock, prices ->
            if (viewModel.addProduct(name, unit, stock, prices)) {
                showAddProductDialog = false
                errorMessage = null
            } else {
                errorMessage = "Revisa los datos: nombre y unidad obligatorios, al menos un precio mayor que cero."
            }
        },
        onDismiss = { showAddProductDialog = false; errorMessage = null },
        errorMessage = errorMessage
    )
}
```

**3b. Edit product dialog (line 158–185):**

Replace:

```kotlin
showEditProductDialog?.let { product ->
    val editPrices = uiState.currencies.associate { c ->
        val pp = product.prices[c.id]
        c.id to (
            (pp?.unitPrice?.stripTrailingZeros()?.toPlainString() ?: "0") to
            (pp?.surcharge?.stripTrailingZeros()?.toPlainString() ?: "0")
        )
    }
    ProductDialog(
        title = "Editar producto",
        units = uiState.units,
        currencies = uiState.currencies,
        initialName = product.name,
        initialUnit = product.unit,
        initialStock = product.stock.stripTrailingZeros().toPlainString(),
        initialPrices = editPrices,
        confirmText = "GUARDAR",
        onConfirm = { name, unit, stock, prices ->
            if (viewModel.editProduct(product.id, name, unit, stock, prices)) {
                showEditProductDialog = null
                errorMessage = null
            } else {
                errorMessage = "Revisa los datos: nombre y unidad obligatorios."
            }
        },
        onDismiss = { showEditProductDialog = null; errorMessage = null },
        errorMessage = errorMessage
    )
}
```

**Verify**: `./gradlew compileDebugKotlin` — should now succeed.

### Step 4: Update StockScreen ProductRow — show all prices

In `StockScreen.kt`, the `ProductRow` composable (line 210–263) was already updated by 001 to iterate `product.prices`. No further changes needed — verify the current display matches the spec:

- Row 1: product name (bold)
- Row 2: stock + unit · then per-currency price chips ("$120.00 +$5.00 · US$0.30")

If 001 left the ProductRow with `currencyCodeOf(curId)` returning the code (e.g. "CUP"), change to show symbol+code for clarity:

```kotlin
product.prices.entries.joinToString(" · ") { (curId, pp) ->
    val sym = symbolOf(curId)
    val code = currencyCodeOf(curId)
    "${sym}${pp.unitPrice.stripTrailingZeros().toPlainString()} ($code)" +
    if (pp.surcharge.signum() > 0) " +${sym}${pp.surcharge.stripTrailingZeros().toPlainString()}" else ""
}
```

Add `symbolOf: (String) -> String` as a parameter to `ProductRow` if not already present, passing:

```kotlin
symbolOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.symbol ?: id }
```

**Verify**: `./gradlew compileDebugKotlin`.

### Step 5: Verify no regressions

**Verify**: `./gradlew test` → all tests pass.
**Verify**: `./gradlew assembleDebug` → full build success.

## Test plan

- No new domain tests (001 covers the model)
- Verify existing tests pass (ProductJsonCurrencyTest, StockDeductionTest)
- Manual verification: build and run the app; create a product with prices in both CUP and USD; verify both prices display in StockScreen

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `./gradlew assembleDebug` exits 0
- [ ] `grep -rn "currencyId.*unitPrice\|unitPrice.*currencyId" app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt` returns no matches (old single-price pattern gone)
- [ ] `grep -rn "currencyId, price, surcharge\|price, surcharge, currencyId" app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` returns no matches (old signature gone)

## STOP conditions

- The code at the locations in "Current state" doesn't match the excerpts (drifted).
- A step's verification fails twice after a reasonable fix attempt.
- `./gradlew assembleDebug` fails after all steps.
- The `ProductDialog` per-currency fields layout is unreadable or unusable — STOP and report the issue for redesign.

## Maintenance notes

- The dialog dynamically renders fields for each currency in `uiState.currencies`. If a new currency is added in settings, the product dialog immediately supports it.
- Products with prices in only one currency are valid (only one price entry). Products with no prices at all are rejected (validation in step 1).
- The `symbolOf` / `currencyCodeOf` helpers in StockScreen resolve from `uiState.currencies`. If currencies are deleted, products retain their stale price entries (no cascade delete of price map keys). This is acceptable — the currency dropdown won't show the deleted currency, and the stale price entry is harmless.
