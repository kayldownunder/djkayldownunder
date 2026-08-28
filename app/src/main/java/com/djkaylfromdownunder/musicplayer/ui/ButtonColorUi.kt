package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.ButtonColorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ButtonColorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ButtonColorRepository(application)

    private val _color = MutableStateFlow(Color(repository.getColor()))
    val color: StateFlow<Color> = _color.asStateFlow()

    fun setColor(color: Color) {
        repository.setColor(color.toArgb())
        _color.value = color
    }
}

/**
 * Shared fill/content colors for every "shortcut" button across the app, driven by the
 * user's chosen color (see ButtonColorViewModel) - white content color throughout so text
 * stays legible regardless of which color is picked.
 */
@Composable
fun shortcutButtonColors(buttonColorViewModel: ButtonColorViewModel): ButtonColors {
    val color by buttonColorViewModel.color.collectAsState()
    return ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
}

/**
 * Settings entry point ("Shortcut Button Color Selection"): opens a color palette:
 * selecting a swatch globally recolors every shortcut button across the app.
 */
@Composable
fun ShortcutButtonColorPicker(
    buttonColorViewModel: ButtonColorViewModel,
    modifier: Modifier = Modifier,
    label: String = "Shortcut Button Color Selection",
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        enabled = enabled,
        colors = shortcutButtonColors(buttonColorViewModel),
        modifier = modifier
    ) {
        Text(label)
    }

    if (showDialog) {
        val current by buttonColorViewModel.color.collectAsState()
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Shortcut Button Color") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(88.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PRESET_COLORS) { (name, presetColor) ->
                        val isSelected = current.toArgb() == presetColor.toArgb()
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(presetColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    buttonColorViewModel.setColor(presetColor)
                                    showDialog = false
                                }
                                .semantics { contentDescription = name }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Close") }
            }
        )
    }
}

/**
 * Playback shortcut shown top-right on the Playlist and Now Playing screens: shuffles every
 * track from every album into one combined queue and starts playing it. Rendered as a
 * small round colored button (see shortcutButtonColors) with its label directly underneath
 * rather than inside the button, so the label has room without widening the button itself.
 *
 * Also doubles as a simple two-state toggle indicator, purely visual (not tied to any
 * persisted setting) - starts white/black, flips to the configured shortcut color on the
 * first tap, and back to white/black on the next, while still firing [onClick] every time.
 */
@Composable
fun RandomSkipAllShortcut(
    onClick: () -> Unit,
    buttonColorViewModel: ButtonColorViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val color by buttonColorViewModel.color.collectAsState()
    var isActive by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(56.dp)
    ) {
        FilledIconButton(
            onClick = {
                isActive = !isActive
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive) color else Color.White,
                contentColor = if (isActive) Color.White else Color.Black
            )
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = "Random skip all albums", modifier = Modifier.size(16.dp))
        }
        Text(
            "Random skip all albums",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
