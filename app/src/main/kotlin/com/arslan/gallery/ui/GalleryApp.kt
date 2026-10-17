package com.arslan.gallery.ui

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.arslan.gallery.MainActivity
import com.arslan.gallery.R
import com.arslan.gallery.data.EXTERNAL
import com.arslan.gallery.ui.components.EmptyState
import com.arslan.gallery.ui.dialogs.DialogHost
import com.arslan.gallery.ui.screens.AlbumScreen
import com.arslan.gallery.ui.screens.HomeScreen
import com.arslan.gallery.ui.settings.ManageFoldersScreen
import com.arslan.gallery.ui.settings.SettingsScreen
import com.arslan.gallery.ui.theme.AlbumScreenExit
import com.arslan.gallery.ui.theme.EmphasizedEasing
import com.arslan.gallery.ui.viewer.FlightCorners
import com.arslan.gallery.ui.viewer.PhotoFlight
import com.arslan.gallery.ui.viewer.PhotoFrame
import com.arslan.gallery.ui.viewer.flightAnchor
import com.arslan.gallery.ui.viewer.ViewerOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Destination {
    val depth: Int

    data object Home : Destination {
        override val depth = 0
    }

    data class Album(val path: String, val name: String) : Destination {
        override val depth = 2
    }

    data object Settings : Destination {
        override val depth = 1
    }

    data class ManageFolders(val hidden: Boolean) : Destination {
        override val depth = 2
    }
}

private val BackStackSaver = listSaver<MutableList<Destination>, String>(
    save = { stack ->
        stack.map {
            when (it) {
                Destination.Home -> "home"
                is Destination.Album -> "album\u0000${it.path}\u0000${it.name}"
                Destination.Settings -> "settings"
                is Destination.ManageFolders -> "folders\u0000${it.hidden}"
            }
        }
    },
    restore = { saved ->
        saved.mapTo(mutableStateListOf()) {
            val parts = it.split('\u0000')
            when (parts.first()) {
                "album" -> Destination.Album(parts[1], parts[2])
                "settings" -> Destination.Settings
                "folders" -> Destination.ManageFolders(parts[1].toBoolean())
                else -> Destination.Home
            }
        }
    }
)

/**
 * State of the full screen viewer. [sourcePath] identifies the media list (album path, or "" for all
 * photos) and [currentPath] the item on screen, which also tells the grid which thumbnail to hide
 * while the photo flies between them.
 */
@Stable
class ViewerController {
    var isOpen by mutableStateOf(false)
        private set
    var sourcePath by mutableStateOf("")
        private set
    var currentPath by mutableStateOf("")

    /** Whether the viewer was opened as a slideshow. */
    var startSlideshow by mutableStateOf(false)
        private set

    /** Bumped on every opening, each one gets a fresh viewer. */
    var session by mutableIntStateOf(0)
        private set

    /** Where the photo starts from when the viewer opens, null to fade it in. */
    var openFrom: PhotoFrame? = null
        private set

    /** Opacity of the backdrop the viewer opens with, left over from a photo turned around mid-air. */
    var openScrim = 0f
        private set

    /** Photos flying back into the grid after the viewer closed. */
    val flights = mutableStateListOf<PhotoFlight>()

    /** Aspect ratio of each photo, as soon as one of its thumbnails has loaded. */
    val aspects = mutableStateMapOf<String, Float>()

    /** Grid thumbnails on screen, see [flightAnchor]. */
    val anchors = HashMap<String, LayoutCoordinates>()

    val corners = FlightCorners()

    fun open(source: String, path: String, slideshow: Boolean = false) {
        // Still flying back: catch it mid-air instead of starting over from the grid.
        val flight = flights.firstOrNull { it.source == source && it.medium.path == path }
        if (flight != null) flights.remove(flight)
        openFrom = flight?.frame(this) ?: thumbnailFrame(source, path)
        openScrim = flight?.scrim() ?: 0f
        sourcePath = source
        currentPath = path
        startSlideshow = slideshow
        session++
        isOpen = true
    }

