package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Plan 033: the account is approved but has no usable membership yet, so it cannot operate.
 * [panelOnly] tells the two cases apart — a SUPERUSER, whose place is the web panel, from
 * someone still waiting to be assigned a business, a branch and a role.
 */
@Composable
fun AssignmentPendingScreen(
    panelOnly: Boolean,
    onRetry: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    val title = if (panelOnly) "Cuenta de administración" else "Falta asignarte tu puesto"
    val body = if (panelOnly) {
        "Esta cuenta administra la plataforma desde el panel web y no opera la caja. " +
            "Para vender, cobrar o ver inventario, iniciá sesión con una cuenta de negocio."
    } else {
        "Tu cuenta ya está aprobada, pero el administrador todavía no te asignó negocio, " +
            "sucursal y rol. En cuanto lo haga, entrás sin hacer nada más."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Only the waiting case can be resolved by retrying: the membership arrives from
            // the server. A SUPERUSER retrying would just land here again.
            if (!panelOnly && onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Volver a comprobar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Cerrar sesión",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
