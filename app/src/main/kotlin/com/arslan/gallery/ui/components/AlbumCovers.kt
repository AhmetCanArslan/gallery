package com.arslan.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.arslan.gallery.data.Album

/** Icons an album can show instead of a photo, by the key stored in the preferences. */
val AlbumIcons: List<Pair<String, ImageVector>> = listOf(
    "folder" to Icons.Rounded.Folder,
    "camera" to Icons.Rounded.CameraAlt,
    "screenshot" to Icons.Rounded.Screenshot,
    "download" to Icons.Rounded.Download,
    "favorite" to Icons.Rounded.Favorite,
    "star" to Icons.Rounded.Star,
    "bookmark" to Icons.Rounded.Bookmark,
    "people" to Icons.Rounded.People,
    "pets" to Icons.Rounded.Pets,
    "home" to Icons.Rounded.Home,
    "travel" to Icons.Rounded.Flight,
    "nature" to Icons.Rounded.Forest,
    "food" to Icons.Rounded.Restaurant,
    "cafe" to Icons.Rounded.LocalCafe,
    "party" to Icons.Rounded.Celebration,
    "birthday" to Icons.Rounded.Cake,
    "work" to Icons.Rounded.Work,
    "school" to Icons.Rounded.School,
    "book" to Icons.AutoMirrored.Rounded.MenuBook,
    "art" to Icons.Rounded.Brush,
    "music" to Icons.Rounded.MusicNote,
    "movie" to Icons.Rounded.Movie,
    "games" to Icons.Rounded.SportsEsports,
    "sports" to Icons.Rounded.FitnessCenter,
)

fun albumIcon(key: String): ImageVector? = AlbumIcons.firstOrNull { it.first == key }?.second

/** The album's picked icon, its cover photo, or a folder when it has neither. */
@Composable
fun AlbumCoverImage(album: Album, iconSize: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = album.icon?.let(::albumIcon)
    val cover = album.cover
    when {
        icon != null -> Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(iconSize))
        }

        cover == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }

        else -> AsyncImage(
            model = remember(cover.signature) { thumbnailRequest(context, cover) },
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}
