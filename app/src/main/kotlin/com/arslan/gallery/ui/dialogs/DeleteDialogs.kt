package com.arslan.gallery.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.arslan.gallery.R
import com.arslan.gallery.data.Medium
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.thumbnailRequest

@Composable
fun DeleteMediaDialog(dialog: AppDialog.DeleteMedia, onDismiss: () -> Unit) {
    DeleteDialog(
        title = pluralStringResource(R.plurals.delete_n_items_question, dialog.media.size, dialog.media.size),
        previews = dialog.media,
        canUseTrash = dialog.canUseTrash,
        onConfirm = dialog.onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun DeleteAlbumsDialog(dialog: AppDialog.DeleteAlbums, onDismiss: () -> Unit) {
    val count = dialog.albums.size
    DeleteDialog(
        title = if (count == 1) {
            stringResource(R.string.delete_album_question, dialog.albums.first().name)
        } else {
            pluralStringResource(R.plurals.delete_n_albums_question, count, count)
        },
        previews = dialog.albums.mapNotNull { it.cover },
        canUseTrash = dialog.canUseTrash,
        onConfirm = dialog.onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Delete confirmation with a fanned preview of what gets deleted. Moving to the trash is the primary
 * action whenever it's possible, deleting permanently is always a deliberate second choice.
 */
@Composable
private fun DeleteDialog(
    title: String,
    previews: List<Medium>,
    canUseTrash: Boolean,
    onConfirm: (toTrash: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val confirm = { toTrash: Boolean ->
        haptics.confirm()
        onDismiss()
        onConfirm(toTrash)
    }

    DialogCard(
        icon = if (canUseTrash) Icons.Rounded.DeleteOutline else Icons.Rounded.DeleteForever,
        title = title,
        message = stringResource(if (canUseTrash) R.string.move_to_trash_hint else R.string.delete_permanently_hint),
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        header = if (previews.isEmpty()) null else ({ PreviewStack(previews) }),
    ) {
        if (canUseTrash) {
            Button(
                onClick = { confirm(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.move_to_trash))
            }
            OutlinedButton(
                onClick = { confirm(false) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.delete_permanently))
            }
        } else {
            Button(
                onClick = { confirm(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_permanently))
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** Up to three thumbnails fanned out like a hand of cards. */
@Composable
private fun PreviewStack(media: List<Medium>) {
    val context = LocalContext.current
    val shown = remember(media) { media.take(3) }
    val border = MaterialTheme.colorScheme.surfaceContainerHigh
    val angles = when (shown.size) {
        1 -> listOf(0f)
        2 -> listOf(-7f, 7f)
        else -> listOf(-10f, 0f, 10f)
    }
    val offsets = when (shown.size) {
        1 -> listOf(0)
        2 -> listOf(-22, 22)
        else -> listOf(-40, 0, 40)
    }

    Box(Modifier.height(112.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        shown.forEachIndexed { index, medium ->
            val isCenter = shown.size == 1 || (shown.size == 3 && index == 1)
            AsyncImage(
                model = remember(medium.signature) { thumbnailRequest(context, medium) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .zIndex(if (isCenter) 1f else 0f)
                    .offset(x = offsets[index].dp, y = if (isCenter) 0.dp else 6.dp)
                    .rotate(angles[index])
                    .size(if (isCenter) 96.dp else 84.dp)
                    .border(3.dp, border, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}
