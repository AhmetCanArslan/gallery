package com.arslan.gallery.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arslan.gallery.R
import com.arslan.gallery.data.ALL_MEDIA
import com.arslan.gallery.data.Album
import com.arslan.gallery.data.EXTERNAL
import com.arslan.gallery.data.FAVORITES
import com.arslan.gallery.data.FileOps
import com.arslan.gallery.data.GROUP_FOLDER
import com.arslan.gallery.data.GROUP_NONE
import com.arslan.gallery.data.GridItem
import com.arslan.gallery.data.MediaLibrary
import com.arslan.gallery.data.Medium
import com.arslan.gallery.data.TRASH
import com.arslan.gallery.data.TYPE_GIF
import com.arslan.gallery.data.TYPE_IMAGE
import com.arslan.gallery.data.TYPE_RAW
import com.arslan.gallery.data.TYPE_VIDEO
import com.arslan.gallery.data.Trash
import com.arslan.gallery.data.buildAlbums
import com.arslan.gallery.data.groupMedia
import com.arslan.gallery.data.prefs
import com.arslan.gallery.data.sortMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@Immutable
data class MediaUiState(
    val items: List<GridItem> = emptyList(),
    val media: List<Medium> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

/** Everything on disk, every list the UI shows is derived from it. */
private data class Library(
    val media: List<Medium> = emptyList(),
    val trash: List<Medium> = emptyList(),
    val external: List<Medium> = emptyList(),
    val isLoaded: Boolean = false,
    /** Bumped when settings that only affect how lists are arranged change. */
    val version: Int = 0,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = app.prefs
    private val source = MediaLibrary(app)
    val ops = FileOps(app)

    private val library = MutableStateFlow(Library())
    private val refreshing = MutableStateFlow(false)
    private var loadJob: Job? = null
    private var observerJob: Job? = null
    private val mediaFlows = HashMap<String, StateFlow<MediaUiState>>()

    /** Set when another app asked us to pick media, hides items of types it can't accept. */
    var mediaTypeFilter: ((Medium) -> Boolean)? = null

    val albums: StateFlow<AlbumsUiState> = combine(library, refreshing) { lib, isRefreshing ->
        AlbumsUiState(
            albums = buildAlbums(
                media = lib.media.filter(shownFilter()),
                sorting = prefs.albumSorting,
                pinned = prefs.pinnedFolders,
                favoritesName = context.getString(R.string.favorites),
                storageName = context.getString(R.string.internal_storage),
                checkHidden = prefs.showHidden,
                covers = prefs.albumCovers(),
            ),
            isLoading = !lib.isLoaded,
            isRefreshing = isRefreshing,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, AlbumsUiState())

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // Bursts of changes (a camera saving, a copy of many files) end up in a single reload.
            observerJob?.cancel()
            observerJob = viewModelScope.launch {
                delay(OBSERVER_DEBOUNCE_MS)
                refresh()
            }
        }
    }

    init {
        val resolver = app.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch(Dispatchers.IO) { Trash.purgeExpired(app) }
    }

    fun refresh(showIndicator: Boolean = false) {
        if (showIndicator) refreshing.value = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch { reload() }
    }

    /** Loads everything from disk and returns once the lists are up to date. */
    suspend fun reload() {
        withContext(Dispatchers.IO) {
            val media = source.loadAll()
            val trash = Trash.list(context)
            library.update { it.copy(media = media, trash = trash, isLoaded = true) }
        }
        refreshing.value = false
    }

    /** Whether [path] is shown in its album. */
    fun isListed(path: String): Boolean {
        val isShown = shownFilter()
        return library.value.media.any { it.path == path && isShown(it) }
    }

    /** Re-arranges every list after sorting, grouping or filter settings changed. */
    fun onArrangementChanged() {
        library.update { it.copy(version = it.version + 1) }
    }

    fun media(path: String): StateFlow<MediaUiState> = synchronized(mediaFlows) {
        mediaFlows.getOrPut(path) {
            combine(library, refreshing) { lib, isRefreshing -> buildMediaState(path, lib, isRefreshing) }
                .flowOn(Dispatchers.Default)
                // Computed right away, so opening an album never flashes a loading state.
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildMediaState(path, library.value, refreshing.value))
        }
    }

    private fun buildMediaState(path: String, lib: Library, isRefreshing: Boolean): MediaUiState {
        val isShown = shownFilter()
        val media = when (path) {
            ALL_MEDIA -> lib.media.filter(isShown)
            FAVORITES -> lib.media.filter { it.isFavorite && isShown(it) }
            TRASH -> return MediaUiState(lib.trash, lib.trash, isLoading = !lib.isLoaded, isRefreshing = isRefreshing)
            EXTERNAL -> return MediaUiState(lib.external, lib.external, isLoading = false)
            else -> lib.media.filter { it.parentPath == path && isShown(it) }
        }

        val sorting = prefs.mediaSorting
        val sorted = sortMedia(media, sorting)
        val grouping = prefs.grouping.let { if (it == GROUP_FOLDER && path != ALL_MEDIA && path != FAVORITES) GROUP_NONE else it }
        val items = groupMedia(
            media = sorted,
            grouping = grouping,
            sorting = sorting,
            typeLabel = ::typeLabel,
            todayLabel = context.getString(R.string.today),
            yesterdayLabel = context.getString(R.string.yesterday),
        )
        return MediaUiState(
            items = items,
            media = if (grouping == GROUP_NONE) sorted else items.filterIsInstance<Medium>(),
            isLoading = !lib.isLoaded,
            isRefreshing = isRefreshing,
        )
    }

    private fun shownFilter(): (Medium) -> Boolean {
        val types = prefs.filter
        val pickFilter = mediaTypeFilter
        return { medium -> types and medium.type != 0 && pickFilter?.invoke(medium) != false }
    }

    private fun typeLabel(type: Int) = context.getString(
        when (type) {
            TYPE_VIDEO -> R.string.videos
            TYPE_GIF -> R.string.gifs
            TYPE_RAW -> R.string.raw_images
            TYPE_IMAGE -> R.string.images
            else -> R.string.images
        }
    )

    /** Shows a single file that isn't part of the library, see [EXTERNAL]. */
    fun setExternal(medium: Medium) {
        library.update { it.copy(external = listOf(medium)) }
    }

    /** Optimistically drops [paths] from every list, e.g. right after deleting or moving. */
    fun removeMedia(paths: Collection<String>) {
        val set = paths.toHashSet()
        library.update { lib ->
            lib.copy(
                media = lib.media.filterNot { it.path in set },
                trash = lib.trash.filterNot { it.path in set },
                external = lib.external.filterNot { it.path in set },
            )
        }
    }

    fun setFavorite(paths: Collection<String>, isFavorite: Boolean) {
        val set = paths.toHashSet()
        val favorites = prefs.favorites.toMutableSet()
        if (isFavorite) favorites.addAll(set) else favorites.removeAll(set)
        prefs.favorites = favorites
        library.update { lib ->
            lib.copy(
                media = lib.media.map { if (it.path in set) it.copy(isFavorite = isFavorite) else it },
                external = lib.external.map { if (it.path in set) it.copy(isFavorite = isFavorite) else it },
            )
        }
    }

    fun updateAlbumColumns(delta: Int): Int {
        val count = (prefs.albumColumns + delta).coerceIn(MIN_ALBUM_COLUMNS, MAX_ALBUM_COLUMNS)
        prefs.albumColumns = count
        return count
    }

    fun updateMediaColumns(delta: Int): Int {
        val count = (prefs.mediaColumns + delta).coerceIn(MIN_MEDIA_COLUMNS, MAX_MEDIA_COLUMNS)
        prefs.mediaColumns = count
        return count
    }

    fun hiddenFolders(): List<String> = source.hiddenFolders()

    fun resolvePath(uri: Uri, includePending: Boolean = false) = source.resolvePath(uri, includePending)

    override fun onCleared() {
        context.contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }

    companion object {
        const val MIN_ALBUM_COLUMNS = 2
        const val MAX_ALBUM_COLUMNS = 5
        const val MIN_MEDIA_COLUMNS = 2
        const val MAX_MEDIA_COLUMNS = 8
        private const val OBSERVER_DEBOUNCE_MS = 600L
    }
}
