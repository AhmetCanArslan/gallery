package com.arslan.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.arslan.gallery.data.FileOps
import com.arslan.gallery.data.MediaLibrary
import com.arslan.gallery.data.extension
import com.arslan.gallery.data.hasAllFilesAccess
import com.arslan.gallery.data.uniqueFile
import com.arslan.gallery.ui.dialogs.AppDialog
import com.arslan.gallery.ui.dialogs.DialogHost
import com.arslan.gallery.ui.dialogs.DialogState
import com.arslan.gallery.ui.editor.VideoEditState
import com.arslan.gallery.ui.editor.VideoEditorScreen
import com.arslan.gallery.ui.editor.VideoSource
import com.arslan.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Trims, rotates and mutes videos, used for our own videos and for edit requests of other apps. */
class VideoEditorActivity : ComponentActivity() {

    private val dialogs = DialogState()
    private var state by mutableStateOf<VideoEditState?>(null)
    /** Export progress from 0 to 1, null while not saving. */
    private var saveProgress by mutableStateOf<Float?>(null)

    /** File being edited, null when another app handed over a uri that can't be resolved to a file. */
    private var path: String? = null
    private var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        uri = intent.data
        path = intent.getStringExtra(EXTRA_PATH) ?: uri?.let { MediaLibrary(this).resolvePath(it)?.path }?.takeIf { File(it).canRead() }

        setContent {
            GalleryTheme(dark = true) {
                Box {
                    VideoEditorScreen(
                        state = state,
                        saveProgress = saveProgress,
                        onClose = ::close,
                        canOverwrite = path?.extension == "mp4",
                        onSave = ::requestSave,
                    )
                    DialogHost(dialogs)
                }
            }
        }

        lifecycleScope.launch {
            val source = withContext(Dispatchers.IO) {
                try {
                    load()
                } catch (e: Exception) {
                    null
                }
            }
            if (source == null) {
                Toast.makeText(this@VideoEditorActivity, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                state = VideoEditState(source)
            }
        }
    }

    private fun close() {
        if (state?.hasChanges != true && saveProgress == null) {
            finish()
            return
        }
        dialogs.show(
            AppDialog.Confirm(
                icon = Icons.Rounded.Close,
                title = getString(R.string.discard_changes_question),
                message = null,
                confirmLabel = getString(R.string.discard),
                destructive = true,
                onConfirm = ::finish,
            )
        )
    }

    private fun requestSave(overwrite: Boolean) {
        if (!hasAllFilesAccess()) {
            dialogs.show(AppDialog.AllFilesAccess)
            return
        }
        save(overwrite)
    }

    private fun save(overwrite: Boolean) {
        val edits = state ?: return
        val original = path
        val target = if (overwrite && original != null) {
            File(original)
        } else {
            val name = (original?.let { File(it).nameWithoutExtension } ?: "VID_${System.currentTimeMillis()}") + "_edited.mp4"
            @Suppress("DEPRECATION")
            val folder = original?.let { File(it).parentFile }?.takeIf { it.canWrite() }
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Edited").apply { mkdirs() }
            uniqueFile(File(folder, name))
        }
        val temp = File(target.parentFile, ".${target.nameWithoutExtension}.tmp.mp4")

        saveProgress = 0f
        lifecycleScope.launch {
            val saved = try {
                export(edits, temp)
                withContext(Dispatchers.IO) {
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }
                true
            } catch (e: Exception) {
                false
            } finally {
                withContext(Dispatchers.IO + NonCancellable) { temp.delete() }
            }
            if (!saved) {
                saveProgress = null
                Toast.makeText(this@VideoEditorActivity, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            FileOps(this@VideoEditorActivity).scan(listOf(target.absolutePath))
            Toast.makeText(this@VideoEditorActivity, R.string.saved, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().setData(Uri.fromFile(target)))
            finish()
        }
    }

    /** Writes the edited video to [output], reporting progress meanwhile. Runs on the main thread, as Transformer wants. */
    @OptIn(UnstableApi::class)
    private suspend fun export(edits: VideoEditState, output: File) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(edits.start)
            .apply { if (edits.end < edits.source.duration) setEndPositionMs(edits.end) }
            .build()
        val item = MediaItem.Builder().setUri(edits.source.uri).setClippingConfiguration(clipping).build()
        val rotation = edits.rotation % 360
        val effects = if (rotation == 0) {
            Effects.EMPTY
        } else {
            Effects(emptyList(), listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees(rotation.toFloat()).build()))
        }
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(edits.muted)
            .setEffects(effects)
            .build()

        var ticker: Job? = null
        try {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(this)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(edited, output.absolutePath)
                // Transformer is bound to the main thread, which is where this activity gets destroyed.
                cont.invokeOnCancellation { transformer.cancel() }

                val progress = ProgressHolder()
                ticker = lifecycleScope.launch {
                    while (true) {
                        if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            saveProgress = progress.progress / 100f
                        }
                        delay(200)
                    }
                }
            }
        } finally {
            ticker?.cancel()
        }
    }

    private fun load(): VideoSource? {
        val source = path?.let { Uri.fromFile(File(it)) } ?: uri ?: return null
        val retriever = MediaMetadataRetriever()
        try {
            if (path != null) retriever.setDataSource(path) else retriever.setDataSource(this, source)
            fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            if (duration <= 0) return null
            val sideways = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
            val width = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"

            val frames = (0 until FRAME_COUNT).mapNotNull { i ->
                val time = duration * 1000 * (2 * i + 1) / (2 * FRAME_COUNT)
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_SIZE, FRAME_SIZE)
                } else {
                    retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val scale = FRAME_SIZE.toFloat() / maxOf(full.width, full.height)
                        Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true)
                    }
                }
                frame
            }
            return VideoSource(
                uri = source,
                duration = duration,
                width = if (sideways) height else width,
                height = if (sideways) width else height,
                hasAudio = hasAudio,
                frames = frames,
            )
        } finally {
            retriever.release()
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val FRAME_COUNT = 8
        private const val FRAME_SIZE = 160
    }
}
