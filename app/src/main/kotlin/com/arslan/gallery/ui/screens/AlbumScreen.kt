package com.arslan.gallery.ui.screens

import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.core.snap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslan.gallery.R
import com.arslan.gallery.data.FAVORITES
import com.arslan.gallery.data.TRASH
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalNavAnimatedVisibilityScope
import com.arslan.gallery.ui.LocalNavigator
import com.arslan.gallery.ui.LocalSharedTransitionScope
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.ui.theme.AlbumScreenExit
import com.arslan.gallery.ui.theme.ContainerTransformBounds
import com.arslan.gallery.ui.components.CenteredLoadingIndicator
import com.arslan.gallery.ui.components.EmptyState
import com.arslan.gallery.ui.components.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumScreen(
    viewModel: GalleryViewModel,
    path: String,
    name: String,
) {
    val activity = LocalGalleryActivity.current
    val navigator = LocalNavigator.current
    val viewer = LocalViewer.current
    val haptics = rememberHaptics()
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current

    val state by remember(path) { viewModel.media(path) }.collectAsStateWithLifecycle()

    var selection by remember { mutableStateOf(emptySet<String>()) }
    var columns by remember { mutableIntStateOf(activity.prefs.mediaColumns) }
    val gridState = rememberLazyGridState()
    val isTrash = path == TRASH

    // Only through derived state or lambdas, see HomeScreen.
    val selecting by remember { derivedStateOf { selection.isNotEmpty() } }
    // The toolbar follows a frame later, see HomeScreen.
    var toolbarSelecting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { selection.isNotEmpty() }.collect { toolbarSelecting = it }
    }
    BackHandler(enabled = selecting) { selection = emptySet() }
    ScrollToViewerItem(viewer = viewer, sourcePath = path, allItems = state.items, gridState = gridState)

    var sheet by remember { mutableStateOf<GallerySheet?>(null) }
    GallerySheetHost(
        sheet = sheet,
        onDismiss = { sheet = null },
        onChanged = viewModel::onArrangementChanged,
    )

    val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val containerModifier = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(albumSharedKey(path)),
            animatedVisibilityScope = navScope,
            exit = AlbumScreenExit,
            boundsTransform = ContainerTransformBounds,
            // Scaled as a whole rather than re-laid out, so the album zooms in and out of its card
            // exactly as it looks at that moment.
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp)),
        )
    }

    Scaffold(
        modifier = containerModifier
            .fillMaxSize()
            .nestedScroll(topBarBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // One bar that morphs into selection mode, so the grid below never jumps.
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
            MediumFlexibleTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = {
                            (fadeIn(tween(220, 80)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 }) togetherWith
                                fadeOut(tween(120))
                        },
                        contentAlignment = Alignment.CenterStart,
                        label = "albumTitle",
                    ) { isSelecting ->
                        if (isSelecting) AnimatedCount(selection.size) else Text(name, maxLines = 1)
                    }
                },
                subtitle = {
                    if (state.media.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.n_items, state.media.size, state.media.size))
                    }
                },
                navigationIcon = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "albumNavIcon",
                    ) { isSelecting ->
                        if (isSelecting) {
                            IconButton(onClick = { selection = emptySet() }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.close))
                            }
                        } else {
                            IconButton(onClick = {
                                navigator.pop()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        contentAlignment = Alignment.CenterEnd,
                        label = "albumActions",
                    ) { isSelecting ->
                        Row {
                            if (isSelecting) {
                                IconButton(onClick = {
                                    selection = state.media.map { it.path }.toSet()
                                }) {
                                    Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all))
                                }
                            } else {
                                if (state.media.isNotEmpty() && !isTrash && activity.pickRequest == null) {
                                    IconButton(onClick = {
                                        viewer.open(path, state.media.first().path, slideshow = true)
                                    }) {
                                        Icon(Icons.Rounded.Slideshow, stringResource(R.string.slideshow))
                                    }
                                }
                                if (isTrash) {
                                    if (state.media.isNotEmpty()) {
                                        OverflowMenu(
                                            actions = listOf(
                                                MenuAction(stringResource(R.string.restore_all), Icons.Rounded.RestoreFromTrash) {
                                                    activity.restoreFromTrash(state.media.map { it.path })
                                                },
                                                MenuAction(stringResource(R.string.empty_trash), Icons.Rounded.DeleteForever) {
                                                    activity.emptyTrash()
                                                },
                                            )
                                        )
                                    }
                                } else {
                                    OverflowMenu(
                                        actions = listOf(
                                            MenuAction(stringResource(R.string.sort_by), Icons.AutoMirrored.Rounded.Sort) { sheet = GallerySheet.Sort(forAlbums = false) },
                                            MenuAction(stringResource(R.string.group_by), Icons.Rounded.ViewModule) { sheet = GallerySheet.Group(allowFolders = path == FAVORITES) },
                                            MenuAction(stringResource(R.string.filter_media), Icons.Rounded.FilterList) { sheet = GallerySheet.Filter },
                                        )
                                    )
                                }
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
            start = padding.calculateStartPadding(layoutDirection) + 4.dp,
            end = padding.calculateEndPadding(layoutDirection) + 4.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 112.dp,
        )

        Box(Modifier.fillMaxSize()) {
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = {
                    haptics.confirm()
                    viewModel.refresh(showIndicator = true)
                },
                state = refreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = refreshState,
                        isRefreshing = state.isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding()),
                    )
                },
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> CenteredLoadingIndicator()
                    state.items.isEmpty() -> EmptyState(
                        icon = if (isTrash) Icons.Rounded.DeleteOutline else Icons.Rounded.Photo,
                        title = stringResource(if (isTrash) R.string.trash_empty else R.string.nothing_here_yet),
                        message = stringResource(if (isTrash) R.string.trash_hint else R.string.no_media_with_filters),
                    )

                    else -> MediaGrid(
                        sourcePath = path,
                        items = state.items,
                        columns = columns,
                        gridState = gridState,
                        selection = { selection },
                        onSelectionChange = { if (activity.pickRequest?.allowMultiple != false) selection = it },
                        onOpen = { openMedium(activity, viewer, path, it) },
                        onColumnsChange = { delta ->
                            val old = columns
                            columns = viewModel.updateMediaColumns(delta)
                            old != columns
                        },
                        contentPadding = contentPadding,
                    )
                }
            }

            AnimatedContent(
                targetState = toolbarSelecting,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(160)) { it } + fadeOut(tween(120))) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                },
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                label = "albumToolbar",
            ) { selecting ->
                if (selecting) {
                    MediaSelectionToolbar(
                        selectedMedia = { state.media.filter { it.path in selection } },
                        onSelectAll = { selection = state.media.map { it.path }.toSet() },
                        onClearSelection = { selection = emptySet() },
                    )
                } else {
                    Box(Modifier)
                }
            }
        }
    }
}
