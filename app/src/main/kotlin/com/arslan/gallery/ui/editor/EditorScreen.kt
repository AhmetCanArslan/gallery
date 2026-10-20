package com.arslan.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BrushColors = listOf(
    Color.White, Color.Black, Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    state: EditState?,
    isSaving: Boolean,
    onClose: () -> Unit,
    canOverwrite: Boolean,
    onSave: (overwrite: Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
            }
            Text(
                stringResource(R.string.edit),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            SaveButton(
                enabled = state?.hasChanges == true && !isSaving,
                canOverwrite = canOverwrite,
                onSave = onSave,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (state == null || isSaving) {
                LoadingIndicator(Modifier.size(64.dp))
            } else {
                EditorCanvas(state)
            }
        }

        if (state != null) {
            ToolPanel(state)
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    ToolButton(state, EditorTool.Crop, Icons.Rounded.Crop, R.string.crop)
                    ToolButton(state, EditorTool.Filters, Icons.Rounded.AutoFixHigh, R.string.filters)
                    ToolButton(state, EditorTool.Adjust, Icons.Rounded.Tune, R.string.adjust)
                    ToolButton(state, EditorTool.Draw, Icons.Rounded.Brush, R.string.draw)
                }
            }
        }
    }
}

/** Tap saves a copy; the arrow or a long press opens the copy / overwrite choice. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveButton(
    enabled: Boolean,
    canOverwrite: Boolean,
    onSave: (overwrite: Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // The leading button has no long-click callback, so a long press is detected from its interactions.
    val interactions = remember { MutableInteractionSource() }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val haptics = LocalHapticFeedback.current
    var longPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactions) {
        var pending: Job? = null
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longPressed = false
                    pending = launch {
                        delay(longPressTimeout)
                        longPressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pending?.cancel()
            }
        }
    }

    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { if (!longPressed) onSave(false) },
                    enabled = enabled,
                    interactionSource = interactions,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    enabled = enabled,
                ) {
                    val rotation by animateFloatAsState(if (menuOpen) 180f else 0f, label = "saveMenuArrow")
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.more_options),
                        Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_as_copy)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                onClick = {
                    menuOpen = false
                    onSave(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overwrite_original)) },
                leadingIcon = { Icon(Icons.Rounded.Save, null) },
                enabled = canOverwrite,
                onClick = {
                    menuOpen = false
                    onSave(true)
                },
            )
        }
    }
}

@Composable
private fun ToolButton(state: EditState, tool: EditorTool, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int) {
    FilledIconToggleButton(
        checked = state.tool == tool,
        onCheckedChange = {
            state.tool = tool
        },
    ) {
        Icon(icon, stringResource(label))
    }
}

/** The image with every edit applied, plus the crop frame or the brush, depending on the tool. */
@Composable
private fun EditorCanvas(state: EditState) {
    val density = LocalDensity.current
    val handleRadius = with(density) { 28.dp.toPx() }
    val cropPadding = with(density) { 28.dp.toPx() }
    val viewPadding = with(density) { 12.dp.toPx() }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    var liveStroke by remember { mutableStateOf<Stroke?>(null) }
    var brushColor by remember { mutableStateOf(BrushColors[2]) }
    val isCrop = state.tool == EditorTool.Crop

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // Recomputed whenever the geometry changes, the reads below make the canvas follow.
        val matrix = state.viewMatrix(width, height, cropped = !isCrop, padding = if (isCrop) cropPadding else viewPadding)
        val imageBounds = RectF(0f, 0f, state.source.width.toFloat(), state.source.height.toFloat()).also { matrix.mapRect(it) }

        val gestures = if (isCrop) {
            Modifier.pointerInput(state.rotation, state.flipped, state.cropRatio, width, height) {
                var mode = CropDrag.None
                detectDragGestures(
                    onDragStart = { start -> mode = cropDragMode(start, cropOnScreen(state, imageBounds), handleRadius) },
                    onDrag = { change, drag ->
                        change.consume()
                        state.crop = dragCrop(state, mode, drag, imageBounds)
                    },
                    onDragEnd = { mode = CropDrag.None },
                )
            }
        } else if (state.tool == EditorTool.Draw) {
            Modifier.pointerInput(state.rotation, state.flipped, state.crop, width, height) {
                val inverse = Matrix().also { matrix.invert(it) }
                val scale = matrix.mapRadius(1f)
                fun toSource(offset: Offset): FloatArray = floatArrayOf(offset.x, offset.y).also { inverse.mapPoints(it) }
                detectDragGestures(
                    onDragStart = { start ->
                        val point = toSource(start)
                        val path = Path().apply { moveTo(point[0], point[1]) }
                        liveStroke = Stroke(path, brushColor.toArgb(), state.brushSize * density.density / scale)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = toSource(change.position)
                        liveStroke?.path?.lineTo(point[0], point[1])
                        state.strokeVersion++
                    },
                    onDragEnd = {
                        liveStroke?.let { state.strokes.add(it) }
                        liveStroke = null
                    },
                    onDragCancel = { liveStroke = null },
                )
            }
        } else {
            Modifier
        }

        val colorMatrix = state.colorMatrix()
        bitmapPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        Canvas(
            Modifier
                .fillMaxSize()
                .then(gestures)
        ) {
            state.strokeVersion
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                if (!isCrop) {
                    // Outside of the crop tool only the cropped part is visible.
                    native.clipRect(cropOnScreen(state, imageBounds))
                }
                native.drawBitmap(state.source, matrix, bitmapPaint)
                native.concat(matrix)
                state.strokes.forEach { native.drawPath(it.path, EditState.strokePaint(it)) }
                liveStroke?.let { native.drawPath(it.path, EditState.strokePaint(it)) }
                native.restore()
            }

            if (isCrop) {
                val crop = cropOnScreen(state, imageBounds)
                val dim = Color.Black.copy(alpha = 0.6f)
                drawRect(dim, Offset(imageBounds.left, imageBounds.top), androidx.compose.ui.geometry.Size(imageBounds.width(), crop.top - imageBounds.top))
                drawRect(dim, Offset(imageBounds.left, crop.bottom), androidx.compose.ui.geometry.Size(imageBounds.width(), imageBounds.bottom - crop.bottom))
                drawRect(dim, Offset(imageBounds.left, crop.top), androidx.compose.ui.geometry.Size(crop.left - imageBounds.left, crop.height()))
                drawRect(dim, Offset(crop.right, crop.top), androidx.compose.ui.geometry.Size(imageBounds.right - crop.right, crop.height()))

                val line = 1.dp.toPx()
                val white = Color.White
                drawRect(white, Offset(crop.left, crop.top), androidx.compose.ui.geometry.Size(crop.width(), crop.height()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                for (i in 1..2) {
                    val x = crop.left + crop.width() * i / 3
                    val y = crop.top + crop.height() * i / 3
                    drawLine(white.copy(alpha = 0.5f), Offset(x, crop.top), Offset(x, crop.bottom), line)
                    drawLine(white.copy(alpha = 0.5f), Offset(crop.left, y), Offset(crop.right, y), line)
                }
                val corner = 20.dp.toPx()
                val thick = 4.dp.toPx()
                listOf(crop.left to crop.top, crop.right to crop.top, crop.left to crop.bottom, crop.right to crop.bottom).forEach { (x, y) ->
                    val dx = if (x == crop.left) corner else -corner
                    val dy = if (y == crop.top) corner else -corner
                    drawLine(white, Offset(x, y), Offset(x + dx, y), thick)
                    drawLine(white, Offset(x, y), Offset(x, y + dy), thick)
                }
            }
        }

        if (state.tool == EditorTool.Draw) {
            BrushColorRow(
                selected = brushColor,
                onSelect = { brushColor = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
    }
}

private enum class CropDrag { None, Move, Left, Top, Right, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }

private fun cropOnScreen(state: EditState, image: RectF): RectF {
    val crop = state.crop
    return RectF(
        image.left + crop.left * image.width(),
        image.top + crop.top * image.height(),
        image.left + crop.right * image.width(),
        image.top + crop.bottom * image.height(),
    )
}

private fun cropDragMode(point: Offset, crop: RectF, radius: Float): CropDrag {
    val nearLeft = abs(point.x - crop.left) < radius
    val nearRight = abs(point.x - crop.right) < radius
    val nearTop = abs(point.y - crop.top) < radius
    val nearBottom = abs(point.y - crop.bottom) < radius
    val withinX = point.x in crop.left - radius..crop.right + radius
    val withinY = point.y in crop.top - radius..crop.bottom + radius
    return when {
        nearLeft && nearTop -> CropDrag.TopLeft
        nearRight && nearTop -> CropDrag.TopRight
        nearLeft && nearBottom -> CropDrag.BottomLeft
        nearRight && nearBottom -> CropDrag.BottomRight
        nearLeft && withinY -> CropDrag.Left
        nearRight && withinY -> CropDrag.Right
        nearTop && withinX -> CropDrag.Top
        nearBottom && withinX -> CropDrag.Bottom
        crop.contains(point.x, point.y) -> CropDrag.Move
        else -> CropDrag.None
    }
}

private const val MIN_CROP = 0.08f

/** Moves or resizes the crop by [drag] screen pixels, keeping the aspect ratio when one is set. */
private fun dragCrop(state: EditState, mode: CropDrag, drag: Offset, image: RectF): RectF {
    val dx = drag.x / image.width()
    val dy = drag.y / image.height()
    val c = RectF(state.crop)
    when (mode) {
        CropDrag.None -> return c
        CropDrag.Move -> {
            val x = dx.coerceIn(-c.left, 1f - c.right)
            val y = dy.coerceIn(-c.top, 1f - c.bottom)
            c.offset(x, y)
            return c
        }

        CropDrag.Left, CropDrag.TopLeft, CropDrag.BottomLeft -> c.left = (c.left + dx).coerceIn(0f, c.right - MIN_CROP)
        CropDrag.Right, CropDrag.TopRight, CropDrag.BottomRight -> c.right = (c.right + dx).coerceIn(c.left + MIN_CROP, 1f)
        else -> Unit
    }
    when (mode) {
        CropDrag.Top, CropDrag.TopLeft, CropDrag.TopRight -> c.top = (c.top + dy).coerceIn(0f, c.bottom - MIN_CROP)
        CropDrag.Bottom, CropDrag.BottomLeft, CropDrag.BottomRight -> c.bottom = (c.bottom + dy).coerceIn(c.top + MIN_CROP, 1f)
        else -> Unit
    }

    val ratio = state.targetRatio() ?: return c
    // Keep the ratio: the width leads, the height follows from the anchored edge.
    val normalizedRatio = ratio * image.height() / image.width()
    val horizontal = mode == CropDrag.Left || mode == CropDrag.Right
    if (horizontal || mode in setOf(CropDrag.TopLeft, CropDrag.TopRight, CropDrag.BottomLeft, CropDrag.BottomRight)) {
        var w = c.width()
        var h = w / normalizedRatio
        val anchorTop = mode == CropDrag.BottomLeft || mode == CropDrag.BottomRight || horizontal
        val maxH = if (anchorTop) 1f - c.top else c.bottom
        if (h > maxH) {
            h = maxH
            w = h * normalizedRatio
        }
        if (mode == CropDrag.Left || mode == CropDrag.TopLeft || mode == CropDrag.BottomLeft) c.left = c.right - w else c.right = c.left + w
        if (anchorTop) c.bottom = c.top + h else c.top = c.bottom - h
    } else {
        var h = c.height()
        var w = h * normalizedRatio
        val maxW = 1f - c.left
        if (w > maxW) {
            w = maxW
            h = w / normalizedRatio
        }
        c.right = c.left + w
        if (mode == CropDrag.Top) c.top = c.bottom - h else c.bottom = c.top + h
    }
    return c
}

@Composable
private fun BrushColorRow(selected: Color, onSelect: (Color) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BrushColors.forEach { color ->
            Box(
                Modifier
                    .size(if (color == selected) 32.dp else 28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (color == selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        onSelect(color)
                    },
            )
        }
    }
}

/** Controls of the selected tool, shown above the tool switcher. */
@Composable
private fun ToolPanel(state: EditState) {
    AnimatedContent(
        targetState = state.tool,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "toolPanel",
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
    ) { tool ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (tool) {
                EditorTool.Crop -> CropPanel(state)
                EditorTool.Filters -> FilterPanel(state)
                EditorTool.Adjust -> AdjustPanel(state)
                EditorTool.Draw -> DrawPanel(state)
            }
        }
    }
}

@Composable
private fun CropPanel(state: EditState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = {
                state.rotate(-90)
            }) { Icon(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.rotate_left)) }
            FilledTonalIconButton(onClick = {
                state.flip()
            }) { Icon(Icons.Rounded.Flip, stringResource(R.string.flip)) }
            FilledTonalIconButton(onClick = {
                state.resetGeometry()
            }) { Icon(Icons.Rounded.RestartAlt, stringResource(R.string.reset)) }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = state.cropRatio == ratio,
                    onClick = {
                        state.setRatio(ratio)
                    },
                    label = {
                        Text(
                            ratio.label ?: stringResource(if (ratio == CropRatio.Free) R.string.free else R.string.original)
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterPanel(state: EditState) {
    val preview = remember(state.source) { previewBitmap(state.source) }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Filter.entries.forEach { filter ->
            val selected = state.filter == filter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        state.filter = filter
                    }
                    .padding(4.dp),
            ) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = remember(filter) { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(filter.matrix().array)) },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    filterName(filter),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
    }
}

