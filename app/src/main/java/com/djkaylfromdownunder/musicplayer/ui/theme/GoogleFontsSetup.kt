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
 * slab, condensed, monospace, etc.) for headline-style theming.
 */
val AVAILABLE_FONT_FAMILIES: List<String> = listOf(
    "System Default",
    // Everyday text faces
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
    "Source Sans Pro",
    "Work Sans",
    "DM Sans",
    "Barlow",
    "Rubik",
    "Karla",
    "Mulish",
    "Libre Baskerville",
    "EB Garamond",
    "Vollkorn",
    "Crimson Text",
    "Cormorant Garamond",
    "Zilla Slab",
    // Bold / decorative display faces
    "Bebas Neue",
    "Anton",
    "Archivo Black",
    "Alfa Slab One",
    "Rubik Mono One",
    "Righteous",
    "Bangers",
    "Bungee",
    "Fredoka",
    "Baloo 2",
    "Chewy",
    "Passion One",
    "Abril Fatface",
    "Cinzel",
    "Teko",
    "Fjalla One",
    "Yanone Kaffeesatz",
    "Kanit",
    "Rajdhani",
    "Orbitron",
    "Exo 2",
    "Press Start 2P",
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
    "Indie Flower",
    "Handlee",
    // Rounded / geometric
    "Comfortaa",
    "Josefin Sans",
    "Quicksand"
)

fun fontFamilyFor(name: String): FontFamily {
    if (name == "System Default" || name.isBlank()) return FontFamily.Default
    return FontFamily(Font(googleFont = GoogleFont(name), fontProvider = googleFontsProvider))
}
