package com.mymoney.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, count: Int = 12, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until count).forEach { i ->
            val c = paletteColor(i)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(keys: List<String>, selected: String, tint: Color, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { key ->
            val isSelected = key == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) tint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) tint else Color.Transparent, CircleShape)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.category(key), contentDescription = key, tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
