package com.arslan.gallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslan.gallery.MainActivity
import com.arslan.gallery.R
import com.arslan.gallery.data.ALL_MEDIA
import com.arslan.gallery.data.Album
import com.arslan.gallery.data.FAVORITES
import com.arslan.gallery.data.GridItem
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.TRASH
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.Destination
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.LocalNavigator
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.ui.Navigator
import com.arslan.gallery.ui.ViewerController
import com.arslan.gallery.ui.components.CenteredLoadingIndicator
import com.arslan.gallery.ui.components.EmptyState
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.rememberScrollAwayBarState
import com.arslan.gallery.ui.components.scrollAway

private const val TAB_PHOTOS = 0
private const val TAB_ALBUMS = 1

fun openMedium(activity: MainActivity, viewer: ViewerController, sourcePath: String, medium: Medium) {
    if (activity.pickRequest != null) {
        activity.deliverPicked(listOf(medium.path))
    } else {
        viewer.open(sourcePath, medium.path)
    }
}

fun openAlbum(navigator: Navigator, album: Album) {
    navigator.navigate(Destination.Album(album.path, album.name))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(viewModel: GalleryViewModel) {
    val activity = LocalGalleryActivity.current
    val navigator = LocalNavigator.current
    val viewer = LocalViewer.current
    val haptics = rememberHaptics()
    val prefs = remember { activity.prefs }

    var tab by rememberSaveable { mutableIntStateOf(if (prefs.startOnPhotos) TAB_PHOTOS else TAB_ALBUMS) }
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val photos by remember { viewModel.media(ALL_MEDIA) }.collectAsStateWithLifecycle()
    val dirs = albums.albums

    var albumSelection by remember { mutableStateOf(emptySet<String>()) }
    var photoSelection by remember { mutableStateOf(emptySet<String>()) }
    var albumColumns by remember { mutableIntStateOf(prefs.albumColumns) }
    var mediaColumns by remember { mutableIntStateOf(prefs.mediaColumns) }
    val albumsGridState = rememberLazyGridState()
    val photosGridState = rememberLazyGridState()
    var animateAlbumsEntrance by remember { mutableStateOf(dirs.isEmpty()) }

    var sheet by remember { mutableStateOf<GallerySheet?>(null) }
    GallerySheetHost(
        sheet = sheet,
        onDismiss = { sheet = null },
        onChanged = viewModel::onArrangementChanged,
    )

    // Only through derived state or lambdas: a drag changes the selection every few frames, which
    // must recompose just what shows it, never the whole screen.
    val selecting by remember { derivedStateOf { (if (tab == TAB_PHOTOS) photoSelection else albumSelection).isNotEmpty() } }
    // The toolbar follows a frame later, so swapping it doesn't land on the same frame as the top
    // bar's morph when a selection starts.
    var toolbarMode by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            when {
                photoSelection.isNotEmpty() -> 2
                albumSelection.isNotEmpty() -> 3
                else -> 1
            }
        }.collect { toolbarMode = it }
    }
    BackHandler(enabled = selecting) {
        albumSelection = emptySet()
        photoSelection = emptySet()
    }

    // Keep the grid scrolled to the item shown in the viewer, so closing it can fly back into place.
    ScrollToViewerItem(viewer = viewer, sourcePath = ALL_MEDIA, allItems = photos.items, gridState = photosGridState)

    val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarState = rememberScrollAwayBarState()
    // Bring a scrolled-away toolbar back whenever its contents change, so it never returns half hidden.
    LaunchedEffect(tab, selecting) {
        toolbarState.show()
    }

    // Both tabs share one collapsing header, match it to the grid that's now shown instead of keeping
    // the previous tab's scroll state.
    LaunchedEffect(tab) {
        val grid = if (tab == TAB_PHOTOS) photosGridState else albumsGridState
        val atTop = grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
        val barState = topBarBehavior.state
        val target = if (atTop) 0f else barState.heightOffsetLimit
        if (atTop) barState.contentOffset = 0f
        if (barState.heightOffset != target) {
            animate(barState.heightOffset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                barState.heightOffset = value
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarBehavior.nestedScrollConnection)
            .nestedScroll(toolbarState),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // A single bar whose contents morph into selection mode, so its height (and the grid's
            // padding) never changes when selecting.
            // Read only while drawing, behind a transparent bar: passed in as its colors, the
            // animation would recompose the whole bar on every frame.
            val barColor = animateColorAsState(
                targetValue = if (selecting) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                animationSpec = tween(220),
                label = "barColor",
            )
            val scrolledBarColor = animateColorAsState(
                targetValue = if (selecting) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = tween(220),
                label = "scrolledBarColor",
            )
            val clearSelection = {
                albumSelection = emptySet()
                photoSelection = emptySet()
            }
            val title = stringResource(if (tab == TAB_PHOTOS) R.string.tab_photos else R.string.tab_albums)
            LargeFlexibleTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = if (selecting) null else title,
                        transitionSpec = {
                            (fadeIn(tween(220, 80)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 }) togetherWith
                                fadeOut(tween(120))
                        },
                        contentAlignment = Alignment.CenterStart,
                        label = "title",
                    ) { text ->
                        if (text == null) AnimatedCount(if (tab == TAB_PHOTOS) photoSelection.size else albumSelection.size) else Text(text)
                    }
                },
                subtitle = {
                    val count = if (tab == TAB_PHOTOS) photos.media.size else dirs.size
                    if (count > 0) {
                        Text(pluralStringResource(if (tab == TAB_PHOTOS) R.plurals.n_items else R.plurals.n_albums, count, count))
                    }
                },
                navigationIcon = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "navIcon",
                    ) { isSelecting ->
                        if (isSelecting) {
                            IconButton(onClick = clearSelection) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.close))
                            }
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        contentAlignment = Alignment.CenterEnd,
                        label = "actions",
                    ) { isSelecting ->
                        Row {
                            if (isSelecting) {
                                IconButton(onClick = {
                                    if (tab == TAB_ALBUMS) {
                                        albumSelection = dirs.map { it.path }.toSet()
                                    } else {
                                        photoSelection = photos.media.map { it.path }.toSet()
                                    }
                                }) {
                                    Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all))
                                }
                            } else {
                                OverflowMenu(actions = homeMenuActions(activity, viewModel, navigator, tab) { sheet = it })
                            }
                        }
                    }
                },
                scrollBehavior = topBarBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                modifier = Modifier.drawBehind {
                    val collapsed = FastOutLinearInEasing.transform(topBarBehavior.state.collapsedFraction)
                    drawRect(lerp(barColor.value, scrolledBarColor.value, collapsed))
                },
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection) + 12.dp,
            end = padding.calculateEndPadding(layoutDirection) + 12.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 120.dp,
        )

        Box(Modifier.fillMaxSize()) {
            val refreshState = rememberPullToRefreshState()
            val isRefreshing = if (tab == TAB_PHOTOS) photos.isRefreshing else albums.isRefreshing
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptics.confirm()
                    viewModel.refresh(showIndicator = true)
                },
                state = refreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = refreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding()),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(100))
                    },
                    label = "tab",
                ) { currentTab ->
                    if (currentTab == TAB_PHOTOS) {
                        when {
                            !activity.hasPermission || (photos.isLoading && photos.items.isEmpty()) -> CenteredLoadingIndicator()
                            photos.items.isEmpty() -> EmptyState(
                                icon = Icons.Rounded.Photo,
                                title = stringResource(R.string.nothing_here_yet),
                                message = stringResource(R.string.no_media_with_filters),
                                actionLabel = stringResource(R.string.filter_media),
                                onAction = { sheet = GallerySheet.Filter },
                            )

                            else -> MediaGrid(
                                sourcePath = ALL_MEDIA,
                                items = photos.items,
                                columns = mediaColumns,
                                gridState = photosGridState,
                                selection = { photoSelection },
                                onSelectionChange = { if (activity.pickRequest?.allowMultiple != false) photoSelection = it },
                                onOpen = { openMedium(activity, viewer, ALL_MEDIA, it) },
                                onColumnsChange = { delta ->
                                    val old = mediaColumns
                                    mediaColumns = viewModel.updateMediaColumns(delta)
                                    old != mediaColumns
                                },
                                contentPadding = contentPadding,
                            )
                        }
                    } else {
                        when {
                            !activity.hasPermission || (albums.isLoading && dirs.isEmpty()) -> CenteredLoadingIndicator()
                            dirs.isEmpty() -> EmptyState(
                                icon = Icons.Rounded.PhotoAlbum,
                                title = stringResource(R.string.nothing_here_yet),
                                message = stringResource(R.string.no_media_with_filters),
                                actionLabel = stringResource(R.string.filter_media),
                                onAction = { sheet = GallerySheet.Filter },
                            )

                            else -> {
                                AlbumsGrid(
                                    albums = dirs,
                                    columns = albumColumns,
                                    gridState = albumsGridState,
                                    selection = albumSelection,
                                    onSelectionChange = { if (activity.pickRequest == null) albumSelection = it },
                                    onOpen = { openAlbum(navigator, it) },
                                    onColumnsChange = { delta ->
                                        val old = albumColumns
                                        albumColumns = viewModel.updateAlbumColumns(delta)
                                        old != albumColumns
                                    },
                                    contentPadding = contentPadding,
                                    animateEntrance = animateAlbumsEntrance,
                                )
                                LaunchedEffect(Unit) { animateAlbumsEntrance = false }
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = toolbarMode,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(160)) { it } + fadeOut(tween(120))) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                },
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .scrollAway(toolbarState)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                label = "bottomToolbar",
            ) { mode ->
                when (mode) {
                    1 -> HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    ) {
                        ToolbarTab(
                            selected = tab == TAB_PHOTOS,
                            icon = Icons.Outlined.Photo,
                            selectedIcon = Icons.Rounded.Photo,
                            label = stringResource(R.string.tab_photos),
                        ) {
                            tab = TAB_PHOTOS
                            prefs.startOnPhotos = true
                        }
                        ToolbarTab(
                            selected = tab == TAB_ALBUMS,
                            icon = Icons.Outlined.PhotoLibrary,
                            selectedIcon = Icons.Rounded.PhotoLibrary,
                            label = stringResource(R.string.tab_albums),
                        ) {
                            tab = TAB_ALBUMS
                            prefs.startOnPhotos = false
                        }
                    }

                    2 -> MediaSelectionToolbar(
                        selectedMedia = { photos.media.filter { it.path in photoSelection } },
                        onSelectAll = { photoSelection = photos.media.map { it.path }.toSet() },
                        onClearSelection = { photoSelection = emptySet() },
                    )

                    else -> AlbumSelectionToolbar(
                        selectedAlbums = dirs.filter { it.path in albumSelection },
                        onClearSelection = { albumSelection = emptySet() },
                    )
                }
            }
        }
    }
}

