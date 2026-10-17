package com.arslan.gallery.ui.viewer

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arslan.gallery.R
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.formatSize
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.components.staggeredEntrance
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class MediumDetails(
    val width: Int = 0,
    val height: Int = 0,
    val camera: String? = null,
    val exposure: String? = null,
    val latLong: DoubleArray? = null,
    val fileSize: Long = 0L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumInfoSheet(medium: Medium, onDismiss: () -> Unit) {
    val activity = LocalGalleryActivity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val details by produceState(MediumDetails(), medium.path) {
        value = withContext(Dispatchers.IO) { loadDetails(activity, medium) }
    }

    val timestamp = if (medium.taken > 0) medium.taken else medium.modified
    val date = if (timestamp > 0) DateFormat.getDateInstance(DateFormat.FULL).format(Date(timestamp)) else medium.name
    val time = if (timestamp > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp)) else ""


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(date, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.staggeredEntrance(0, true))
            Text(
                time,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.staggeredEntrance(1, true),
            )
            Spacer(Modifier.height(12.dp))

            var index = 2
            DetailCard(index++, Icons.AutoMirrored.Rounded.InsertDriveFile, stringResource(R.string.file_details)) {
                DetailLine(Icons.Rounded.Image, medium.name)
                if (medium.parentPath.isNotEmpty()) DetailLine(Icons.Rounded.Folder, medium.parentPath)
                val resolution = if (details.width > 0) {
                    val mp = details.width.toLong() * details.height / 1_000_000.0
                    "${details.width} × ${details.height}" + if (mp >= 0.1) "  ·  ${String.format(Locale.getDefault(), "%.1f MP", mp)}" else ""
                } else {
                    null
                }
                val size = details.fileSize.takeIf { it > 0 } ?: medium.size
                DetailLine(Icons.Rounded.Straighten, listOfNotNull(resolution, size.takeIf { it > 0 }?.formatSize()).joinToString("  ·  "))
                if (medium.modified > 0) DetailLine(Icons.Rounded.Schedule, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(medium.modified)))
            }

            if (details.camera != null || details.exposure != null) {
                DetailCard(index++, Icons.Rounded.CameraAlt, stringResource(R.string.camera_details)) {
                    details.camera?.let { DetailLine(Icons.Rounded.Camera, it) }
                    details.exposure?.let { DetailLine(Icons.Rounded.CameraAlt, it) }
                }
            }

            details.latLong?.let { latLong ->
                DetailCard(index, Icons.Rounded.LocationOn, stringResource(R.string.location_details)) {
                    DetailLine(Icons.Rounded.LocationOn, String.format(Locale.US, "%.5f, %.5f", latLong[0], latLong[1]))
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { activity.showOnMap(medium.path) }) {
                        Text(stringResource(R.string.show_on_map))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailCard(
    index: Int,
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, true)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun loadDetails(context: Context, medium: Medium): MediumDetails {
    val details = readDetails(context, medium)
    return if (medium.isUri) details else details.copy(fileSize = File(medium.path).length())
}

private fun readDetails(context: Context, medium: Medium): MediumDetails {
    return try {
        if (medium.isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, medium.uri())
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) MediumDetails(height, width) else MediumDetails(width, height)
            } finally {
                retriever.release()
            }
        } else {
            val open = { context.contentResolver.openInputStream(medium.uri()) }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, options) }
            var width = options.outWidth
            var height = options.outHeight

            val exif = try {
                open()?.use { ExifInterface(it) }
            } catch (e: Exception) {
                null
            }

            if (exif?.rotationDegrees == 90 || exif?.rotationDegrees == 270) {
                width = height.also { height = width }
            }

            val make = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim()
            val model = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim()
            val camera = listOfNotNull(make, model).filter { it.isNotEmpty() }.let { parts ->
                when {
                    parts.isEmpty() -> null
                    model != null && make != null && model.startsWith(make, true) -> model
                    else -> parts.joinToString(" ")
                }
            }

            val exposure = buildList {
                exif?.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { add("ƒ/${trim(it)}") }
                exif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                    add(if (it < 1) "1/${(1 / it).toInt()}s" else "${trim(it)}s")
                }
                exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)?.takeIf { it > 0 }?.let { add("${trim(it)}mm") }
                exif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO $it") }
            }.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")

            val latLong = exif?.latLong
            MediumDetails(width, height, camera, exposure, latLong)
        }
    } catch (e: Exception) {
        MediumDetails()
    }
}

private fun trim(value: Double): String {
    val formatted = String.format(Locale.US, "%.1f", value)
    return formatted.removeSuffix(".0")
}
