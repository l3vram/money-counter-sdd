package com.moneycounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.CounterResult
import com.moneycounter.domain.CounterStatus
import com.moneycounter.domain.Currency
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductSelection
import com.moneycounter.ui.components.DenominationRow
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoEmptyState
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoTextField
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.ui.theme.LuisoYellow
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyCounterScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val currency = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }
        ?: com.moneycounter.domain.DefaultCurrencies.CUP
    val currencySymbol = currency.symbol

    Scaffold(
        topBar = {
            Column {
                LuisoTopBar(
                    title = "El Luiso",
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configurar",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Contador de dinero",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(LuisoYellow)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                ProductsSection(
                    selections = uiState.productSelections,
                    products = uiState.products,
                    currencies = uiState.currencies,
                    selectedCurrencyId = currency.id,
                    productLineTotal = { viewModel.productLineTotal(it) },
                    onAddRow = { viewModel.addProductRow() },
                    onRemoveRow = { index -> viewModel.removeProductRow(index) },
                    onSelectProduct = { index, productId -> viewModel.updateProductSelection(index, productId) },
                    onQuantityChange = { index, text -> viewModel.updateProductQuantity(index, text) },
                    onSelectCurrency = { viewModel.selectCurrency(it) },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            item {
                SummarySection(
                    result = uiState.result,
                    targetAmount = viewModel.productsTotal().takeIf { it.signum() > 0 },
                    currencySymbol = currencySymbol,
                    savedCountId = uiState.savedCountId,
                    onSave = { viewModel.saveCount() }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DENOMINACIONES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = {
                        if (uiState.hasActiveCount) {
                            showClearDialog = true
                        } else {
                            viewModel.clearAll()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Borrar todo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            items(uiState.denominations, key = { it.id }) { denomination ->
                val quantity = uiState.quantities[denomination.id] ?: 0L
                val subtotal = BigDecimal.valueOf(denomination.value * quantity)
                    .setScale(Money.SCALE)

                DenominationRow(
                    denomination = denomination,
                    quantity = quantity,
                    subtotal = subtotal,
                    symbol = currencySymbol,
                    onIncrement = { viewModel.incrementQuantity(denomination.id) },
                    onDecrement = { viewModel.decrementQuantity(denomination.id) },
                    onQuantityChanged = { newQty ->
                        viewModel.updateQuantity(denomination.id, newQty)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar todo") },
            text = { Text("Se eliminará la selección de productos y todas las cantidades. Los productos y denominaciones configurados se mantendrán.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    viewModel.clearProductSelections()
                    showClearDialog = false
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProductsSection(
    selections: List<ProductSelection>,
    products: List<Product>,
    currencies: List<Currency>,
    selectedCurrencyId: String,
    productLineTotal: (ProductSelection) -> BigDecimal,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onSelectProduct: (Int, String) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val currency = currencies.firstOrNull { it.id == selectedCurrencyId } ?: currencies.firstOrNull()
    val productsWithPrice = products.filter { it.hasPriceIn(selectedCurrencyId) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PRODUCTOS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                CurrencySelector(
                    currencies = currencies,
                    selectedCurrencyId = selectedCurrencyId,
                    onSelectCurrency = onSelectCurrency
                )
            }

            if (productsWithPrice.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LuisoEmptyState(
                    message = "El Luiso está listo. Registra tu primer conteo.",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("CONFIGURAR PRODUCTOS")
                }
            } else {
                selections.forEachIndexed { index, selection ->
                    ProductRow(
                        selection = selection,
                        products = productsWithPrice,
                        currencies = currencies,
                        selectedCurrencyId = selectedCurrencyId,
                        symbol = currency?.symbol ?: "$",
                        lineTotal = productLineTotal(selection),
                        onSelectProduct = { productId -> onSelectProduct(index, productId) },
                        onQuantityChange = { text -> onQuantityChange(index, text) },
                        onRemove = { onRemoveRow(index) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onAddRow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("AGREGAR PRODUCTO")
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    selection: ProductSelection,
    products: List<Product>,
    currencies: List<Currency>,
    selectedCurrencyId: String,
    symbol: String,
    lineTotal: BigDecimal,
    onSelectProduct: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val selectedProduct = products.firstOrNull { it.id == selection.productId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProductSelector(
                    products = products,
                    selectedProduct = selectedProduct,
                    onSelect = onSelectProduct,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar producto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LuisoTextField(
                    value = selection.quantityText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            onQuantityChange(newValue)
                        }
                    },
                    label = "Cantidad",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(0.40f)

                )

                Text(
                    text = selectedProduct?.unit ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.10f)
                )

                Column(
                    modifier = Modifier.weight(0.58f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (selectedProduct != null && lineTotal.signum() > 0) {
                            formatMoneyBigDecimal(lineTotal, symbol)
                        } else {
                            "—"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    val pp = selectedProduct?.priceFor(selectedCurrencyId)
                    if (selectedProduct != null && pp != null && pp.surcharge.signum() > 0 && lineTotal.signum() > 0) {
                        Text(
                            text = "incluye recargo ${pp.surcharge.stripTrailingZeros().toPlainString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                if (selectedProduct != null && selectedProduct.prices.size > 1) {
                        val otherPrices = selectedProduct.prices.entries
                            .filter { it.key != selectedCurrencyId }
                            .joinToString(" · ") { (curId, pp) ->
                                val otherSymbol = currencies.firstOrNull { it.id == curId }?.symbol ?: curId
                                "$otherSymbol ${pp.effectiveUnitPrice.stripTrailingZeros().toPlainString()}"
                            }
                        if (otherPrices.isNotBlank()) {
                            Text(
                                text = "También: $otherPrices",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (selectedProduct != null) {
                val pp = selectedProduct.priceFor(selectedCurrencyId)
                if (pp != null) {
                    val outOfRange = selection.quantity() > selectedProduct.stock
                    Text(
                        text = "${symbol}${pp.unitPrice.stripTrailingZeros().toPlainString()} por ${selectedProduct.unit}" +
                                " · disponible: ${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outOfRange) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (outOfRange) {
                        Text(
                            text = "⚠ Cantidad mayor que el stock disponible (${selectedProduct.stock.stripTrailingZeros().toPlainString()} ${selectedProduct.unit})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "Sin precio en esta moneda",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductSelector(
    products: List<Product>,
    selectedProduct: Product?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        LuisoOutlineButton(
            text = selectedProduct?.name ?: "Seleccionar…",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            products.forEach { product ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${product.name} (${product.unit})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(product.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrencySelector(
    currencies: List<Currency>,
    selectedCurrencyId: String,
    onSelectCurrency: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = currencies.firstOrNull { it.id == selectedCurrencyId } ?: currencies.firstOrNull()

    Box {
        LuisoOutlineButton(
            text = selected?.let { "${it.symbol} ${it.code}" } ?: "—",
            onClick = { expanded = true },
            modifier = Modifier.width(96.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                    onClick = {
                        onSelectCurrency(currency.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SummarySection(
    result: CounterResult,
    targetAmount: BigDecimal?,
    currencySymbol: String,
    savedCountId: String?,
    onSave: () -> Unit
) {
    val zero = Money.ZERO
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (targetAmount != null && targetAmount > zero) {
                SummaryRow(
                    label = "OBJETIVO",
                    value = formatMoneyBigDecimal(targetAmount, currencySymbol),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SummaryRow(
                label = "CONTADO",
                value = formatMoneyBigDecimal(result.countedTotal, currencySymbol),
                color = when (result.status) {
                    CounterStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    CounterStatus.OVER -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (targetAmount != null && targetAmount > zero) {
                val progress = if (targetAmount > zero) {
                    (result.countedTotal.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1.5f)
                } else 0f

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress.coerceAtMost(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = when (result.status) {
                        CounterStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        CounterStatus.OVER -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                val percentage = (progress * 100).toInt()
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (result.status) {
                    CounterStatus.COMPLETED -> {
                        Text(
                            text = "✓ MONTO COMPLETADO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (savedCountId != null) {
                            LuisoButton(
                                text = "¡Conteo registrado!",
                                onClick = {},
                                enabled = false,
                                leadingIcon = Icons.Default.Check,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LuisoButton(
                                text = "GUARDAR EN HISTORIAL",
                                onClick = onSave,
                                leadingIcon = Icons.Default.Check,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    CounterStatus.OVER -> {
                        Text(
                            text = "EXCEDENTE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "+${formatMoneyBigDecimal(result.excess, currencySymbol)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CounterStatus.COUNTING -> {
                        Text(
                            text = "FALTAN",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = formatMoneyBigDecimal(result.remaining, currencySymbol),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CounterStatus.EMPTY -> {
                        Text(
                            text = "Selecciona productos para comenzar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Agrega productos y cantidades para ver el progreso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}