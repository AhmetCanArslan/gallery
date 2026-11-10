package com.arslan.gallery.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class ToolbarTabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Pill shaped tabs for the floating toolbar. One pill slides from tab to tab, its leading edge a
 * little ahead of the trailing one so it stretches on the way, and each tab's colors follow how much
 * of it the pill covers. Both tabs always show their label, so the toolbar keeps a constant width.
 */
@Composable
fun ToolbarTabs(
    tabs: List<ToolbarTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Edges of each tab within the row, filled in as they're placed.
    val starts = remember(tabs.size) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(Float.NaN) } } }
    val ends = remember(tabs.size) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(Float.NaN) } } }
    val pillStart = remember { Animatable(Float.NaN) }
    val pillEnd = remember { Animatable(Float.NaN) }

    val targetStart = starts.getOrNull(selectedIndex) ?: Float.NaN
    val targetEnd = ends.getOrNull(selectedIndex) ?: Float.NaN
    LaunchedEffect(targetStart, targetEnd) {
        if (targetStart.isNaN() || targetEnd.isNaN()) return@LaunchedEffect
        if (pillStart.value.isNaN()) {
            pillStart.snapTo(targetStart)
            pillEnd.snapTo(targetEnd)
            return@LaunchedEffect
        }
        val forward = targetStart > pillStart.value
        launch { pillStart.animateTo(targetStart, if (forward) TrailingEdge else LeadingEdge) }
        launch { pillEnd.animateTo(targetEnd, if (forward) LeadingEdge else TrailingEdge) }
    }

    val pillColor = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = modifier
            .height(48.dp)
            .drawBehind {
                val start = pillStart.value
                val end = pillEnd.value
                if (start.isNaN() || end.isNaN() || end <= start) return@drawBehind
                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(start, 0f),
                    size = Size(end - start, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            // How much of this tab the pill covers, from 0 to 1.
            val coverage by remember(index, selectedIndex) {
                derivedStateOf {
                    val start = starts[index]
                    val end = ends[index]
                    val pillS = pillStart.value
                    val pillE = pillEnd.value
                    if (start.isNaN() || pillS.isNaN() || end <= start) {
                        if (index == selectedIndex) 1f else 0f
                    } else {
                        ((minOf(end, pillE) - maxOf(start, pillS)) / (end - start)).coerceIn(0f, 1f)
                    }
                }
            }
            ToolbarTab(
                tab = tab,
                selected = index == selectedIndex,
                coverage = { coverage },
                onClick = { onSelect(index) },
                modifier = Modifier.onPlaced {
                    val x = it.positionInParent().x
                    if (starts[index] != x) starts[index] = x
                    if (ends[index] != x + it.size.width) ends[index] = x + it.size.width
                },
            )
        }
    }
}

@Composable
private fun ToolbarTab(
    tab: ToolbarTabItem,
    selected: Boolean,
    coverage: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconScale = remember { Animatable(1f) }
    var popped by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !popped) {
            iconScale.snapTo(0.7f)
            iconScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
        popped = selected
    }

    val fraction = coverage()
    val content = lerp(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer, fraction)
    Row(
        modifier = modifier
            .height(48.dp)
            .clickable(interactionSource = interactionSource, indication = ripple(bounded = false), onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (fraction > 0.5f) tab.selectedIcon else tab.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(tab.label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private val LeadingEdge = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
private val TrailingEdge = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
