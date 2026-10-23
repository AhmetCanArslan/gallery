package com.arslan.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.arslan.gallery.data.GridItem
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.Section
import com.arslan.gallery.data.formatDuration
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.ui.components.FastScroller
import com.arslan.gallery.ui.components.SelectionIndicator
import com.arslan.gallery.ui.components.dragToSelect
import com.arslan.gallery.ui.components.pinchToChangeColumns
import com.arslan.gallery.ui.components.pressScale
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.thumbnailRequest
import com.arslan.gallery.ui.viewer.FlightCorners
import com.arslan.gallery.ui.viewer.flightAnchor

private fun GridItem.gridKey(index: Int): Any = when (this) {
    is Medium -> path
    is Section -> "section:$index:$title"
}

@Composable
fun MediaGrid(
    sourcePath: String,
    items: List<GridItem>,
    columns: Int,
    gridState: LazyGridState,
    /** Read lazily, so a changing selection only recomposes the items it flips. */
    selection: () -> Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (Medium) -> Unit,
    onColumnsChange: (delta: Int) -> Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.prefs }
    val haptics = rememberHaptics()
    // The grid's content never reads the selection itself, only each item does: changing it then
    // recomposes just the items it flips, not the whole grid, every time a drag reaches a new photo.
    val selectionState by rememberUpdatedState(selection)
    val currentSelection by remember { derivedStateOf { selectionState() } }
    val selectionMode by remember { derivedStateOf { currentSelection.isNotEmpty() } }
    val currentItems by rememberUpdatedState(items)
    val sectionPaths = remember(items) { sectionPaths(items) }

    val sectionTitles = remember(items) {
        var last: String? = null
        Array(items.size) { index ->
            (items[index] as? Section)?.let { last = it.title }
            last
        }
    }

    val spacing = 3.dp
    val corner = FlightCorners.THUMBNAIL_CORNER
    val showDuration = remember { prefs.showVideoDuration }
    val showTypes = remember { prefs.showFileTypes }
    val markFavorites = remember { prefs.markFavorites }
    val animateGifs = remember { prefs.animateGifs }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(onColumnsChange)
                .dragToSelect(
                    gridState = gridState,
                    indexToKey = { (currentItems.getOrNull(it) as? Medium)?.path },
                    selection = { currentSelection },
                    onSelectionChange = onSelectionChange,
                    contentPadding = contentPadding,
                ),
        ) {
            // One interval per run of media instead of one per photo: huge libraries otherwise rebuild
            // tens of thousands of intervals every time the grid's content changes.
            var index = 0
            while (index < items.size) {
                val item = items[index]
                if (item is Section) {
                    val sectionIndex = index
                    stickyHeader(key = item.gridKey(sectionIndex), contentType = "section") {
                        val paths = sectionPaths[sectionIndex].orEmpty()
                        val allSelected by remember(paths) { derivedStateOf { paths.isNotEmpty() && paths.all { it in currentSelection } } }
                        SectionHeader(
                            title = item.title,
                            selectionMode = selectionMode,
                            allSelected = allSelected,
                            onToggleSection = {
                                val deselect = paths.all { it in currentSelection }
                                haptics.toggle(!deselect)
                                onSelectionChange(if (deselect) currentSelection - paths.toSet() else currentSelection + paths)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    index++
                    continue
                }

                val start = index
                while (index < items.size && items[index] is Medium) index++
                items(
                    count = index - start,
                    key = { (items[start + it] as Medium).path },
                    contentType = { "medium" },
                ) { offset ->
                    val item = items[start + offset] as Medium
                    val selected by remember(item.path) { derivedStateOf { item.path in currentSelection } }
                    MediaThumbnail(
                        sourcePath = sourcePath,
                        medium = item,
                        selected = selected,
                        selectionMode = { selectionMode },
                        corner = corner,
                        showDuration = showDuration,
                        showType = showTypes,
                        markFavorite = markFavorites,
                        animateGif = animateGifs,
                        onClick = {
                            if (selectionMode) {
                                val selected = item.path in currentSelection
                                haptics.toggle(!selected)
                                onSelectionChange(if (selected) currentSelection - item.path else currentSelection + item.path)
                            } else {
                                onOpen(item)
                            }
                        },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                        ),
                    )
                }
            }
        }

        FastScroller(
            gridState = gridState,
            totalItems = items.size,
            labelFor = { sectionTitles.getOrNull(it) },
            contentPadding = contentPadding,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Paths of the media below each section header, by the header's index. */
private fun sectionPaths(items: List<GridItem>): Map<Int, List<String>> {
    val result = HashMap<Int, List<String>>()
    var current: ArrayList<String>? = null
    items.forEachIndexed { index, item ->
        when (item) {
            is Section -> current = ArrayList<String>().also { result[index] = it }
            is Medium -> current?.add(item.path)
        }
    }
    return result
}

@Composable
private fun SectionHeader(
    title: String,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggleSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface,
                    0.7f to MaterialTheme.colorScheme.surface,
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                )
            )
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = selectionMode,
            enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSection),
                contentAlignment = Alignment.Center,
            ) {
                SelectionIndicator(selected = allSelected, visible = true, size = 22.dp)
            }
        }
    }
}

