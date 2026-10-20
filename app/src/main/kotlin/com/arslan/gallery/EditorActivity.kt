package com.arslan.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.arslan.gallery.data.FileOps
import com.arslan.gallery.data.MediaLibrary
import com.arslan.gallery.data.extension
import com.arslan.gallery.data.hasAllFilesAccess
import com.arslan.gallery.data.uniqueFile
import com.arslan.gallery.ui.dialogs.AppDialog
import com.arslan.gallery.ui.dialogs.DialogHost
import com.arslan.gallery.ui.dialogs.DialogState
import com.arslan.gallery.ui.editor.EditState
import com.arslan.gallery.ui.editor.EditorScreen
import com.arslan.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Photo editor, used for our own photos and for edit requests of other apps. */
class EditorActivity : ComponentActivity() {

    private val dialogs = DialogState()
    private var state by mutableStateOf<EditState?>(null)
    private var isSaving by mutableStateOf(false)

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
                    EditorScreen(
                        state = state,
                        isSaving = isSaving,
                        onClose = ::close,
                        canOverwrite = path?.extension in overwritableFormats,
                        onSave = ::requestSave,
                    )
                    DialogHost(dialogs)
                }
            }
        }

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { load() }
            if (bitmap == null) {
                Toast.makeText(this@EditorActivity, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                state = EditState(bitmap)
            }
        }
    }

    private fun close() {
        if (state?.hasChanges != true) {
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
        isSaving = true
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    write(edits, overwrite)
                } catch (e: Exception) {
                    null
                }
            }
            if (saved == null) {
                isSaving = false
                Toast.makeText(this@EditorActivity, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            FileOps(this@EditorActivity).scan(listOf(saved.absolutePath))
            Toast.makeText(this@EditorActivity, R.string.saved, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().setData(Uri.fromFile(saved)))
            finish()
        }
    }

    private fun write(edits: EditState, overwrite: Boolean): File {
        val original = path
        val ext = original?.extension?.takeIf { it in overwritableFormats } ?: "jpg"
        val target = if (overwrite && original != null) {
            File(original)
        } else {
            val name = (original?.let { File(it).nameWithoutExtension } ?: "IMG_${System.currentTimeMillis()}") + "_edited.$ext"
            @Suppress("DEPRECATION")
            val folder = original?.let { File(it).parentFile }?.takeIf { it.canWrite() }
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Edited").apply { mkdirs() }
            uniqueFile(File(folder, name))
        }

        // Read the metadata before a possible overwrite replaces it.
        val exif = readExif()
        val result = edits.render()
        val format = when (ext) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }

        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.outputStream().use { result.compress(format, JPEG_QUALITY, it) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        if (exif != null && format == Bitmap.CompressFormat.JPEG) {
            try {
                ExifInterface(target.absolutePath).apply {
                    copiedExifTags.forEach { tag -> exif.getAttribute(tag)?.let { setAttribute(tag, it) } }
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    saveAttributes()
                }
            } catch (ignored: Exception) {
            }
        }
        return target
    }

    private fun readExif(): ExifInterface? = try {
        path?.let { ExifInterface(it) } ?: uri?.let { contentResolver.openInputStream(it)?.use { stream -> ExifInterface(stream) } }
    } catch (e: Exception) {
        null
    }

    /** Decodes the image upright, at most [MAX_SIZE] px on its longest side to stay within memory. */
    private fun load(): Bitmap? {
        val open = { path?.let { File(it).inputStream() } ?: uri?.let { contentResolver.openInputStream(it) } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val scale = MAX_SIZE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        readExif()?.let { exif ->
            if (exif.isFlipped) matrix.postScale(-1f, 1f)
            matrix.postRotate(exif.rotationDegrees.toFloat())
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val MAX_SIZE = 4096
        private const val JPEG_QUALITY = 95
        private val overwritableFormats = setOf("jpg", "jpeg", "png", "webp")
        private val copiedExifTags = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
    }
}
