package com.moneycounter.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DayGroup
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.MonthGroup
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.groupByMonthDay
import com.moneycounter.domain.isCurrentMonth
import com.moneycounter.domain.isToday
import com.moneycounter.domain.keyString
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

    val filterCurrency = uiState.currencies.firstOrNull { it.id == filterCurrencyId }
    val filtered = uiState.history.filter { it.currencyId == filterCurrencyId }
    val groups = remember(filtered, ascending) { groupByMonthDay(filtered, ascending) }

    if (expandedKeys.isEmpty()) {
        expandedKeys = buildSet {
            groups.forEach { g -> if (isCurrentMonth(g.key)) add(g.key.keyString()) }
            groups.forEach { g -> g.days.forEach { d -> if (isToday(d.key)) add(d.key.keyString()) } }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes") },
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
                    OutlinedButton(onClick = { ascending = !ascending }) {
                        Text(if (ascending) "Antiguos ↑" else "Recientes ↓")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalButton(onClick = { selectionMode = !selectionMode }) {
                        Text(if (selectionMode) "Listo" else "Seleccionar")
                    }
                    FilledTonalButton(
                        onClick = { onOpenSummary(selectedIds.toList()) },
                        enabled = selectionMode && selectedIds.isNotEmpty()
                    ) {
                        Text(
                            if (selectedIds.isEmpty()) "GENERAR RESUMEN"
                            else "GENERAR RESUMEN (${selectedIds.size})"
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No hay registros guardados para ${filterCurrency?.code ?: filterCurrencyId}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is MonthItem -> {
                            val monthIds = row.group.days.flatMap { it.counts }.map { it.id }
                            MonthHeaderRow(
                                group = row.group,
                                expanded = row.group.key.keyString() in expandedKeys,
                                selectionMode = selectionMode,
                                allSelected = monthIds.all { it in selectedIds },
                                onToggleExpand = { toggleKey(row.group.key.keyString()) },
                                onToggleAll = { toggleAll(monthIds) }
                            )
                        }
                        is DayItem -> {
                            val dayIds = row.day.counts.map { it.id }
                            DayHeaderRow(
                                day = row.day,
                                expanded = row.day.key.keyString() in expandedKeys,
                                selectionMode = selectionMode,
                                allSelected = dayIds.all { it in selectedIds },
                                symbol = filterCurrency?.symbol ?: "$",
                                onToggleExpand = { toggleKey(row.day.key.keyString()) },
                                onToggleAll = { toggleAll(dayIds) }
                            )
                        }
                        is CountItem -> {
                            val code = uiState.currencies
                                .firstOrNull { it.id == row.saved.currencyId }
                                ?.code
                                ?: row.saved.currencyId
                            ReportRow(
                                saved = row.saved,
                                selected = row.saved.id in selectedIds,
                                code = code,
                                selectionMode = selectionMode,
                                onToggle = { toggleSingle(row.saved.id) },
                                onOpenDetail = { onOpenDetail(row.saved.id) }
                            )
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
private fun MonthHeaderRow(
    group: MonthGroup,
    expanded: Boolean,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAll: () -> Unit
) {
    val monthLabel = remember(group.key) {
        val cal = Calendar.getInstance().apply { set(group.key.year, group.key.month - 1, 1, 12, 0, 0) }
        SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(cal.timeInMillis))
    }
    val operaciones = group.days.sumOf { it.counts.size }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleAll() })
            }
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "($operaciones operaciones)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Contraer mes" else "Expandir mes"
                )
            }
        }
    }
}

@Composable
private fun DayHeaderRow(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "· ${day.counts.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Contraer día" else "Expandir día"
                )
            }
        }
    }
}

@Composable
private fun ReportRow(
    saved: SavedCount,
    selected: Boolean,
    code: String,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (selectionMode) onToggle else onOpenDetail),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() }
                )
            } else {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatMoneyBigDecimal(saved.targetAmount, saved.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "· " + formatTime(saved.savedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            if (selectionMode) {
                IconButton(onClick = onOpenDetail) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "Ver detalle"
                    )
                }
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(96.dp)
        ) {
            Text(
                text = selected?.let { "${it.symbol} ${it.code}" } ?: "—",
                style = MaterialTheme.typography.bodySmall
            )
        }
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