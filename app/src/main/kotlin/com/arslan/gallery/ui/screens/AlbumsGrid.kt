package com.arslan.gallery.ui.screens

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.gallery.data.Album
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalNavAnimatedVisibilityScope
import com.arslan.gallery.ui.LocalSharedTransitionScope
import com.arslan.gallery.ui.theme.AlbumCardEnter
import com.arslan.gallery.ui.theme.ContainerTransformBounds
import com.arslan.gallery.ui.components.AlbumCoverImage
import com.arslan.gallery.ui.components.SelectionIndicator
import com.arslan.gallery.ui.components.pinchToChangeColumns
import com.arslan.gallery.ui.components.pressScale
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.staggeredEntrance

fun albumSharedKey(path: String) = "album:$path"

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    columns: Int,
    gridState: LazyGridState,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (Album) -> Unit,
    onColumnsChange: (delta: Int) -> Boolean,
    contentPadding: PaddingValues,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val pinned = remember(albums) { context.prefs.pinnedFolders }
    val selectionMode = selection.isNotEmpty()

    val toggle: (Album) -> Unit = { album ->
        val selected = album.path in selection
        haptics.toggle(!selected)
        onSelectionChange(if (selected) selection - album.path else selection + album.path)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxSize()
            .pinchToChangeColumns(onColumnsChange),
    ) {
        itemsIndexed(albums, key = { _, album -> album.path }, contentType = { _, _ -> "album" }) { index, album ->
            AlbumCard(
                album = album,
                selected = album.path in selection,
                selectionMode = selectionMode,
                isPinned = album.path in pinned,
                columns = columns,
                onClick = { if (selectionMode) toggle(album) else onOpen(album) },
                onLongClick = { toggle(album) },
                modifier = Modifier
                    .animateItem(placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    .staggeredEntrance(index, animateEntrance),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.albumSharedBounds(path: String, shape: RoundedCornerShape): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    return with(sharedScope) {
        this@albumSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(albumSharedKey(path)),
            animatedVisibilityScope = navScope,
            enter = AlbumCardEnter,
            boundsTransform = ContainerTransformBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Album,
    selected: Boolean,
    selectionMode: Boolean,
    isPinned: Boolean,
    columns: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val baseCorner = when {
        columns <= 2 -> 32.dp
        columns == 3 -> 24.dp
        else -> 16.dp
    }
    val corner by animateDpAsState(
        targetValue = if (selected) baseCorner + 20.dp else baseCorner,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "albumCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "albumScale",
    )
    val shape = RoundedCornerShape(corner)

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pressScale(interactionSource, 0.93f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .albumSharedBounds(album.path, RoundedCornerShape(baseCorner))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            AlbumCoverImage(album, iconSize = if (columns <= 2) 56.dp else 40.dp)

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                badgeIcon(album, isPinned)?.let { icon ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }

            SelectionIndicator(
                selected = selected,
                visible = selectionMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            style = if (columns <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (album.count > 0) {
            Text(
                text = album.count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun badgeIcon(album: Album, isPinned: Boolean): ImageVector? = when {
    album.isFavorites -> Icons.Rounded.Favorite
    isPinned -> Icons.Rounded.PushPin
    album.isHidden -> Icons.Rounded.VisibilityOff
    else -> null
}
