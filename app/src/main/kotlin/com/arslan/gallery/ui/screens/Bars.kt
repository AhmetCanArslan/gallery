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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R

/**
 * "N selected" where only the number nudges up or down when [count] changes, the words around it
 * stay put. A single animation that restarts on every change, so a drag selection changing it every
 * few frames never piles up transitions. Tabular digits keep the words from wobbling sideways while
 * the number of digits stays the same.
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
    val nudged = Modifier.graphicsLayer {
        val value = nudge.value
        translationY = value * size.height * 0.5f
        alpha = 1f - abs(value).coerceAtMost(1f) * 0.7f
    }
    val text = pluralStringResource(R.plurals.n_selected, count, count)
    val number = String.format(LocalConfiguration.current.locales[0], "%d", count)
    val start = text.indexOf(number)
    // A translation may spell the number out, then the whole text nudges.
    if (start < 0) {
        Text(text, modifier.then(nudged), maxLines = 1)
        return
    }
    val style = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(modifier) {
        if (start > 0) Text(text.substring(0, start), style = style, maxLines = 1)
        Text(number, nudged, style = style, maxLines = 1)
        if (start + number.length < text.length) Text(text.substring(start + number.length), style = style, maxLines = 1)
    }
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
    Box(modifier) {
        IconButton(onClick = {
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
