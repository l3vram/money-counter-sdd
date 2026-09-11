package com.moneycounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moneycounter.ui.theme.Dimen12
import com.moneycounter.ui.theme.Dimen16
import com.moneycounter.ui.theme.Dimen56
import com.moneycounter.ui.theme.LuisoError
import com.moneycounter.ui.theme.LuisoErrorContainer
import com.moneycounter.ui.theme.LuisoGreen
import com.moneycounter.ui.theme.LuisoInfoContainer
import com.moneycounter.ui.theme.LuisoOnWarning
import com.moneycounter.ui.theme.LuisoWarningContainer
import com.moneycounter.ui.theme.LuisoYellow
import com.moneycounter.domain.MovementType

@Composable
fun LuisoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.padding(end = Dimen16)
            )
        }
        Text(text = text)
    }
}

@Composable
fun LuisoOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder,
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 0.dp
        ),
        colors = if (contentColor != null) {
            ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Text(text = text)
    }
}

@Composable
fun LuisoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(Dimen16)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(Dimen16)) {
                content()
            }
        }
    }
}

@Composable
fun LuisoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = modifier.height(58.dp),
        placeholder = hint?.let { { Text(text = it) } },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        keyboardOptions = keyboardOptions
    )
}

@Composable
fun LuisoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(Dimen56),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimen16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
    }
}

enum class LuisoNoticeType { INFO, WARNING, ERROR }

private data class LuisoNoticeColors(
    val container: Color,
    val content: Color,
    val label: String,
    val icon: ImageVector
)

@Composable
fun LuisoNotice(
    message: String,
    type: LuisoNoticeType = LuisoNoticeType.INFO,
    modifier: Modifier = Modifier
) {
    val colors = when (type) {
        LuisoNoticeType.INFO ->
            LuisoNoticeColors(LuisoInfoContainer, LuisoGreen, "INFORMACIÓN", Icons.Filled.Info)
        LuisoNoticeType.WARNING ->
            LuisoNoticeColors(LuisoWarningContainer, LuisoOnWarning, "AVISO", Icons.Filled.Warning)
        LuisoNoticeType.ERROR ->
            LuisoNoticeColors(LuisoErrorContainer, LuisoError, "ERROR", Icons.Filled.ErrorOutline)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.container
    ) {
        Row(
            modifier = Modifier.padding(Dimen12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = colors.content.copy(alpha = 0.14f)
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = colors.icon,
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimen12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = colors.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.content
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Small info affordance placed next to an on-screen label. Tapping it opens a dialog
 * showing the correct accounting term, the old label still shown in the UI, and a
 * short explanation. Purely presentational — no data/logic side effects.
 */
@Composable
fun TermInfo(
    correctTerm: String,
    oldName: String,
    explanation: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier.size(20.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "Información: $correctTerm",
            tint = LuisoGreen,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = correctTerm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuisoGreen
                )
            },
            text = {
                Column {
                    Text(
                        text = "Nombre anterior: $oldName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimen12))
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Entendido")
                }
            },
            containerColor = LuisoInfoContainer
        )
    }
}

@Composable
fun LuisoStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    LuisoCard(modifier = modifier) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor
        )
    }
}

@Composable
fun LuisoSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(8.dp)
                    .height(20.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (accent) LuisoYellow else MaterialTheme.colorScheme.primary
            ) {}
        }
        Text(
            text = text,
            style = if (accent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Dimen16)
        )
    }
}

@Composable
fun LuisoAvatar(
    photoUrl: String?,
    fallbackText: String?,
    modifier: Modifier = Modifier,
    size: Int = 32,
    borderColor: Color = LuisoGreen
) {
    val initial = fallbackText?.firstOrNull()?.uppercase().orEmpty()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Foto de perfil",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size / 2).dp)
            )
        }
    }
}

private data class MovementTypeVisual(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun movementTypeVisual(type: MovementType, warningColor: Color, errorColor: Color, primaryColor: Color): MovementTypeVisual =
    when (type) {
        MovementType.VENTA -> MovementTypeVisual(Icons.Filled.PointOfSale, LuisoGreen, "Venta")
        MovementType.VENTA_FIADO -> MovementTypeVisual(Icons.Filled.Schedule, warningColor, "Fiado")
        MovementType.COBRO -> MovementTypeVisual(Icons.Filled.Paid, LuisoGreen, "Cobro")
        MovementType.MERMA -> MovementTypeVisual(Icons.Filled.Warning, errorColor, "Merma")
        MovementType.GASTO -> MovementTypeVisual(Icons.Filled.MoneyOff, errorColor, "Gasto")
        MovementType.ALTA -> MovementTypeVisual(Icons.Filled.AddBox, primaryColor, "Alta")
        MovementType.ENTRADA -> MovementTypeVisual(Icons.Filled.MoveToInbox, primaryColor, "Entrada")
    }

/** Colored icon + label identifying a journal movement's type. Exhaustive over [MovementType]. */
@Composable
fun MovementTypeBadge(type: MovementType, modifier: Modifier = Modifier) {
    val visual = movementTypeVisual(
        type = type,
        warningColor = LuisoYellow,
        errorColor = MaterialTheme.colorScheme.error,
        primaryColor = MaterialTheme.colorScheme.primary
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = visual.label,
            tint = visual.color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = visual.label,
            style = MaterialTheme.typography.labelMedium,
            color = visual.color
        )
    }
}

/** Cash-flow sign for a movement type: GASTO/MERMA outflows (-), cash inflows (+), VENTA_FIADO is pending credit ("~"). */
fun MovementType.moneySign(): String = when (this) {
    MovementType.GASTO, MovementType.MERMA -> "-"
    MovementType.VENTA_FIADO -> "~"
    else -> "+"
}
