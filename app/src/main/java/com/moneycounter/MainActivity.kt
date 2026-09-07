package com.moneycounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneycounter.ui.screens.DenominationManagementScreen
import com.moneycounter.ui.screens.HistoryDetailScreen
import com.moneycounter.ui.screens.MoneyCounterScreen
import com.moneycounter.ui.screens.ReportsScreen
import com.moneycounter.ui.screens.StockReportScreen
import com.moneycounter.ui.screens.StockScreen
import com.moneycounter.ui.screens.UnifiedReportScreen
import com.moneycounter.ui.theme.MoneyCounterTheme
import com.moneycounter.viewmodel.MoneyCounterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyCounterTheme {
                MoneyCounterApp()
            }
        }
    }
}

@Composable
fun MoneyCounterApp() {
    val viewModel: MoneyCounterViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("counter") }
    var selectedHistoryId by remember { mutableStateOf<String?>(null) }
    var selectedReportIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val showBottomBar =
        currentScreen == "counter" || currentScreen == "stock" || currentScreen == "reports"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == "counter",
                        onClick = { currentScreen = "counter" },
                        icon = { Icon(Icons.Filled.Paid, contentDescription = null) },
                        label = { Text("Contador") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "stock",
                        onClick = { currentScreen = "stock" },
                        icon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                        label = { Text("Stock") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "reports",
                        onClick = { currentScreen = "reports" },
                        icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                        label = { Text("Reportes") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "counter" -> MoneyCounterScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { currentScreen = "settings" }
                )
                "stock" -> StockScreen(
                    viewModel = viewModel,
                    onNavigateToReport = { currentScreen = "report" }
                )
                "report" -> StockReportScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = "stock" }
                )
                "settings" -> DenominationManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = "counter" }
                )
                "reports" -> ReportsScreen(
                    viewModel = viewModel,
                    onOpenDetail = { id ->
                        selectedHistoryId = id
                        currentScreen = "detail"
                    },
                    onOpenSummary = { ids ->
                        selectedReportIds = ids
                        currentScreen = "summary"
                    }
                )
                "summary" -> UnifiedReportScreen(
                    viewModel = viewModel,
                    selectedCountIds = selectedReportIds,
                    onNavigateBack = { currentScreen = "reports" }
                )
                "detail" -> HistoryDetailScreen(
                    viewModel = viewModel,
                    savedCountId = selectedHistoryId.orEmpty(),
                    onNavigateBack = { currentScreen = "reports" }
                )
            }
        }
    }
}