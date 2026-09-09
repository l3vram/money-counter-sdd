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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DayGroup
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.MonthGroup
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.groupByMonthDay
import com.moneycounter.domain.keyString
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.TermInfo
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
    onOpenSummary: (List<String>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterCurrencyId by remember { mutableStateOf(DefaultCurrencies.CUP.id) }
    var ascending by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
    val filtered = uiState.history.filter { it.currencyId == filterCurrencyId }
    val groups = remember(filtered, ascending) { groupByMonthDay(filtered, ascending) }

    val now = remember { Calendar.getInstance() }
    val currentYear = now.get(Calendar.YEAR)
    val currentMonth = now.get(Calendar.MONTH) + 1

    fun toggleKey(key: String) {
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    fun toggleSingle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun toggleAll(ids: List<String>) {
        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids.toSet() else selectedIds + ids
    }

    val rows = buildList {
        groups.forEach { group ->
            add(MonthItem(group))
            if (group.key.keyString() in expandedKeys) {
                group.days.forEach { day ->
                    add(DayItem(day))
                    if (day.key.keyString() in expandedKeys) {
                        day.counts.forEach { add(CountItem(it)) }
                    }
                }
            }
        }
    }

    val lastCountKeys = buildSet {
        groups.forEach { month ->
            month.days.forEach { day ->
                day.counts.lastOrNull()?.let { add(it.id) }
            }
        }
    }

    Scaffold(
        topBar = {
            LuisoTopBar(title = "Reportes")
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
                            selectedIds = emptySet()
                            expandedKeys = emptySet()
                        }
                    )
                    LuisoOutlineButton(
                        text = if (ascending) "Antiguos ↑" else "Recientes ↓",
                        onClick = { ascending = !ascending }
                    )
                    LuisoButton(
                        text = if (selectionMode) "Listo" else "Selección",
                        onClick = { selectionMode = !selectionMode }
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
                        text = "REPORTES EN $currencyLabel",
                        modifier = Modifier.weight(1f)
                    )
                    TermInfo(
                        correctTerm = "Cierre / Resumen consolidado",
                        oldName = "Reporte unificado",
                        explanation = "Consolidación de las ventas del período."
                    )
                }
            }

            if (selectionMode) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LuisoOutlineButton(
                            text = "ELIMINAR",
                            onClick = { confirmDelete = true },
                            modifier = Modifier.weight(1f),
                            enabled = selectedIds.isNotEmpty(),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                        LuisoButton(
                            text = if (selectedIds.isEmpty()) "RESUMEN"
                            else "RESUMEN (${selectedIds.size})",
                            onClick = { onOpenSummary(selectedIds.toList()) },
                            modifier = Modifier.weight(1f),
                            enabled = selectedIds.isNotEmpty()
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    LuisoNotice(
                        message = "Todavía no hay reportes.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            } else {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is MonthItem -> {
                            val monthIds = row.group.days.flatMap { it.counts }.map { it.id }
                            val isCurrentMonth = row.group.key.year == currentYear && row.group.key.month == currentMonth
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                MonthRow(
                                    group = row.group,
                                    expanded = row.group.key.keyString() in expandedKeys,
                                    selectionMode = selectionMode,
                                    allSelected = monthIds.all { it in selectedIds },
                                    accent = isCurrentMonth,
                                    onToggleExpand = { toggleKey(row.group.key.keyString()) },
                                    onToggleAll = { toggleAll(monthIds) }
                                )
                            }
                            Separator()
                        }
                        is DayItem -> {
                            val dayIds = row.day.counts.map { it.id }
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                DayRow(
                                    day = row.day,
                                    expanded = row.day.key.keyString() in expandedKeys,
                                    selectionMode = selectionMode,
                                    allSelected = dayIds.all { it in selectedIds },
                                    symbol = filterCurrency?.symbol ?: "$",
                                    onToggleExpand = { toggleKey(row.day.key.keyString()) },
                                    onToggleAll = { toggleAll(dayIds) }
                                )
                            }
                            Separator()
                        }
                        is CountItem -> {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                ReportRow(
                                    saved = row.saved,
                                    selected = row.saved.id in selectedIds,
                                    selectionMode = selectionMode,
                                    onToggle = { toggleSingle(row.saved.id) },
                                    onOpenDetail = { onOpenDetail(row.saved.id) }
                                )
                            }
                            if (row.saved.id !in lastCountKeys) {
                                Separator()
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar operaciones") },
            text = {
                Text(
                    if (selectedIds.size == 1) "¿Eliminar esta operación?"
                    else "¿Eliminar ${selectedIds.size} operaciones? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedCounts(selectedIds.toList())
                        selectedIds = emptySet()
                        confirmDelete = false
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun MonthRow(
    group: MonthGroup,
    expanded: Boolean,
    selectionMode: Boolean,
    allSelected: Boolean,
    accent: Boolean = false,
    onToggleExpand: () -> Unit,
    onToggleAll: () -> Unit
) {
    val monthLabel = remember(group.key) {
        val cal = Calendar.getInstance().apply { set(group.key.year, group.key.month - 1, 1, 12, 0, 0) }
        SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val operaciones = group.days.sumOf { it.counts.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
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
private fun DayRow(
    day: DayGroup,
    expanded: Boolean,
    selectionMode: Boolean,
    allSelected: Boolean,
    symbol: String,
    onToggleExpand: () -> Unit,
    onToggleAll: () -> Unit
) {
    val dayLabel = remember(day.key) {
        val cal = Calendar.getInstance().apply { set(day.key.year, day.key.month - 1, day.key.day, 12, 0, 0) }
        SimpleDateFormat("EEE dd/MM/yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val dayTotal = day.counts.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.targetAmount) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleAll() })
        }
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
            text = "· ${day.counts.size}",
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
private fun ReportRow(
    saved: SavedCount,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .clickable(onClick = if (selectionMode) onToggle else onOpenDetail),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMoneyBigDecimal(saved.targetAmount, saved.currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatTime(saved.savedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!selectionMode) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

sealed interface ReportItem {
    val key: String
}

data class MonthItem(val group: MonthGroup) : ReportItem {
    override val key: String get() = group.key.keyString()
}

data class DayItem(val day: DayGroup) : ReportItem {
    override val key: String get() = day.key.keyString()
}

data class CountItem(val saved: SavedCount) : ReportItem {
    override val key: String get() = saved.id
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
