package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.Denomination
import com.moneycounter.domain.MeasurementUnit
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTextField
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.viewmodel.MoneyCounterViewModel

@Composable
fun DenominationManagementScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencySymbol = uiState.currencies.firstOrNull { it.id == uiState.selectedCurrencyId }?.symbol ?: "$"
    var showAddDenominationDialog by remember { mutableStateOf(false) }
    var showEditDenominationDialog by remember { mutableStateOf<Denomination?>(null) }
    var showDeleteDenominationDialog by remember { mutableStateOf<Denomination?>(null) }
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    var showEditCurrencyDialog by remember { mutableStateOf<Currency?>(null) }
    var showDeleteCurrencyDialog by remember { mutableStateOf<Currency?>(null) }
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var showEditUnitDialog by remember { mutableStateOf<MeasurementUnit?>(null) }
    var showDeleteUnitDialog by remember { mutableStateOf<MeasurementUnit?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Ajustes",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
                LuisoSectionHeader(text = "MONEDA")
            }

            items(uiState.currencies, key = { it.id }) { currency ->
                CurrencyManagementRow(
                    currency = currency,
                    isSelected = currency.id == uiState.selectedCurrencyId,
                    onSelect = { viewModel.selectCurrency(currency.id) },
                    onEdit = {
                        showEditCurrencyDialog = currency
                        errorMessage = null
                    },
                    onDelete = { showDeleteCurrencyDialog = currency }
                )
            }

            item {
                LuisoButton(
                    text = "AGREGAR MONEDA",
                    onClick = {
                        showAddCurrencyDialog = true
                        errorMessage = null
                    },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                LuisoSectionHeader(text = "UNIDADES DE MEDIDA")
            }

            items(uiState.units, key = { it.id }) { unit ->
                UnitManagementRow(
                    unit = unit,
                    onEdit = {
                        showEditUnitDialog = unit
                        errorMessage = null
                    },
                    onDelete = { showDeleteUnitDialog = unit }
                )
            }

            item {
                LuisoButton(
                    text = "AGREGAR UNIDAD",
                    onClick = {
                        showAddUnitDialog = true
                        errorMessage = null
                    },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                LuisoSectionHeader(text = "DENOMINACIONES")
            }

            if (uiState.hasActiveCount) {
                item {
                    LuisoCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No puedes modificar las denominaciones mientras haya un conteo activo. Borra el conteo primero.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(uiState.denominations) { denomination ->
                DenominationManagementRow(
                    denomination = denomination,
                    isFirst = uiState.denominations.firstOrNull()?.id == denomination.id,
                    isLast = uiState.denominations.lastOrNull()?.id == denomination.id,
                    isDisabled = uiState.hasActiveCount,
                    symbol = currencySymbol,
                    onEdit = {
                        showEditDenominationDialog = denomination
                        errorMessage = null
                    },
                    onDelete = { showDeleteDenominationDialog = denomination },
                    onMoveUp = { viewModel.moveDenominationUp(denomination.id) },
                    onMoveDown = { viewModel.moveDenominationDown(denomination.id) }
                )
            }

            item {
                LuisoButton(
                    text = "AGREGAR DENOMINACIÓN",
                    onClick = {
                        showAddDenominationDialog = true
                        errorMessage = null
                    },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                LuisoOutlineButton(
                    text = "CERRAR SESIÓN",
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDenominationDialog) {
        DenominationValueDialog(
            title = "Nueva denominación",
            initialValue = "",
            confirmText = "GUARDAR",
            onConfirm = { value ->
                val success = viewModel.addDenomination(value)
                if (success) {
                    showAddDenominationDialog = false
                    errorMessage = null
                } else {
                    if (value <= 0) {
                        errorMessage = "El valor debe ser mayor que cero."
                    } else {
                        errorMessage = "Esta denominación ya existe."
                    }
                }
            },
            onDismiss = {
                showAddDenominationDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showEditDenominationDialog?.let { denomination ->
        DenominationValueDialog(
            title = "Editar denominación",
            initialValue = denomination.value.toString(),
            confirmText = "GUARDAR",
            onConfirm = { value ->
                val success = viewModel.editDenomination(denomination.id, value)
                if (success) {
                    showEditDenominationDialog = null
                    errorMessage = null
                } else {
                    if (value <= 0) {
                        errorMessage = "El valor debe ser mayor que cero."
                    } else {
                        errorMessage = "Esta denominación ya existe."
                    }
                }
            },
            onDismiss = {
                showEditDenominationDialog = null
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showDeleteDenominationDialog?.let { denomination ->
        AlertDialog(
            onDismissRequest = { showDeleteDenominationDialog = null },
            title = { Text("Eliminar denominación") },
            text = { Text("¿Estás seguro de que deseas eliminar la denominación de ${formatMoney(denomination.value, currencySymbol)}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDenomination(denomination.id)
                    showDeleteDenominationDialog = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDenominationDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddCurrencyDialog) {
        CurrencyDialog(
            title = "Nueva moneda",
            initialCode = "",
            initialName = "",
            initialSymbol = "",
            confirmText = "GUARDAR",
            onConfirm = { code, name, symbol ->
                if (viewModel.addCurrency(code, name, symbol)) {
                    showAddCurrencyDialog = false
                    errorMessage = null
                } else {
                    errorMessage = "El código debe tener 3 letras y no repetirse; el nombre y símbolo son obligatorios."
                }
            },
            onDismiss = {
                showAddCurrencyDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showEditCurrencyDialog?.let { currency ->
        CurrencyDialog(
            title = "Editar moneda",
            initialCode = currency.code,
            initialName = currency.name,
            initialSymbol = currency.symbol,
            confirmText = "GUARDAR",
            onConfirm = { code, name, symbol ->
                if (viewModel.editCurrency(currency.id, code, name, symbol)) {
                    showEditCurrencyDialog = null
                    errorMessage = null
                } else {
                    errorMessage = "El código debe tener 3 letras y no repetirse; el nombre y símbolo son obligatorios."
                }
            },
            onDismiss = {
                showEditCurrencyDialog = null
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showDeleteCurrencyDialog?.let { currency ->
        AlertDialog(
            onDismissRequest = { showDeleteCurrencyDialog = null },
            title = { Text("Eliminar moneda") },
            text = { Text("¿Seguro que deseas eliminar la moneda ${currency.symbol} ${currency.code}?") },
            confirmButton = {
                TextButton(onClick = {
                    val success = viewModel.deleteCurrency(currency.id)
                    if (success) {
                        showDeleteCurrencyDialog = null
                    } else {
                        errorMessage = "No se puede eliminar: solo queda una moneda o está en uso."
                    }
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCurrencyDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddUnitDialog) {
        UnitDialog(
            title = "Nueva unidad",
            initialName = "",
            confirmText = "GUARDAR",
            onConfirm = { name ->
                if (viewModel.addUnit(name)) {
                    showAddUnitDialog = false
                    errorMessage = null
                } else {
                    errorMessage = "La unidad ya existe o el nombre está vacío."
                }
            },
            onDismiss = {
                showAddUnitDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showEditUnitDialog?.let { unit ->
        UnitDialog(
            title = "Editar unidad",
            initialName = unit.name,
            confirmText = "GUARDAR",
            onConfirm = { name ->
                if (viewModel.editUnit(unit.id, name)) {
                    showEditUnitDialog = null
                    errorMessage = null
                } else {
                    errorMessage = "La unidad ya existe o el nombre está vacío."
                }
            },
            onDismiss = {
                showEditUnitDialog = null
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showDeleteUnitDialog?.let { unit ->
        AlertDialog(
            onDismissRequest = { showDeleteUnitDialog = null },
            title = { Text("Eliminar unidad") },
            text = { Text("¿Seguro que deseas eliminar la unidad \"${unit.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val success = viewModel.deleteUnit(unit.id)
                    if (success) {
                        showDeleteUnitDialog = null
                    } else {
                        errorMessage = "No se puede eliminar: es la única unidad o está en uso por un producto."
                    }
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUnitDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CurrencyManagementRow(
    currency: Currency,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LuisoCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSelect) {
                    Icon(
                        if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Seleccionar moneda",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${currency.symbol} ${currency.code}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currency.name + if (isSelected) " — en uso" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, enabled = !isSelected) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitManagementRow(
    unit: MeasurementUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LuisoCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = unit.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun DenominationManagementRow(
    denomination: Denomination,
    isFirst: Boolean,
    isLast: Boolean,
    isDisabled: Boolean,
    symbol: String = "$",
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    LuisoCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst && !isDisabled
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Mover arriba",
                        tint = if (isFirst || isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast && !isDisabled
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Mover abajo",
                        tint = if (isLast || isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = formatMoney(denomination.value, symbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(
                    onClick = onEdit,
                    enabled = !isDisabled
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isDisabled
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DenominationValueDialog(
    title: String,
    initialValue: String,
    confirmText: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var input by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            input = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    label = { Text("Valor") },
                    prefix = { Text("$ ") }
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
            LuisoButton(
                text = confirmText,
                onClick = {
                    val value = input.toLongOrNull() ?: 0L
                    onConfirm(value)
                }
            )
        },
        dismissButton = {
            LuisoOutlineButton(
                text = "CANCELAR",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun UnitDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var input by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LuisoTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = "Nombre (ej: Lb, Galón, Unidad)"
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
            LuisoButton(
                text = confirmText,
                onClick = { onConfirm(input) }
            )
        },
        dismissButton = {
            LuisoOutlineButton(
                text = "CANCELAR",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun CurrencyDialog(
    title: String,
    initialCode: String,
    initialName: String,
    initialSymbol: String,
    confirmText: String,
    onConfirm: (code: String, name: String, symbol: String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var code by remember { mutableStateOf(initialCode) }
    var name by remember { mutableStateOf(initialName) }
    var symbol by remember { mutableStateOf(initialSymbol) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LuisoTextField(
                    value = code,
                    onValueChange = { if (it.length <= 3) code = it.filter { c -> c.isLetterOrDigit() }.uppercase() },
                    label = "Código (3 letras)"
                )
                Spacer(modifier = Modifier.height(8.dp))
                LuisoTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nombre"
                )
                Spacer(modifier = Modifier.height(8.dp))
                LuisoTextField(
                    value = symbol,
                    onValueChange = { if (it.length <= 8) symbol = it },
                    label = "Símbolo"
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
            LuisoButton(
                text = confirmText,
                onClick = { onConfirm(code, name, symbol) }
            )
        },
        dismissButton = {
            LuisoOutlineButton(
                text = "CANCELAR",
                onClick = onDismiss
            )
        }
    )
}
