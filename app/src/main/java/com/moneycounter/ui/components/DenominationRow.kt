package com.moneycounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneycounter.domain.Denomination
import com.moneycounter.domain.QuantityParser
import java.math.BigDecimal

@Composable
fun DenominationRow(
    denomination: Denomination,
    quantity: Long,
    subtotal: BigDecimal,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onQuantityChanged: (Long) -> Unit,
    symbol: String = "$",
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMoney(denomination.value, symbol),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.24f)
            )

            Row(
                modifier = Modifier.weight(0.52f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = {
                        isEditing = false
                        editText = ""
                        onDecrement()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrementar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                BasicTextField(
                    value = when {
                        isEditing -> editText
                        quantity == 0L -> ""
                        else -> quantity.toString()
                    },
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            editText = newValue
                            isEditing = true
                            onQuantityChanged(
                                (QuantityParser.parse(newValue) ?: 0L).coerceAtLeast(0L)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && isEditing) {
                                val parsed = QuantityParser.parse(editText) ?: 0L
                                onQuantityChanged(parsed.coerceAtLeast(0L))
                                isEditing = false
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = QuantityParser.parse(editText) ?: 0L
                            onQuantityChanged(parsed.coerceAtLeast(0L))
                            isEditing = false
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 2.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        isEditing = false
                        editText = ""
                        onIncrement()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Incrementar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = formatMoneyBigDecimal(subtotal, symbol),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.24f),
                textAlign = TextAlign.End
            )
        }
    }
}

fun formatMoney(value: Long, symbol: String = "$"): String {
    return "$symbol${value.toString().reversed().chunked(3).joinToString(".").reversed()}"
}

fun formatMoneyBigDecimal(value: BigDecimal, symbol: String = "$"): String {
    val scaled = value.stripTrailingZeros()
    val plain = scaled.toPlainString()
    val parts = plain.split(".")
    val intPart = parts[0]
    val decPart = parts.getOrElse(1) { "00" }.padEnd(2, '0').take(2)
    val formattedInt = intPart.reversed().chunked(3).joinToString(".").reversed()
    return "$symbol$formattedInt,$decPart"
}
