package com.arslan.gallery.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.arslan.gallery.R
import com.arslan.gallery.data.formatDuration
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.delay

/** What the video editor knows about the video before any edit. */
class VideoSource(
    val uri: Uri,
    val duration: Long,
    /** Size as displayed, with the stored rotation applied. */
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val frames: List<Bitmap>,
)

@Stable
class VideoEditState(val source: VideoSource) {
    var start by mutableLongStateOf(0L)
    var end by mutableLongStateOf(source.duration)
    /** Counter-clockwise, in multiples of 90. Not wrapped, so the preview keeps turning the same way. */
    var rotation by mutableIntStateOf(0)
    var muted by mutableStateOf(false)

    val isTrimmed get() = start > 0 || end < source.duration
    val hasChanges get() = isTrimmed || rotation % 360 != 0 || muted
}

private const val MIN_TRIM_MS = 500L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoEditorScreen(
    state: VideoEditState?,
    /** Export progress from 0 to 1, null while not saving. */
    saveProgress: Float?,
    onClose: () -> Unit,
    canOverwrite: Boolean,
    onSave: (overwrite: Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
            }
            Text(
                stringResource(R.string.edit),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            SaveButton(
                enabled = state?.hasChanges == true && saveProgress == null,
                canOverwrite = canOverwrite,
                onSave = onSave,
            )
        }

        if (state == null || saveProgress != null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (saveProgress == null) {
                    LoadingIndicator(Modifier.size(64.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(progress = { saveProgress }, modifier = Modifier.size(64.dp))
                        Text(
                            "${(saveProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        } else {
            VideoEditor(state, Modifier.weight(1f))
        }
    }
}

@AndroidxOptIn(UnstableApi::class)
@Composable
private fun VideoEditor(state: VideoEditState, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(state.source.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(state.source.uri))
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    // Read only while drawing the trim bar, so playing doesn't recompose anything.
    val position = remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    // Keeps playback inside the trimmed range.
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying && player.currentPosition >= state.end) {
                player.pause()
                player.seekTo(state.start)
            }
            position.longValue = player.currentPosition
            delay(if (player.isPlaying) 16 else 100)
        }
    }

    LaunchedEffect(state.muted) { player.volume = if (state.muted) 0f else 1f }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boxWidth = constraints.maxWidth.toFloat()
            val boxHeight = constraints.maxHeight.toFloat()
            val source = state.source
            // A sideways video has to shrink to still fit once turned.
            val sideways = (state.rotation / 90) % 2 != 0
            val fitScale = if (sideways && source.width > 0 && source.height > 0) {
                val upright = min(boxWidth / source.width, boxHeight / source.height)
                val turned = min(boxWidth / source.height, boxHeight / source.width)
                turned / upright
            } else {
                1f
            }
            val rotation by animateFloatAsState(-state.rotation.toFloat(), label = "videoRotation")
            val scale by animateFloatAsState(fitScale, label = "videoScale")

            ContentFrame(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                shutter = {},
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    },
            )

            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isPlaying) 0f else 0.9f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.currentPosition !in state.start until state.end - 100) player.seekTo(state.start)
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!isPlaying) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        stringResource(R.string.play_video),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }

        val frames = remember(state.source) { state.source.frames.map { it.asImageBitmap() } }
        TrimBar(
            state = state,
            frames = frames,
            position = { position.longValue },
            onSeek = { time ->
                player.pause()
                player.seekTo(time)
                position.longValue = time
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            Text((state.start / 1000).toInt().formatDuration(), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(
                ((state.end - state.start) / 1000).toInt().formatDuration(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text((state.end / 1000).toInt().formatDuration(), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            FilledTonalIconButton(onClick = { state.rotation += 90 }) {
                Icon(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.rotate_left))
            }
            if (state.source.hasAudio) {
                FilledIconToggleButton(checked = state.muted, onCheckedChange = { state.muted = it }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.mute_video))
                }
            }
        }
    }
}

private enum class TrimDrag { None, Start, End, Seek }

/** Frame strip with draggable start and end handles; dragging inside the range scrubs. */
@Composable
private fun TrimBar(
    state: VideoEditState,
    frames: List<ImageBitmap>,
    position: () -> Long,
    onSeek: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val handleWidth = with(density) { 14.dp.toPx() }
    val touchSlop = with(density) { 32.dp.toPx() }
    val primary = MaterialTheme.colorScheme.primary
    val duration = state.source.duration.coerceAtLeast(1L)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp),
    ) {
        val width = constraints.maxWidth.toFloat()
        val track = width - 2 * handleWidth
        fun xOf(time: Long) = handleWidth + track * time / duration
        fun timeOf(x: Float) = ((x - handleWidth) / track * duration).toLong().coerceIn(0L, duration)

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = with(density) { handleWidth.toDp() }, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            frames.forEach { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(width, duration) {
                    var mode = TrimDrag.None
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            val toStart = abs(start.x - (xOf(state.start) - handleWidth / 2))
                            val toEnd = abs(start.x - (xOf(state.end) + handleWidth / 2))
                            mode = when {
                                toStart < touchSlop && toStart <= toEnd -> TrimDrag.Start
                                toEnd < touchSlop -> TrimDrag.End
                                start.x in xOf(state.start)..xOf(state.end) -> TrimDrag.Seek
                                else -> TrimDrag.None
                            }
                            if (mode == TrimDrag.Seek) onSeek(timeOf(start.x))
                        },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            when (mode) {
                                TrimDrag.Start -> {
                                    state.start = (state.start + (drag / track * duration).toLong()).coerceIn(0L, state.end - MIN_TRIM_MS)
                                    onSeek(state.start)
                                }
                                TrimDrag.End -> {
                                    state.end = (state.end + (drag / track * duration).toLong()).coerceIn(state.start + MIN_TRIM_MS, duration)
                                    onSeek(state.end)
                                }
                                TrimDrag.Seek -> onSeek(timeOf(change.position.x).coerceIn(state.start, state.end))
                                TrimDrag.None -> Unit
                            }
                        },
                        onDragEnd = { mode = TrimDrag.None },
                    )
                },
        ) {
            val startX = xOf(state.start)
            val endX = xOf(state.end)
            val top = 4.dp.toPx()
            val bottom = size.height - top
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset(handleWidth, top), Size(startX - handleWidth, bottom - top))
            drawRect(dim, Offset(endX, top), Size(size.width - handleWidth - endX, bottom - top))

            val border = 3.dp.toPx()
            drawRect(primary, Offset(startX, border / 2), Size(endX - startX, size.height - border), style = Stroke(border))
            val radius = CornerRadius(6.dp.toPx())
            drawRoundRect(primary, Offset(startX - handleWidth, 0f), Size(handleWidth, size.height), radius)
            drawRoundRect(primary, Offset(endX, 0f), Size(handleWidth, size.height), radius)
            val grip = 2.dp.toPx()
            val gripHeight = size.height / 3
            drawLine(Color.Black, Offset(startX - handleWidth / 2, (size.height - gripHeight) / 2), Offset(startX - handleWidth / 2, (size.height + gripHeight) / 2), grip)
            drawLine(Color.Black, Offset(endX + handleWidth / 2, (size.height - gripHeight) / 2), Offset(endX + handleWidth / 2, (size.height + gripHeight) / 2), grip)

            val playhead = xOf(position().coerceIn(state.start, state.end))
            drawLine(Color.White, Offset(playhead, 0f), Offset(playhead, size.height), 2.dp.toPx())
        }
    }
}
