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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.UnitedDenomination
import com.moneycounter.domain.UnitedProduct
import com.moneycounter.domain.uniteCounts
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoNoticeType
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoStatCard
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.TermInfo
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.util.ExcelExporter
import com.moneycounter.util.PdfExporter
import com.moneycounter.viewmodel.MoneyCounterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedReportScreen(
    viewModel: MoneyCounterViewModel,
    selectedCountIds: List<String>,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selected = uiState.history.filter { it.id in selectedCountIds.toSet() }
    val countryCode = uiState.currencies
        .firstOrNull { it.id == selected.firstOrNull()?.currencyId }?.code.orEmpty()
    val united = remember(selected, uiState.currencies) {
        runCatching { uniteCounts(selected, countryCode) }
    }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Resumen unificado",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    TermInfo(
                        correctTerm = "Cierre / Resumen consolidado",
                        oldName = "Reporte unificado",
                        explanation = "Consolidación de las ventas del período."
                    )
                }
            )
        }
    ) { padding ->
        when {
            selected.isEmpty() -> {
                LuisoNotice(
                    message = "No hay registros seleccionados.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                )
            }

            united.isFailure -> {
                LuisoNotice(
                    message = "Los registros seleccionados son de monedas distintas. Selecciona ventas de una misma moneda.",
                    type = LuisoNoticeType.ERROR,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                )
            }

            else -> {
                val u = united.getOrThrow()
                var exportMenuOpen by remember { mutableStateOf(false) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    item {
                        LuisoStatCard(
                            label = "${u.currencyCode} (${u.currencySymbol})",
                            value = formatMoneyBigDecimal(u.total(), u.currencySymbol),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        LuisoCard(modifier = Modifier.fillMaxWidth()) {
                            DetailRow("MONEDA", "${u.currencySymbol} (${u.currencyCode})")
                        }
                    }

                    item {
                        LuisoCard(modifier = Modifier.fillMaxWidth()) {
                            DetailRow(
                                "VENTAS",
                                "${u.count}"
                            )
                        }
                    }

                    if (u.products.isNotEmpty()) {
                        item {
                            LuisoSectionHeader(
                                text = "PRODUCTOS"
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PRODUCTO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "CANTIDAD",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "TOTAL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(u.products, key = { "${it.name}-${it.unit}" }) { item ->
                            UnitedProductLine(item = item, symbol = u.currencySymbol)
                        }
                    }

                    item {
                        LuisoSectionHeader(
                            text = "DENOMINACIONES"
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DENOMINACIÓN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "CANTIDAD",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "TOTAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (u.items.isEmpty()) {
                        item {
                            Text(
                                text = "No hay denominaciones registradas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(u.items, key = { "${it.denominationValue}" }) { item ->
                            UnitedItemLine(item = item, symbol = u.currencySymbol)
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LuisoButton(
                                text = "Exportar ▾",
                                leadingIcon = Icons.Default.Description,
                                onClick = { exportMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = exportMenuOpen,
                                onDismissRequest = { exportMenuOpen = false }
                            ) {
                                DropdownMenuItem(text = { Text("PDF") }, onClick = {
                                    exportMenuOpen = false
                                    PdfExporter(context).exportUnited(u, u.currencySymbol)
                                })
                                DropdownMenuItem(text = { Text("Excel (CSV)") }, onClick = {
                                    exportMenuOpen = false
                                    ExcelExporter(context).exportUnited(u, u.currencySymbol)
                                })
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UnitedItemLine(item: UnitedDenomination, symbol: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMoney(item.denominationValue, symbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "x ${item.quantity}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMoneyBigDecimal(item.subtotal, symbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UnitedProductLine(item: UnitedProduct, symbol: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${item.quantity.toPlainString()} ${item.unit}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMoneyBigDecimal(item.subtotal, symbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
