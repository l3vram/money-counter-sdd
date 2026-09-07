package com.moneycounter.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Currency
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.util.ExcelExporter
import com.moneycounter.util.PdfExporter
import com.moneycounter.viewmodel.MoneyCounterViewModel

@Composable
fun StockReportScreen(viewModel: MoneyCounterViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var reportCurrencyId by remember { mutableStateOf(uiState.selectedCurrencyId) }
    val reportCurrency = uiState.currencies.firstOrNull { it.id == reportCurrencyId }
    val currencySymbol = reportCurrency?.symbol ?: "$"
    val currencyCode = reportCurrency?.code ?: ""
    val inStock = uiState.products.filter { it.stock.signum() != 0 }
    val totalValue = inStock.fold(Money.ZERO) { acc, product ->
        acc.add(product.stockValueFor(reportCurrencyId) ?: Money.ZERO)
    }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Existencias",
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
            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Moneda:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StockReportCurrencySelector(
                        currencies = uiState.currencies,
                        selectedCurrencyId = reportCurrencyId,
                        onSelectCurrency = { reportCurrencyId = it }
                    )
                }
            }

            item {
                LuisoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DetailRow(
                            "TOTAL EN EXISTENCIA ($currencyCode)",
                            formatMoneyBigDecimal(totalValue, currencySymbol),
                            emphasize = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow(
                            "Productos con existencias",
                            "${inStock.size}"
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRODUCTO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "CANTIDAD",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "V.UNIT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (inStock.isEmpty()) {
                item {
                    Text(
                        text = "No hay existencias registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(inStock, key = { it.id }) { product ->
                    StockLine(
                        product = product,
                        selectedCurrencyId = reportCurrencyId,
                        symbol = currencySymbol
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                LuisoButton(
                    text = "EXPORTAR PDF",
                    onClick = {
                        PdfExporter(context).exportStockReport(
                            uiState.products,
                            reportCurrencyId,
                            currencySymbol,
                            currencyCode,
                            System.currentTimeMillis()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Share
                )
            }

            item {
                LuisoButton(
                    text = "EXPORTAR EXCEL (CSV)",
                    onClick = {
                        ExcelExporter(context).exportStockReport(
                            uiState.products,
                            reportCurrencyId,
                            currencySymbol,
                            currencyCode,
                            System.currentTimeMillis()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Share
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StockReportCurrencySelector(
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
            modifier = Modifier.width(120.dp)
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StockLine(product: Product, selectedCurrencyId: String, symbol: String = "$") {
    LuisoCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${product.stock.stripTrailingZeros().toPlainString()} ${product.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatMoneyBigDecimal(product.effectiveUnitPriceFor(selectedCurrencyId) ?: Money.ZERO, symbol),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = formatMoneyBigDecimal(product.stockValueFor(selectedCurrencyId) ?: Money.ZERO, symbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
