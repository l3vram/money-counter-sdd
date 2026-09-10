package com.moneycounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Closing
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.Money
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.computeClosing
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoNoticeType
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.MovementTypeBadge
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.util.ExcelExporter
import com.moneycounter.util.PdfExporter
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cierre = period close (corte de caja/inventario), not a report: tallies the OPEN
 * journal movements for one currency, snapshots remaining stock, and stamps those
 * movements so they can never be counted in another cierre. Default flow is one tap
 * ("CERRAR EL DÍA", all open movements); manual selection is also supported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierresScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var filterCurrencyId by remember { mutableStateOf(uiState.selectedCurrencyId) }
    var manualMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val currency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
    val symbol = currency?.symbol ?: "$"
    val open = remember(uiState.movements, filterCurrencyId) {
        viewModel.openMovements(filterCurrencyId)
    }
    val pastClosings = uiState.closings.filter { it.currencyId == filterCurrencyId }

    val selectedMovements = if (manualMode) open.filter { it.id in selectedIds } else open
    val preview = remember(selectedMovements, uiState.products, filterCurrencyId) {
        computeClosing(
            id = "preview",
            at = 0L,
            movements = selectedMovements,
            products = uiState.products,
            currencyId = filterCurrencyId
        )
    }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Cierres",
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
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CierresCurrencySelector(
                        currencies = uiState.currencies,
                        selectedCurrencyId = filterCurrencyId,
                        onSelectCurrency = {
                            filterCurrencyId = it
                            selectedIds = emptySet()
                            resultMessage = null
                        }
                    )
                    LuisoOutlineButton(
                        text = if (manualMode) "Selección manual" else "Todos los abiertos",
                        onClick = {
                            manualMode = !manualMode
                            selectedIds = emptySet()
                        }
                    )
                }
            }

            item { LuisoSectionHeader(text = "MOVIMIENTOS ABIERTOS ($symbol)") }

            if (open.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "No hay movimientos abiertos para cerrar en esta moneda.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            } else {
                items(open, key = { it.id }) { movement ->
                    OpenMovementRow(
                        movement = movement,
                        symbol = symbol,
                        selectable = manualMode,
                        selected = movement.id in selectedIds,
                        onToggle = {
                            selectedIds = if (movement.id in selectedIds) {
                                selectedIds - movement.id
                            } else {
                                selectedIds + movement.id
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                ClosingPreviewCard(preview = preview, symbol = symbol)
            }

            item {
                val canClose = selectedMovements.isNotEmpty()
                LuisoButton(
                    text = if (manualMode) "CERRAR SELECCIÓN" else "CERRAR EL DÍA",
                    onClick = {
                        val ids = selectedMovements.map { it.id }
                        val newId = viewModel.createClosing(ids)
                        resultMessage = if (newId != null) {
                            selectedIds = emptySet()
                            "Cierre creado."
                        } else {
                            "No se pudo crear el cierre."
                        }
                    },
                    enabled = canClose,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            resultMessage?.let { msg ->
                item {
                    LuisoNotice(
                        message = msg,
                        type = if (msg.startsWith("Cierre creado")) LuisoNoticeType.INFO else LuisoNoticeType.ERROR
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { LuisoSectionHeader(text = "CIERRES ANTERIORES") }

            if (pastClosings.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "Todavía no hay cierres para esta moneda.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            } else {
                items(pastClosings, key = { it.id }) { closing ->
                    PastClosingRow(
                        closing = closing,
                        symbol = symbol,
                        onExportCsv = { ExcelExporter(context).exportClosing(closing, symbol) },
                        onExportPdf = { PdfExporter(context).exportClosing(closing, symbol) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OpenMovementRow(
    movement: Movement,
    symbol: String,
    selectable: Boolean,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val subtitle = movement.concept?.takeIf { it.isNotBlank() }
        ?: movement.products.firstOrNull()?.name
        ?: ""
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .let { if (selectable) it.clickable(onClick = onToggle) else it },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectable) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            }
            Column(modifier = Modifier.weight(1f)) {
                MovementTypeBadge(type = movement.type)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = formatMoneyBigDecimal(movement.amount, symbol),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ClosingPreviewCard(preview: Closing, symbol: String) {
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "VISTA PREVIA DEL CIERRE",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        MovementType.entries.forEach { type ->
            val amount = preview.totalsByType[type] ?: Money.ZERO
            if (amount.signum() != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = type.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = formatMoneyBigDecimal(amount, symbol), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "NETO EN CAJA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = formatMoneyBigDecimal(preview.netCash, symbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "EXISTENCIAS RESTANTES",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        if (preview.stockSnapshot.isEmpty()) {
            Text(
                text = "No hay existencias.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            preview.stockSnapshot.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = line.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${line.quantity.stripTrailingZeros().toPlainString()} ${line.unit}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PastClosingRow(
    closing: Closing,
    symbol: String,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    val dateStr = remember(closing.at) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).format(Date(closing.at))
    }
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = "${closing.movementIds.size} movimientos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMoneyBigDecimal(closing.netCash, symbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LuisoOutlineButton(text = "CSV", onClick = onExportCsv)
            LuisoOutlineButton(text = "PDF", onClick = onExportPdf)
        }
    }
}

@Composable
private fun CierresCurrencySelector(
    currencies: List<Currency>,
    selectedCurrencyId: String,
    onSelectCurrency: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = currencies.firstOrNull { it.id == selectedCurrencyId }

    Box {
        LuisoOutlineButton(
            text = selected?.let { "${it.symbol} ${it.code}" } ?: "—",
            onClick = { expanded = true },
            modifier = Modifier.width(110.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.symbol} ${c.code} — ${c.name}") },
                    onClick = {
                        onSelectCurrency(c.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
