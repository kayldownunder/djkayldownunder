package com.djkaylfromdownunder.musicplayer.metadata

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.djkaylfromdownunder.musicplayer.MainActivity
import com.djkaylfromdownunder.musicplayer.data.LocalMetadataReader
import com.djkaylfromdownunder.musicplayer.data.MetadataStore
import com.djkaylfromdownunder.musicplayer.data.OnlineMetadataFetcher
import com.djkaylfromdownunder.musicplayer.data.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MetadataFetchProgress(
    val isRunning: Boolean = false,
    val playlistName: String? = null,   // which playlist is currently being fetched
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val currentTrackName: String? = null,
    // Batch ("Fetch All") specific fields — 0/0 when not running a batch.
    val isBatch: Boolean = false,
    val playlistIndex: Int = 0,
    val totalPlaylists: Int = 0
)

/**
 * Runs metadata fetching (embedded tags first, then online lookups) as a foreground
 * service rather than a plain ViewModel coroutine. A ViewModel-scoped coroutine dies the
 * moment the screen locks or Android's background-execution limits kick in - both routine
 * on most devices (especially Samsung's aggressive battery management) - which is why a
 * "Fetch All" pass on a large library could silently stop partway through. A foreground
 * service, with its own lifecycle independent of any Activity/ViewModel, keeps running
 * through both.
 *
 * The batch to run is handed over via [pendingBatch] (an in-memory reference, not Intent
 * extras) since this is a same-process service - there's no need to serialize a
 * potentially large track list through a Binder transaction just to hand it to a
 * component living in the same process.
 */
class MetadataFetchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var localReader: LocalMetadataReader
    private lateinit var onlineFetcher: OnlineMetadataFetcher
    private lateinit var store: MetadataStore

    override fun onCreate() {
        super.onCreate()
        localReader = LocalMetadataReader(this)
        onlineFetcher = OnlineMetadataFetcher()
        store = MetadataStore.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called within seconds of startForegroundService() regardless of
        // whether there's actually work to do, or the system kills the app for it.
        startForegroundCompat(buildNotification("Fetching song info…", 0, 0))

        val batch = pendingBatch
        pendingBatch = null

        if (batch.isNullOrEmpty()) {
            stopSelfCleanly(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                batch.forEachIndexed { index, playlist ->
                    runOnePlaylist(
                        playlist = playlist,
                        isBatch = batch.size > 1,
                        playlistIndex = index + 1,
                        totalPlaylists = batch.size
                    )
                }
            } finally {
                _progress.value = _progress.value.copy(
                    isRunning = false,
                    isBatch = false,
                    currentTrackName = null
                )
                stopSelfCleanly(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runOnePlaylist(
        playlist: Playlist,
        isBatch: Boolean,
        playlistIndex: Int,
        totalPlaylists: Int
    ) {
        _progress.value = _progress.value.copy(
            isRunning = true,
            playlistName = playlist.name,
            totalTracks = playlist.tracks.size,
            completedTracks = 0,
            isBatch = isBatch,
            playlistIndex = playlistIndex,
            totalPlaylists = totalPlaylists
        )

        playlist.tracks.forEachIndexed { index, track ->
            _progress.value = _progress.value.copy(currentTrackName = track.displayName)

            val trackUriStr = track.uri.toString()
            if (store.get(trackUriStr) == null) {
                val local = withContext(Dispatchers.IO) { localReader.read(track.uri) }
                if (local != null && (local.title != null || local.hasEmbeddedArt)) {
                    store.putInMemory(trackUriStr, local)
                } else {
                    val online = onlineFetcher.fetch(track.displayName)
                    if (online != null) {
                        store.putInMemory(trackUriStr, online)
                    }
                }
            }

            _progress.value = _progress.value.copy(completedTracks = index + 1)

            // Flush periodically rather than only at the end of the playlist, so an
            // interrupted fetch only loses a handful of tracks' worth of progress.
            if ((index + 1) % 10 == 0) {
                withContext(Dispatchers.IO) { store.flush() }
            }

            updateNotification(playlist.name, index + 1, playlist.tracks.size)
        }

        withContext(Dispatchers.IO) { store.flush() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopSelfCleanly(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String, completed: Int, total: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fetching song info")
            .setContentText(if (total > 0) "$text ($completed/$total)" else text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setProgress(total, completed, total == 0)
            .build()
    }

    private fun updateNotification(playlistName: String, completed: Int, total: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(playlistName, completed, total))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Metadata sync", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "metadata_fetch"
        private const val NOTIFICATION_ID = 42

        @Volatile private var pendingBatch: List<Playlist>? = null

        private val _progress = MutableStateFlow(MetadataFetchProgress())
        val progress: StateFlow<MetadataFetchProgress> = _progress.asStateFlow()

        /** Starts fetching a single playlist as a foreground service. */
        fun fetchPlaylist(context: Context, playlist: Playlist) {
            pendingBatch = listOf(playlist)
            ContextCompat.startForegroundService(context, Intent(context, MetadataFetchService::class.java))
        }

        /**
         * Starts fetching every given playlist, one at a time (sequential on purpose - see
         * OnlineMetadataFetcher, running these in parallel risks getting rate-limited).
         */
        fun fetchAll(context: Context, playlists: List<Playlist>) {
            pendingBatch = playlists
            ContextCompat.startForegroundService(context, Intent(context, MetadataFetchService::class.java))
        }
    }
}
