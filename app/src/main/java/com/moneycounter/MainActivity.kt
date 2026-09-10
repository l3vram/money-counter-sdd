package com.moneycounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneycounter.access.UserProfileData
import com.moneycounter.ui.AuthenticationGate
import com.moneycounter.ui.screens.DenominationManagementScreen
import com.moneycounter.ui.screens.MoneyCounterScreen
import com.moneycounter.ui.screens.MovementDetailScreen
import com.moneycounter.ui.screens.ReportsScreen
import com.moneycounter.ui.screens.StockReportScreen
import com.moneycounter.ui.screens.StockScreen
import com.moneycounter.ui.screens.UnifiedReportScreen
import com.moneycounter.ui.screens.UserProfileScreen
import com.moneycounter.ui.theme.MoneyCounterTheme
import com.moneycounter.viewmodel.MoneyCounterViewModel
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyCounterTheme {
                AuthenticationGate { onLogout, profile, onLoadProfile ->
                    MoneyCounterApp(
                        onLogout = onLogout,
                        profile = profile,
                        onLoadProfile = onLoadProfile
                    )
                }
            }
        }
    }
}

@Composable
fun MoneyCounterApp(
    onLogout: () -> Unit,
    profile: UserProfileData?,
    onLoadProfile: () -> Unit
) {
    val viewModel: MoneyCounterViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("counter") }
    var selectedHistoryId by remember { mutableStateOf<String?>(null) }
    var selectedReportIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val showBottomBar =
        currentScreen == "counter" || currentScreen == "stock" || currentScreen == "reports"

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            currentDensity.density,
            fontScale = min(currentDensity.fontScale, 1.2f)
        )
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Surface (
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp
                        ) {
                            Row (
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                // CONTADOR
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            currentScreen = "counter"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column (
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Paid,
                                            contentDescription = "Contador",
                                            modifier = Modifier.size(23.dp),
                                            tint = if (currentScreen == "counter")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "Contador",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentScreen == "counter")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // STOCK
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            currentScreen = "stock"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Inventory2,
                                            contentDescription = "Stock",
                                            modifier = Modifier.size(23.dp),
                                            tint = if (currentScreen == "stock")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "Stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentScreen == "stock")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // REPORTES
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            currentScreen = "reports"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Receipt,
                                            contentDescription = "Historial",
                                            modifier = Modifier.size(23.dp),
                                            tint = if (currentScreen == "reports")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "Historial",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentScreen == "reports")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
            ) {
                when (currentScreen) {
                    "counter" -> MoneyCounterScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { currentScreen = "settings" },
                        onNavigateToProfile = { currentScreen = "profile" },
                        onNavigateToStock = { currentScreen = "stock" },
                        profile = profile
                    )
                    "profile" -> UserProfileScreen(
                        profile = profile,
                        onBack = { currentScreen = "counter" },
                        onLogout = onLogout,
                        onRetry = onLoadProfile
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
                    "detail" -> MovementDetailScreen(
                        viewModel = viewModel,
                        movementId = selectedHistoryId.orEmpty(),
                        onNavigateBack = { currentScreen = "reports" }
                    )
                }
            }
        }
    }
}