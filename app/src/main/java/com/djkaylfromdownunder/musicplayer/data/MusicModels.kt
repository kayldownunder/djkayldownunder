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
