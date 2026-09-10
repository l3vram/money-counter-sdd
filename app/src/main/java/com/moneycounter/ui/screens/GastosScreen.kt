package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneycounter.ui.components.LuisoButton
import com.moneycounter.ui.components.LuisoNotice
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTextField
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.viewmodel.MoneyCounterViewModel

@Composable
fun GastosScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCurrencyId by remember { mutableStateOf(uiState.selectedCurrencyId) }
    val selectedCurrency = uiState.currencies.firstOrNull { it.id == selectedCurrencyId }
    val currencySymbol = selectedCurrency?.symbol ?: "$"
    var concept by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var currencyMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Gastos",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            LuisoSectionHeader(text = "REGISTRAR GASTO")

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { currencyMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Moneda: $currencySymbol ${selectedCurrency?.code ?: ""}",
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownMenu(
                    expanded = currencyMenuOpen,
                    onDismissRequest = { currencyMenuOpen = false }
                ) {
                    uiState.currencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                            onClick = {
                                selectedCurrencyId = currency.id
                                currencyMenuOpen = false
                            }
                        )
                    }
                }
            }

            LuisoTextField(
                value = concept,
                onValueChange = {
                    concept = it
                    errorMessage = null
                    successMessage = null
                },
                label = "Concepto",
                hint = "Ej: pago por descarga",
                modifier = Modifier.fillMaxWidth()
            )

            LuisoTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorMessage = null
                    successMessage = null
                },
                label = "Monto ($currencySymbol)",
                hint = "0.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                LuisoNotice(message = it)
            }
            successMessage?.let {
                LuisoNotice(message = it)
            }

            LuisoButton(
                text = "REGISTRAR GASTO",
                onClick = {
                    if (viewModel.recordExpense(concept, amountText, selectedCurrencyId)) {
                        successMessage = "Gasto registrado."
                        errorMessage = null
                        concept = ""
                        amountText = ""
                    } else {
                        errorMessage = "Revisa los datos: concepto obligatorio y monto mayor que cero."
                        successMessage = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
