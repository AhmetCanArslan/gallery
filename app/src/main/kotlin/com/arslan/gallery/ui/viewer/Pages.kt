package com.arslan.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import com.arslan.gallery.R
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.formatDuration
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.components.fullImageRequest
import com.arslan.gallery.ui.components.rememberHaptics
import com.arslan.gallery.ui.components.thumbnailRequest
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aspect-correct thumbnail, what the photo flies in as. Uses the grid's memory-cached thumbnail, so
 * it is available on the very first frame.
 */
@Composable
private fun Poster(medium: Medium, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewer = LocalViewer.current
    val painter = rememberAsyncImagePainter(remember(medium.signature) { thumbnailRequest(context, medium) })
    val painterState by painter.state.collectAsState()
    val ratio = (painterState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize?.let {
        if (it.width > 0 && it.height > 0) it.width / it.height else null
    }
    LaunchedEffect(ratio) {
        if (ratio != null) viewer.setAspect(medium.path, Size(ratio, 1f))
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize(),
        )
    }
}

/**
 * Zoomable photo. The grid thumbnail is shown right away while the full image decodes below it, the
 * full image then fades in once it's displayed in full quality, so there is a single smooth
 * transition instead of several visible quality jumps.
 */
@Composable
fun ImagePage(
    medium: Medium,
    inTransition: Boolean,
    onZoomChange: (fraction: Float, contentBounds: Rect) -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 6f))
    val imageState = rememberZoomableImageState(zoomableState)
    val zoomFraction = zoomableState.zoomFraction ?: 0f

    LaunchedEffect(zoomableState) {
        snapshotFlow { (zoomableState.zoomFraction ?: 0f) to zoomableState.transformedContentBounds }
            .collect { (fraction, bounds) -> onZoomChange(fraction, bounds) }
    }

    // Reset zoom when the page is about to animate back into the grid.
    LaunchedEffect(inTransition) {
        if (inTransition && zoomFraction > 0f) zoomableState.resetZoom(animationSpec = spring())
    }

    // Latched: zooming in later loads sharper tiles, which must not bring the thumbnail back.
    var isReady by remember(medium.signature) { mutableStateOf(false) }
    LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        if (imageState.isImageDisplayedInFullQuality) isReady = true
    }

    val showFull = isReady && !inTransition
    val fullAlpha by animateFloatAsState(
        targetValue = if (showFull) 1f else 0f,
        animationSpec = if (showFull) tween(FULL_IMAGE_FADE_MS) else snap(),
        label = "fullImageAlpha",
    )

    // Opening, the full image (and its decoder) only joins a few frames in, after the photo and the
    // bars are already on their way.
    val composeFull = rememberAfterFrames(if (inTransition) 3 else 0)

    Box(Modifier.fillMaxSize()) {
        // The thumbnail stays below until the full image is completely opaque, so nothing ever
        // flashes through. It's hidden afterwards to not shine through transparent images.
        Poster(
            medium = medium,
            modifier = Modifier.graphicsLayer { alpha = if (fullAlpha >= 1f) 0f else 1f },
        )

        if (composeFull) {
            ZoomableAsyncImage(
                model = remember(medium.signature) { fullImageRequest(context, medium) },
                contentDescription = medium.name,
                state = imageState,
                contentScale = ContentScale.Fit,
                onClick = { onTap() },
                onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 2.5f),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullAlpha },
            )
        }
    }
}

private const val FULL_IMAGE_FADE_MS = 200

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPage(
    medium: Medium,
    isCurrent: Boolean,
    inTransition: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.prefs }
    var playRequested by remember(medium.path) { mutableStateOf(false) }

    LaunchedEffect(isCurrent, inTransition) {
        if (!isCurrent) {
            playRequested = false
        } else if (!inTransition && prefs.autoplayVideos) {
            playRequested = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome,
            ),
    ) {
        Poster(medium = medium)

        if (playRequested && isCurrent && !inTransition) {
            VideoPlayer(medium = medium, chromeVisible = chromeVisible)
        } else if (!(inTransition && isCurrent && (playRequested || prefs.autoplayVideos))) {
            // Not shown while a video that is about to play flies in, it would only pop up and
            // vanish again the moment the player takes over.
            BigPlayButton(
                modifier = Modifier.align(Alignment.Center),
                onClick = { playRequested = true },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigPlayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }

    Box(
        modifier = modifier
            .size(84.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(MaterialShapes.Cookie7Sided.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.play_video),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoPlayer(medium: Medium, chromeVisible: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { context.prefs }

    val player = remember(medium.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(medium.uri()))
            repeatMode = if (prefs.loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (prefs.muteVideos) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(prefs.muteVideos) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                view.keepScreenOn = playing
            }
        }
        player.addListener(listener)
        onDispose {
            view.keepScreenOn = false
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!seeking) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize()) {
        ContentFrame(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            // No black shutter until the first frame, the poster below shows meanwhile.
            shutter = {},
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = chromeVisible || !isPlaying,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val morph by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                label = "playMorph",
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer { rotationZ = morph * 90f }
                    .clip(if (isPlaying) MaterialShapes.Square.toShape() else MaterialShapes.Cookie7Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                    .clickable {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.pause_video else R.string.play_video),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { rotationZ = -morph * 90f },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.75f)) { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ((if (seeking) (seekValue * duration).toLong() else position) / 1000).toInt().formatDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = if (seeking) seekValue else if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = {
                        seeking = true
                        seekValue = it
                    },
                    onValueChangeFinished = {
                        player.seekTo((seekValue * duration).toLong())
                        position = (seekValue * duration).toLong()
                        seeking = false
                    },
                    colors = SliderDefaults.colors(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text(
                    text = (duration / 1000).toInt().formatDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                }) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.mute_video),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Heart that pops with an overshooting spring and sprays a small ring of particles when liked.
 */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val favoriteColor = Color(0xFFFF5C7A)

    IconButton(onClick = {
        val newValue = !isFavorite
        haptics.toggle(newValue)
        onToggle(newValue)
        scope.launch {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        }
        if (newValue) {
            scope.launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }) {
        Box(contentAlignment = Alignment.Center) {
            if (burst.value < 1f) {
                Canvas(Modifier.size(48.dp)) {
                    val progress = burst.value
                    val radius = size.minDimension / 2f * (0.35f + 0.65f * progress)
                    val alpha = 1f - progress
                    repeat(8) { i ->
                        val angle = Math.toRadians(i * 45.0 - 90.0)
                        drawCircle(
                            color = favoriteColor.copy(alpha = alpha),
                            radius = 3.dp.toPx() * (1f - progress * 0.5f),
                            center = center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()),
                        )
                    }
                }
            }
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                tint = if (isFavorite) favoriteColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            )
        }
    }
}
