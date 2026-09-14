package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Forced password change on first sign-in.
 *
 * [knowsCurrentPassword] is the normal case: the user typed the temporary password moments ago
 * to get here, so the ViewModel holds it and this screen asks **only for the new one, twice**.
 * Asking again for a generated password nobody memorised is where people got stuck.
 *
 * The field for the current password appears only when that is false — the app was reopened
 * from a restored session, so nothing was typed on this run.
 */
@Composable
fun ChangePasswordScreen(
    isChangingPassword: Boolean = false,
    errorMessage: String? = null,
    knowsCurrentPassword: Boolean = true,
    onChangePassword: (current: String, new: String, confirm: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val newLongEnough = newPassword.length >= MIN_PASSWORD_LENGTH
    val matches = confirm.isNotEmpty() && newPassword == confirm
    val currentReady = knowsCurrentPassword || current.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Elige tu contraseña",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Que sea simple y tuya, algo que recuerdes sin pensarlo. " +
                    "Mínimo $MIN_PASSWORD_LENGTH caracteres.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "¿Se te olvida más adelante? No es un problema: el administrador te la " +
                    "resetea por WhatsApp cuando quieras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!knowsCurrentPassword) {
                PasswordField(
                    value = current,
                    onValueChange = { current = it },
                    label = "Contraseña actual",
                    enabled = !isChangingPassword,
                    imeAction = ImeAction.Next
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            PasswordField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = "Nueva contraseña",
                enabled = !isChangingPassword,
                imeAction = ImeAction.Next
            )

            if (newPassword.isNotEmpty() && !newLongEnough) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Te faltan caracteres: deben ser al menos $MIN_PASSWORD_LENGTH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Repite la nueva contraseña",
                enabled = !isChangingPassword,
                imeAction = ImeAction.Done
            )

            if (confirm.isNotEmpty() && !matches) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Las dos contraseñas no son iguales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onChangePassword(current, newPassword, confirm) },
                enabled = !isChangingPassword && currentReady && newLongEnough && matches,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isChangingPassword) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Guardar contraseña",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Password field with a show/hide toggle. The whole point of this screen is that someone with
 * little practice can change their password without a mistyped character they cannot see, so
 * every field here gets the eye.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Text(
                    text = if (visible) "🙈" else "👁",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Same minimum the login screen enforces, so both behave alike. */
private const val MIN_PASSWORD_LENGTH = 8
