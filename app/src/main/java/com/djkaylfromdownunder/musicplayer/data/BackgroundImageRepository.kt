package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKGROUND_FOLDER_NAME = "background"

/**
 * Manages the "background" subfolder inside the user's chosen music folder, which holds
 * every image available for the Library/Settings background pickers. Keeping these images
 * inside the music folder (rather than app-private storage) means they're visible from a
 * normal file manager too, and can be shared across reinstalls the same way the music
 * itself is.
 */
class BackgroundImageRepository(private val context: Context) {

    /** Lists every image in the background folder, creating the folder first if it's missing. */
    suspend fun listBackgroundImages(rootUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val folder = getOrCreateBackgroundFolder(rootUri) ?: return@withContext emptyList()
        folder.listFiles()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { it.uri }
    }

    /**
     * Copies the picked image into the background folder so it's available for future
     * selection. Returns the new file's URI, or null if the copy failed (or no music
     * folder has been chosen yet).
     */
    suspend fun addBackgroundImage(rootUri: Uri, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val folder = getOrCreateBackgroundFolder(rootUri) ?: return@withContext null
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        val displayName = DocumentFile.fromSingleUri(context, sourceUri)?.name
            ?: "image_${System.currentTimeMillis()}"
        val newFile = folder.createFile(mimeType, displayName) ?: return@withContext null

        val copied = try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
        } catch (e: Exception) {
            false
        }

        if (copied) newFile.uri else {
            newFile.delete()
            null
        }
    }

    private fun getOrCreateBackgroundFolder(rootUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        return root.findFile(BACKGROUND_FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: root.createDirectory(BACKGROUND_FOLDER_NAME)
    }
}
