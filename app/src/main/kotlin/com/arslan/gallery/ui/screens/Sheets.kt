package com.arslan.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

import com.arslan.gallery.R
import com.arslan.gallery.data.GROUP_DAY
import com.arslan.gallery.data.GROUP_FOLDER
import com.arslan.gallery.data.GROUP_MONTH
import com.arslan.gallery.data.GROUP_NONE
import com.arslan.gallery.data.GROUP_TYPE
import com.arslan.gallery.data.SORT_COUNT
import com.arslan.gallery.data.SORT_DESCENDING
import com.arslan.gallery.data.SORT_MODIFIED
import com.arslan.gallery.data.SORT_NAME
import com.arslan.gallery.data.SORT_SIZE
import com.arslan.gallery.data.SORT_TAKEN
import com.arslan.gallery.data.SORT_TYPE_MASK
import com.arslan.gallery.data.TYPE_ALL
import com.arslan.gallery.data.TYPE_GIF
import com.arslan.gallery.data.TYPE_IMAGE
import com.arslan.gallery.data.TYPE_RAW
import com.arslan.gallery.data.TYPE_VIDEO
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalGalleryActivity

sealed interface GallerySheet {
    data class Sort(val forAlbums: Boolean) : GallerySheet
    data class Group(val allowFolders: Boolean) : GallerySheet
    data object Filter : GallerySheet
}

@Composable
fun GallerySheetHost(sheet: GallerySheet?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    when (sheet) {
        is GallerySheet.Sort -> SortSheet(sheet.forAlbums, onDismiss, onChanged)
        is GallerySheet.Group -> GroupSheet(sheet.allowFolders, onDismiss, onChanged)
        GallerySheet.Filter -> FilterSheet(onDismiss, onChanged)
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetScaffold(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SegmentedChoices(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            SegmentedListItem(
                selected = isSelected,
                onClick = {
                    onSelect(value)
                },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                trailingContent = {
                    AnimatedVisibility(isSelected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderToggle(descending: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ToggleButton(
            checked = !descending,
            onCheckedChange = {
                onChange(false)
            },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(ToggleButtonDefaults.IconSize))
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(R.string.ascending))
        }
        ToggleButton(
            checked = descending,
            onCheckedChange = {
                onChange(true)
            },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(ToggleButtonDefaults.IconSize))
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(R.string.descending))
        }
    }
}

@Composable
private fun SortSheet(forAlbums: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val prefs = LocalGalleryActivity.current.prefs
    val initial = remember { if (forAlbums) prefs.albumSorting else prefs.mediaSorting }
    var type by remember { mutableIntStateOf((initial and SORT_TYPE_MASK).takeIf { it != 0 } ?: SORT_TAKEN) }
    var descending by remember { mutableStateOf(initial and SORT_DESCENDING != 0) }

    val options = buildList {
        add(SORT_TAKEN to stringResource(R.string.date_taken))
        add(SORT_MODIFIED to stringResource(R.string.last_modified))
        add(SORT_NAME to stringResource(R.string.name))
        add(SORT_SIZE to stringResource(R.string.size))
        if (forAlbums) add(SORT_COUNT to stringResource(R.string.number_of_items))
    }

    val commit = {
        val sorting = type or (if (descending) SORT_DESCENDING else 0)
        if (sorting != initial) {
            if (forAlbums) prefs.albumSorting = sorting else prefs.mediaSorting = sorting
            onChanged()
        }
        onDismiss()
    }

    SheetScaffold(title = stringResource(R.string.sort_by), onDismiss = commit) {
        SegmentedChoices(options, type) { type = it }
        Spacer(Modifier.height(20.dp))
        OrderToggle(descending) { descending = it }
    }
}

@Composable
private fun GroupSheet(allowFolders: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val prefs = LocalGalleryActivity.current.prefs
    val initial = remember { prefs.grouping }
    var type by remember { mutableIntStateOf(initial) }

    val options = buildList {
        add(GROUP_NONE to stringResource(R.string.do_not_group))
        add(GROUP_DAY to stringResource(R.string.by_day))
        add(GROUP_MONTH to stringResource(R.string.by_month))
        add(GROUP_TYPE to stringResource(R.string.by_file_type))
        if (allowFolders) add(GROUP_FOLDER to stringResource(R.string.by_folder))
    }

    val commit = {
        if (type != initial) {
            prefs.grouping = type
            onChanged()
        }
        onDismiss()
    }

    SheetScaffold(title = stringResource(R.string.group_by), onDismiss = commit) {
        SegmentedChoices(options, type) { type = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val prefs = LocalGalleryActivity.current.prefs
    val initial = remember { prefs.filter }
    var filter by remember { mutableIntStateOf(initial) }

    val options = listOf(
        Triple(TYPE_IMAGE, R.string.images, Icons.Rounded.Image),
        Triple(TYPE_VIDEO, R.string.videos, Icons.Rounded.Movie),
        Triple(TYPE_GIF, R.string.gifs, Icons.Rounded.Gif),
        Triple(TYPE_RAW, R.string.raw_images, Icons.Rounded.RawOn),
    )

    val commit = {
        val result = filter.takeIf { it != 0 } ?: TYPE_ALL
        if (result != initial) {
            prefs.filter = result
            onChanged()
        }
        onDismiss()
    }

    SheetScaffold(title = stringResource(R.string.filter_media), onDismiss = commit) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (type, label, icon) ->
                val selected = filter and type != 0
                FilterChip(
                    selected = selected,
                    onClick = {
                        filter = filter xor type
                    },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { FilterIcon(selected, icon) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}

@Composable
private fun FilterIcon(selected: Boolean, icon: ImageVector) {
    androidx.compose.animation.AnimatedContent(selected, label = "filterIcon") { isSelected ->
        Icon(if (isSelected) Icons.Rounded.Check else icon, null, Modifier.size(FilterChipDefaults.IconSize))
    }
}
