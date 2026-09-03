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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Settings entry point ("Shortcut Button Color Selection"): opens a color palette. Selecting
 * a swatch recolors the "Random skip all albums" shortcut (see [RandomSkipAllShortcut]) on
 * the Library, Play Lists, and Now Playing screens - the Settings shortcuts themselves are
 * plain icon+label rows and don't use this color.
 */
@Composable
fun ShortcutButtonColorPicker(
    buttonColorViewModel: ButtonColorViewModel,
    modifier: Modifier = Modifier,
    label: String = "Shortcut Button Color Selection",
    icon: ImageVector = Icons.Default.Palette,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsShortcutRow(
        icon = icon,
        label = label,
        enabled = enabled,
        onClick = { showDialog = true },
        onLongClick = onLongClick,
        modifier = modifier
    )

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
 * Playback shortcut shown top-right on the Library, Playlist, and Now Playing screens:
 * shuffles every track from every album into one combined queue and starts playing it.
 * Rendered as a small round colored button (colored via ButtonColorViewModel) with its label
 * directly underneath rather than inside the button, so the label has room without
 * widening the button itself.
 *
 * [isActive] must reflect PlayerViewModel's actual isShuffleAllActive state (not a local
 * toggle owned by this composable) - it's the single source of truth shared across every
 * screen, so turning "Random Skip All Albums" on or off from any one of these three
 * highlights (or un-highlights) the button identically everywhere else it's shown, and
 * the highlighted state survives navigating between screens.
 */
@Composable
fun RandomSkipAllShortcut(
    isActive: Boolean,
    onClick: () -> Unit,
    buttonColorViewModel: ButtonColorViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val color by buttonColorViewModel.color.collectAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(56.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
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