    /** Closes right away, the photo flies back into the grid on its own with [flight]. */
    fun close(flight: PhotoFlight? = null) {
        if (flight != null) flights += flight
        isOpen = false
    }

    fun isShowing(source: String, path: String) = isOpen && sourcePath == source && currentPath == path

    /** Whether the thumbnail is hidden in the grid because the photo is in the viewer or on its way back. */
    fun hides(source: String, path: String) = isShowing(source, path) || flights.any { it.source == source && it.medium.path == path }

    /** Whether the viewer, or a photo still on its way back, covers most of the screen. */
    val isOnScreen get() = isOpen || flights.any { it.isCovering }

    /** The grid thumbnail of a photo, if it's on screen. */
    fun thumbnailFrame(source: String, path: String): PhotoFrame? {
        val coordinates = anchors[anchorKey(source, path)]?.takeIf { it.isAttached } ?: return null
        val bounds = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        return if (bounds.isEmpty) null else PhotoFrame.crop(bounds, aspects[path])
    }

    fun setAspect(path: String, size: Size) {
        if (!(size.width > 0f && size.height > 0f)) return
        val aspect = size.width / size.height
        if (aspects[path] != aspect) aspects[path] = aspect
    }

    companion object {
        fun anchorKey(source: String, path: String) = "$source\u0000$path"
    }
}

@Stable
class Navigator(private val backStack: MutableList<Destination>) {
    val current get() = backStack.last()

    fun navigate(destination: Destination) {
        backStack.add(destination)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

val LocalGalleryActivity = staticCompositionLocalOf<MainActivity> { error("No MainActivity") }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> { error("No SharedTransitionScope") }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope> { error("No AnimatedVisibilityScope") }
val LocalViewer = staticCompositionLocalOf<ViewerController> { error("No ViewerController") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }

/** Scope that outlives individual screens and toolbars, e.g. for work started from a closing sheet. */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope> { error("No app CoroutineScope") }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel) {
    val activity = LocalGalleryActivity.current
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Destination>(Destination.Home) }
    val navigator = remember(backStack) { Navigator(backStack) }
    val viewer = remember { ViewerController() }
    val scope = rememberCoroutineScope()

    val current = backStack.last()
    val seekableState = remember { SeekableTransitionState<Destination>(current) }
    LaunchedEffect(current) {
        if (seekableState.currentState != current || seekableState.targetState != current) {
            seekableState.animateTo(current)
        }
    }

    // Predictive back: the album shrinks back into its card while the user drags.
    PredictiveBackHandler(enabled = backStack.size > 1 && !viewer.isOpen) { progress ->
        val previous = backStack[backStack.size - 2]
        try {
            progress.collect { event ->
                seekableState.seekTo(event.progress, targetState = previous)
            }
            backStack.removeAt(backStack.lastIndex)
        } catch (e: CancellationException) {
            scope.launch { seekableState.animateTo(current) }
            throw e
        }
    }

    val transition = rememberTransition(seekableState, label = "navigation")
    val isStandalone = activity.isStandaloneViewer
    if (isStandalone) {
        StandaloneViewerEffect(activity, viewModel, viewer)
    }

