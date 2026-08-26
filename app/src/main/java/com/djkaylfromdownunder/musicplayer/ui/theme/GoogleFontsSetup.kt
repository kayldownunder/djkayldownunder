package com.djkaylfromdownunder.musicplayer.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.djkaylfromdownunder.musicplayer.R

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * The 15 font families offered in the Font Settings modal, plus "System Default" as the
 * no-override fallback. Fetched on demand from Google's downloadable-fonts provider
 * (requires Google Play services on the device) - if a font can't be fetched, Compose
 * silently falls back to the default typeface rather than failing.
 */
val AVAILABLE_FONT_FAMILIES: List<String> = listOf(
    "System Default",
    "Roboto",
    "Open Sans",
    "Lato",
    "Montserrat",
    "Oswald",
    "Poppins",
    "Noto Sans",
    "Raleway",
    "Nunito",
    "Merriweather",
    "PT Sans",
    "Playfair Display",
    "Ubuntu",
    "Inter",
    "Source Sans Pro"
)

fun fontFamilyFor(name: String): FontFamily {
    if (name == "System Default" || name.isBlank()) return FontFamily.Default
    return FontFamily(Font(googleFont = GoogleFont(name), fontProvider = googleFontsProvider))
}
