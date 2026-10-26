package com.arslan.gallery.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * [base] plus some fixed space, only read where the padding is used. A Scaffold's padding changes on
 * every frame its collapsing top bar moves: added up while composing, it would recompose the whole
 * screen for each of those frames instead of just measuring the grid again.
 */
@Composable
fun rememberExtendedPadding(
    base: PaddingValues,
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues {
    val current = rememberUpdatedState(base)
    return remember(start, top, end, bottom) { ExtendedPadding(current, start, top, end, bottom) }
}

@Stable
private class ExtendedPadding(
    private val base: State<PaddingValues>,
    private val start: Dp,
    private val top: Dp,
    private val end: Dp,
    private val bottom: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        base.value.calculateLeftPadding(layoutDirection) + if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateTopPadding() = base.value.calculateTopPadding() + top

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        base.value.calculateRightPadding(layoutDirection) + if (layoutDirection == LayoutDirection.Ltr) end else start

    override fun calculateBottomPadding() = base.value.calculateBottomPadding() + bottom
}
