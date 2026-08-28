package com.djkaylfromdownunder.musicplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings screen entry point: button that launches the SAF directory picker. Renders as a
 * colored shortcut button when [buttonColorViewModel] is supplied (Settings' shortcut grid);
 * left null it falls back to the plain outlined style used by the first-run "no library
 * chosen yet" prompt, which isn't one of the reorderable shortcuts.
 */
@Composable
fun ChooseMusicFolderButton(
    viewModel: MusicLibraryViewModel,
    modifier: Modifier = Modifier,
    label: String = "Choose Music Folder",
    enabled: Boolean = true,
    buttonColorViewModel: ButtonColorViewModel? = null
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onRootFolderChosen(uri)
        }
    }

    if (buttonColorViewModel != null) {
        Button(
            onClick = { launcher.launch(null) },
            enabled = enabled,
            colors = shortcutButtonColors(buttonColorViewModel),
            modifier = modifier
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = { launcher.launch(null) }, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    }
}
