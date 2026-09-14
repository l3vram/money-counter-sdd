package com.moneycounter.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * "Sin conexión", visible en **todas** las pantallas (plan 034, decisión del dueño: el estado
 * desconectado tiene que verse siempre, no ser un aviso que pasa).
 *
 * Vive en el shell y no en cada pantalla, por dos razones: una sola implementación no puede
 * quedar desincronizada, y las pantallas no tienen por qué saber del estado de la red.
 *
 * Se apaga solo. El poll de membresía ya late cada 10 s
 * ([com.moneycounter.appwrite.AppwriteMembershipRepository]), así que una respuesta suya es la
 * señal más barata de que la conectividad volvió — no hace falta un segundo temporizador.
 */
@Composable
fun OfflineAwareContent(
    isOffline: Boolean,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // AnimatedVisibility, no un `if`: así el contenido no salta de golpe cuando el cartel
        // aparece o se va.
        AnimatedVisibility(visible = isOffline) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "Sin conexión — estás viendo datos guardados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                )
            }
        }
        content()
    }
}
