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
import com.moneycounter.domain.groupMovementsByMonthDay
import com.moneycounter.domain.keyString
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.MovementTypeBadge
import com.moneycounter.ui.components.formatMoneyBigDecimal
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
    onOpenSummary: (List<String>) -> Unit,
    onNavigateToGasto: () -> Unit,
    onNavigateToCierres: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
    var ascending by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
    val filtered = uiState.movements.filter { it.currencyId == filterCurrencyId }
    val groups = remember(filtered, ascending) { groupMovementsByMonthDay(filtered, ascending) }

    val now = remember { Calendar.getInstance() }
    val currentYear = now.get(Calendar.YEAR)
    val currentMonth = now.get(Calendar.MONTH) + 1

    fun toggleKey(key: String) {
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
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
                        }
                    )
                    LuisoOutlineButton(
                        text = if (ascending) "Antiguos ↑" else "Recientes ↓",
                        onClick = { ascending = !ascending }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LuisoButton(
                        text = "REGISTRAR GASTO",
                        onClick = onNavigateToGasto,
                        modifier = Modifier.weight(1f)
                    )
                    LuisoButton(
                        text = "CIERRES",
                        onClick = onNavigateToCierres,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                val currencyLabel = filterCurrency?.let { "${it.symbol} (${it.code}) — ${it.name}" } ?: "—"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LuisoSectionHeader(
                        text = "HISTORIAL DE MOVIMIENTOS EN $currencyLabel",
                        modifier = Modifier.weight(1f)
                    )
                    val summaryIds = uiState.history.filter { it.currencyId == filterCurrencyId }.map { it.id }
                    LuisoButton(
                        text = "RESUMEN",
                        onClick = { onOpenSummary(summaryIds) },
                        enabled = summaryIds.isNotEmpty()
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
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementMonthRow(
                                    group = row.group,
                                    expanded = row.group.key.keyString() in expandedKeys,
                                    accent = isCurrentMonth,
                                    onToggleExpand = { toggleKey(row.group.key.keyString()) }
                                )
                            }
                            Separator()
                        }
                        is MovementDayItem -> {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementDayRow(
                                    day = row.day,
                                    expanded = row.day.key.keyString() in expandedKeys,
                                    symbol = filterCurrency?.symbol ?: "$",
                                    onToggleExpand = { toggleKey(row.day.key.keyString()) }
                                )
                            }
                            Separator()
                        }
                        is MovementRowItem -> {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MovementRow(
                                    movement = row.movement,
                                    symbol = filterCurrency?.symbol ?: "$",
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
    onToggleExpand: () -> Unit
) {
    val dayLabel = remember(day.key) {
        val cal = Calendar.getInstance().apply { set(day.key.year, day.key.month - 1, day.key.day, 12, 0, 0) }
        SimpleDateFormat("EEE dd/MM/yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val dayTotal = day.movements.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.amount) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "TOTAL " + formatMoneyBigDecimal(dayTotal, symbol),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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
}

@Composable
private fun MovementRow(
    movement: Movement,
    symbol: String,
    onOpenDetail: () -> Unit
) {
    val subtitle = movement.concept?.takeIf { it.isNotBlank() }
        ?: movement.products.firstOrNull()?.name
        ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .clickable(onClick = onOpenDetail),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatMoneyBigDecimal(movement.amount, symbol),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatTime(movement.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Ver detalle",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
