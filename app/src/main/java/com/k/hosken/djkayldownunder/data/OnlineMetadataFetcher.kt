package com.k.hosken.djkayldownunder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up missing metadata online, trying multiple free sources in order since no single
 * one covers everything well:
 *   1. MusicBrainz + Cover Art Archive - best for correct titles/artists, but many
 *      commercial releases have no linked cover art.
 *   2. iTunes Search API - no API key required, excellent artwork coverage for mainstream
 *      commercial music.
 *   3. Deezer's public search API - no API key required, a second independent catalog as
 *      a last resort for anything the first two miss.
 *
 * Each source is tried in turn and the first one to return a usable result wins.
 */
class OnlineMetadataFetcher {

    private val userAgent = "DJKaylFromDownUnder/1.0 (contact: kayl.hosken@gmail.com)"

    suspend fun fetch(guessTitle: String): TrackMetadata? {
        fetchFromMusicBrainz(guessTitle)?.let { return it }
        fetchFromItunes(guessTitle)?.let { return it }
        fetchFromDeezer(guessTitle)?.let { return it }
        return null
    }

    // --- Source 1: MusicBrainz + Cover Art Archive -------------------------------------

    private suspend fun fetchFromMusicBrainz(guessTitle: String): TrackMetadata? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(guessTitle, "UTF-8")
            val url = URL("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=1")
            val response = get(url) ?: return@withContext null
            val json = JSONObject(response)
            val recordings = json.optJSONArray("recordings") ?: return@withContext null
            if (recordings.length() == 0) return@withContext null

            val recording = recordings.getJSONObject(0)
            val title = recording.optString("title").ifBlank { null }
            val artist = recording.optJSONArray("artist-credit")
                ?.optJSONObject(0)?.optString("name")?.ifBlank { null }

            val releases = recording.optJSONArray("releases")
            var album: String? = null
            var artworkUrl: String? = null
            if (releases != null && releases.length() > 0) {
                val release = releases.getJSONObject(0)
                album = release.optString("title").ifBlank { null }
                val mbid = release.optString("id").ifBlank { null }
                if (mbid != null) {
                    artworkUrl = lookupCoverArt(mbid)
                }
            }

            // Only count this as a usable result if we got at least a title/artist match
            // AND artwork - otherwise fall through to the next source, since a title with
            // no picture isn't much better than what local tags might already have given us.
            if (title == null && artworkUrl == null) return@withContext null

            TrackMetadata(
                title = title,
                artist = artist,
                album = album,
                artworkUrl = artworkUrl,
                hasEmbeddedArt = false,
                source = "musicbrainz"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun lookupCoverArt(releaseMbid: String): String? {
        return try {
            val checkUrl = URL("https://coverartarchive.org/release/$releaseMbid/front")
            val connection = checkUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 5000
            val code = connection.responseCode
            connection.disconnect()
            if (code in 300..399) "https://coverartarchive.org/release/$releaseMbid/front" else null
        } catch (e: Exception) {
            null
        }
    }

    // --- Source 2: iTunes Search API -----------------------------------------------------

    private suspend fun fetchFromItunes(guessTitle: String): TrackMetadata? = withContext(Dispatchers.IO) {
        try {
            val term = URLEncoder.encode(guessTitle, "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$term&media=music&limit=1")
            val response = get(url) ?: return@withContext null
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null

            val result = results.getJSONObject(0)
            val title = result.optString("trackName").ifBlank { null }
            val artist = result.optString("artistName").ifBlank { null }
            val album = result.optString("collectionName").ifBlank { null }
            // iTunes gives a small 100x100 thumbnail by default - swap the size in the URL
            // for a much sharper 600x600 version.
            val smallArt = result.optString("artworkUrl100").ifBlank { null }
            val artworkUrl = smallArt?.replace("100x100bb", "600x600bb")

            if (title == null && artworkUrl == null) return@withContext null

            TrackMetadata(
                title = title,
                artist = artist,
                album = album,
                artworkUrl = artworkUrl,
                hasEmbeddedArt = false,
                source = "itunes"
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- Source 3: Deezer public search API ----------------------------------------------

    private suspend fun fetchFromDeezer(guessTitle: String): TrackMetadata? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(guessTitle, "UTF-8")
            val url = URL("https://api.deezer.com/search?q=$q&limit=1")
            val response = get(url) ?: return@withContext null
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return@withContext null
            if (data.length() == 0) return@withContext null

            val track = data.getJSONObject(0)
            val title = track.optString("title").ifBlank { null }
            val artist = track.optJSONObject("artist")?.optString("name")?.ifBlank { null }
            val albumObj = track.optJSONObject("album")
            val album = albumObj?.optString("title")?.ifBlank { null }
            val artworkUrl = albumObj?.optString("cover_big")?.ifBlank { null }

            if (title == null && artworkUrl == null) return@withContext null

            TrackMetadata(
                title = title,
                artist = artist,
                album = album,
                artworkUrl = artworkUrl,
                hasEmbeddedArt = false,
                source = "deezer"
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- Shared HTTP helper ----------------------------------------------------------------

    private fun get(url: URL): String? {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
