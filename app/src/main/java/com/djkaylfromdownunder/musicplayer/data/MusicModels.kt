package com.djkaylfromdownunder.musicplayer.data

import android.net.Uri

/**
 * A single audio file found inside a playlist folder.
 */
data class Track(
    val uri: Uri,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String?
)

/**
 * A playlist = one subfolder directly inside the chosen root music directory.
 */
data class Playlist(
    val folderUri: Uri,
    val name: String,
    val tracks: List<Track>
) {
    val trackCount: Int get() = tracks.size
}

/**
 * Best-effort "parent folder" grouping key derived purely from this folder URI's document
 * ID, without touching SAF - `DocumentFile.parentFile` is unreliable for a URI
 * reconstructed via `fromTreeUri` (it always returns null, since the parent chain is only
 * preserved on DocumentFile instances obtained by walking down from `listFiles()`, not on
 * one rebuilt fresh from a stored URI). Document IDs on the common external-storage
 * provider look like "primary:Music/Artist/Album", so two folder URIs sharing this key are
 * assumed to be sibling subfolders of the same parent directory (e.g. two Album folders
 * under the same Artist folder). Used for the Now Playing screen's "more from this
 * folder" recommendations.
 */
fun Uri.parentFolderKey(): String {
    val docId = Uri.decode(lastPathSegment ?: toString())
    return docId.substringBeforeLast('/', "")
}

/** Supported audio mime type prefixes when scanning folders. */
val SUPPORTED_AUDIO_MIME_PREFIX = "audio/"

/** Fallback check by extension, since some files report a generic mime type. */
val SUPPORTED_AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma"
)

/**
 * Shared folder name (directly under the user's chosen music root) for everything the app
 * itself persists about the library: the metadata JSON cache (see MetadataStore) and any
 * album-art images swept out of individual album folders (see
 * MusicFolderRepository.consolidateArtworkImages) - one place instead of one picture per
 * album folder cluttering a photo gallery app's view of the music library.
 */
const val METADATA_FOLDER_NAME = "MetaData"

/** Fallback check by extension for image files, since some providers report generic mime types. */
val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
