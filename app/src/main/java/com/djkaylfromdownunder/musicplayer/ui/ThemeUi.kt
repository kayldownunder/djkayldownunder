package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.AndroidViewModel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.djkaylfromdownunder.musicplayer.data.BackgroundPrefs
import com.djkaylfromdownunder.musicplayer.data.BackgroundImageRepository
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import com.djkaylfromdownunder.musicplayer.data.MusicFolderRepository
import com.djkaylfromdownunder.musicplayer.data.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ThemeRepository(application)
    private val folderRepository = MusicFolderRepository(application)
    private val backgroundImageRepository = BackgroundImageRepository(application)

    private val libraryBackground: StateFlow<BackgroundPrefs> = repository.flowFor(BackgroundTarget.LIBRARY)
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundPrefs())
    private val settingsBackground: StateFlow<BackgroundPrefs> = repository.flowFor(BackgroundTarget.SETTINGS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundPrefs())

    fun backgroundFor(target: BackgroundTarget): StateFlow<BackgroundPrefs> = when (target) {
        BackgroundTarget.LIBRARY -> libraryBackground
        BackgroundTarget.SETTINGS -> settingsBackground
    }

    fun selectImage(target: BackgroundTarget, uri: Uri) {
        viewModelScope.launch { repository.setImage(target, uri.toString()) }
    }

    fun clearImage(target: BackgroundTarget) {
        viewModelScope.launch { repository.clearImage(target) }
    }

    /** Every image currently in the music folder's "background" subfolder, or empty if no music folder is chosen yet. */
    suspend fun listBackgroundImages(): List<Uri> {
        val root = folderRepository.getSavedRootFolder() ?: return emptyList()
        return backgroundImageRepository.listBackgroundImages(root)
    }

    /** Copies a newly-picked image into the background folder. Returns its new URI, or null on failure. */
    suspend fun addBackgroundImage(sourceUri: Uri): Uri? {
        val root = folderRepository.getSavedRootFolder() ?: return null
        return backgroundImageRepository.addBackgroundImage(root, sourceUri)
    }
}

/**
 * Wrap a screen's content in this to apply its independently-chosen background image.
 * [scrim] darkens the image so text/controls placed over it stay readable regardless of
 * how busy the photo is - on by default since that's the common case for a background image.
 */
@Composable
fun TargetedBackground(
    target: BackgroundTarget,
    themeViewModel: ThemeViewModel,
    scrim: Boolean = true,
    content: @Composable () -> Unit
) {
    val background by themeViewModel.backgroundFor(target).collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (background.selectedImageUri != null) {
            AsyncImage(
                model = background.selectedImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (scrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        content()
    }
}

/** Drop this into your Settings screen for background customization. */
@Composable
fun BackgroundSettingsSection(themeViewModel: ThemeViewModel) {
    var openPickerFor by remember { mutableStateOf<BackgroundTarget?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Background", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { openPickerFor = BackgroundTarget.LIBRARY },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Library Background")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openPickerFor = BackgroundTarget.SETTINGS },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings Screen Background")
        }
    }

    openPickerFor?.let { target ->
        BackgroundPickerDialog(
            target = target,
            themeViewModel = themeViewModel,
            onDismiss = { openPickerFor = null }
        )
    }
}

/**
 * Lets the user pick a background image from the music folder's "background" subfolder,
 * or add a new one from their device (which gets copied into that folder so it's
 * available next time too).
 */
@Composable
private fun BackgroundPickerDialog(
    target: BackgroundTarget,
    themeViewModel: ThemeViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAdding by remember { mutableStateOf(false) }
    val current by themeViewModel.backgroundFor(target).collectAsState()

    fun refresh() {
        scope.launch {
            isLoading = true
            images = themeViewModel.listBackgroundImages()
            isLoading = false
        }
    }

    LaunchedEffect(target) { refresh() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isAdding = true
                themeViewModel.addBackgroundImage(uri)
                isAdding = false
                refresh()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (target == BackgroundTarget.LIBRARY) "Library Background" else "Settings Screen Background")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !isAdding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAdding) "Adding…" else "Add Image")
                }

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    images.isEmpty() -> Text(
                        "No background images yet. Choose a music folder in Settings first, " +
                            "then tap \"Add Image\" to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 320.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images) { uri ->
                            val isSelected = current.selectedImageUri == uri.toString()
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        themeViewModel.selectImage(target, uri)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                themeViewModel.clearImage(target)
                onDismiss()
            }) {
                Text("Use Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
