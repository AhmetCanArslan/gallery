package com.arslan.gallery.ui.viewer

import android.app.Activity
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.delay
import com.arslan.gallery.ui.theme.EmphasizedEasing
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.arslan.gallery.R
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.screens.MenuAction
import com.arslan.gallery.ui.screens.OverflowMenu
import com.arslan.gallery.ui.screens.ToolbarIcon
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun ViewerOverlay(viewModel: GalleryViewModel) {
    val viewer = LocalViewer.current
    // Closing hands the photo over to a flight of its own (see PhotoFlight) and hides the viewer right
    // away, every opening gets a fresh one. Opening and closing photos never share any state, so
    // tapping, swiping and reopening can overlap freely.
    FlyingPhotos()

    // Tearing the viewer down is heavy, so it happens a frame after the photo took off instead of on
    // the same frame. Meanwhile it isn't placed: not drawn, not touchable.
    var tornDownSession by remember { mutableIntStateOf(viewer.session) }
    LaunchedEffect(viewer.isOpen) {
        if (!viewer.isOpen) {
            withFrameNanos { }
            tornDownSession = viewer.session
        }
    }

    ViewerTheme {
        if (viewer.session != tornDownSession) {
            key(viewer.session) {
                Box(Modifier.placedIf { viewer.isOpen }) {
                    ViewerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

private fun Modifier.placedIf(placed: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (placed()) placeable.place(0, 0)
    }
}

/**
 * False until [frames] frames after the first composition, then true. Spreads the work of composing
 * a big screen over a few frames, so what moves first never waits for what can come a moment later.
 */
@Composable
internal fun rememberAfterFrames(frames: Int): Boolean {
    var ready by remember { mutableStateOf(frames <= 0) }
    if (!ready) {
        LaunchedEffect(Unit) {
            // Starting the effect already takes a frame.
            repeat(frames - 1) { withFrameNanos { } }
            ready = true
        }
    }
    return ready
}

/** The viewer is always dark, using the dark variant of the current (dynamic) palette. */
@Composable
private fun ViewerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val scheme: ColorScheme = remember(context, isDark) {
        when {
            isDark -> base
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
            else -> darkColorScheme()
        }
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerScreen(viewModel: GalleryViewModel) {
    val activity = LocalGalleryActivity.current
    val viewer = LocalViewer.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    val sourcePath = remember { viewer.sourcePath }
    val state by remember(sourcePath) { viewModel.media(sourcePath) }.collectAsStateWithLifecycle()
    val media = state.media
    val initialPage = remember { media.indexOfFirst { it.path == viewer.currentPath }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { media.size }
    val currentMedia by rememberUpdatedState(media)

    // The photo grows out of its thumbnail, or out of wherever it was caught flying back, into its
    // spot on screen. That spot is followed live, so it can be dragged before it even got there.
    val openFrom = remember { viewer.openFrom }
    val openScrim = remember { viewer.openScrim }
    val opening = remember { Animatable(0f) }
    var isOpening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        opening.animateTo(1f, FlightSpring)
        isOpening = false
    }

    var slideshow by remember { mutableStateOf(viewer.startSlideshow) }
    var chromeVisible by remember { mutableStateOf(!viewer.startSlideshow) }
    var showInfo by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    val zoomFractions = remember { mutableStateMapOf<String, Float>() }
    val zoomedBounds = remember { HashMap<String, Rect>() }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentMedia.getOrNull(page)?.let { viewer.currentPath = it.path }
        }
    }

    LaunchedEffect(media.isEmpty(), state.isLoading) {
        if (media.isEmpty() && !state.isLoading) viewer.close()
    }

    val currentMedium = media.getOrNull(pagerState.currentPage)

    // Slideshow: advance through the photos until the end, any tap stops it.
    val view = LocalView.current
    LaunchedEffect(slideshow, pagerState.settledPage, isOpening) {
        if (!slideshow || isOpening) return@LaunchedEffect
        view.keepScreenOn = true
        try {
            delay(activity.prefs.slideshowInterval * 1000L)
            val next = (pagerState.settledPage + 1 until media.size).firstOrNull { !media[it].isVideo }
            if (next == null) {
                slideshow = false
                chromeVisible = true
            } else {
                pagerState.animateScrollToPage(next, animationSpec = tween(SLIDESHOW_PAGE_MS, easing = EmphasizedEasing))
            }
        } finally {
            view.keepScreenOn = false
        }
    }
    val toggleChrome = {
        if (slideshow) slideshow = false
        chromeVisible = !chromeVisible
    }

    // The first frame only has the backdrop and the photo, the bars slide in from the next ones.
    val topBarReady = rememberAfterFrames(1)
    val bottomBarReady = rememberAfterFrames(2)
    // Neighbouring pages are composed once the photo has settled, not while it's still flying.
    var neighboursReady by remember { mutableStateOf(false) }
    LaunchedEffect(isOpening) {
        if (!isOpening) {
            delay(NEIGHBOURS_DELAY_MS)
            neighboursReady = true
        }
    }

    ViewerSystemBars(hidden = !chromeVisible)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = Rect(Offset.Zero, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        SideEffect { viewer.corners.viewport = viewport.size }
        val heightPx = viewport.height
        val dismissThreshold = heightPx * 0.14f

        // Only read while drawing, so dragging and flying never recompose the viewer.
        fun downProgress() = (dragOffset.y / (heightPx * 0.5f)).coerceIn(0f, 1f)
        fun scrim() = lerp(openScrim, 1f, opening.value.coerceIn(0f, 1f)) *
            (1f - downProgress() * 1.4f).coerceIn(0f, 1f) * (1f - predictiveBackProgress * 0.5f)
        fun aspect() = currentMedia.getOrNull(pagerState.currentPage)?.let { viewer.aspects[it.path] }

        /** Where the current photo is: fitted to the screen, moved and shrunk by gestures, and on its way in. */
        fun currentFrame(): PhotoFrame {
            val scale = (1f - downProgress() * 0.4f) * (1f - predictiveBackProgress * 0.12f)
            val resting = PhotoFrame.fit(Rect(dragOffset + viewport.center * (1f - scale), viewport.size * scale), aspect())
            val progress = opening.value
            return if (progress >= 1f) resting else lerp(openFrom ?: resting.faded(0.9f), resting, progress)
        }

        val close: (velocity: Offset) -> Unit = { velocity ->
            val medium = currentMedia.getOrNull(pagerState.currentPage)
            viewer.close(
                medium?.let {
                    // A zoomed in photo shrinks right from where it's zoomed to.
                    val zoomed = zoomedBounds[it.path]?.takeIf { bounds -> (zoomFractions[it.path] ?: 0f) > 0.01f && !bounds.isEmpty }
                    val from = zoomed?.let { bounds -> PhotoFrame(bounds, bounds.intersect(viewport)) } ?: currentFrame()
                    PhotoFlight(sourcePath, it, from, scrim(), velocity)
                },
            )
        }

        // Predictive back shrinks the photo a little before it flies back into the grid.
        PredictiveBackHandler { progress ->
            try {
                progress.collect { predictiveBackProgress = it.progress }
                close(Offset.Zero)
            } catch (e: CancellationException) {
                scope.launch {
                    animate(predictiveBackProgress, 0f, animationSpec = spring()) { value, _ -> predictiveBackProgress = value }
                }
                throw e
            }
        }

        val armed by remember(dismissThreshold) { derivedStateOf { dragOffset.y > dismissThreshold } }
        LaunchedEffect(armed) {
            if (isDragging) haptics.threshold()
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = scrim()) }
        )

        val settle: (velocity: Offset) -> Unit = { velocity ->
            isDragging = false
            when {
                dragOffset.y > dismissThreshold || velocity.y > 1800f -> close(velocity)
                dragOffset.y < -dismissThreshold * 0.8f || velocity.y < -1800f -> {
                    showInfo = true
                    scope.launch {
                        animate(Offset.VectorConverter, dragOffset, Offset.Zero, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragOffset = v }
                    }
                }

                else -> scope.launch {
                    animate(Offset.VectorConverter, dragOffset, Offset.Zero, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragOffset = v }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            key = { media.getOrNull(it)?.path ?: it },
            beyondViewportPageCount = if (neighboursReady) 1 else 0,
            pageSpacing = 16.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            ),
            userScrollEnabled = !isDragging && !isOpening,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val zoomedNow = (zoomFractions[viewer.currentPath] ?: 0f) > 0.01f
                        if (zoomedNow) return@awaitEachGesture

                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size > 1) {
                                if (dragging) settle(Offset.Zero)
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    val velocity = tracker.calculateVelocity()
                                    settle(Offset(velocity.x, velocity.y))
                                }
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            if (!dragging) {
                                total += delta
                                if (abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.4f && abs(pagerState.currentPageOffsetFraction) < 0.01f) {
                                    dragging = true
                                    isDragging = true
                                } else if (abs(total.x) > slop) {
                                    break
                                }
                            } else {
                                val dy = if (dragOffset.y + delta.y < 0) delta.y * 0.45f else delta.y
                                dragOffset += Offset(delta.x * 0.6f, dy)
                                change.consume()
                            }
                        }
                    }
                },
        ) { page ->
            val medium = media.getOrNull(page) ?: return@HorizontalPager
            val isCurrent = page == pagerState.currentPage
            val frameModifier = if (isCurrent) {
                Modifier.photoFrame(viewer.corners, natural = { PhotoFrame.fit(viewport, aspect()) }, frame = { currentFrame() })
            } else {
                Modifier
            }

            Box(Modifier.fillMaxSize().then(frameModifier)) {
                if (medium.isVideo) {
                    VideoPage(
                        medium = medium,
                        isCurrent = isCurrent && pagerState.settledPage == page,
                        isOpening = isOpening,
                        isDragging = isDragging,
                        chromeVisible = chromeVisible,
                        onToggleChrome = toggleChrome,
                    )
                } else {
                    ImagePage(
                        medium = medium,
                        inTransition = isOpening,
                        onZoomChange = { fraction, bounds ->
                            if (zoomFractions[medium.path] != fraction) zoomFractions[medium.path] = fraction
                            zoomedBounds[medium.path] = bounds
                        },
                        onTap = toggleChrome,
                    )
                }
            }
        }

        // Top chrome
        AnimatedVisibility(
            visible = topBarReady && chromeVisible && !isDragging && predictiveBackProgress == 0f,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            currentMedium?.let { medium ->
                ViewerTopBar(
                    medium = medium,
                    onBack = { close(Offset.Zero) },
                    onSlideshow = {
                        chromeVisible = false
                        slideshow = true
                    },
                )
            }
        }

        // Bottom chrome
        AnimatedVisibility(
            visible = bottomBarReady && chromeVisible && !isDragging && predictiveBackProgress == 0f,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            currentMedium?.let { medium ->
                ViewerBottomToolbar(
                    medium = medium,
                    onInfo = { showInfo = true },
                )
            }
        }
    }

    if (showInfo && currentMedium != null) {
        MediumInfoSheet(medium = currentMedium, onDismiss = { showInfo = false })
    }
}

