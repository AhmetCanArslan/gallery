package com.arslan.gallery.data

import android.text.format.DateFormat
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Compares names case-insensitively with numbers by value, so "IMG_9" sorts before "IMG_10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

fun sortMedia(media: List<Medium>, sorting: Int): List<Medium> {
    val comparator: Comparator<Medium> = when (sorting and SORT_TYPE_MASK) {
        SORT_NAME -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
        // Plain comparisons, compareBy would box two Longs per comparison.
        SORT_SIZE -> Comparator { a, b -> a.size.compareTo(b.size) }
        SORT_MODIFIED -> Comparator { a, b -> a.modified.compareTo(b.modified) }
        else -> Comparator { a, b -> a.taken.compareTo(b.taken) }
    }
    return media.sortedWith(if (sorting and SORT_DESCENDING != 0) comparator.reversed() else comparator)
}

/**
 * Splits sorted [media] into sections. Date groups keep the order of the sorting, other groups are
 * listed in the order their first item appears.
 */
fun groupMedia(media: List<Medium>, grouping: Int, sorting: Int, typeLabel: (Int) -> String, todayLabel: String, yesterdayLabel: String): List<GridItem> {
    if (grouping == GROUP_NONE || media.isEmpty()) return media

    val byDate = grouping == GROUP_DAY || grouping == GROUP_MONTH
    val sorted = if (byDate && sorting and SORT_TYPE_MASK !in setOf(SORT_TAKEN, SORT_MODIFIED)) {
        sortMedia(media, SORT_TAKEN or (sorting and SORT_DESCENDING))
    } else {
        media
    }
    val useModified = sorting and SORT_TYPE_MASK == SORT_MODIFIED

    val calendar = Calendar.getInstance()
    val groups = LinkedHashMap<Any, MutableList<Medium>>()
    sorted.forEach { medium ->
        val key: Any = when (grouping) {
            GROUP_DAY -> calendar.dayStart(if (useModified) medium.modified else medium.taken, month = false)
            GROUP_MONTH -> calendar.dayStart(if (useModified) medium.modified else medium.taken, month = true)
            GROUP_TYPE -> medium.type
            else -> medium.parentPath
        }
        groups.getOrPut(key) { ArrayList() }.add(medium)
    }

    val locale = Locale.getDefault()
    val today = calendar.dayStart(System.currentTimeMillis(), month = false)
    val yesterday = today - 24 * 60 * 60 * 1000
    val thisYear = calendar.get(Calendar.YEAR)
    val formats = HashMap<String, java.text.SimpleDateFormat>()
    fun format(ts: Long, skeleton: String) = formats.getOrPut(skeleton) { dateFormat(skeleton, locale) }
        .format(Date(ts)).replaceFirstChar { it.titlecase(locale) }
    val result = ArrayList<GridItem>(sorted.size + groups.size)
    groups.forEach { (key, items) ->
        val title = when (grouping) {
            GROUP_DAY -> when (key as Long) {
                today -> todayLabel
                yesterday -> yesterdayLabel
                else -> format(key, if (calendar.yearOf(key) == thisYear) "EEEdMMMM" else "EEEdMMMMyyyy")
            }

            GROUP_MONTH -> format(key as Long, if (calendar.yearOf(key) == thisYear) "MMMM" else "MMMMyyyy")
            GROUP_TYPE -> typeLabel(key as Int)
            else -> (key as String).filename
        }
        result.add(Section(title))
        result.addAll(items)
    }
    return result
}

/** Builds the album list out of every medium, with favorites and pinned folders first. */
fun buildAlbums(
    media: List<Medium>,
    sorting: Int,
    pinned: Set<String>,
    favoritesName: String,
    storageName: String,
    checkHidden: Boolean,
    covers: Map<String, AlbumCover> = emptyMap(),
): List<Album> {
    val folders = media.groupBy { it.parentPath }.map { (folder, items) ->
        val name = if (folder.endsWith("/emulated/0")) storageName else folder.filename
        albumOf(folder, name, items, checkHidden, covers[folder])
    }

    val comparator: Comparator<Album> = when (sorting and SORT_TYPE_MASK) {
        SORT_NAME -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
        SORT_SIZE -> Comparator { a, b -> a.size.compareTo(b.size) }
        SORT_COUNT -> Comparator { a, b -> a.count.compareTo(b.count) }
        SORT_MODIFIED -> Comparator { a, b -> a.modified.compareTo(b.modified) }
        else -> Comparator { a, b -> a.taken.compareTo(b.taken) }
    }
    val sorted = folders.sortedWith(if (sorting and SORT_DESCENDING != 0) comparator.reversed() else comparator)
    val (pinnedAlbums, others) = sorted.partition { it.path in pinned }

    val favorites = media.filter { it.isFavorite }
    val favoritesAlbum = if (favorites.isEmpty()) null else albumOf(FAVORITES, favoritesName, favorites, checkHidden = false, covers[FAVORITES])
    return listOfNotNull(favoritesAlbum) + pinnedAlbums + others
}

private fun albumOf(path: String, name: String, items: List<Medium>, checkHidden: Boolean, custom: AlbumCover?): Album {
    var cover = items.first()
    var modified = 0L
    var taken = 0L
    var size = 0L
    items.forEach {
        if (it.taken > cover.taken) cover = it
        if (it.modified > modified) modified = it.modified
        if (it.taken > taken) taken = it.taken
        size += it.size
    }
    // A picked photo that's gone, e.g. deleted or moved, falls back to the latest one.
    if (custom is AlbumCover.Photo) items.firstOrNull { it.path == custom.path }?.let { cover = it }
    return Album(
        path = path,
        name = name,
        cover = cover,
        icon = (custom as? AlbumCover.Icon)?.key,
        count = items.size,
        modified = modified,
        taken = taken,
        size = size,
        isHidden = checkHidden && File(path, MediaLibrary.NOMEDIA).exists(),
    )
}

private fun Calendar.dayStart(ts: Long, month: Boolean): Long = run {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    if (month) set(Calendar.DAY_OF_MONTH, 1)
    timeInMillis
}

private fun Calendar.yearOf(ts: Long): Int {
    timeInMillis = ts
    return get(Calendar.YEAR)
}

private fun dateFormat(skeleton: String, locale: Locale) =
    java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
