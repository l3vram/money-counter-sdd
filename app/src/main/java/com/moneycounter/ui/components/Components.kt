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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        modifier = modifier.height(40.dp),
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
            if (initial.isNotEmpty()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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
