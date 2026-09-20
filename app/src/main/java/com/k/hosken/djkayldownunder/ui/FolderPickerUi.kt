package com.k.hosken.djkayldownunder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Settings screen entry point: button that launches the SAF directory picker. Renders as a
 * transparent icon+label shortcut row when [buttonColorViewModel] is supplied (Settings'
 * shortcut list); left null it falls back to the plain outlined style used by the first-run
 * "no library chosen yet" prompt, which isn't one of the reorderable shortcuts.
 */
@Composable
fun ChooseMusicFolderButton(
    viewModel: MusicLibraryViewModel,
    modifier: Modifier = Modifier,
    label: String = "Choose Music Folder",
    icon: ImageVector = Icons.Default.FolderOpen,
    enabled: Boolean = true,
    buttonColorViewModel: ButtonColorViewModel? = null,
    onLongClick: (() -> Unit)? = null
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onRootFolderChosen(uri)
        }
    }

    if (buttonColorViewModel != null) {
        SettingsShortcutRow(
            icon = icon,
            label = label,
            enabled = enabled,
            onClick = { launcher.launch(null) },
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        OutlinedButton(onClick = { launcher.launch(null) }, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    }
}
