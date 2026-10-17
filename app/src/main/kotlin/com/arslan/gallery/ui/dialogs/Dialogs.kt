package com.arslan.gallery.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arslan.gallery.data.Album
import com.arslan.gallery.data.Medium

/** Dialogs of the app. They are drawn inside the activity window, see [DialogHost]. */
sealed interface AppDialog {
    class DeleteMedia(val media: List<Medium>, val canUseTrash: Boolean, val onConfirm: (toTrash: Boolean) -> Unit) : AppDialog
    class DeleteAlbums(val albums: List<Album>, val canUseTrash: Boolean, val onConfirm: (toTrash: Boolean) -> Unit) : AppDialog
    class Confirm(
        val icon: ImageVector,
        val title: String,
        val message: String?,
        val confirmLabel: String,
        val destructive: Boolean,
        val secondaryLabel: String? = null,
        val onSecondary: () -> Unit = {},
        val onConfirm: () -> Unit,
    ) : AppDialog

    class Rename(val title: String, val initialName: String, val onConfirm: (String) -> Unit) : AppDialog
    class PickFolder(val title: String, val currentFolders: Set<String>, val onPick: (String) -> Unit) : AppDialog
    data object AllFilesAccess : AppDialog
}

@Stable
class DialogState {
    var current by mutableStateOf<AppDialog?>(null)
        private set

    fun show(dialog: AppDialog) {
        current = dialog
    }

    fun dismiss() {
        current = null
    }
}

/**
 * Hosts the current dialog. Dialogs are drawn in the activity window instead of a separate dialog
 * window: that keeps the viewer's hidden system bars hidden, lets the dialog animate out smoothly
 * and avoids the flash a new window causes when the content behind it changes at the same time.
 */
@Composable
fun DialogHost(state: DialogState) {
    val dialog = state.current
    // Keep showing the last dialog while it animates out.
    val last = remember { arrayOfNulls<AppDialog>(1) }
    if (dialog != null) last[0] = dialog
    val shown = dialog ?: last[0]

    BackHandler(enabled = dialog != null) { state.dismiss() }

    AnimatedVisibility(
        visible = dialog != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { state.dismiss() } }
                .safeDrawingPadding()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.animateEnterExit(
                    enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f),
                    exit = scaleOut(tween(160), targetScale = 0.92f),
                )
            ) {
                when (val current = shown) {
                    is AppDialog.DeleteMedia -> DeleteMediaDialog(current, state::dismiss)
                    is AppDialog.DeleteAlbums -> DeleteAlbumsDialog(current, state::dismiss)
                    is AppDialog.Confirm -> ConfirmDialog(current, state::dismiss)
                    is AppDialog.Rename -> RenameDialog(current, state::dismiss)
                    is AppDialog.PickFolder -> PickFolderDialog(current, state::dismiss)
                    AppDialog.AllFilesAccess -> AllFilesAccessDialog(state::dismiss)
                    null -> Unit
                }
            }
        }
    }
}

/** Card of a dialog: centered icon, title and message followed by [content]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogCard(
    icon: ImageVector?,
    title: String,
    message: String?,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            // Taps on the card must not reach the scrim, which would dismiss the dialog.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                header != null -> header()
                icon != null -> Box(
                    Modifier
                        .size(64.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}
