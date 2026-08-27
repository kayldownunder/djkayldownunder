package com.djkaylfromdownunder.musicplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings screen entry point: button that launches the SAF directory picker.
 */
@Composable
fun ChooseMusicFolderButton(
    viewModel: MusicLibraryViewModel,
    modifier: Modifier = Modifier,
    label: String = "Choose Music Folder",
    enabled: Boolean = true
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onRootFolderChosen(uri)
        }
    }

    OutlinedButton(onClick = { launcher.launch(null) }, enabled = enabled, modifier = modifier) {
        Text(label)
    }
}
