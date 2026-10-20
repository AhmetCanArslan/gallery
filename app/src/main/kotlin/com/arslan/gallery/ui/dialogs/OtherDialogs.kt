package com.arslan.gallery.ui.dialogs

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import com.arslan.gallery.R
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.components.AlbumCoverImage
import com.arslan.gallery.ui.components.rememberHaptics
import java.io.File

@Composable
fun ConfirmDialog(dialog: AppDialog.Confirm, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    DialogCard(
        icon = dialog.icon,
        title = dialog.title,
        message = dialog.message,
        iconContainerColor = if (dialog.destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        iconColor = if (dialog.destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Button(
            onClick = {
                haptics.confirm()
                onDismiss()
                dialog.onConfirm()
            },
            colors = if (dialog.destructive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(dialog.confirmLabel)
        }
        if (dialog.secondaryLabel != null) {
            OutlinedButton(
                onClick = {
                    haptics.confirm()
                    onDismiss()
                    dialog.onSecondary()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(dialog.secondaryLabel)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
fun RenameDialog(dialog: AppDialog.Rename, onDismiss: () -> Unit) {
    val initial = dialog.initialName
    val nameEnd = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, nameEnd))) }
    val focusRequester = remember { FocusRequester() }
    val name = value.text.trim()
    val isValid = name.isNotEmpty() && name != initial && !name.contains('/')
    val submit = {
        if (isValid) {
            onDismiss()
            dialog.onConfirm(name)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DialogCard(icon = Icons.Rounded.DriveFileRenameOutline, title = dialog.title, message = null) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = submit, enabled = isValid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.rename))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** Lets the user pick one of the albums, or create a new folder, as destination of a copy or move. */
@Composable
fun PickFolderDialog(dialog: AppDialog.PickFolder, onDismiss: () -> Unit) {
    val activity = LocalGalleryActivity.current
    val haptics = rememberHaptics()
    val albumsState by activity.viewModel.albums.collectAsStateWithLifecycle()
    val albums = remember(albumsState) {
        albumsState.albums.filter { !it.isFavorites && it.path !in dialog.currentFolders }
    }
    var creating by remember { mutableStateOf(false) }

    val pick = { path: String ->
        haptics.confirm()
        onDismiss()
        dialog.onPick(path)
    }

    DialogCard(icon = Icons.Rounded.FolderOpen, title = dialog.title, message = null) {
        AnimatedContent(creating, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pickFolder") { isCreating ->
            if (isCreating) {
                var name by remember { mutableStateOf("") }
                val focusRequester = remember { FocusRequester() }
                val isValid = name.isNotBlank() && !name.contains('/')
                @Suppress("DEPRECATION")
                val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val submit = { if (isValid) pick(File(parent, name.trim()).absolutePath) }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.folder_name)) },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = submit, enabled = isValid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.create))
                    }
                    TextButton(onClick = { creating = false }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.back))
                    }
                }
            } else {
                Column {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            FolderRow(
                                name = stringResource(R.string.new_folder),
                                path = null,
                                cover = { Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                                highlighted = true,
                                onClick = {
                                    creating = true
                                },
                            )
                        }
                        items(albums, key = { it.path }) { album ->
                            FolderRow(
                                name = album.name,
                                path = album.path,
                                cover = { AlbumCoverImage(album, iconSize = 24.dp) },
                                onClick = { pick(album.path) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    path: String?,
    cover: @Composable () -> Unit,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            cover()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (path != null) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AllFilesAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    DialogCard(
        icon = Icons.Rounded.Folder,
        title = stringResource(R.string.all_files_access_title),
        message = stringResource(R.string.all_files_access_message),
    ) {
        Button(
            onClick = {
                onDismiss()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.open_settings))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
