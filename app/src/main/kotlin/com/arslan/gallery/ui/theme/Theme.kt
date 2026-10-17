package com.arslan.gallery.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslan.gallery.data.THEME_DARK
import com.arslan.gallery.data.THEME_LIGHT
import com.arslan.gallery.data.THEME_SYSTEM
import com.arslan.gallery.data.prefs

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val BaseTypography = Typography()

/** Emphasized display/headline styles give the expressive, editorial look on large titles. */
private val ExpressiveTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.emphasize(),
    displayMedium = BaseTypography.displayMedium.emphasize(),
    displaySmall = BaseTypography.displaySmall.emphasize(),
    headlineLarge = BaseTypography.headlineLarge.emphasize(),
    headlineMedium = BaseTypography.headlineMedium.emphasize(),
    headlineSmall = BaseTypography.headlineSmall.emphasize(),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private fun TextStyle.emphasize() = copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF1F2C6F),
    primaryContainer = Color(0xFF374387),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF5E1133),
    secondaryContainer = Color(0xFF7B2949),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF8BD1E8),
    onTertiary = Color(0xFF003543),
    tertiaryContainer = Color(0xFF004E60),
    onTertiaryContainer = Color(0xFFB4EBFF),
)

/** Theme settings, observable so changing them restyles the app right away. */
object Appearance {
    var theme by mutableIntStateOf(THEME_SYSTEM)
    var dynamicColors by mutableStateOf(true)

    fun load(context: Context) {
        theme = context.prefs.theme
        dynamicColors = context.prefs.dynamicColors
    }
}

/** [dark] forces a light or dark scheme, the theme setting decides when it's null. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = dark ?: when (Appearance.theme) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> systemDark
    }
    val dynamic = Appearance.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme: ColorScheme = remember(darkTheme, dynamic, context) {
        when {
            dynamic && darkTheme -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            darkTheme -> ExpressiveDarkColorScheme
            else -> expressiveLightColorScheme()
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