@Composable
private fun filterName(filter: Filter) = stringResource(
    when (filter) {
        Filter.Original -> R.string.original
        Filter.Vivid -> R.string.filter_vivid
        Filter.Warm -> R.string.filter_warm
        Filter.Cool -> R.string.filter_cool
        Filter.Mono -> R.string.filter_mono
        Filter.Noir -> R.string.filter_noir
        Filter.Sepia -> R.string.filter_sepia
        Filter.Fade -> R.string.filter_fade
        Filter.Chrome -> R.string.filter_chrome
    }
)

private fun previewBitmap(source: Bitmap): ImageBitmap {
    val scale = 160f / max(source.width, source.height)
    val width = max(1, (source.width * scale).toInt())
    val height = max(1, (source.height * scale).toInt())
    return Bitmap.createScaledBitmap(source, width, height, true).asImageBitmap()
}

@Composable
private fun AdjustPanel(state: EditState) {
    var selected by remember { mutableStateOf(Adjustment.Brightness) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Adjustment.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = {
                        selected = type
                    },
                    label = {
                        Text(
                            stringResource(
                                when (type) {
                                    Adjustment.Brightness -> R.string.brightness
                                    Adjustment.Contrast -> R.string.contrast
                                    Adjustment.Saturation -> R.string.saturation
                                    Adjustment.Warmth -> R.string.warmth
                                }
                            )
                        )
                    },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = state.adjustment(selected),
                onValueChange = { state.setAdjustment(selected, if (abs(it) < 0.04f) 0f else it) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                state.setAdjustment(selected, 0f)
            }) {
                Icon(Icons.Rounded.RestartAlt, stringResource(R.string.reset), tint = Color.White)
            }
        }
    }
}

@Composable
private fun DrawPanel(state: EditState) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Brush, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Slider(
            value = state.brushSize,
            onValueChange = { state.brushSize = it },
            valueRange = 2f..32f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        FilledTonalIconButton(
            onClick = {
                state.strokes.removeLastOrNull()
            },
            enabled = state.strokes.isNotEmpty(),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo))
        }
    }
}
