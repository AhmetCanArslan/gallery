package com.arslan.gallery.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.data.Medium
import com.arslan.gallery.ui.components.pressScale
import com.arslan.gallery.ui.components.rememberHaptics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaSelectionToolbar(
    /** Read lazily: it changes with every photo a drag selects, the toolbar only with what it shows. */
    selectedMedia: () -> List<Medium>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalGalleryActivity.current
    if (activity.pickRequest != null) {
        PickSelectionToolbar(selectedMedia, modifier)
        return
    }

    val haptics = rememberHaptics()
    val currentSelectedMedia by rememberUpdatedState(selectedMedia)
    val selected = remember { derivedStateOf { currentSelectedMedia() } }
    val allFavorite by remember { derivedStateOf { selected.value.let { it.isNotEmpty() && it.all { medium -> medium.isFavorite } } } }
    val inTrash by remember { derivedStateOf { selected.value.any { it.isInTrash } } }
    val paths = { selected.value.map { it.path } }

    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        if (inTrash) {
            ToolbarIcon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.restore)) {
                activity.restoreFromTrash(paths())
                onClearSelection()
            }
            ToolbarIcon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_permanently)) {
                activity.deleteMedia(selected.value) { onClearSelection() }
            }
            ToolbarIcon(Icons.Rounded.SelectAll, stringResource(R.string.select_all), onClick = onSelectAll)
            return@HorizontalFloatingToolbar
        }

        ToolbarIcon(Icons.Rounded.Share, stringResource(R.string.share)) {
            activity.share(paths())
        }
        ToolbarIcon(ImageVector.vectorResource(R.drawable.ic_tailscale), stringResource(R.string.send_via_tailscale)) {
            activity.sendViaTailscale(paths())
        }
        ToolbarIcon(
            icon = if (allFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = stringResource(if (allFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
        ) {
            haptics.toggle(!allFavorite)
            activity.setFavorite(paths(), !allFavorite)
        }
        ToolbarIcon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) {
            activity.deleteMedia(selected.value) { onClearSelection() }
        }
        OverflowMenu(
            icon = Icons.Rounded.MoreVert,
            actions = listOf(
                MenuAction(stringResource(R.string.copy_to), Icons.Rounded.ContentCopy) {
                    activity.copyMove(paths(), isCopy = true, onDone = onClearSelection)
                },
                MenuAction(stringResource(R.string.move_to), Icons.AutoMirrored.Rounded.DriveFileMove) {
                    activity.copyMove(paths(), isCopy = false, onDone = onClearSelection)
                },
                MenuAction(stringResource(R.string.select_all), Icons.Rounded.SelectAll, onSelectAll),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickSelectionToolbar(selectedMedia: () -> List<Medium>, modifier: Modifier) {
    val activity = LocalGalleryActivity.current
    val haptics = rememberHaptics()
    val count = selectedMedia().size
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        Button(onClick = {
            haptics.confirm()
            activity.deliverPicked(selectedMedia().map { it.path })
        }) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.pick_n_items, count, count))
        }
    }
}

@Composable
fun ToolbarIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.pressScale(interactionSource, 0.85f),
    ) {
        Icon(icon, contentDescription = label)
    }
}
