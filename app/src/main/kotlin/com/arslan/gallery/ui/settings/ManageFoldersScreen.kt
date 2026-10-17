package com.arslan.gallery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R
import com.arslan.gallery.data.filename
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.LocalNavigator
import com.arslan.gallery.ui.components.CenteredLoadingIndicator
import com.arslan.gallery.ui.components.EmptyState
import com.arslan.gallery.ui.components.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists hidden ([hidden]) or excluded folders and lets the user bring them back. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageFoldersScreen(viewModel: GalleryViewModel, hidden: Boolean) {
    val activity = LocalGalleryActivity.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var folders by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(hidden) {
        folders = if (hidden) {
            withContext(Dispatchers.IO) { viewModel.hiddenFolders() }.sorted()
        } else {
            activity.prefs.excludedFolders.sorted()
        }
    }

    val restore = { folder: String ->
        haptics.confirm()
        folders = folders?.minus(folder)
        if (hidden) {
            activity.hideAlbums(listOf(folder), hide = false)
        } else {
            activity.prefs.excludedFolders = activity.prefs.excludedFolders - folder
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(if (hidden) R.string.hidden_folders else R.string.excluded_folders)) },
                subtitle = {
                    Text(stringResource(if (hidden) R.string.hidden_folders_summary else R.string.excluded_folders_summary))
                },
                navigationIcon = {
                    IconButton(onClick = navigator::pop) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                scrollBehavior = topBarBehavior,
            )
        },
    ) { padding ->
        val list = folders
        when {
            list == null -> CenteredLoadingIndicator()
            list.isEmpty() -> EmptyState(
                icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Block,
                title = stringResource(if (hidden) R.string.no_hidden_folders else R.string.no_excluded_folders),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            ) {
                items(list, key = { it }) { folder ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.filename, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                folder,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalIconButton(onClick = { restore(folder) }) {
                            Icon(
                                if (hidden) Icons.Rounded.Visibility else Icons.Rounded.Close,
                                stringResource(if (hidden) R.string.unhide else R.string.remove),
                            )
                        }
                    }
                }
            }
        }
    }
}
