package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import com.moneycounter.domain.CounterStatus
import com.moneycounter.domain.Money
import com.moneycounter.ui.components.DenominationRow
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyCounterScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var targetInput by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contador de dinero\nEl Luiso") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Ver historial",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configurar denominaciones",
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
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                TargetSection(
                    targetAmount = uiState.targetAmount,
                    targetInput = targetInput,
                    onTargetInputChange = { targetInput = it },
                    onTargetSet = { amount ->
                        viewModel.setTargetAmount(amount)
                        targetInput = if (amount != null) amount.toPlainString() else ""
                    },
                    onTargetClear = {
                        viewModel.setTargetAmount(null)
                        targetInput = ""
                    }
                )
            }

            item {
                SummarySection(
                    result = uiState.result,
                    targetAmount = uiState.targetAmount,
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
                            targetInput = ""
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
            text = { Text("Se eliminará el objetivo y todas las cantidades. Las denominaciones configuradas se mantendrán.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    targetInput = ""
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
private fun TargetSection(
    targetAmount: BigDecimal?,
    targetInput: String,
    onTargetInputChange: (String) -> Unit,
    onTargetSet: (BigDecimal?) -> Unit,
    onTargetClear: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "OBJETIVO",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || Regex("^\\d*(?:[.,]\\d{0,2})?$").matches(newValue)) {
                            onTargetInputChange(newValue)
                            val amount = parseDecimalInput(newValue)
                            if (amount != null && amount > BigDecimal.ZERO) {
                                onTargetSet(amount)
                            } else {
                                onTargetClear()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Monto objetivo") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    prefix = { Text("$ ") }
                )
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onTargetClear()
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Limpiar objetivo",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(
    result: com.moneycounter.domain.CounterResult,
    targetAmount: BigDecimal?,
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
                    value = "$${formatMoneyBigDecimal(targetAmount)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SummaryRow(
                label = "CONTADO",
                value = "$${formatMoneyBigDecimal(result.countedTotal)}",
                color = when (result.status) {
                    CounterStatus.COMPLETED -> Color(0xFF1B6B3A)
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
                        CounterStatus.COMPLETED -> Color(0xFF1B6B3A)
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
                            color = Color(0xFF1B6B3A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (savedCountId != null) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("GUARDADO")
                            }
                        } else {
                            Button(
                                onClick = onSave,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("GUARDAR EN HISTORIAL")
                            }
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
                            text = "+$${formatMoneyBigDecimal(result.excess)}",
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
                            text = "$${formatMoneyBigDecimal(result.remaining)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CounterStatus.EMPTY -> {
                        Text(
                            text = "Ingresa un objetivo y comienza a contar",
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
                    text = "Ingresa un objetivo para ver el progreso",
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

private fun parseDecimalInput(input: String): BigDecimal? {
    if (input.isBlank()) return null
    return try {
        Money.of(input.replace(',', '.'))
    } catch (e: Exception) {
        null
    }
}
