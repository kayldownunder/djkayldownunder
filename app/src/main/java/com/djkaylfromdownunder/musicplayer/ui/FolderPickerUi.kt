package com.djkaylfromdownunder.musicplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Settings screen entry point: button that launches the SAF directory picker.
 */
@Composable
fun ChooseMusicFolderButton(viewModel: MusicLibraryViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onRootFolderChosen(uri)
        }
    }

    OutlinedButton(onClick = { launcher.launch(null) }) {
        Text("Choose Music Folder")
    }
}
