package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.MeasurementUnit
import com.moneycounter.domain.Product
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoEmptyState
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTextField
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal

@Composable
fun StockScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToReport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showEditProductDialog by remember { mutableStateOf<Product?>(null) }
    var showDeleteProductDialog by remember { mutableStateOf<Product?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            LuisoTopBar(title = "Inventario")
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                LuisoSectionHeader(text = "PRODUCTOS")
            }

            if (uiState.products.isEmpty()) {
                item {
                    LuisoEmptyState(
                        message = "Todavía no hay productos. Agrega el primero."
                    )
                }
            } else {
                items(uiState.products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        symbolOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.symbol ?: id },
                        onEdit = {
                            showEditProductDialog = product
                            errorMessage = null
                        },
                        onDelete = { showDeleteProductDialog = product }
                    )
                }
            }

            item {
                LuisoButton(
                    text = "AGREGAR PRODUCTO",
                    onClick = {
                        showAddProductDialog = true
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Add
                )
            }

            item {
                LuisoButton(
                    text = "REPORTE DE EXISTENCIA",
                    onClick = onNavigateToReport,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Description
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddProductDialog) {
        ProductDialog(
            title = "Nuevo producto",
            units = uiState.units,
            currencies = uiState.currencies,
            initialName = "",
            initialUnit = uiState.units.firstOrNull()?.name ?: "",
            initialCurrencyId = uiState.selectedCurrencyId,
            initialStock = "0",
            initialPrice = "",
            initialSurcharge = "0",
            confirmText = "GUARDAR",
            onConfirm = { name, unit, stock, price, surcharge, currencyId ->
                if (viewModel.addProduct(name, unit, stock, currencyId, price, surcharge)) {
                    showAddProductDialog = false
                    errorMessage = null
                } else {
                    errorMessage = "Revisa los datos: nombre y unidad obligatorios, precio y recargo no negativos y al menos uno mayor que cero."
                }
            },
            onDismiss = {
                showAddProductDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showEditProductDialog?.let { product ->
        val editCurrencyId = if (product.hasPriceIn(uiState.selectedCurrencyId)) uiState.selectedCurrencyId
            else product.prices.keys.firstOrNull() ?: uiState.selectedCurrencyId
        val editPrice = product.priceFor(editCurrencyId)
        ProductDialog(
            title = "Editar producto",
            units = uiState.units,
            currencies = uiState.currencies,
            initialName = product.name,
            initialUnit = product.unit,
            initialCurrencyId = editCurrencyId,
            initialStock = product.stock.stripTrailingZeros().toPlainString(),
            initialPrice = editPrice?.unitPrice?.stripTrailingZeros()?.toPlainString() ?: "",
            initialSurcharge = editPrice?.surcharge?.stripTrailingZeros()?.toPlainString() ?: "0",
            confirmText = "GUARDAR",
            onConfirm = { name, unit, stock, price, surcharge, currencyId ->
                if (viewModel.editProduct(product.id, name, unit, stock, currencyId, price, surcharge)) {
                    showEditProductDialog = null
                    errorMessage = null
                } else {
                    errorMessage = "Revisa los datos: nombre y unidad obligatorios, precio y recargo no negativos."
                }
            },
            onDismiss = {
                showEditProductDialog = null
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showDeleteProductDialog?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteProductDialog = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Seguro que deseas eliminar el producto \"${product.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product.id)
                    showDeleteProductDialog = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProductDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProductRow(
    product: Product,
    symbolOf: (String) -> String = { it },
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
                            product.prices.entries.joinToString(" · ") { (currencyId, pp) ->
                                "${symbolOf(currencyId)}${pp.unitPrice.stripTrailingZeros().toPlainString()}" +
                                        if (pp.surcharge.signum() > 0)
                                            " +${symbolOf(currencyId)}${pp.surcharge.stripTrailingZeros().toPlainString()}"
                                        else ""
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDialog(
    title: String,
    units: List<MeasurementUnit>,
    currencies: List<Currency>,
    initialName: String,
    initialUnit: String,
    initialCurrencyId: String,
    initialStock: String,
    initialPrice: String,
    initialSurcharge: String,
    confirmText: String,
    onConfirm: (name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal, currencyId: String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var unit by remember {
        mutableStateOf(
            if (units.any { it.name == initialUnit }) initialUnit else units.firstOrNull()?.name ?: ""
        )
    }
    var currencyId by remember {
        mutableStateOf(
            if (currencies.any { it.id == initialCurrencyId }) initialCurrencyId
            else currencies.firstOrNull()?.id.orEmpty()
        )
    }
    var stock by remember { mutableStateOf(initialStock) }
    var price by remember { mutableStateOf(initialPrice) }
    var surcharge by remember { mutableStateOf(initialSurcharge) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var currencyMenuOpen by remember { mutableStateOf(false) }

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
                        Text(
                            text = "Unidad: $unit",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = unitMenuOpen,
                        onDismissRequest = { unitMenuOpen = false }
                    ) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.name) },
                                onClick = {
                                    unit = u.name
                                    unitMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val selectedCurrency = currencies.firstOrNull { it.id == currencyId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { currencyMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Moneda: ${selectedCurrency?.let { "${it.symbol} ${it.code}" } ?: ""}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = currencyMenuOpen,
                        onDismissRequest = { currencyMenuOpen = false }
                    ) {
                        currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.symbol} ${c.code} — ${c.name}") },
                                onClick = {
                                    currencyId = c.id
                                    currencyMenuOpen = false
                                }
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

                LuisoTextField(
                    value = price,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            price = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Precio por unidad",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LuisoTextField(
                    value = surcharge,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            surcharge = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Recargo fijo por unidad",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
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
                val parsedPrice = parseDecimalInput(price)
                val parsedSurcharge = parseDecimalInput(surcharge)
                onConfirm(name, unit, parsedStock, parsedPrice, parsedSurcharge, currencyId)
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        }
    )
}

private fun parseDecimalInput(input: String): BigDecimal {
    if (input.isBlank()) return BigDecimal.ZERO
    return runCatching { BigDecimal(input.trim().replace(',', '.')) }
        .getOrElse { BigDecimal.ZERO }
}