@Composable
private fun homeMenuActions(
    activity: MainActivity,
    viewModel: GalleryViewModel,
    navigator: Navigator,
    tab: Int,
    onSheet: (GallerySheet) -> Unit,
): List<MenuAction> {
    val prefs = activity.prefs
    val showHidden = prefs.showHidden
    return buildList {
        if (tab == TAB_PHOTOS) {
            add(MenuAction(stringResource(R.string.sort_by), Icons.AutoMirrored.Rounded.Sort) { onSheet(GallerySheet.Sort(forAlbums = false)) })
            add(MenuAction(stringResource(R.string.group_by), Icons.Rounded.ViewModule) { onSheet(GallerySheet.Group(allowFolders = true)) })
        } else {
            add(MenuAction(stringResource(R.string.sort_by), Icons.AutoMirrored.Rounded.Sort) { onSheet(GallerySheet.Sort(forAlbums = true)) })
        }
        add(MenuAction(stringResource(R.string.filter_media), Icons.Rounded.FilterList) { onSheet(GallerySheet.Filter) })
        add(
            MenuAction(
                stringResource(if (showHidden) R.string.stop_showing_hidden else R.string.show_hidden),
                if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            ) {
                activity.withWriteAccess {
                    prefs.showHidden = !showHidden
                    viewModel.refresh(showIndicator = true)
                }
            }
        )
        add(MenuAction(stringResource(R.string.trash), Icons.Rounded.DeleteOutline) {
            navigator.navigate(Destination.Album(TRASH, activity.getString(R.string.trash)))
        })
        add(MenuAction(stringResource(R.string.settings), Icons.Rounded.Settings) { navigator.navigate(Destination.Settings) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumSelectionToolbar(
    selectedAlbums: List<Album>,
    onClearSelection: () -> Unit,
) {
    val activity = LocalGalleryActivity.current
    val paths = selectedAlbums.map { it.path }
    val folders = paths.filter { it != FAVORITES }
    val pinned = activity.prefs.pinnedFolders
    val anyUnpinned = folders.any { it !in pinned }
    val anyHidden = selectedAlbums.any { it.isHidden }

    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        ToolbarIcon(
            Icons.Rounded.PushPin,
            stringResource(if (anyUnpinned) R.string.pin_album else R.string.unpin_album),
            enabled = folders.isNotEmpty(),
        ) {
            activity.pinAlbums(folders, anyUnpinned)
            onClearSelection()
        }
        ToolbarIcon(
            if (anyHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            stringResource(if (anyHidden) R.string.unhide else R.string.hide),
            enabled = folders.isNotEmpty(),
        ) {
            activity.hideAlbums(folders, hide = !anyHidden)
            onClearSelection()
        }
        ToolbarIcon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) {
            activity.deleteAlbums(selectedAlbums)
            onClearSelection()
        }
        OverflowMenu(
            icon = Icons.Rounded.MoreVert,
            actions = buildList {
                if (selectedAlbums.size == 1 && !selectedAlbums.first().isFavorites) {
                    add(MenuAction(stringResource(R.string.rename), Icons.Rounded.Edit) {
                        activity.renameAlbum(selectedAlbums.first())
                        onClearSelection()
                    })
                }
                if (folders.isNotEmpty()) {
                    add(MenuAction(stringResource(R.string.exclude), Icons.Rounded.Block) {
                        activity.excludeAlbums(folders)
                        onClearSelection()
                    })
                }
                add(MenuAction(stringResource(R.string.copy_to), Icons.Rounded.ContentCopy) {
                    activity.copyMoveAlbums(paths, isCopy = true)
                    onClearSelection()
                })
                add(MenuAction(stringResource(R.string.move_to), Icons.AutoMirrored.Rounded.DriveFileMove) {
                    activity.copyMoveAlbums(paths, isCopy = false)
                    onClearSelection()
                })
            },
        )
    }
}

/** Scrolls [gridState] so the medium currently shown in the viewer is on screen. */
@Composable
fun ScrollToViewerItem(
    viewer: ViewerController,
    sourcePath: String,
    allItems: List<GridItem>,
    gridState: LazyGridState,
) {
    val target = if (viewer.isOpen && viewer.sourcePath == sourcePath) viewer.currentPath else null
    LaunchedEffect(target) {
        if (target == null || allItems.isEmpty()) return@LaunchedEffect
        val index = allItems.indexOfFirst { it is Medium && it.path == target }
        if (index < 0) return@LaunchedEffect
        val visible = gridState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { info ->
            info.index == index &&
                info.offset.y >= gridState.layoutInfo.viewportStartOffset + 200 &&
                info.offset.y + info.size.height <= gridState.layoutInfo.viewportEndOffset - 200
        }
        if (!fullyVisible) {
            gridState.scrollToItem(index, scrollOffset = -gridState.layoutInfo.viewportSize.height / 3)
        }
    }
}
