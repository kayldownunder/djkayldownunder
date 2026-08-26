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

/** Supported audio mime type prefixes when scanning folders. */
val SUPPORTED_AUDIO_MIME_PREFIX = "audio/"

/** Fallback check by extension, since some files report a generic mime type. */
val SUPPORTED_AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma"
)
