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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.UserProfileData
import com.moneycounter.ui.components.LuisoAvatar
import com.moneycounter.ui.components.LuisoCard
import com.moneycounter.ui.components.LuisoOutlineButton
import com.moneycounter.ui.components.LuisoSectionHeader
import com.moneycounter.ui.components.LuisoTopBar
import com.moneycounter.ui.theme.LuisoGreenBright
import com.moneycounter.ui.theme.LuisoYellow
import com.moneycounter.ui.theme.LuisoError
import com.moneycounter.ui.theme.LuisoGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserProfileScreen(
    profile: UserProfileData?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            LuisoTopBar(
                title = "Perfil",
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LuisoAvatar(
                        photoUrl = profile?.photoUrl,
                        fallbackText = profile?.displayName,
                        size = 96,
                        borderColor = LuisoGreen
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = profile?.displayName ?: "…",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile?.email ?: "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                LuisoSectionHeader(text = "ESTADO DE ACCESO")
            }

            item {
                LuisoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Acceso",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = accessLabel(profile?.access),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accessColor(profile?.access)
                            )
                        }
                        if (profile?.createdAtMillis != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Creado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatProfileDate(profile.createdAtMillis),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        if (profile?.updatedAtMillis != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Actualizado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatProfileDate(profile.updatedAtMillis),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                LuisoSectionHeader(text = "SESIÓN")
            }

            item {
                LuisoOutlineButton(
                    text = "CERRAR SESIÓN",
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private fun accessLabel(status: AccessStatus?): String = when (status) {
    AccessStatus.APPROVED -> "APROBADO"
    AccessStatus.PENDING -> "PENDIENTE"
    AccessStatus.BLOCKED -> "BLOQUEADO"
    null -> "—"
}

private fun accessColor(status: AccessStatus?): androidx.compose.ui.graphics.Color = when (status) {
    AccessStatus.APPROVED -> LuisoGreenBright
    AccessStatus.PENDING -> LuisoYellow
    AccessStatus.BLOCKED -> LuisoError
    null -> androidx.compose.ui.graphics.Color.Gray
}

private fun formatProfileDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}