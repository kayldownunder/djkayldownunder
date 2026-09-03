package com.djkaylfromdownunder.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.djkaylfromdownunder.musicplayer.data.FontPrefs

private val AppDarkColorScheme = darkColorScheme(
    primary = AccentCoral,
    onPrimary = BackgroundBlack,
    secondary = AccentCoralDim,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFFF5252)
)

/**
 * Applies the user's Font Settings choices on top of [AppTypography]'s base styles: swaps
 * in [prefs]'s font family, scales every size by [sizeScale], and - only when a color
 * override is actually set - recolors text that doesn't already specify its own explicit
 * color (Text() calls that pass `color = ...` still win over this, e.g. error text stays
 * red). [sizeScale] is passed in separately (rather than read off [prefs] directly) because
 * [FontPrefs] carries two independent size scales - [FontPrefs.albumTextSizeScale] for the
 * app at large and [FontPrefs.settingsTextSizeScale] for just the Settings screen - sharing
 * the same family/color.
 */
private fun buildTypography(prefs: FontPrefs, sizeScale: Float): Typography {
    val family = fontFamilyFor(prefs.fontFamilyName)
    val overrideColor = if (prefs.fontColorArgb != -1) Color(prefs.fontColorArgb) else null

    fun TextStyle.themed(): TextStyle = copy(
        fontFamily = family,
        fontSize = (fontSize.value * sizeScale).sp,
        color = overrideColor ?: color
    )

    return Typography(
        headlineLarge = AppTypography.headlineLarge.themed(),
        headlineSmall = AppTypography.headlineSmall.themed(),
        titleLarge = AppTypography.titleLarge.themed(),
        titleMedium = AppTypography.titleMedium.themed(),
        bodyLarge = AppTypography.bodyLarge.themed(),
        bodyMedium = AppTypography.bodyMedium.themed(),
        bodySmall = AppTypography.bodySmall.themed(),
        labelSmall = AppTypography.labelSmall.themed()
    )
}

@Composable
fun DJKaylTheme(fontPrefs: FontPrefs = FontPrefs(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography = buildTypography(fontPrefs, fontPrefs.albumTextSizeScale),
        content = content
    )
}

/**
 * Nested typography override for the Settings screen and everything opened from it (Font
 * Settings, background pickers, etc.) - swaps in [FontPrefs.settingsTextSizeScale] in place
 * of the app-wide [FontPrefs.albumTextSizeScale] from [DJKaylTheme], while leaving color
 * scheme and shapes untouched by simply not overriding them.
 */
@Composable
fun SettingsTypography(fontPrefs: FontPrefs, content: @Composable () -> Unit) {
    MaterialTheme(
        typography = buildTypography(fontPrefs, fontPrefs.settingsTextSizeScale),
        content = content
    )
}
