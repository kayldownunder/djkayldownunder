package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.djkaylfromdownunder.musicplayer.ui.theme.AVAILABLE_FONT_FAMILIES
import com.djkaylfromdownunder.musicplayer.ui.theme.fontFamilyFor

/** Which of the two independent font configurations [FontSettingsDialog] is showing. */
private enum class FontSection { PLAYLIST, SETTINGS }

/**
 * "Visual configuration" modal opened from Settings' "Fonts" shortcut. The landing screen is
 * just two buttons, "Playlist Text" and "Settings Text" - picking one drills into a
 * sub-screen with that area's full, self-contained font family/size/color/preview controls
 * (see [FontSectionControls]) and a back arrow that returns to the two buttons. [FontPrefs]
 * backs each area with its own independent (family, size, color) triple. Edits are kept in
 * local [draft] state and only committed to [FontPreferencesViewModel] when the modal fully
 * closes (X button, tap outside, or system back all route through [close], from either
 * screen) - a lightweight auto-save-on-back so there's no separate Save button to remember
 * to tap.
 */
@Composable
fun FontSettingsDialog(
    fontPreferencesViewModel: FontPreferencesViewModel,
    onDismiss: () -> Unit
) {
    val savedPrefs by fontPreferencesViewModel.fontPrefs.collectAsState()
    var draft by remember { mutableStateOf(savedPrefs) }
    var activeSection by remember { mutableStateOf<FontSection?>(null) }

    fun close() {
        fontPreferencesViewModel.save(draft)
        onDismiss()
    }

    Dialog(onDismissRequest = ::close) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeSection != null) {
                            IconButton(onClick = { activeSection = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            when (activeSection) {
                                null -> "Font Settings"
                                FontSection.PLAYLIST -> "Playlist Text"
                                FontSection.SETTINGS -> "Settings Text"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = ::close) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (activeSection) {
                    null -> Column {
                        Button(
                            onClick = { activeSection = FontSection.PLAYLIST },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Playlist Text")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { activeSection = FontSection.SETTINGS },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Settings Text")
                        }
                    }
                    FontSection.PLAYLIST -> Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        FontSectionControls(
                            familyName = draft.albumFontFamilyName,
                            onFamilyChange = { draft = draft.copy(albumFontFamilyName = it) },
                            sizeScale = draft.albumTextSizeScale,
                            onSizeChange = { draft = draft.copy(albumTextSizeScale = it) },
                            colorArgb = draft.albumFontColorArgb,
                            onColorChange = { draft = draft.copy(albumFontColorArgb = it) }
                        )
                    }
                    FontSection.SETTINGS -> Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        FontSectionControls(
                            familyName = draft.settingsFontFamilyName,
                            onFamilyChange = { draft = draft.copy(settingsFontFamilyName = it) },
                            sizeScale = draft.settingsTextSizeScale,
                            onSizeChange = { draft = draft.copy(settingsTextSizeScale = it) },
                            colorArgb = draft.settingsFontColorArgb,
                            onColorChange = { draft = draft.copy(settingsFontColorArgb = it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One full, self-contained font configuration block - live sample preview, Font Family,
 * Text Size, and Font Color - shown for whichever [FontSection] [FontSettingsDialog] has
 * drilled into, since each area's font choices are fully independent (see [FontPrefs]).
 */
@Composable
private fun FontSectionControls(
    familyName: String,
    onFamilyChange: (String) -> Unit,
    sizeScale: Float,
    onSizeChange: (Float) -> Unit,
    colorArgb: Int,
    onColorChange: (Int) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            val previewColor = if (colorArgb != -1) Color(colorArgb) else MaterialTheme.colorScheme.onSurface
            Text(
                text = "The quick brown fox jumps",
                fontFamily = fontFamilyFor(familyName),
                fontSize = (16 * sizeScale).sp,
                color = previewColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Font Family", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FontFamilyDropdown(selected = familyName, onSelected = onFamilyChange)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Text Size (${"%.0f".format(sizeScale * 100)}%)",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(value = sizeScale, onValueChange = onSizeChange, valueRange = 0.75f..1.5f)

        Spacer(modifier = Modifier.height(12.dp))

        Text("Font Color", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FontColorDropdown(argb = colorArgb, onSelected = onColorChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilyDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        // The collapsed field's own value previews the selected family too, not just the
        // list - so the "what does this look like" preview is consistent everywhere, not
        // only while the menu happens to be open.
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = fontFamilyFor(selected)),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AVAILABLE_FONT_FAMILIES.forEach { name ->
                val isSelected = name == selected
                DropdownMenuItem(
                    text = {
                        // Each option's own name is rendered in that font - the option
                        // itself doubles as a live example of what picking it looks like.
                        Text(
                            name,
                            fontFamily = fontFamilyFor(name),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        // Selecting immediately updates the shared draft, which is what
                        // the sample preview box at the top of the dialog reads from -
                        // so it live-updates the instant a family is tapped, same as
                        // every other control in this dialog.
                        onSelected(name)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Named preset colors offered in the Font Color dropdown. "Default" clears the override. */
private val PRESET_FONT_COLORS: List<Pair<String, Color?>> =
    listOf<Pair<String, Color?>>("Default" to null) + PRESET_COLORS.map { (name, color) -> name to color }

/** Dropdown of predefined colors, each shown with a small swatch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontColorDropdown(argb: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = PRESET_FONT_COLORS.firstOrNull { (_, color) ->
        (color == null && argb == -1) || (color != null && color.toArgb() == argb)
    }?.first ?: "Custom"
    val currentSwatch = if (argb != -1) Color(argb) else MaterialTheme.colorScheme.onSurface

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentName,
            onValueChange = {},
            readOnly = true,
            leadingIcon = { ColorSwatch(currentSwatch) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PRESET_FONT_COLORS.forEach { (name, color) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorSwatch(color ?: MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(name)
                        }
                    },
                    onClick = {
                        onSelected(color?.toArgb() ?: -1)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
    )
}