    // Keep the system bar icons readable on the current theme.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isStandalone
    val view = LocalView.current
    // The viewer is always dark, light icons stay until its photo has mostly flown back into the grid.
    val lightBars = !darkTheme && !viewer.isOnScreen
    LifecycleResumeEffect(lightBars) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        onPauseOrDispose { }
    }

    Surface(color = if (isStandalone) Color.Black else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalViewer provides viewer,
                LocalNavigator provides navigator,
                LocalAppScope provides scope,
            ) {
                when {
                    isStandalone -> ProcessingBackdrop(isProcessing = activity.isProcessingFile, isViewerOpen = viewer.isOpen)
                    !activity.hasPermission && activity.permissionAsked -> EmptyState(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = stringResource(R.string.permission_title),
                        message = stringResource(R.string.permission_message),
                        actionLabel = stringResource(R.string.grant_access),
                        onAction = activity::requestPermission,
                    )

                    else -> transition.AnimatedContent(
                        transitionSpec = { navigationTransform() },
                        contentKey = { it },
                    ) { destination ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            when (destination) {
                                Destination.Home -> HomeScreen(viewModel = viewModel)
                                is Destination.Album -> AlbumScreen(viewModel = viewModel, path = destination.path, name = destination.name)
                                Destination.Settings -> SettingsScreen(viewModel = viewModel)
                                is Destination.ManageFolders -> ManageFoldersScreen(viewModel = viewModel, hidden = destination.hidden)
                            }
                        }
                    }
                }

                ViewerOverlay(viewModel = viewModel)

                Box(Modifier.fillMaxSize()) {
                    SnackbarHost(
                        hostState = activity.snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 88.dp),
                    )
                }

                DialogHost(activity.dialogs)
            }
        }
    }
}

/** Opens the file another app handed over and closes the activity together with the viewer. */
@Composable
private fun StandaloneViewerEffect(activity: MainActivity, viewModel: GalleryViewModel, viewer: ViewerController) {
    var wasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(activity.hasPermission, activity.permissionAsked) {
        if (!activity.hasPermission && !activity.permissionAsked) return@LaunchedEffect
        val medium = activity.resolveIncomingFile()
        if (medium == null) {
            activity.toast(R.string.unknown_error)
            activity.finish()
            return@LaunchedEffect
        }

        // Show files of the library within their album, so the user can swipe through it. MediaStore
        // can lag a moment behind a camera, so retry a few times before showing the file alone.
        if (!medium.isUri && activity.hasPermission) {
            repeat(LOAD_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(LOAD_RETRY_DELAY_MS)
                viewModel.reload()
                if (viewModel.isListed(medium.path)) {
                    viewer.open(medium.parentPath, medium.path)
                    wasOpened = true
                    return@LaunchedEffect
                }
            }
        }

        viewModel.setExternal(medium)
        viewer.open(EXTERNAL, medium.path)
        wasOpened = true
    }
    LaunchedEffect(viewer.isOpen) {
        if (wasOpened && !viewer.isOpen) activity.finish()
    }
}

/**
 * Black backdrop shown while the file is resolved. Progress only appears when waiting gets
 * noticeable, e.g. while the camera still processes the photo, so quick opens don't flash a spinner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingBackdrop(isProcessing: Boolean, isViewerOpen: Boolean) {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = showProgress && !isViewerOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator(modifier = Modifier.size(64.dp))
                if (isProcessing) {
                    Text(
                        text = stringResource(R.string.processing_photo),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

private const val LOAD_ATTEMPTS = 5
private const val LOAD_RETRY_DELAY_MS = 400L
private const val PROGRESS_DELAY_MS = 400L

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun AnimatedContentTransitionScope<Destination>.navigationTransform(): ContentTransform {
    // Albums open with a container transform (shared bounds), so the screens themselves only fade.
    if (targetState is Destination.Album || initialState is Destination.Album) {
        // A closing album stays until it has shrunk into its card, see [AlbumScreenExit].
        val exit = if (initialState is Destination.Album) AlbumScreenExit else fadeOut(tween(260))
        return (fadeIn(tween(220, delayMillis = 60)) togetherWith exit)
            .using(SizeTransform(clip = false))
    }

    val forward = targetState.depth >= initialState.depth
    return if (forward) {
        (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(380, easing = EmphasizedEasing), initialScale = 0.94f)) togetherWith
            (fadeOut(tween(160)) + scaleOut(tween(380, easing = EmphasizedEasing), targetScale = 1.04f))
    } else {
        (fadeIn(tween(260)) + scaleIn(tween(380, easing = EmphasizedEasing), initialScale = 1.04f)) togetherWith
            (fadeOut(tween(200)) + scaleOut(tween(380, easing = EmphasizedEasing), targetScale = 0.92f))
    }.using(SizeTransform(clip = false))
}