@Composable
fun MediaThumbnail(
    sourcePath: String,
    medium: Medium,
    selected: Boolean,
    /** Read only by the indicator, so entering selection mode doesn't recompose every thumbnail. */
    selectionMode: () -> Boolean,
    corner: androidx.compose.ui.unit.Dp,
    showDuration: Boolean,
    showType: Boolean,
    markFavorite: Boolean,
    animateGif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewer = LocalViewer.current
    val interactionSource = remember { MutableInteractionSource() }
    val request = remember(medium.signature, animateGif) { thumbnailRequest(context, medium, animateGif) }

    // Only read while drawing: the bouncy inset never measures or recomposes anything, and its
    // overshoot past zero just grows the photo a hair instead of breaking the layout.
    val selectedFraction = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "selected",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .onSizeChanged { viewer.corners.thumbnailSize = it.width.toFloat() }
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(corner))
            .pressScale(interactionSource, pressedScale = 0.9f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fraction = selectedFraction.value
                    val scale = 1f - 2f * SELECTED_INSET.toPx() * fraction / size.width
                    scaleX = scale
                    scaleY = scale
                    // In the layer's own, unscaled coordinates.
                    shape = RoundedCornerShape(lerp(corner, SELECTED_CORNER, fraction).toPx() / scale)
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = request,
                contentDescription = medium.name,
                contentScale = ContentScale.Crop,
                onSuccess = { viewer.setAspect(medium.path, it.painter.intrinsicSize) },
                modifier = Modifier
                    .fillMaxSize()
                    .flightAnchor(viewer, sourcePath, medium.path)
                    .graphicsLayer { alpha = if (viewer.hides(sourcePath, medium.path)) 0f else 1f },
            )

            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick,
                    )
            )

            if (medium.isVideo || (showType && (medium.isGif || medium.isRaw))) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (medium.isVideo) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        if (showDuration && medium.duration > 0) {
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = medium.duration.formatDuration(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Text(
                            text = medium.name.substringAfterLast('.').uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (markFavorite && medium.isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(16.dp),
                )
            }
        }

        ThumbnailSelectionIndicator(
            selected = selected,
            selectionMode = selectionMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        )
    }
}

@Composable
private fun ThumbnailSelectionIndicator(selected: Boolean, selectionMode: () -> Boolean, modifier: Modifier = Modifier) {
    SelectionIndicator(selected = selected, visible = selectionMode(), modifier = modifier)
}

private val SELECTED_INSET = 10.dp
private val SELECTED_CORNER = 20.dp
