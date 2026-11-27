package com.aghielaan22.label

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun GlassNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(32.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else Color.Transparent,
                animationSpec = tween(250),
                label = "navBackground"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.94f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                label = "navScale"
            )
            val tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Row(
                modifier = Modifier
                    .weight(if (isSelected) 1.5f else 1f)                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(background)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = tint)
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}