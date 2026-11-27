package com.aghielaan22.label

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A titled section. With liquid glass on, its contents sit on a glass card. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
    )
    val panel = if (AppSettings.glassEnabled) Modifier.glassPanel(RoundedCornerShape(24.dp))
    else Modifier
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(panel)
            .padding(vertical = 8.dp),
        content = content
    )
}

@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    if (description != null) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
            colors = colors,
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
        )
    } else {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
            colors = colors,
            headlineContent = { Text(title) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
        )
    }
}

@Composable
fun <T> ChoiceSetting(
    title: String,
    description: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}