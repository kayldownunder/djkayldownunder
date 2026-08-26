package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry when browsing a folder level: either something to navigate into, or a playable playlist. */
sealed class FolderBrowseItem {
    data class SubFolder(val uri: Uri, val name: String) : FolderBrowseItem()
    data class LeafPlaylist(val playlist: Playlist) : FolderBrowseItem()

    val sortName: String get() = when (this) {
        is SubFolder -> name
        is LeafPlaylist -> playlist.name
    }
}

/**
 * Handles everything related to the user-chosen root music directory:
 * - persisting permission to access it across app restarts
 * - scanning the entire folder tree (any depth) for playable playlists, used by Search,
 *   Settings' "Fetch All", and the Skip Review screen
 * - level-by-level browsing for the Library tab itself, so nested folders like
 *   Band/Album/track.mp3 can be navigated into one level at a time
 */
class MusicFolderRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_prefs", Context.MODE_PRIVATE)

    // SAF's listFiles() is a ContentProvider round-trip per folder, so repeatedly
    // re-browsing the same folder (e.g. navigating back and forth) would otherwise redo
    // that walk every time. Cached per session and cleared whenever the underlying
    // folder contents might have changed (new root chosen, or an explicit refresh/delete).
    private val folderLevelCache = mutableMapOf<String, List<FolderBrowseItem>>()

    companion object {
        private const val KEY_ROOT_URI = "root_music_uri"
    }

    fun invalidateFolderCache() {
        folderLevelCache.clear()
    }

    /** Call this right after the SAF picker returns a URI. */
    fun persistRootFolder(uri: Uri) {
        // Take persistable permission so we can still read it after the app/device restarts.
        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)

        prefs.edit().putString(KEY_ROOT_URI, uri.toString()).apply()
    }

    /** Returns the saved root folder URI, or null if none has been chosen yet. */
    fun getSavedRootFolder(): Uri? {
        val raw = prefs.getString(KEY_ROOT_URI, null) ?: return null
        return Uri.parse(raw)
    }

    /**
     * Walks the ENTIRE folder tree below rootUri, at any depth, collecting every folder
     * that directly contains audio files as a Playlist. A folder that only contains other
     * folders (e.g. a "Band" folder holding several "Album" subfolders) is traversed but
     * not itself added as a playlist. Used anywhere the app needs the complete flat list:
     * Search, "Fetch Metadata for All Playlists", and Skip Review.
     */
    suspend fun scanPlaylists(rootUri: Uri): List<Playlist> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        if (!root.isDirectory) return@withContext emptyList()

        val results = mutableListOf<Playlist>()
        collectPlaylistsRecursive(root, results)
        results.sortedBy { it.name.lowercase() }
    }

    private fun collectPlaylistsRecursive(folder: DocumentFile, out: MutableList<Playlist>) {
        val children = folder.listFiles()
        val subfolders = children.filter { it.isDirectory }
        val audioFiles = children.filter { it.isFile && isAudioFile(it) }

        if (audioFiles.isNotEmpty()) {
            out.add(buildPlaylist(folder, audioFiles))
        }
        subfolders.forEach { collectPlaylistsRecursive(it, out) }
    }

    /**
     * Lists just the immediate children of one folder, for the Library tab's level-by-level
     * browsing. A child folder that itself contains subfolders is returned as a SubFolder
     * (navigate into it to go deeper); a child folder with audio files directly inside and
     * no further subfolders is returned as a ready-to-play LeafPlaylist.
     */
    suspend fun listFolderLevel(folderUri: Uri): List<FolderBrowseItem> {
        val cacheKey = folderUri.toString()
        folderLevelCache[cacheKey]?.let { return it }
        return listFolderLevelUncached(folderUri).also { folderLevelCache[cacheKey] = it }
    }

    private suspend fun listFolderLevelUncached(folderUri: Uri): List<FolderBrowseItem> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!folder.isDirectory) return@withContext emptyList()

        val children = folder.listFiles()

        val subfolderItems = children
            .filter { it.isDirectory }
            .mapNotNull { sub ->
                val subChildren = sub.listFiles()
                val hasSubfolders = subChildren.any { it.isDirectory }
                if (hasSubfolders) {
                    FolderBrowseItem.SubFolder(sub.uri, sub.name ?: "Untitled")
                } else {
                    val audioFiles = subChildren.filter { it.isFile && isAudioFile(it) }
                    if (audioFiles.isEmpty()) null // empty folder, nothing to show
                    else FolderBrowseItem.LeafPlaylist(buildPlaylist(sub, audioFiles))
                }
            }

        // A folder can contain audio files directly alongside its subfolders (e.g. a band
        // folder with Album subfolders plus a few loose singles) - surface those as their
        // own playable entry so they aren't silently hidden from this level.
        val directAudioFiles = children.filter { it.isFile && isAudioFile(it) }
        val ownTracksItem = if (directAudioFiles.isEmpty()) {
            emptyList()
        } else {
            listOf(FolderBrowseItem.LeafPlaylist(buildPlaylist(folder, directAudioFiles)))
        }

        (subfolderItems + ownTracksItem).sortedBy { it.sortName.lowercase() }
    }

    private fun buildPlaylist(folder: DocumentFile, audioFiles: List<DocumentFile>): Playlist {
        val tracks = audioFiles
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                Track(
                    uri = file.uri,
                    displayName = name.substringBeforeLast('.'),
                    fileName = name,
                    sizeBytes = file.length(),
                    mimeType = file.type
                )
            }
            .sortedBy { it.displayName.lowercase() }

        return Playlist(
            folderUri = folder.uri,
            name = folder.name ?: "Untitled Playlist",
            tracks = tracks
        )
    }

    /**
     * Resolves any folder - leaf or branching, any depth - into a single playable Playlist
     * containing every track nested anywhere inside it. Used to favorite/sync a whole
     * branching folder (e.g. an artist folder with several albums) as one unit, even
     * though the Library browses it level by level. Returns null if the folder no longer
     * exists.
     */
    suspend fun buildAggregatePlaylist(folderUri: Uri): Playlist? {
        val name = withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(context, folderUri)?.name
        } ?: return null
        val nested = scanPlaylists(folderUri)
        return Playlist(folderUri = folderUri, name = name, tracks = nested.flatMap { it.tracks })
    }

    /**
     * Permanently deletes a folder and everything inside it (files and nested subfolders,
     * any depth) from device storage. Children are deleted individually before their
     * parent, since not every SAF provider supports removing a non-empty directory in a
     * single call. Returns true only if the folder itself was successfully removed.
     */
    suspend fun deleteFolderRecursively(folderUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
        val deleted = folder?.let { deleteRecursive(it) } ?: false
        invalidateFolderCache()
        deleted
    }

    private fun deleteRecursive(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            file.listFiles().forEach { child -> deleteRecursive(child) }
        }
        return file.delete()
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && mime.startsWith(SUPPORTED_AUDIO_MIME_PREFIX)) return true

        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_AUDIO_EXTENSIONS
    }

    /**
     * Sweeps the entire music tree for image files sitting alongside audio files - the
     * "cover.jpg"/"folder.jpg" that commonly comes bundled with a downloaded album - and
     * moves every one of them into the single shared [METADATA_FOLDER_NAME] folder under
     * the root, so a photo gallery app scanning the whole library doesn't see one picture
     * per album folder. Same-named images from different albums are disambiguated by
     * prefixing the album folder's own name. Returns how many images were moved.
     */
    suspend fun consolidateArtworkImages(rootUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext 0
        if (!root.isDirectory) return@withContext 0

        val metadataFolder = root.findFile(METADATA_FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: root.createDirectory(METADATA_FOLDER_NAME)
            ?: return@withContext 0

        var moved = 0
        sweepImagesInto(root, metadataFolder) { moved++ }
        invalidateFolderCache()
        moved
    }

    private fun sweepImagesInto(folder: DocumentFile, metadataFolder: DocumentFile, onMoved: () -> Unit) {
        // Never sweep the destination itself, or the background-picker's own image
        // folder - those are user-chosen wallpaper images, not stray album art.
        if (folder.uri == metadataFolder.uri) return
        if (folder.name?.equals(BACKGROUND_FOLDER_NAME, ignoreCase = true) == true) return

        val children = folder.listFiles()
        children.filter { it.isFile && isImageFile(it) }.forEach { image ->
            if (moveImageInto(image, metadataFolder, folder.name ?: "folder")) onMoved()
        }
        children.filter { it.isDirectory }.forEach { sub -> sweepImagesInto(sub, metadataFolder, onMoved) }
    }

    /** Copies [image] into [destFolder] under a collision-free name, then deletes the original. */
    private fun moveImageInto(image: DocumentFile, destFolder: DocumentFile, parentFolderName: String): Boolean {
        val originalName = image.name ?: "image"
        val safeParent = parentFolderName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "folder" }
        val dot = originalName.lastIndexOf('.')
        val base = if (dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (dot > 0) originalName.substring(dot) else ""

        var targetName = "${safeParent}_$originalName"
        var suffix = 1
        while (destFolder.findFile(targetName) != null) {
            targetName = "${safeParent}_${base}_$suffix$ext"
            suffix++
        }

        val newFile = destFolder.createFile(image.type ?: "image/*", targetName) ?: return false
        val copied = runCatching {
            context.contentResolver.openInputStream(image.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)

        return if (copied) {
            image.delete()
            true
        } else {
            newFile.delete()
            false
        }
    }

    private fun isImageFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && mime.startsWith("image/")) return true

        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }
}

// Must match BackgroundImageRepository's own folder name - kept as a local constant here
// rather than a cross-file reference, since it's only needed for this one skip check.
private const val BACKGROUND_FOLDER_NAME = "background"
