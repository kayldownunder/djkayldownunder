package com.djkaylfromdownunder.musicplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.djkaylfromdownunder.musicplayer.data.LocalMetadataReader
import com.djkaylfromdownunder.musicplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small in-memory cache of decoded (and downsampled) embedded-art thumbnails, keyed by
 * track URI. Without this, every list/grid recomposition or scroll re-triggers a fresh
 * MediaMetadataRetriever read + bitmap decode for the same track, which is the single
 * biggest source of jank when browsing a large library.
 */
private object EmbeddedArtCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/** Decodes embedded art downsampled to roughly [targetSizePx], instead of at full resolution. */
private fun decodeSampledBitmap(bytes: ByteArray, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetSizePx && bounds.outHeight / (sampleSize * 2) >= targetSizePx) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * Shows a track's artwork: a remote fetched image if metadata was fetched online,
 * embedded art decoded from the file itself, or a placeholder note icon.
 *
 * Watches MetadataViewModel's fetch progress so the artwork automatically appears
 * once fetching completes, without needing a manual refresh.
 */
@Composable
fun TrackArtwork(track: Track?, metadataViewModel: MetadataViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Collecting progress here means this composable recomposes as each track finishes
    // fetching, so freshly-fetched artwork appears without a manual screen refresh.
    val progress by metadataViewModel.progress.collectAsState()
    val metadata = track?.let { metadataViewModel.metadataFor(it.uri.toString()) }
    val trackUriStr = track?.uri?.toString()

    // Seed synchronously from the cache so already-decoded artwork doesn't flash blank
    // while scrolling a track back into view.
    var embeddedBitmap by remember(trackUriStr) {
        mutableStateOf(trackUriStr?.let { EmbeddedArtCache.get(it) })
    }

    LaunchedEffect(track?.uri, metadata?.hasEmbeddedArt) {
        if (track != null && metadata?.hasEmbeddedArt == true) {
            val key = track.uri.toString()
            val cached = EmbeddedArtCache.get(key)
            if (cached != null) {
                embeddedBitmap = cached
            } else {
                // Reading the file and decoding the bitmap are both blocking I/O/CPU work -
                // keep them off the main thread so scrolling stays smooth.
                val decoded = withContext(Dispatchers.IO) {
                    val bytes = LocalMetadataReader(context).readEmbeddedArt(track.uri)
                    bytes?.let { decodeSampledBitmap(it, targetSizePx = 256) }?.asImageBitmap()
                }
                if (decoded != null) EmbeddedArtCache.put(key, decoded)
                embeddedBitmap = decoded
            }
        } else {
            embeddedBitmap = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            metadata?.artworkUrl != null -> {
                AsyncImage(
                    model = metadata.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            embeddedBitmap != null -> {
                Image(
                    bitmap = embeddedBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
