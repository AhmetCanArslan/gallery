package com.arslan.gallery.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.gallery.R
import com.arslan.gallery.data.THEME_DARK
import com.arslan.gallery.data.THEME_LIGHT
import com.arslan.gallery.data.THEME_SYSTEM
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.Destination
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.LocalNavigator
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.theme.Appearance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: GalleryViewModel) {
    val activity = LocalGalleryActivity.current
    val navigator = LocalNavigator.current
    val prefs = remember { activity.prefs }
    val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = navigator::pop) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                scrollBehavior = topBarBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.appearance)) {
                    ThemeChooser()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        var dynamic by remember { mutableStateOf(Appearance.dynamicColors) }
                        SwitchItem(Icons.Rounded.Palette, stringResource(R.string.dynamic_colors), stringResource(R.string.dynamic_colors_summary), dynamic) {
                            dynamic = it
                            prefs.dynamicColors = it
                            Appearance.dynamicColors = it
                        }
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.thumbnails)) {
                    PrefSwitch(Icons.Rounded.Timer, stringResource(R.string.show_video_duration), null, prefs.showVideoDuration) { prefs.showVideoDuration = it }
                    PrefSwitch(Icons.Rounded.Style, stringResource(R.string.show_file_types), null, prefs.showFileTypes) { prefs.showFileTypes = it }
                    PrefSwitch(Icons.Rounded.Favorite, stringResource(R.string.mark_favorites), null, prefs.markFavorites) { prefs.markFavorites = it }
                    PrefSwitch(Icons.Rounded.Gif, stringResource(R.string.animate_gifs), null, prefs.animateGifs) { prefs.animateGifs = it }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.viewer)) {
                    PrefSwitch(Icons.Rounded.PlayCircle, stringResource(R.string.autoplay_videos), null, prefs.autoplayVideos) { prefs.autoplayVideos = it }
                    PrefSwitch(Icons.Rounded.Loop, stringResource(R.string.loop_videos), null, prefs.loopVideos) { prefs.loopVideos = it }
                    PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.mute_videos), null, prefs.muteVideos) { prefs.muteVideos = it }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.deleting)) {
                    PrefSwitch(Icons.Rounded.DeleteOutline, stringResource(R.string.use_trash), stringResource(R.string.use_trash_summary), prefs.useTrash) { prefs.useTrash = it }
                    PrefSwitch(Icons.Rounded.QuestionMark, stringResource(R.string.confirm_delete), null, prefs.confirmDelete) { prefs.confirmDelete = it }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.folders)) {
                    var showHidden by remember { mutableStateOf(prefs.showHidden) }
                    SwitchItem(Icons.Rounded.Visibility, stringResource(R.string.show_hidden), stringResource(R.string.show_hidden_summary), showHidden) { checked ->
                        activity.withWriteAccess {
                            showHidden = checked
                            prefs.showHidden = checked
                            viewModel.refresh()
                        }
                    }
                    LinkItem(Icons.Rounded.VisibilityOff, stringResource(R.string.hidden_folders), stringResource(R.string.hidden_folders_summary)) {
                        activity.withWriteAccess { navigator.navigate(Destination.ManageFolders(hidden = true)) }
                    }
                    LinkItem(Icons.Rounded.Block, stringResource(R.string.excluded_folders), stringResource(R.string.excluded_folders_summary)) {
                        navigator.navigate(Destination.ManageFolders(hidden = false))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.version, remember { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty() }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun ItemRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val toggle = { value: Boolean ->
        haptics.toggle(value)
        onChange(value)
    }
    ItemRow(icon, title, summary, onClick = { toggle(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

/** Switch bound to a preference, reading it once and writing every change. */
@Composable
private fun PrefSwitch(icon: ImageVector, title: String, summary: String?, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    SwitchItem(icon, title, summary, checked) {
        checked = it
        onChange(it)
    }
}

@Composable
fun LinkItem(icon: ImageVector, title: String, summary: String?, onClick: () -> Unit) {
    ItemRow(icon, title, summary, onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeChooser() {
    val context = LocalGalleryActivity.current
    val options = listOf(
        Triple(THEME_SYSTEM, R.string.theme_system, Icons.Rounded.BrightnessAuto),
        Triple(THEME_LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
        Triple(THEME_DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { index, (value, label, icon) ->
                ToggleButton(
                    checked = Appearance.theme == value,
                    onCheckedChange = {
                        Appearance.theme = value
                        context.prefs.theme = value
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
    }
}
