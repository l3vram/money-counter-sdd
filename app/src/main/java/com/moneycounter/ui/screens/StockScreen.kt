package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.runtime.MutableState
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
import com.moneycounter.domain.Member
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductPrice
import com.moneycounter.domain.mayEditStock
import com.moneycounter.domain.mayRegisterWriteoff
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTextField
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.TermInfo
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToReport: () -> Unit,
    member: Member? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val canRegisterWriteoff = member?.role.mayRegisterWriteoff()
    val canEditStock = member?.role.mayEditStock()
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddStockDialog by remember { mutableStateOf<Product?>(null) }
    var showDeleteProductDialog by remember { mutableStateOf<Product?>(null) }
    var showWriteoffDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addStockError by remember { mutableStateOf<String?>(null) }
    var writeoffError by remember { mutableStateOf<String?>(null) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LuisoSectionHeader(text = "PRODUCTOS")
                    TermInfo(
                        correctTerm = "Inventario / Existencias",
                        oldName = "Stock",
                        explanation = "Cantidad disponible de cada producto."
                    )
                }
            }

            if (uiState.products.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "Todavía no hay productos. Agrega el primero."
                    )
                }
            } else {
                items(uiState.products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        symbolOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.symbol ?: id },
                        currencyCodeOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.code ?: id },
                        onAddStock = {
                            showAddStockDialog = product
                            addStockError = null
                        },
                        onDelete = { showDeleteProductDialog = product },
                        canDelete = canEditStock
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

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LuisoSectionHeader(text = "BAJAS POR MERMA")
                    TermInfo(
                        correctTerm = "Ajuste de inventario por merma",
                        oldName = "—",
                        explanation = "Salida de inventario sin venta, por pérdida."
                    )
                }
            }

            if (canRegisterWriteoff) {
                item {
                    LuisoButton(
                        text = "BAJA POR MERMA",
                        onClick = {
                            showWriteoffDialog = true
                            writeoffError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Default.Delete,
                        enabled = uiState.products.any { it.hasPriceIn(uiState.selectedCurrencyId) }
                    )
                }
            }

            val mermaMovements = uiState.movements.filter {
                it.type == MovementType.MERMA && it.currencyId == uiState.selectedCurrencyId
            }
            if (mermaMovements.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "Todavía no hay bajas por merma registradas."
                    )
                }
            } else {
                items(mermaMovements, key = { it.id }) { writeoff ->
                    WriteoffRow(
                        writeoff = writeoff,
                        symbolOf = { id -> uiState.currencies.firstOrNull { it.id == id }?.symbol ?: id }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

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
            onDismiss = {
                showAddProductDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showAddStockDialog?.let { product ->
        AddStockDialog(
            product = product,
            onConfirm = { quantityText ->
                if (viewModel.addStock(product.id, quantityText)) {
                    showAddStockDialog = null
                    addStockError = null
                } else {
                    addStockError = "Revisa los datos: cantidad mayor que cero."
                }
            },
            onDismiss = {
                showAddStockDialog = null
                addStockError = null
            },
            errorMessage = addStockError
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

    if (showWriteoffDialog) {
        val eligibleProducts = uiState.products.filter { it.hasPriceIn(uiState.selectedCurrencyId) }
        WriteoffDialog(
            products = eligibleProducts,
            onConfirm = { productId, quantityText, reason ->
                if (viewModel.registerWriteoff(productId, quantityText, reason)) {
                    showWriteoffDialog = false
                    writeoffError = null
                } else {
                    writeoffError = "Revisa los datos: selecciona un producto y una cantidad mayor que cero."
                }
            },
            onDismiss = {
                showWriteoffDialog = false
                writeoffError = null
            },
            errorMessage = writeoffError
        )
    }
}

@Composable
private fun WriteoffRow(
    writeoff: Movement,
    symbolOf: (String) -> String
) {
    val line = writeoff.products.firstOrNull()
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${writeoffDateFormatter.format(Date(writeoff.at))} · " +
                            "${line?.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${line?.unit.orEmpty()}" +
                            (writeoff.concept?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "-${symbolOf(writeoff.currencyId)}${writeoff.amount.toPlainString()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private val writeoffDateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

@Composable
private fun WriteoffDialog(
    products: List<Product>,
    onConfirm: (productId: String, quantityText: String, reason: String?) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: "") }
    var quantityText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var productMenuOpen by remember { mutableStateOf(false) }

    val selectedProduct = products.firstOrNull { it.id == selectedProductId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Baja por merma") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (products.isEmpty()) {
                    Text(
                        text = "No hay productos con precio en la moneda activa.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { productMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Producto: ${selectedProduct?.name ?: "Selecciona"}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DropdownMenu(
                            expanded = productMenuOpen,
                            onDismissRequest = { productMenuOpen = false }
                        ) {
                            products.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        selectedProductId = p.id
                                        productMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LuisoTextField(
                        value = quantityText,
                        onValueChange = { newValue ->
                            if (
                                newValue.isEmpty() ||
                                newValue.trim()
                                    .replace(',', '.')
                                    .matches(Regex("\\d*\\.?\\d*"))
                            ) {
                                quantityText = newValue
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Cantidad (${selectedProduct?.unit ?: ""})",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LuisoTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Motivo (opcional)"
                    )
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
            TextButton(
                onClick = {
                    onConfirm(selectedProductId, quantityText, reason.takeIf { it.isNotBlank() })
                },
                enabled = products.isNotEmpty()
            ) {
                Text("CONFIRMAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        }
    )
}

@Composable
private fun AddStockDialog(
    product: Product,
    onConfirm: (quantityText: String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var quantityText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alta de stock") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stock actual: ${product.stock.stripTrailingZeros().toPlainString()} ${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LuisoTextField(
                    value = quantityText,
                    onValueChange = { newValue ->
                        if (
                            newValue.isEmpty() ||
                            newValue.trim()
                                .replace(',', '.')
                                .matches(Regex("\\d*\\.?\\d*"))
                        ) {
                            quantityText = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Cantidad a agregar (${product.unit})",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
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
            TextButton(
                onClick = { onConfirm(quantityText) }
            ) {
                Text("AGREGAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        }
    )
}

@Composable
private fun ProductRow(
    product: Product,
    symbolOf: (String) -> String = { it },
    currencyCodeOf: (String) -> String = { it },
    onAddStock: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean = true
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
                                val sym = symbolOf(currencyId)
                                val code = currencyCodeOf(currencyId)
                                "$sym${pp.unitPrice.stripTrailingZeros().toPlainString()} ($code)" +
                                        if (pp.surcharge.signum() > 0)
                                            " +$sym${pp.surcharge.stripTrailingZeros().toPlainString()}"
                                        else ""
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onAddStock) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Dar entrada",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (canDelete) {
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
}

@Composable
private fun ProductDialog(
    title: String,
    units: List<MeasurementUnit>,
    currencies: List<Currency>,
    initialName: String,
    initialUnit: String,
    initialStock: String,
    initialPrices: Map<String, Pair<String, String>>,
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
    val priceStates: Map<String, Pair<MutableState<String>, MutableState<String>>> = remember(currencies) {
        currencies.associate { c ->
            val initial = initialPrices[c.id] ?: ("0" to "0")
            c.id to (
                mutableStateOf(initial.first) to mutableStateOf(initial.second)
            )
        }
    }
    var unitMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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

                LuisoTextField(
                    value = stock,
                    onValueChange = { newValue ->
                        if (
                            newValue.isEmpty() ||
                            newValue.trim()
                                .replace(',', '.')
                                .matches(Regex("\\d*\\.?\\d*"))
                        ) {
                            stock = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Cantidad (stock)",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                currencies.forEach { c ->
                    val price = priceStates[c.id] ?: return@forEach
                    Text(
                        text = "${c.symbol} ${c.code}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LuisoTextField(
                        value = price.first.value,
                        onValueChange = { newValue ->
                            if (
                                newValue.isEmpty() ||
                                newValue.trim()
                                    .replace(',', '.')
                                    .matches(Regex("\\d*\\.?\\d*"))
                            ) {
                                price.first.value = newValue
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Precio por unidad",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LuisoTextField(
                        value = price.second.value,
                        onValueChange = { newValue ->
                            if (
                                newValue.isEmpty() ||
                                newValue.trim()
                                    .replace(',', '.')
                                    .matches(Regex("\\d*\\.?\\d*"))
                            ) {
                                price.second.value = newValue
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Recargo fijo por unidad",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
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
            TextButton(
                onClick = {
                    val parsedStock = parseDecimalInput(stock)

                    val prices = currencies.mapNotNull { c ->
                        val price = priceStates[c.id]
                            ?: return@mapNotNull null

                        val parsedPrice =
                            parseDecimalInput(price.first.value)

                        val parsedSurcharge =
                            parseDecimalInput(price.second.value)

                        if (
                            parsedPrice.signum() > 0 ||
                            parsedSurcharge.signum() > 0
                        ) {
                            c.id to ProductPrice(
                                parsedPrice,
                                parsedSurcharge
                            )
                        } else {
                            null
                        }
                    }.toMap()

                    onConfirm(
                        name,
                        unit,
                        parsedStock,
                        prices
                    )
                }
            ) {
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