package com.arslan.gallery.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val SORT_NAME = 1
const val SORT_SIZE = 2
const val SORT_MODIFIED = 4
const val SORT_TAKEN = 8
const val SORT_COUNT = 16
const val SORT_DESCENDING = 1024
const val SORT_TYPE_MASK = SORT_NAME or SORT_SIZE or SORT_MODIFIED or SORT_TAKEN or SORT_COUNT

const val GROUP_NONE = 0
const val GROUP_DAY = 1
const val GROUP_MONTH = 2
const val GROUP_TYPE = 3
const val GROUP_FOLDER = 4

const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

class Prefs private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Custom album covers by album path, see [AlbumCover]. */
    private val covers: SharedPreferences = context.getSharedPreferences("album_covers", Context.MODE_PRIVATE)

    var theme by int("theme", THEME_SYSTEM)
    var dynamicColors by boolean("dynamic_colors", true)

    var albumSorting by int("album_sorting", SORT_TAKEN or SORT_DESCENDING)
    var mediaSorting by int("media_sorting", SORT_TAKEN or SORT_DESCENDING)
    var grouping by int("grouping", GROUP_MONTH)
    var filter by int("filter", TYPE_ALL)
    var albumColumns by int("album_columns", 2)
    var mediaColumns by int("media_columns", 4)
    var startOnPhotos by boolean("start_on_photos", true)

    var pinnedFolders by stringSet("pinned_folders")
    var excludedFolders by stringSet("excluded_folders")
    var favorites by stringSet("favorites")
    var showHidden by boolean("show_hidden", false)

    var useTrash by boolean("use_trash", true)
    var confirmDelete by boolean("confirm_delete", true)

    var showVideoDuration by boolean("show_video_duration", true)
    var showFileTypes by boolean("show_file_types", false)
    var markFavorites by boolean("mark_favorites", true)
    var animateGifs by boolean("animate_gifs", true)

    var autoplayVideos by boolean("autoplay_videos", true)
    var loopVideos by boolean("loop_videos", false)
    var muteVideos by boolean("mute_videos", false)

    fun albumCovers(): Map<String, AlbumCover> = covers.all.mapNotNull { (path, value) ->
        AlbumCover.parse(value as? String ?: return@mapNotNull null)?.let { path to it }
    }.toMap()

    /** Sets the cover of the album at [path], null goes back to its latest photo. */
    fun setAlbumCover(path: String, cover: AlbumCover?) {
        covers.edit().apply { if (cover == null) remove(path) else putString(path, cover.encode()) }.apply()
    }

    private fun int(key: String, default: Int) = object : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getInt(key, default)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) = prefs.edit().putInt(key, value).apply()
    }

    private fun boolean(key: String, default: Boolean) = object : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    }

    private fun stringSet(key: String) = object : ReadWriteProperty<Any, Set<String>> {
        // Copy, the returned instance must not be modified.
        override fun getValue(thisRef: Any, property: KProperty<*>): Set<String> = HashSet(prefs.getStringSet(key, emptySet())!!)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext).also { instance = it }
        }
    }
}

val Context.prefs get() = Prefs.get(this)
