package com.moneycounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneycounter.config.ContactConfig
import com.moneycounter.domain.Role
import com.moneycounter.signup.SignupRequest
import com.moneycounter.signup.SignupWhatsAppMessage
import com.moneycounter.util.openWhatsApp

private val roleOptions = listOf(
    Role.OWNER to "DUEÑO",
    Role.ADMIN to "ADMINISTRADOR",
    Role.SELLER to "VENDEDOR"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    isSigningUp: Boolean = false,
    errorMessage: String? = null,
    onSignUp: (email: String, role: Role, businessName: String?, branches: List<String>) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(Role.OWNER) }
    var businessName by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf(listOf("")) }

    fun updateBranch(index: Int, value: String) {
        branches = branches.toMutableList().also { it[index] = value }
    }

    fun addBranch() {
        branches = branches + ""
    }

    fun removeBranch(index: Int) {
        if (branches.size > 1) {
            branches = branches.filterIndexed { i, _ -> i != index }
        }
    }

    fun submit() {
        val normalizedBranches = branches.map { it.trim() }.filter { it.isNotBlank() }
        val normalizedBusinessName = businessName.trim().takeIf { it.isNotBlank() }
        val isOwner = selectedRole == Role.OWNER
        if (email.isBlank() || (isOwner && (normalizedBusinessName == null || normalizedBranches.isEmpty()))) return
        onSignUp(
            email.trim(),
            selectedRole,
            if (isOwner) normalizedBusinessName else null,
            if (isOwner) normalizedBranches else emptyList()
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Crear cuenta",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                singleLine = true,
                enabled = !isSigningUp,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Rol solicitado",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                roleOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selectedRole == option.first,
                        onClick = { selectedRole = option.first },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = roleOptions.size)
                    ) {
                        Text(option.second)
                    }
                }
            }

            if (selectedRole == Role.OWNER) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Nombre del negocio") },
                    singleLine = true,
                    enabled = !isSigningUp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sucursales",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                branches.forEachIndexed { index, branch ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { updateBranch(index, it) },
                            label = { Text("Sucursal ${index + 1}") },
                            singleLine = true,
                            enabled = !isSigningUp,
                            modifier = Modifier.weight(1f)
                        )
                        if (branches.size > 1) {
                            IconButton(
                                onClick = { removeBranch(index) },
                                enabled = !isSigningUp
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar sucursal"
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { addBranch() },
                    enabled = !isSigningUp
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Agregar sucursal")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { submit() },
                enabled = !isSigningUp && email.isNotBlank() &&
                    (selectedRole != Role.OWNER ||
                        (businessName.isNotBlank() && branches.any { it.isNotBlank() })),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isSigningUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Crear cuenta",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            TextButton(
                onClick = onBack,
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Volver",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SignUpSuccessScreen(
    tempPassword: String,
    request: SignupRequest,
    superuserWhatsapp: String?,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val number = superuserWhatsapp?.takeIf { it.isNotBlank() } ?: ContactConfig.WHATSAPP_NUMBER
    val message = SignupWhatsAppMessage.build(
        email = request.email,
        role = request.role,
        businessName = request.businessName,
        branches = request.branches,
        tempPassword = tempPassword
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Solicitud enviada",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tu solicitud de acceso está en revisión. Guarda esta contraseña temporal; la usarás en tu primer inicio de sesión.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                // Selectable: this password is the only way into the account, and it was
                // impossible to even select, let alone copy.
                SelectionContainer {
                    Text(
                        text = tempPassword,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(tempPassword))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (copied) "¡Contraseña copiada!" else "Copiar contraseña",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cámbiala en tu primer inicio de sesión",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (number.isNotBlank()) {
                Button(
                    onClick = { openWhatsApp(context, number, message) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Solicitar acceso por WhatsApp",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Text(
                    text = "No hay un número de WhatsApp configurado para solicitudes. Guarda la contraseña temporal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Volver al inicio de sesión",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}