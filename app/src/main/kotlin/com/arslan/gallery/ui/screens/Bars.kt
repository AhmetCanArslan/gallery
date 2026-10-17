package com.arslan.gallery.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import kotlin.math.sign
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R
import com.arslan.gallery.ui.components.rememberHaptics

/**
 * Text that nudges up or down when [count] changes. A single text with a single animation that
 * restarts on every change, so a drag selection changing it every few frames never piles up
 * transitions (or measures the app bar again).
 */
@Composable
fun AnimatedCount(count: Int, modifier: Modifier = Modifier) {
    val nudge = remember { Animatable(0f) }
    var previous by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        val direction = (count - previous).sign
        previous = count
        if (direction != 0) {
            nudge.snapTo(direction.toFloat())
            nudge.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
        }
    }
    Text(
        text = pluralStringResource(R.plurals.n_selected, count, count),
        modifier = modifier.graphicsLayer {
            val value = nudge.value
            translationY = value * size.height * 0.5f
            alpha = 1f - abs(value).coerceAtMost(1f) * 0.7f
        },
    )
}

data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

@Composable
fun OverflowMenu(
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.MoreVert,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box(modifier) {
        IconButton(onClick = {
            haptics.tap()
            expanded = true
        }) {
            Icon(icon, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = action.icon?.let { { Icon(it, contentDescription = null) } },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
