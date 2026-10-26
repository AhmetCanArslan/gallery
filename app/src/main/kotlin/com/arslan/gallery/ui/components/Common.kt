package com.arslan.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenteredLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

/**
 * Empty / info state with a slowly rotating expressive "cookie" shape behind the icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val rotation by rememberInfiniteTransition(label = "emptyRotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Round selection indicator that pops in with a spring, shown on top of selectable thumbnails.
 * Drawn rather than composed of nested layouts: entering selection mode pops in dozens at once,
 * which then only starts a couple of animations each instead of composing new content.
 */
@Composable
fun SelectionIndicator(
    selected: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val shown = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium) else spring(stiffness = Spring.StiffnessHigh),
        label = "selectionShown",
    )
    val fill = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "selectionFill",
    )
    val fillColor = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary

    Spacer(
        modifier
            .size(size)
            .drawBehind {
                val scale = shown.value
                if (scale <= 0.01f) return@drawBehind
                scale(scale) {
                    val radius = this.size.minDimension / 2f
                    val fraction = fill.value
                    drawCircle(Color.Black, radius = radius, alpha = (0.25f * (1f - fraction)).coerceIn(0f, 1f))
                    val stroke = 2.dp.toPx()
                    drawCircle(Color.White, radius = radius - stroke / 2f, style = Stroke(stroke))
                    if (fraction > 0f) {
                        drawCircle(fillColor, radius = radius * fraction.coerceAtMost(1f))
                        drawCheck(checkColor, this.size.minDimension * 0.7f * fraction)
                    }
                }
            },
    )
}

/**
 * The rounded check mark, drawn as two strokes instead of a vector painter: building one is the
 * costliest part of a thumbnail, and the grid creates thumbnails on every frame of a fast scroll.
 */
private fun DrawScope.drawCheck(color: Color, iconSize: Float) {
    // Points of the Material check, on its 24 unit grid.
    val unit = iconSize / 24f
    val origin = center - Offset(iconSize / 2f, iconSize / 2f)
    fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
    val width = 2f * unit
    drawLine(color, point(4.5f, 12.7f), point(9f, 17.2f), width, StrokeCap.Round)
    drawLine(color, point(9f, 17.2f), point(19.8f, 6.4f), width, StrokeCap.Round)
}
