package com.djkaylfromdownunder.musicplayer.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "MetadataStore"
// The metadata folder used to be named "metadata" (lowercase) - see migrateOldFolderName.
private const val OLD_METADATA_FOLDER_NAME = "metadata"
private const val METADATA_FILE_NAME = "track_metadata.json"

/**
 * Enriched metadata for a track, either read from embedded tags or fetched online.
 * We never rewrite the user's original audio files - all enrichment lives here,
 * keyed by the track's content URI, and is overlaid onto Track objects for display.
 */
data class TrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,   // remote URL (Cover Art Archive) if fetched online
    val hasEmbeddedArt: Boolean = false, // true if art came from the file itself
    val source: String // "embedded" | "musicbrainz" | "none"
)

/**
 * On-disk JSON cache mapping track URI -> TrackMetadata, so fetched titles/artwork don't
 * need to be re-downloaded every session. Lives inside the user's chosen music folder (a
 * "metadata" subfolder, alongside "background") once one is known, rather than app-private
 * internal storage - that way it survives an app uninstall/reinstall or a full device
 * flash, same as the music itself, instead of being wiped along with the rest of the app's
 * private data. Falls back to app-private storage only until a music folder is chosen.
 *
 * A process-wide singleton (via [getInstance]) - both MetadataViewModel (reads, for
 * display) and MetadataFetchService (writes, while fetching) need to see the exact same
 * in-memory cache, since they run as separate components that may be created independently
 * of each other.
 */
@SuppressLint("StaticFieldLeak") // held context is always applicationContext, see getInstance()
class MetadataStore private constructor(private val context: Context) {

    private val cache: MutableMap<String, TrackMetadata> = mutableMapOf()
    private val internalFallbackFile = File(context.filesDir, METADATA_FILE_NAME)

    // Set once a music folder is known (see attachRoot) - null means we're still reading
    // from/writing to the app-private fallback file.
    @Volatile private var externalFile: DocumentFile? = null

    init {
        if (internalFallbackFile.exists()) {
            mergeFrom(runCatching { internalFallbackFile.readText() }.getOrNull())
        }
    }

    @Synchronized
    fun get(trackUri: String): TrackMetadata? = cache[trackUri]

    /**
     * Updates the in-memory cache only - callers doing a batch of puts (e.g. fetching a
     * whole playlist) should call [flush] once afterwards rather than paying for a full
     * disk rewrite on every track. Use [put] instead for a single one-off update.
     */
    @Synchronized
    fun putInMemory(trackUri: String, metadata: TrackMetadata) {
        cache[trackUri] = metadata
    }

    @Synchronized
    fun put(trackUri: String, metadata: TrackMetadata) {
        cache[trackUri] = metadata
        save()
    }

    /** Persists whatever is currently in memory - call after a batch of [putInMemory] calls. */
    @Synchronized
    fun flush() {
        save()
    }

    /**
     * Points this store at the given music folder's "metadata" subfolder, migrating
     * anything already cached (e.g. from the old app-private file, or fetched before a
     * folder was chosen this session) into it, then persisting there from now on. Safe to
     * call every time the root folder is (re)established - app startup, and whenever the
     * user picks a new folder. Pass null when no folder is chosen (falls back to
     * app-private storage).
     */
    suspend fun attachRoot(rootUri: Uri?) = withContext(Dispatchers.IO) {
        if (rootUri == null) {
            externalFile = null
            return@withContext
        }
        try {
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext
            val folder = root.findFile(METADATA_FOLDER_NAME)?.takeIf { it.isDirectory }
                ?: migrateOldFolderName(root)
                ?: root.createDirectory(METADATA_FOLDER_NAME)
                ?: return@withContext
            val file = folder.findFile(METADATA_FILE_NAME)
                ?: folder.createFile("application/json", METADATA_FILE_NAME)
                ?: return@withContext

            val externalText = runCatching {
                context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            // External data (if any) overlays whatever's already cached in memory, then
            // that merged result becomes the new external file - this is what performs
            // the one-time migration off the old internal-storage file.
            mergeFrom(externalText)
            externalFile = file
            save()
            Log.i(TAG, "Attached metadata store at ${file.uri} (${cache.size} entries)")

            if (internalFallbackFile.exists()) {
                internalFallbackFile.delete()
            }
        } catch (e: Exception) {
            // SAF permission may have lapsed (e.g. after a flash, before the folder is
            // re-picked) - keep using whatever's already cached/internal for now.
            Log.w(TAG, "Couldn't attach metadata store under $rootUri, falling back to internal storage", e)
        }
    }

    /**
     * One-time migration: the metadata folder used to live under the lowercase name
     * "metadata". If that still exists and the current "MetaData" folder doesn't yet,
     * rename it in place rather than starting a fresh empty folder and silently losing
     * every track's already-cached metadata.
     */
    private fun migrateOldFolderName(root: DocumentFile): DocumentFile? {
        val old = root.findFile(OLD_METADATA_FOLDER_NAME)?.takeIf { it.isDirectory } ?: return null
        return if (old.renameTo(METADATA_FOLDER_NAME)) old else null
    }

    @Synchronized
    private fun mergeFrom(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val entry = obj.getJSONObject(key)
                cache[key] = TrackMetadata(
                    title = entry.optString("title").ifBlank { null },
                    artist = entry.optString("artist").ifBlank { null },
                    album = entry.optString("album").ifBlank { null },
                    artworkUrl = entry.optString("artworkUrl").ifBlank { null },
                    hasEmbeddedArt = entry.optBoolean("hasEmbeddedArt", false),
                    source = entry.optString("source", "none")
                )
            }
        }
    }

    @Synchronized
    private fun save() {
        val json = JSONObject()
        cache.forEach { (key, meta) ->
            val obj = JSONObject()
            obj.put("title", meta.title ?: "")
            obj.put("artist", meta.artist ?: "")
            obj.put("album", meta.album ?: "")
            obj.put("artworkUrl", meta.artworkUrl ?: "")
            obj.put("hasEmbeddedArt", meta.hasEmbeddedArt)
            obj.put("source", meta.source)
            json.put(key, obj)
        }
        val text = json.toString()

        val target = externalFile
        if (target != null) {
            runCatching {
                context.contentResolver.openOutputStream(target.uri, "wt")?.use {
                    it.write(text.toByteArray())
                }
            }.onFailure { Log.w(TAG, "Failed writing metadata store to ${target.uri}", it) }
        } else {
            runCatching { internalFallbackFile.writeText(text) }
                .onFailure { Log.w(TAG, "Failed writing internal fallback metadata store", it) }
        }
    }

    companion object {
        @Volatile private var instance: MetadataStore? = null

        fun getInstance(context: Context): MetadataStore =
            instance ?: synchronized(this) {
                instance ?: MetadataStore(context.applicationContext).also { instance = it }
            }
    }
}
