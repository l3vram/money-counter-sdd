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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementDayGroup
import com.moneycounter.domain.MovementMonthGroup
import com.moneycounter.domain.MovementProductLine
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.groupMovementsByMonthDay
import com.moneycounter.domain.keyString
import com.moneycounter.domain.netCashTotal
import com.moneycounter.domain.receivableTotal
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.MovementTypeBadge
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.ui.components.moneySign
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MoneyCounterViewModel,
    onOpenDetail: (String) -> Unit,
    onNavigateToGasto: () -> Unit,
    onOpenSummary: (List<String>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
    var ascending by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
    val filtered = uiState.movements.filter { it.currencyId == filterCurrencyId }
    val groups = remember(filtered, ascending) { groupMovementsByMonthDay(filtered, ascending) }

    val now = remember { Calendar.getInstance() }
    val currentYear = now.get(Calendar.YEAR)
    val currentMonth = now.get(Calendar.MONTH) + 1

    fun toggleKey(key: String) {
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    fun toggleId(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun toggleGroup(ids: List<String>) {
        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids.toSet() else selectedIds + ids.toSet()
    }

    val rows = buildList {
        groups.forEach { group ->
            add(MovementMonthItem(group))
            if (group.key.keyString() in expandedKeys) {
                group.days.forEach { day ->
                    add(MovementDayItem(day))
                    if (day.key.keyString() in expandedKeys) {
                        day.movements.forEach { add(MovementRowItem(it)) }
                    }
                }
            }
        }
    }

    val lastMovementKeys = buildSet {
        groups.forEach { month ->
            month.days.forEach { day ->
                day.movements.lastOrNull()?.let { add(it.id) }
            }
        }
    }

    Scaffold(
        topBar = {
            LuisoTopBar(title = "Historial")
        },
        bottomBar = {
            if (selectionMode) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Seleccionadas: ${selectedIds.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        LuisoButton(
                            text = if (selectedIds.isEmpty()) "GENERAR RESUMEN" else "GENERAR RESUMEN (${selectedIds.size})",
                            onClick = { onOpenSummary(selectedIds.toList()) },
                            enabled = selectedIds.isNotEmpty()
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ReportsCurrencySelector(
                        currencies = uiState.currencies,
                        selectedCurrencyId = filterCurrencyId,
                        onSelectCurrency = {
                            filterCurrencyId = it
                            expandedKeys = emptySet()
                            selectedIds = emptySet()
                        }
                    )
                    LuisoOutlineButton(
                        text = if (selectionMode) "Listo" else "Selección",
                        onClick = { selectionMode = !selectionMode },
                        modifier = Modifier.width(110.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LuisoOutlineButton(
                        text = if (ascending) "Antiguos ↑" else "Recientes ↓",
                        onClick = { ascending = !ascending }
                    )
                    if (selectionMode) {
                        Text(
                            text = "Marca las ventas a consolidar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                LuisoButton(
                    text = "REGISTRAR GASTO",
                    onClick = onNavigateToGasto,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val currencyLabel = filterCurrency?.let { "${it.symbol} (${it.code}) — ${it.name}" } ?: "—"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LuisoSectionHeader(
                        text = "MOVIMIENTOS EN $currencyLabel",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "Todavía no hay movimientos.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            } else {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is MovementMonthItem -> {
                            val isCurrentMonth = row.group.key.year == currentYear && row.group.key.month == currentMonth
                            val monthSelectable = row.group.days
                                .flatMap { it.movements }
                                .filter { it.denominations.isNotEmpty() }
                                .map { it.id }
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementMonthRow(
                                    group = row.group,
                                    expanded = row.group.key.keyString() in expandedKeys,
                                    accent = isCurrentMonth,
                                    selectionMode = selectionMode,
                                    selectableCount = monthSelectable.size,
                                    allSelected = monthSelectable.isNotEmpty() && monthSelectable.all { it in selectedIds },
                                    onToggleAll = { toggleGroup(monthSelectable) },
                                    onToggleExpand = { toggleKey(row.group.key.keyString()) }
                                )
                            }
                            Separator()
                        }
                        is MovementDayItem -> {
                            val daySelectable = row.day.movements
                                .filter { it.denominations.isNotEmpty() }
                                .map { it.id }
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementDayRow(
                                    day = row.day,
                                    expanded = row.day.key.keyString() in expandedKeys,
                                    symbol = filterCurrency?.symbol ?: "$",
                                    selectionMode = selectionMode,
                                    selectableCount = daySelectable.size,
                                    allSelected = daySelectable.isNotEmpty() && daySelectable.all { it in selectedIds },
                                    onToggleAll = { toggleGroup(daySelectable) },
                                    onToggleExpand = { toggleKey(row.day.key.keyString()) }
                                )
                            }
                            Separator()
                        }
                        is MovementRowItem -> {
                            val selectable = row.movement.denominations.isNotEmpty()
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementRow(
                                    movement = row.movement,
                                    symbol = filterCurrency?.symbol ?: "$",
                                    selectionMode = selectionMode,
                                    selectable = selectable,
                                    selected = row.movement.id in selectedIds,
                                    onToggle = { toggleId(row.movement.id) },
                                    onOpenDetail = { onOpenDetail(row.movement.id) }
                                )
                            }
                            if (row.movement.id !in lastMovementKeys) {
                                Separator()
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun MovementMonthRow(
    group: MovementMonthGroup,
    expanded: Boolean,
    accent: Boolean = false,
    selectionMode: Boolean = false,
    selectableCount: Int = 0,
    allSelected: Boolean = false,
    onToggleAll: () -> Unit = {},
    onToggleExpand: () -> Unit
) {
    val monthLabel = remember(group.key) {
        val cal = Calendar.getInstance().apply { set(group.key.year, group.key.month - 1, 1, 12, 0, 0) }
        SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val operaciones = group.days.sumOf { it.movements.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode && selectableCount > 0) {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleAll() })
        }
        LuisoSectionHeader(
            text = monthLabel,
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "($operaciones)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Contraer mes" else "Expandir mes"
            )
        }
    }
}

@Composable
private fun MovementDayRow(
    day: MovementDayGroup,
    expanded: Boolean,
    symbol: String,
    selectionMode: Boolean = false,
    selectableCount: Int = 0,
    allSelected: Boolean = false,
    onToggleAll: () -> Unit = {},
    onToggleExpand: () -> Unit
) {
    val dayLabel = remember(day.key) {
        val cal = Calendar.getInstance().apply { set(day.key.year, day.key.month - 1, day.key.day, 12, 0, 0) }
        SimpleDateFormat("EEE dd/MM/yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val dayCash = netCashTotal(day.movements)
    val dayReceivable = receivableTotal(day.movements)
    val cashPrefix = if (dayCash.signum() < 0) "-" else if (dayCash.signum() > 0) "+" else ""
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode && selectableCount > 0) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleAll() })
            }
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "TOTAL $cashPrefix" + formatMoneyBigDecimal(dayCash.abs(), symbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (dayCash.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "· ${day.movements.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Contraer día" else "Expandir día"
                )
            }
        }
        if (dayReceivable.signum() > 0) {
            Text(
                text = "Por cobrar (fiado): ~" + formatMoneyBigDecimal(dayReceivable, symbol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun MovementRow(
    movement: Movement,
    symbol: String,
    selectionMode: Boolean = false,
    selectable: Boolean = true,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
    onOpenDetail: () -> Unit
) {
    val concept = movement.concept?.takeIf { it.isNotBlank() }
    val productSummary = MovementProductSummary(movement.products)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .clickable(
                enabled = !selectionMode || selectable,
                onClick = { if (selectionMode) onToggle() else onOpenDetail() }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode && selectable) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MovementTypeBadge(type = movement.type)
            if (concept != null) {
                Text(
                    text = concept,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (productSummary.isNotBlank()) {
                Text(
                    text = productSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = movement.type.moneySign() + " " + formatMoneyBigDecimal(movement.amount, symbol),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when (movement.type) {
                    MovementType.GASTO, MovementType.MERMA -> MaterialTheme.colorScheme.error
                    MovementType.VENTA_FIADO -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = formatTime(movement.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!selectionMode) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Ver detalle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    )
}

private fun MovementProductSummary(products: List<MovementProductLine>): String =
    products.joinToString("  ·  ") { line ->
        "${line.name} ${line.quantity.stripTrailingZeros().toPlainString()} ${line.unit}"
    }

sealed interface MovementReportItem {
    val key: String
}

data class MovementMonthItem(val group: MovementMonthGroup) : MovementReportItem {
    override val key: String get() = group.key.keyString()
}

data class MovementDayItem(val day: MovementDayGroup) : MovementReportItem {
    override val key: String get() = day.key.keyString()
}

data class MovementRowItem(val movement: Movement) : MovementReportItem {
    override val key: String get() = movement.id
}

@Composable
private fun ReportsCurrencySelector(
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
