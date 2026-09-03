package com.moneycounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneycounter.ui.screens.DenominationManagementScreen
import com.moneycounter.ui.screens.HistoryDetailScreen
import com.moneycounter.ui.screens.HistoryScreen
import com.moneycounter.ui.screens.MoneyCounterScreen
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

    when (currentScreen) {
        "counter" -> MoneyCounterScreen(
            viewModel = viewModel,
            onNavigateToSettings = { currentScreen = "settings" },
            onNavigateToHistory = { currentScreen = "history" }
        )
        "settings" -> DenominationManagementScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = "counter" }
        )
        "history" -> HistoryScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = "counter" },
            onOpenDetail = { id ->
                selectedHistoryId = id
                currentScreen = "detail"
            }
        )
        "detail" -> HistoryDetailScreen(
            viewModel = viewModel,
            savedCountId = selectedHistoryId.orEmpty(),
            onNavigateBack = { currentScreen = "history" }
        )
    }
}
