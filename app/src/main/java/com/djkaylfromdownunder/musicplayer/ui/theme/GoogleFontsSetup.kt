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
 * The font families offered in the Font Settings modal, plus "System Default" as the
 * no-override fallback. Fetched on demand from Google's downloadable-fonts provider
 * (requires Google Play services on the device) - if a font can't be fetched, Compose
 * silently falls back to the default typeface rather than failing.
 *
 * Grouped roughly by feel so the dropdown reads as more than an alphabetical dump: everyday
 * sans/serif text faces first, then a much wider set of bold/decorative display faces (script,
 * slab, condensed, monospace, etc.) for headline-style theming. The everyday-text group is
 * deliberately short - most humanist sans faces at this size read as near-identical, so it
 * keeps only ones with a genuinely distinct look (geometric, condensed, elegant serif, slab)
 * rather than a dozen near-duplicates of each other.
 */
val AVAILABLE_FONT_FAMILIES: List<String> = listOf(
    "System Default",
    // Everyday text faces
    "Roboto",
    "Montserrat",
    "Oswald",
    "Poppins",
    "Raleway",
    "Rubik",
    "Merriweather",
    "Libre Baskerville",
    "Playfair Display",
    "Zilla Slab",
    // Bold / decorative display faces
    "Bebas Neue",
    "Anton",
    "Archivo Black",
    "Rubik Mono One",
    "Righteous",
    "Bungee",
    "Fredoka",
    "Baloo 2",
    "Passion One",
    "Abril Fatface",
    "Cinzel",
    "Teko",
    "Yanone Kaffeesatz",
    "Rajdhani",
    "Orbitron",
    "Exo 2",
    "Special Elite",
    // Script / handwriting display faces
    "Pacifico",
    "Lobster",
    "Dancing Script",
    "Great Vibes",
    "Sacramento",
    "Satisfy",
    "Caveat",
    "Permanent Marker",
    "Shrikhand",
    "Amatic SC",
    "Kalam",
    "Handlee",
    // Rounded / geometric
    "Josefin Sans",
    "Quicksand"
)

fun fontFamilyFor(name: String): FontFamily {
    if (name == "System Default" || name.isBlank()) return FontFamily.Default
    return FontFamily(Font(googleFont = GoogleFont(name), fontProvider = googleFontsProvider))
}