/**
 * Draws the page so that its photo, which rests at [natural], shows at [frame] instead, rounding the
 * corners as it shrinks. Only transforms the layer, following a gesture or a flight never measures
 * or recomposes the page.
 */
private fun Modifier.photoFrame(corners: FlightCorners, natural: () -> PhotoFrame, frame: () -> PhotoFrame): Modifier = graphicsLayer {
    val rest = natural()
    val current = frame()
    if (current == rest) return@graphicsLayer
    val scale = current.image.width / rest.image.width
    if (!(scale > 0f && scale.isFinite())) return@graphicsLayer

    val translation = current.image.topLeft - rest.image.topLeft * scale
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = translation.x
    translationY = translation.y
    alpha = current.alpha
    // The clip goes in the page's own coordinates, before the layer's transform.
    clip = true
    shape = RoundedRectAt(
        rect = Rect((current.clip.topLeft - translation) / scale, current.clip.size / scale),
        radius = corners.radius(current.clip.size, this) / scale,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerTopBar(medium: Medium, onBack: () -> Unit, onSlideshow: () -> Unit) {
    val activity = LocalGalleryActivity.current
    val timestamp = if (medium.taken > 0) medium.taken else medium.modified
    val date = remember(timestamp) { if (timestamp > 0) DateFormat.getDateInstance(DateFormat.LONG).format(Date(timestamp)) else null }
    val time = remember(timestamp) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp)) }
    val isFile = !medium.isUri && !medium.isInTrash
    val isImage = !medium.isVideo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(date ?: medium.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (date != null) {
                Text(
                    text = "$time · ${medium.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
            OverflowMenu(
                actions = buildList {
                    if (isFile && isImage) {
                        add(MenuAction(stringResource(R.string.rotate_left), Icons.AutoMirrored.Rounded.RotateLeft) { activity.rotate(medium.path, -90) })
                        add(MenuAction(stringResource(R.string.rotate_right), Icons.AutoMirrored.Rounded.RotateRight) { activity.rotate(medium.path, 90) })
                    }
                    if (!medium.isInTrash) {
                        add(MenuAction(stringResource(R.string.set_as), Icons.Rounded.Wallpaper) { activity.setAs(medium.path) })
                    }
                    add(MenuAction(stringResource(R.string.open_with), Icons.AutoMirrored.Rounded.OpenInNew) { activity.openWith(medium.path) })
                    if (isFile) {
                        add(MenuAction(stringResource(R.string.rename), Icons.Rounded.DriveFileRenameOutline) { activity.rename(medium.path) })
                        add(MenuAction(stringResource(R.string.copy_to), Icons.Rounded.ContentCopy) { activity.copyMove(listOf(medium.path), isCopy = true) })
                        add(MenuAction(stringResource(R.string.move_to), Icons.AutoMirrored.Rounded.DriveFileMove) { activity.copyMove(listOf(medium.path), isCopy = false) })
                        add(
                            MenuAction(
                                stringResource(if (medium.isHidden) R.string.unhide else R.string.hide),
                                if (medium.isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            ) { activity.setHidden(medium.path, !medium.isHidden) }
                        )
                    }
                    add(MenuAction(stringResource(R.string.show_on_map), Icons.Rounded.Map) { activity.showOnMap(medium.path) })
                    if (!medium.isInTrash && !activity.isStandaloneViewer) {
                        add(MenuAction(stringResource(R.string.slideshow), Icons.Rounded.Slideshow, onSlideshow))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerBottomToolbar(medium: Medium, onInfo: () -> Unit) {
    val activity = LocalGalleryActivity.current

    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (medium.isInTrash) {
            ToolbarIcon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.restore)) {
                activity.restoreFromTrash(listOf(medium.path))
            }
        } else {
            ToolbarIcon(Icons.Rounded.Share, stringResource(R.string.share)) {
                activity.share(listOf(medium.path))
            }
            if (!medium.isVideo) {
                ToolbarIcon(Icons.Rounded.Edit, stringResource(R.string.edit)) {
                    activity.edit(medium.path)
                }
            }
            if (!medium.isUri) {
                FavoriteButton(
                    isFavorite = medium.isFavorite,
                    onToggle = { activity.setFavorite(listOf(medium.path), it) },
                )
            }
        }
        ToolbarIcon(Icons.Rounded.Info, stringResource(R.string.details), onClick = onInfo)
        if (!medium.isUri) {
            ToolbarIcon(
                if (medium.isInTrash) Icons.Rounded.DeleteForever else Icons.Rounded.DeleteOutline,
                stringResource(if (medium.isInTrash) R.string.delete_permanently else R.string.delete),
            ) {
                activity.deleteMedia(listOf(medium))
            }
        }
    }
}

private const val SLIDESHOW_PAGE_MS = 600

/** Long enough for the full image to fade in first. */
private const val NEIGHBOURS_DELAY_MS = 300L

@Composable
private fun ViewerSystemBars(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }

    DisposableEffect(controller) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(hidden) {
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
