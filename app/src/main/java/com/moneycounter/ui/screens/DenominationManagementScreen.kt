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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Denomination
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.viewmodel.MoneyCounterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DenominationManagementScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Denomination?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Denomination?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Denominaciones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Agregar denominación",
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

            if (uiState.hasActiveCount) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "No puedes modificar las denominaciones mientras haya un conteo activo. Borra el conteo primero.",
                            modifier = Modifier.padding(16.dp),
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
                    onEdit = { showEditDialog = denomination },
                    onDelete = { showDeleteDialog = denomination },
                    onMoveUp = { viewModel.moveDenominationUp(denomination.id) },
                    onMoveDown = { viewModel.moveDenominationDown(denomination.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("AGREGAR DENOMINACIÓN")
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        DenominationValueDialog(
            title = "Nueva denominación",
            initialValue = "",
            confirmText = "GUARDAR",
            onConfirm = { value ->
                val success = viewModel.addDenomination(value)
                if (success) {
                    showAddDialog = false
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
                showAddDialog = false
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showEditDialog?.let { denomination ->
        DenominationValueDialog(
            title = "Editar denominación",
            initialValue = denomination.value.toString(),
            confirmText = "GUARDAR",
            onConfirm = { value ->
                val success = viewModel.editDenomination(denomination.id, value)
                if (success) {
                    showEditDialog = null
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
                showEditDialog = null
                errorMessage = null
            },
            errorMessage = errorMessage
        )
    }

    showDeleteDialog?.let { denomination ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Eliminar denominación") },
            text = { Text("¿Estás seguro de que deseas eliminar la denominación de $${formatMoney(denomination.value)}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDenomination(denomination.id)
                    showDeleteDialog = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DenominationManagementRow(
    denomination: Denomination,
    isFirst: Boolean,
    isLast: Boolean,
    isDisabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDisabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                text = "$${formatMoney(denomination.value)}",
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
                        else MaterialTheme.colorScheme.primary
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
                Text(
                    text = "Valor",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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
            TextButton(onClick = {
                val value = input.toLongOrNull() ?: 0L
                onConfirm(value)
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
