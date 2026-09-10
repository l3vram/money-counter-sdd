package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Movement
import com.moneycounter.domain.MovementDenomination
import com.moneycounter.domain.MovementProductLine
import com.moneycounter.domain.hasDenominations
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoNoticeType
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.components.MovementTypeBadge
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.ui.components.formatMoneyBigDecimal
import com.moneycounter.viewmodel.MoneyCounterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(millis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDetailScreen(
    viewModel: MoneyCounterViewModel,
    movementId: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val movement = uiState.movements.firstOrNull { it.id == movementId }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Detalle",
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
        if (movement == null) {
            LuisoNotice(
                message = "Movimiento no encontrado",
                type = LuisoNoticeType.ERROR,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            )
        } else {
            val currency = uiState.currencies.firstOrNull { it.id == movement.currencyId }
            val symbol = currency?.symbol ?: "$"

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                item {
                    LuisoCard(modifier = Modifier.fillMaxWidth()) {
                        MovementTypeBadge(type = movement.type)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Fecha", formatDate(movement.at))
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailRow(
                            "MONEDA",
                            "$symbol (${currency?.code ?: movement.currencyId})"
                        )
                        if (!movement.concept.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DetailRow("Concepto", movement.concept)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailRow(
                            "Monto total",
                            formatMoneyBigDecimal(movement.amount, symbol),
                            emphasize = true
                        )
                    }
                }

                if (movement.products.isNotEmpty()) {
                    item {
                        LuisoSectionHeader(text = "PRODUCTOS")
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

                    items(
                        movement.products,
                        key = { "${movement.id}-${it.name}-${it.quantity.toPlainString()}-${it.subtotal.toPlainString()}" }
                    ) { line ->
                        MovementProductLineRow(item = line, symbol = symbol)
                    }
                }

                if (movement.type.hasDenominations()) {
                    item {
                        LuisoSectionHeader(text = "DENOMINACIONES")
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

                    if (movement.denominations.isEmpty()) {
                        item {
                            Text(
                                text = "No hay denominaciones registradas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(movement.denominations, key = { "${movement.id}-${it.value}" }) { item ->
                            MovementDenominationRow(item, symbol)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
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
private fun MovementDenominationRow(item: MovementDenomination, symbol: String = "$") {
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
                text = formatMoney(item.value, symbol),
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
private fun MovementProductLineRow(item: MovementProductLine, symbol: String = "$") {
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
