package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry when browsing a folder level: either something to navigate into, or a playable playlist. */
sealed class FolderBrowseItem {
    /**
     * [hasDirectTracks] is true only when this folder has audio files directly inside it,
     * alongside its subfolders (e.g. an artist folder with a few loose singles next to its
     * Album subfolders) - false for a folder that contains only nested subfolders and no
     * songs of its own, which is what the Library screen uses to hide the "Update Metadata"
     * icon on folders that have nothing of their own to fetch a shortcut for.
     */
    data class SubFolder(val uri: Uri, val name: String, val hasDirectTracks: Boolean = false) : FolderBrowseItem()
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
                    val hasDirectTracks = subChildren.any { it.isFile && isAudioFile(it) }
                    FolderBrowseItem.SubFolder(sub.uri, sub.name ?: "Untitled", hasDirectTracks)
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

    /**
     * Returns a previously-generated collage thumbnail for a branching folder (see
     * [generateCollageThumbnail]) without doing any of the work to build one, so browsing
     * the Library can show whatever's already cached without a rescan. Null if none has
     * been generated yet, or no root/MetaData folder exists.
     */
    suspend fun findCollageThumbnail(rootUri: Uri, folderUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
        val metadataFolder = root.findFile(METADATA_FOLDER_NAME)?.takeIf { it.isDirectory } ?: return@withContext null
        metadataFolder.findFile(collageFileName(folderUri))?.uri
    }

    /**
     * Builds (or rebuilds) a thumbnail for a branching folder - one that contains other
     * album subfolders rather than tracks directly (e.g. an artist folder holding several
     * album subfolders, like "AC/DC") - by compositing up to 4 of its subfolders' own cover
     * art into a single square collage image, and saves it into the shared MetaData folder
     * the same way [consolidateArtworkImages] already treats stray album art: generated
     * images live and persist there rather than being recomputed every time or scattered
     * across the library. Source art comes only from tracks' embedded artwork (no network
     * fetch), so this works offline and doesn't depend on online metadata having been
     * fetched first. Returns the saved collage's Uri, or null if no source art could be
     * found anywhere inside, or there's no root folder to save into.
     */
    suspend fun generateCollageThumbnail(rootUri: Uri, folderUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null

        val sourceImages = mutableListOf<ByteArray>()
        folder.listFiles()
            .filter { it.isDirectory }
            .sortedBy { it.name?.lowercase() ?: "" }
            .forEach { subAlbum ->
                if (sourceImages.size >= 4) return@forEach
                firstEmbeddedArt(subAlbum)?.let { sourceImages.add(it) }
            }
        if (sourceImages.isEmpty()) return@withContext null

        val collage = composeCollage(sourceImages) ?: return@withContext null
        val metadataFolder = root.findFile(METADATA_FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: root.createDirectory(METADATA_FOLDER_NAME)
            ?: return@withContext null

        val name = collageFileName(folderUri)
        metadataFolder.findFile(name)?.delete()
        val file = metadataFolder.createFile("image/jpeg", name)
        val saved = file != null && runCatching {
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                collage.compress(Bitmap.CompressFormat.JPEG, 85, out)
            } != null
        }.getOrDefault(false)
        collage.recycle()
        if (saved) file?.uri else null
    }

    /**
     * Looks for a custom cover image sitting directly inside a branching folder (e.g. an
     * image dropped straight into an artist folder like "AC/DC", alongside its Album
     * subfolders) - this lets a user override the auto-generated collage (see
     * [generateCollageThumbnail]) just by adding a picture to that folder, no explicit
     * "set cover" action needed. Only looks at the folder's own direct children, not any
     * nested subfolders. Returns the first image found (alphabetically), or null if none.
     */
    suspend fun findFolderCoverImage(folderUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
        if (!folder.isDirectory) return@withContext null
        folder.listFiles()
            .filter { it.isFile && isImageFile(it) }
            .minByOrNull { it.name?.lowercase() ?: "" }
            ?.uri
    }

    /** Depth-first search for the first track with embedded art anywhere inside [folder]. */
    private fun firstEmbeddedArt(folder: DocumentFile, depth: Int = 0): ByteArray? {
        if (depth > 6) return null // guard against pathological nesting
        val children = folder.listFiles()
        children.filter { it.isFile && isAudioFile(it) }
            .sortedBy { it.name?.lowercase() ?: "" }
            .forEach { audio ->
                val bytes = LocalMetadataReader(context).readEmbeddedArt(audio.uri)
                if (bytes != null) return bytes
            }
        children.filter { it.isDirectory }
            .sortedBy { it.name?.lowercase() ?: "" }
            .forEach { sub ->
                firstEmbeddedArt(sub, depth + 1)?.let { return it }
            }
        return null
    }

    /** Composites 1-4 source images into a single square bitmap - a plain center-cropped image for one, a 2x2 tiled grid (repeating images to fill gaps) for more. */
    private fun composeCollage(images: List<ByteArray>): Bitmap? {
        if (images.size == 1) return decodeCenterCropped(images[0], COLLAGE_SIZE_PX, COLLAGE_SIZE_PX)

        val cell = COLLAGE_SIZE_PX / 2
        val output = Bitmap.createBitmap(COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        for (i in 0 until 4) {
            val bytes = images[i % images.size]
            val cellBitmap = decodeCenterCropped(bytes, cell, cell) ?: continue
            canvas.drawBitmap(cellBitmap, ((i % 2) * cell).toFloat(), ((i / 2) * cell).toFloat(), null)
            cellBitmap.recycle()
        }
        return output
    }

    /** Decodes [bytes] downsampled, then center-crops/scales to an exact [targetPx] x [targetPx] square. */
    private fun decodeCenterCropped(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetW && bounds.outHeight / (sampleSize * 2) >= targetH) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val srcSize = minOf(decoded.width, decoded.height)
        val x = (decoded.width - srcSize) / 2
        val y = (decoded.height - srcSize) / 2
        val cropped = if (srcSize == decoded.width && srcSize == decoded.height) decoded
            else Bitmap.createBitmap(decoded, x, y, srcSize, srcSize)
        val scaled = if (cropped.width == targetW && cropped.height == targetH) cropped
            else Bitmap.createScaledBitmap(cropped, targetW, targetH, true)

        if (cropped !== decoded) decoded.recycle()
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /** Stable, collision-free filename for a folder's collage - independent of same-named folders under different parents. */
    private fun collageFileName(folderUri: Uri): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(folderUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "collage_$digest.jpg"
    }
}

private const val COLLAGE_SIZE_PX = 480

// Must match BackgroundImageRepository's own folder name - kept as a local constant here
// rather than a cross-file reference, since it's only needed for this one skip check.
private const val BACKGROUND_FOLDER_NAME = "background"
