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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.moneycounter.domain.MeasurementUnit
import com.moneycounter.domain.Product
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToReport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencySymbol = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }?.symbol ?: "$"
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showEditProductDialog by remember { mutableStateOf<Product?>(null) }
    var showDeleteProductDialog by remember { mutableStateOf<Product?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
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
                SectionTitle("PRODUCTOS")
            }

            if (uiState.products.isEmpty()) {
                item {
                    Text(
                        text = "No hay productos en el stock. Agrega uno con cantidad, precio y recargo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        symbol = currencySymbol,
                        onEdit = {
                            showEditProductDialog = product
                            errorMessage = null
                        },
                        onDelete = { showDeleteProductDialog = product }
                    )
                }
            }

            item {
                FilledTonalButton(
                    onClick = {
                        showAddProductDialog = true
                        errorMessage = null
                    },
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

            item {
                FilledTonalButton(
                    onClick = onNavigateToReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("REPORTE DE EXISTENCIA")
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddProductDialog) {
        ProductDialog(
            title = "Nuevo producto",
            units = uiState.units,
            initialName = "",
            initialUnit = uiState.units.firstOrNull()?.name ?: "",
            initialStock = "0",
            initialPrice = "",
            initialSurcharge = "0",
            confirmText = "GUARDAR",
            onConfirm = { name, unit, stock, price, surcharge ->
                if (viewModel.addProduct(name, unit, stock, price, surcharge)) {
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
        ProductDialog(
            title = "Editar producto",
            units = uiState.units,
            initialName = product.name,
            initialUnit = product.unit,
            initialStock = product.stock.stripTrailingZeros().toPlainString(),
            initialPrice = product.unitPrice.stripTrailingZeros().toPlainString(),
            initialSurcharge = product.surcharge.stripTrailingZeros().toPlainString(),
            confirmText = "GUARDAR",
            onConfirm = { name, unit, stock, price, surcharge ->
                if (viewModel.editProduct(product.id, name, unit, stock, price, surcharge)) {
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ProductRow(
    product: Product,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
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
                    text = "${product.stock.stripTrailingZeros().toPlainString()} ${product.unit} · " +
                            "${symbol}${product.unitPrice.stripTrailingZeros().toPlainString()} por ${product.unit}" +
                            if (product.surcharge.signum() > 0)
                                " · + recargo ${symbol}${product.surcharge.stripTrailingZeros().toPlainString()}"
                            else "",
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
    initialName: String,
    initialUnit: String,
    initialStock: String,
    initialPrice: String,
    initialSurcharge: String,
    confirmText: String,
    onConfirm: (name: String, unit: String, stock: BigDecimal, unitPrice: BigDecimal, surcharge: BigDecimal) -> Unit,
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
    var price by remember { mutableStateOf(initialPrice) }
    var surcharge by remember { mutableStateOf(initialSurcharge) }
    var unitMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true
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

                OutlinedTextField(
                    value = stock,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            stock = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cantidad (stock)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = price,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            price = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Precio por unidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = surcharge,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.trim().replace(',', '.').matches(Regex("\\d*\\.?\\d*"))) {
                            surcharge = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recargo fijo por unidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
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
                onConfirm(name, unit, parsedStock, parsedPrice, parsedSurcharge)
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