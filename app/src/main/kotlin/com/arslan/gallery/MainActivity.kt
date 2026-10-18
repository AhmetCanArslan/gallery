package com.arslan.gallery

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.arslan.gallery.data.Album
import com.arslan.gallery.data.FAVORITES
import com.arslan.gallery.data.MediaLibrary
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.TYPE_IMAGE
import com.arslan.gallery.data.TYPE_VIDEO
import com.arslan.gallery.data.filename
import com.arslan.gallery.data.hasAllFilesAccess
import com.arslan.gallery.data.mediaTypeOf
import com.arslan.gallery.data.mimeType
import com.arslan.gallery.data.prefs
import com.arslan.gallery.ui.GalleryApp
import com.arslan.gallery.ui.GalleryViewModel
import com.arslan.gallery.ui.LocalGalleryActivity
import com.arslan.gallery.ui.dialogs.AppDialog
import com.arslan.gallery.ui.dialogs.DialogState
import com.arslan.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class PickRequest(val allowImages: Boolean, val allowVideos: Boolean, val allowMultiple: Boolean)

/**
 * The gallery. A single activity whose whole UI is written in Compose, this class runs the file
 * operations the UI asks for.
 */
open class MainActivity : ComponentActivity() {

    val viewModel: GalleryViewModel by viewModels()
    val dialogs = DialogState()
    val snackbar = SnackbarHostState()
    private val ops get() = viewModel.ops

    var hasPermission by mutableStateOf(false)
        private set

    /** Whether the permission request was answered at least once, so the UI can explain a denial. */
    var permissionAsked by mutableStateOf(false)
        private set

    /** Set when another app asked us to show a single file, only the viewer is shown then. */
    open val isStandaloneViewer = false

    /** True while a photo handed over by the camera is still being processed. */
    var isProcessingFile by mutableStateOf(false)
        private set

    /** Set when another app launched us to pick photos or videos, tapping an item then returns it. */
    var pickRequest: PickRequest? = null
        private set

    private var wasResumedOnce = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionAsked = true
        onPermissionChecked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        pickRequest = parsePickRequest(intent)?.also { request ->
            viewModel.mediaTypeFilter = { medium -> if (medium.isVideo) request.allowVideos else request.allowImages }
        }

        setContent {
            CompositionLocalProvider(LocalGalleryActivity provides this) {
                GalleryTheme(dark = if (isStandaloneViewer) true else null) {
                    GalleryApp(viewModel = viewModel)
                }
            }
        }

