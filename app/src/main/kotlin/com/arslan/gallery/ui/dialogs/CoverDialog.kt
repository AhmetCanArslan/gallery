package com.arslan.gallery.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.arslan.gallery.R
import com.arslan.gallery.data.AlbumCover
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.components.AlbumIcons
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.thumbnailRequest

/** Lets the user pick a photo of the album or an icon as its cover, or go back to the latest photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChooseCoverDialog(dialog: AppDialog.ChooseCover, onDismiss: () -> Unit) {
    val activity = LocalGalleryActivity.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val album = dialog.album
    val current = remember { activity.prefs.albumCovers()[album.path] }
    val state by remember { activity.viewModel.media(album.path) }.collectAsStateWithLifecycle()
    var showIcons by remember { mutableStateOf(current is AlbumCover.Icon) }

    val pick = { cover: AlbumCover? ->
        haptics.confirm()
        onDismiss()
        activity.setAlbumCover(album.path, cover)
    }

    DialogCard(icon = Icons.Rounded.PhotoAlbum, title = stringResource(R.string.change_cover), message = album.name) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToggleButton(
                checked = !showIcons,
                onCheckedChange = { showIcons = false },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Image, null, Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(R.string.cover_photo))
            }
            ToggleButton(
                checked = showIcons,
                onCheckedChange = { showIcons = true },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Category, null, Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(R.string.cover_icon))
            }
        }
        Spacer(Modifier.height(8.dp))

        AnimatedContent(showIcons, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "coverKind") { icons ->
            val gridModifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp))
            if (icons) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = gridModifier,
                ) {
                    items(AlbumIcons, key = { it.first }) { (key, icon) ->
                        val selected = (current as? AlbumCover.Icon)?.key == key
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { pick(AlbumCover.Icon(key)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = key,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = gridModifier,
                ) {
                    items(state.media, key = { it.path }) { medium ->
                        val selected = (current as? AlbumCover.Photo)?.path == medium.path
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                                .clickable { pick(AlbumCover.Photo(medium.path)) },
                        ) {
                            AsyncImage(
                                model = remember(medium.signature) { thumbnailRequest(context, medium) },
                                contentDescription = medium.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pick(null) },
            enabled = current != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.use_latest_photo))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
