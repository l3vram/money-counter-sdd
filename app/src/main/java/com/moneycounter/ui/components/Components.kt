package com.moneycounter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moneycounter.ui.theme.Dimen16
import com.moneycounter.ui.theme.Dimen56
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
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder
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
            Box(modifier = Modifier.padding(Dimen16)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.padding(Dimen16)) {
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
        modifier = modifier,
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

@Composable
fun LuisoEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LuisoCircle(
            content = {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "L",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimen16)
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
private fun LuisoCircle(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 48,
    background: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(size / 2),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
