package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Reads whatever metadata is already embedded in an audio file (ID3 tags, cover art, etc.)
 * This is always tried first, before hitting the network, since it's free and instant.
 */
class LocalMetadataReader(private val context: Context) {

    fun read(trackUri: Uri): TrackMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, trackUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val hasArt = retriever.embeddedPicture != null

            if (title == null && artist == null && album == null && !hasArt) {
                null // nothing useful embedded, caller should try online fetch
            } else {
                TrackMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUrl = null,
                    hasEmbeddedArt = hasArt,
                    source = "embedded"
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Raw embedded artwork bytes, if present - use to build a Bitmap for display. */
    fun readEmbeddedArt(trackUri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, trackUri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