        if (isPermissionGranted()) {
            onPermissionChecked()
        } else {
            requestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasResumedOnce) {
            if (!hasPermission && isPermissionGranted()) {
                onPermissionChecked()
            } else if (hasPermission && !isStandaloneViewer) {
                viewModel.refresh()
            }
        }
        wasResumedOnce = true
    }

    fun requestPermission() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )

            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    private fun isPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) return true
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionChecked() {
        hasPermission = isPermissionGranted()
        if (hasPermission && !isStandaloneViewer) {
            viewModel.refresh()
        }
    }

    private fun parsePickRequest(intent: Intent?): PickRequest? {
        if (intent?.action != Intent.ACTION_PICK && intent?.action != Intent.ACTION_GET_CONTENT) return null

        val types = listOfNotNull(intent.type) + intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty()
        val data = intent.data
        val images = types.any { it.startsWith("image/") || it == MediaStore.Images.Media.CONTENT_TYPE } ||
            data == MediaStore.Images.Media.EXTERNAL_CONTENT_URI || data == MediaStore.Images.Media.INTERNAL_CONTENT_URI
        val videos = types.any { it.startsWith("video/") || it == MediaStore.Video.Media.CONTENT_TYPE } ||
            data == MediaStore.Video.Media.EXTERNAL_CONTENT_URI || data == MediaStore.Video.Media.INTERNAL_CONTENT_URI
        val anything = !images && !videos
        return PickRequest(
            allowImages = images || anything,
            allowVideos = videos || anything,
            allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
        )
    }

    /** Hands the picked files back to the app that asked for them. */
    fun deliverPicked(paths: List<String>) {
        if (paths.isEmpty()) return

        val output = intent.extras?.let { BundleCompat.getParcelable(it, MediaStore.EXTRA_OUTPUT, Uri::class.java) }
        if (output != null && paths.size == 1) {
            try {
                contentResolver.openOutputStream(output)?.use { out ->
                    File(paths.first()).inputStream().use { it.copyTo(out) }
                }
                setResult(RESULT_OK)
                finish()
                return
            } catch (e: Exception) {
                toast(R.string.unknown_error)
            }
        }

        val uris = paths.map(::shareUri)
        val result = Intent().apply {
            setDataAndTypeAndNormalize(uris.first(), paths.first().mimeType())
            clipData = ClipData("Attachment", arrayOf("image/*", "video/*"), ClipData.Item(uris.first())).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    // region Opening files of other apps

    /**
     * Resolves the file another app handed over. Cameras pass photos that are still being processed as
     * pending MediaStore items, so this waits until the final file is published.
     */
    suspend fun resolveIncomingFile(): Medium? {
        val uri = intent.data ?: return null
        val isMediaStoreUri = uri.authority == MediaStore.AUTHORITY

        val path = withTimeoutOrNull(PROCESSING_TIMEOUT_MS) {
            try {
                var resolved = withContext(Dispatchers.IO) { viewModel.resolvePath(uri, includePending = true) }
                while (resolved?.isPending == true || (resolved == null && isMediaStoreUri)) {
                    isProcessingFile = true
                    delay(POLL_INTERVAL_MS)
                    resolved = withContext(Dispatchers.IO) { viewModel.resolvePath(uri, includePending = true) }
                }
                resolved?.path
            } finally {
                isProcessingFile = false
            }
        }

        return withContext(Dispatchers.IO) {
            path?.let { MediaLibrary.mediumFromFile(it, prefs.favorites) } ?: mediumFromUri(uri)
        }
    }

    private fun mediumFromUri(uri: Uri): Medium? {
        var name = uri.lastPathSegment?.filename ?: return null
        var size = 0L
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { displayName -> name = displayName }
                    size = it.getLong(1)
                }
            }
        } catch (ignored: Exception) {
        }

        val mime = intent.type ?: contentResolver.getType(uri)
        val type = mediaTypeOf(name, mime).takeIf { it != 0 } ?: if (mime?.startsWith("video") == true) TYPE_VIDEO else TYPE_IMAGE
        return Medium(
            path = uri.toString(),
            name = name,
            parentPath = "",
            modified = 0L,
            taken = 0L,
            size = size,
            type = type,
        )
    }

    // endregion

    // region Media actions

    /** Runs [action] when files may be modified, asks for the needed access otherwise. */
    fun withWriteAccess(action: () -> Unit) {
        if (hasAllFilesAccess()) action() else dialogs.show(AppDialog.AllFilesAccess)
    }

    fun shareUri(path: String): Uri =
        if (path.startsWith("content://")) Uri.parse(path) else FileProvider.getUriForFile(this, "$packageName.provider", File(path))

    fun share(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val uris = ArrayList(paths.map(::shareUri))
        val types = paths.map { it.mimeType() }.distinct()
        val intent = Intent().apply {
            if (uris.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            type = types.singleOrNull() ?: if (types.all { it.startsWith("video") }) "video/*" else if (types.all { it.startsWith("image") }) "image/*" else "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(intent, R.string.share)
    }

    /** Opens Tailscale's Taildrop device picker directly, skipping the system share sheet. */
    fun sendViaTailscale(path: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(TAILSCALE_PACKAGE)
            type = path.mimeType()
            putExtra(Intent.EXTRA_STREAM, shareUri(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.tailscale_not_installed)
        }
    }

    fun copyToClipboard(path: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, path.filename, shareUri(path)))
        // Android 13+ shows its own confirmation for clipboard changes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(R.string.copied_to_clipboard)
    }

    fun setFavorite(paths: Collection<String>, isFavorite: Boolean) = viewModel.setFavorite(paths, isFavorite)

    /**
     * Deletes [media], asking first unless confirmations are off. Items go into the trash unless they
     * already are in it or the user picks a permanent delete; [onDeleted] receives the removed paths.
     */
    fun deleteMedia(media: Collection<Medium>, onDeleted: (List<String>) -> Unit = {}) {
        val items = media.filter { !it.isUri }
        if (items.isEmpty()) return
        val canUseTrash = prefs.useTrash && items.none { it.isInTrash }

        withWriteAccess {
            if (prefs.confirmDelete) {
                dialogs.show(AppDialog.DeleteMedia(items, canUseTrash) { toTrash -> performDelete(items, toTrash, onDeleted) })
            } else {
                performDelete(items, canUseTrash, onDeleted)
            }
        }
    }

    private fun performDelete(items: List<Medium>, toTrash: Boolean, onDeleted: (List<String>) -> Unit) {
        val paths = items.map { it.path }
        viewModel.removeMedia(paths)
        onDeleted(paths)
        lifecycleScope.launch {
            if (toTrash) {
                val trashed = ops.moveToTrash(items)
                viewModel.refresh()
                if (trashed.isEmpty()) {
                    toast(R.string.unknown_error)
                    return@launch
                }
                val result = snackbar.showSnackbar(
                    message = resources.getQuantityString(R.plurals.moved_n_to_trash, trashed.size, trashed.size),
                    actionLabel = getString(R.string.undo),
                    duration = SnackbarDuration.Long,
                    withDismissAction = false,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    ops.restore(trashed)
                    viewModel.refresh()
                }
            } else {
                val deleted = ops.delete(paths)
                viewModel.refresh()
                if (deleted.size < paths.size) toast(R.string.unknown_error)
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(resources.getQuantityString(R.plurals.deleted_n_items, deleted.size, deleted.size))
            }
        }
    }

    fun restoreFromTrash(trashPaths: List<String>) {
        if (trashPaths.isEmpty()) return
        withWriteAccess {
            viewModel.removeMedia(trashPaths)
            lifecycleScope.launch {
                val restored = ops.restore(trashPaths)
                viewModel.refresh()
                snackbar.showSnackbar(resources.getQuantityString(R.plurals.restored_n_items, restored.size, restored.size))
            }
        }
    }

    fun emptyTrash() = withWriteAccess {
        dialogs.show(
            AppDialog.Confirm(
                icon = Icons.Rounded.DeleteForever,
                title = getString(R.string.empty_trash_question),
                message = getString(R.string.delete_permanently_hint),
                confirmLabel = getString(R.string.empty_trash),
                destructive = true,
            ) {
                lifecycleScope.launch {
                    ops.emptyTrash()
                    viewModel.refresh()
                }
            }
        )
    }

    fun copyMove(paths: Collection<String>, isCopy: Boolean, onDone: () -> Unit = {}) {
        if (paths.isEmpty()) return
        withWriteAccess {
            dialogs.show(
                AppDialog.PickFolder(
                    title = getString(if (isCopy) R.string.copy_to else R.string.move_to),
                    currentFolders = if (isCopy) emptySet() else paths.map { File(it).parent.orEmpty() }.toSet(),
                ) { destination ->
                    if (!isCopy) viewModel.removeMedia(paths)
                    onDone()
                    lifecycleScope.launch {
                        val created = ops.transfer(paths, destination, move = !isCopy)
                        viewModel.refresh()
                        if (created.size < paths.size) toast(R.string.unknown_error)
                        val message = if (isCopy) R.plurals.copied_n_items else R.plurals.moved_n_items
                        snackbar.showSnackbar(resources.getQuantityString(message, created.size, created.size, destination.filename))
                    }
                }
            )
        }
    }

    fun rename(path: String, onRenamed: (String) -> Unit = {}) = withWriteAccess {
        dialogs.show(AppDialog.Rename(getString(R.string.rename), path.filename) { name ->
            lifecycleScope.launch {
                val newPath = ops.rename(path, name)
                if (newPath == null) {
                    toast(R.string.rename_failed)
                } else {
                    onRenamed(newPath)
                    viewModel.refresh()
                }
            }
        })
    }

    fun rotate(path: String, degrees: Int) = withWriteAccess {
        lifecycleScope.launch {
            if (!ops.rotate(path, degrees)) toast(R.string.rotate_unsupported)
            viewModel.refresh()
        }
    }

    fun setHidden(path: String, hide: Boolean, onDone: (String) -> Unit = {}) = withWriteAccess {
        lifecycleScope.launch {
            val newPath = ops.setFileHidden(path, hide)
            if (newPath == null) toast(R.string.unknown_error) else onDone(newPath)
            viewModel.refresh()
        }
    }

    fun edit(path: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_PATH, path))
    }

    fun setAs(path: String) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(shareUri(path), path.mimeType())
            putExtra("mimeType", path.mimeType())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(intent, R.string.set_as)
    }

    fun openWith(path: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri(path), path.mimeType())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(intent, R.string.open_with)
    }

    fun showOnMap(path: String) {
        lifecycleScope.launch {
            val latLong = withContext(Dispatchers.IO) {
                try {
                    val exif = if (path.startsWith("content://")) {
                        contentResolver.openInputStream(Uri.parse(path))?.use { ExifInterface(it) }
                    } else {
                        ExifInterface(path)
                    }
                    exif?.latLong
                } catch (e: Exception) {
                    null
                }
            }
            if (latLong == null) {
                toast(R.string.no_location)
                return@launch
            }
            val (lat, lon) = latLong[0] to latLong[1]
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                toast(R.string.no_app_found)
            }
        }
    }

    // endregion

    // region Album actions

    fun pinAlbums(paths: Collection<String>, pin: Boolean) {
        prefs.pinnedFolders = if (pin) prefs.pinnedFolders + paths else prefs.pinnedFolders - paths.toSet()
        viewModel.onArrangementChanged()
    }

    fun hideAlbums(paths: Collection<String>, hide: Boolean) = withWriteAccess {
        lifecycleScope.launch {
            paths.filter { it != FAVORITES }.forEach { ops.setFolderHidden(it, hide) }
            viewModel.refresh()
        }
    }

    fun excludeAlbums(paths: Collection<String>) {
        prefs.excludedFolders = prefs.excludedFolders + paths.filter { it != FAVORITES }
        viewModel.refresh()
    }

    fun renameAlbum(album: Album) = withWriteAccess {
        dialogs.show(AppDialog.Rename(getString(R.string.rename_album), album.name) { name ->
            lifecycleScope.launch {
                if (ops.rename(album.path, name) == null) toast(R.string.rename_failed)
                viewModel.refresh()
            }
        })
    }

    private fun albumMedia(albums: Collection<String>): List<Medium> {
        val all = viewModel.media("").value.media
        val set = albums.toSet()
        return all.filter { it.parentPath in set || (FAVORITES in set && it.isFavorite) }
    }

    fun copyMoveAlbums(paths: Collection<String>, isCopy: Boolean) = copyMove(albumMedia(paths).map { it.path }, isCopy)

    fun deleteAlbums(albums: List<Album>) {
        val media = albumMedia(albums.map { it.path })
        if (media.isEmpty()) return
        withWriteAccess {
            dialogs.show(AppDialog.DeleteAlbums(albums, prefs.useTrash) { toTrash -> performDelete(media, toTrash) {} })
        }
    }

    // endregion

    private fun startChooser(intent: Intent, title: Int) {
        try {
            startActivity(Intent.createChooser(intent, getString(title)))
        } catch (e: Exception) {
            toast(R.string.no_app_found)
        }
    }

    fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val PROCESSING_TIMEOUT_MS = 30_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}

/** Opens a single file another app handed over, like a photo just taken by the camera. */
class ViewerActivity : MainActivity() {
    override val isStandaloneViewer = true
}
