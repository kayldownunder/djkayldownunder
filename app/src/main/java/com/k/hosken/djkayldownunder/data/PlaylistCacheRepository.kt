package com.k.hosken.djkayldownunder.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "PlaylistCache"
private const val CACHE_FILE_NAME = "playlist_cache.json"

/**
 * On-disk cache of the last full library scan (see MusicFolderRepository.scanPlaylists), so
 * the Library tab's "Random Skip All Albums" shortcut etc. can appear immediately on app
 * startup instead of waiting for a fresh recursive SAF walk of the whole tree to finish - see
 * MusicLibraryViewModel.init. Purely a "show something now" optimization: a real scan always
 * follows and overwrites it once done, so a file deleted/renamed outside the app only really
 * drops out once that completes.
 */
class PlaylistCacheRepository(context: Context) {

    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

    fun load(): List<Playlist> = runCatching {
        if (!cacheFile.exists()) return emptyList()
        val arr = JSONArray(cacheFile.readText())
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val tracksArr = obj.getJSONArray("tracks")
            val tracks = (0 until tracksArr.length()).map { j ->
                val t = tracksArr.getJSONObject(j)
                Track(
                    uri = Uri.parse(t.getString("uri")),
                    displayName = t.getString("displayName"),
                    fileName = t.getString("fileName"),
                    sizeBytes = t.optLong("sizeBytes", 0L),
                    mimeType = t.optString("mimeType").ifBlank { null }
                )
            }
            Playlist(
                folderUri = Uri.parse(obj.getString("folderUri")),
                name = obj.getString("name"),
                tracks = tracks
            )
        }
    }.onFailure {
        Log.w(TAG, "Failed reading playlist cache", it)
    }.getOrDefault(emptyList())

    fun save(playlists: List<Playlist>) {
        runCatching {
            val arr = JSONArray()
            playlists.forEach { playlist ->
                val tracksArr = JSONArray()
                playlist.tracks.forEach { track ->
                    tracksArr.put(
                        JSONObject().apply {
                            put("uri", track.uri.toString())
                            put("displayName", track.displayName)
                            put("fileName", track.fileName)
                            put("sizeBytes", track.sizeBytes)
                            put("mimeType", track.mimeType ?: "")
                        }
                    )
                }
                arr.put(
                    JSONObject().apply {
                        put("folderUri", playlist.folderUri.toString())
                        put("name", playlist.name)
                        put("tracks", tracksArr)
                    }
                )
            }
            cacheFile.writeText(arr.toString())
        }.onFailure {
            Log.w(TAG, "Failed writing playlist cache", it)
        }
    }
}
