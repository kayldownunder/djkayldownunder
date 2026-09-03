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
 * Applies a Font Settings choice on top of [AppTypography]'s base styles: swaps in
 * [familyName], scales every size proportionally so that [AppTypography.bodyLarge]'s own
 * size lands exactly on [sizeSp], and - only when a color override is actually set
 * ([colorArgb] != -1) - recolors text that doesn't already specify its own explicit color
 * (Text() calls that pass `color = ...` still win over this, e.g. error text stays red).
 * Scaling around bodyLarge (rather than applying [sizeSp] as a literal size to every style)
 * keeps headlines bigger than body text at every setting, the same relative hierarchy
 * [AppTypography] already defines. Takes plain primitives rather than a [FontPrefs] directly
 * because [FontPrefs] carries two fully independent (family, size, color) triples - one for
 * the app at large ("Playlist Text") and one for just the Settings screen ("Settings Text")
 * - and this same builder is used for both.
 */
private fun buildTypography(familyName: String, sizeSp: Float, colorArgb: Int): Typography {
    val family = fontFamilyFor(familyName)
    val overrideColor = if (colorArgb != -1) Color(colorArgb) else null
    val scale = sizeSp / AppTypography.bodyLarge.fontSize.value

    fun TextStyle.themed(): TextStyle = copy(
        fontFamily = family,
        fontSize = (fontSize.value * scale).sp,
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
        typography = buildTypography(
            fontPrefs.albumFontFamilyName,
            fontPrefs.albumTextSizeSp,
            fontPrefs.albumFontColorArgb
        ),
        content = content
    )
}

/**
 * Nested typography override for the Settings screen and everything opened from it (Font
 * Settings, background pickers, etc.) - swaps in the "Settings Text" (family, size, color)
 * from [FontPrefs] in place of the app-wide "Playlist Text" one from [DJKaylTheme], while
 * leaving color scheme and shapes untouched by simply not overriding them.
 */
@Composable
fun SettingsTypography(fontPrefs: FontPrefs, content: @Composable () -> Unit) {
    MaterialTheme(
        typography = buildTypography(
            fontPrefs.settingsFontFamilyName,
            fontPrefs.settingsTextSizeSp,
            fontPrefs.settingsFontColorArgb
        ),
        content = content
    )
}
