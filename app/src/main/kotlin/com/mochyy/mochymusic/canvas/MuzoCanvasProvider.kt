// MuzoCanvasProvider.kt
//
// Muzo Data's canvas loops, answered from one video id. Ported from Neiro's
// MuzoCanvasProvider (archivetune/canvas): the one canvas source keyed on the video id itself,
// so it can never hand back the wrong song's loop. The whole lookup is one keyless HTTP GET —
// the service injects its own auth on the stream route, which is why this provider carries no
// token plumbing of any kind (unlike SpotifyCanvasProvider). A key set in the app's Muzo Data
// settings is for the JSON endpoints — streams and lyrics — and is deliberately not needed here.
//
// The verdict is a small probe, not a download: a ranged GET of the first few bytes confirms the
// loop exists (206 video/mp4 from a server that honours Range) before the resolver commits to
// playing the URL. Redirects are left unfollowed on purpose — the service answers a video
// without a canvas with a 302 to a thumbnail host, and chasing that would hand the video player
// an image under a videoUrl field, which is the one failure mode this provider exists to
// prevent. Anything that is not a direct video answer is simply no canvas, exactly as every
// other provider here treats its misses.

package com.mochyy.mochymusic.canvas

import com.mochyy.mochymusic.utils.followAppProxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object MuzoCanvasProvider {
    private const val STREAM_URL = "https://data.kouzu.in/api/v1/canvas/stream"

    /** Hits outlive most sessions; misses are worth a retry within one. */
    private const val CACHE_TTL_MS = 60L * 60 * 1000
    private const val NEGATIVE_CACHE_TTL_MS = 15L * 60 * 1000

    /** How much of the loop the probe pulls: enough to prove the file, no more. */
    private const val PROBE_RANGE = "bytes=0-31"

    // The probe is a presence check, not a download attempt — a miss must read as a miss, never
    // as an exception for the resolver to carry. Redirects stay unanswered for the reason in the
    // class note: the 302 goes to a thumbnail host, not to a loop.
    private val client = OkHttpClient.Builder()
        .followAppProxy()
        .followRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * The looping canvas video for [videoId], or null when Muzo Data has none for it. The URL
     * handed back is the service's own stream route, which the existing video artwork players
     * open directly — the same arrangement the Spotify canvas uses for its videoUrl.
     */
    suspend fun getByVideoId(
        videoId: String,
        songTitle: String? = null,
        artistName: String? = null,
    ): CanvasArtwork? {
        if (videoId.isBlank()) return null

        cache[videoId]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(videoId)
        }

        val artwork = withContext(Dispatchers.IO) {
            try {
                probe(videoId, songTitle, artistName)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                null
            }
        }
        // A miss is cached like a hit: "this video has no Muzo loop" is the common case for any
        // catalogue id, and without the negative entry every revisit would pay for the probe
        // again to learn the same thing.
        val ttl = if (artwork != null) CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
        cache[videoId] = CacheEntry(artwork, System.currentTimeMillis() + ttl)
        return artwork
    }

    private fun probe(
        videoId: String,
        songTitle: String?,
        artistName: String?,
    ): CanvasArtwork? {
        val request = Request.Builder()
            .url("$STREAM_URL?id=$videoId")
            .header("Range", PROBE_RANGE)
            .build()
        client.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type")?.lowercase().orEmpty()
            val exists = response.code == 200 || response.code == 206
            // A 200 with no content type is still treated as video: the bytes are the file's own
            // header and the player is the authority on whether it decodes. Anything that names
            // a non-video type, or the thumbnail redirect, is not a loop.
            if (!exists || (contentType.isNotEmpty() && !contentType.contains("video/"))) {
                return null
            }
            return CanvasArtwork(
                name = songTitle,
                artist = artistName,
                albumId = "yt:$videoId",
                albumName = null,
                static = null,
                animated = null,
                videoUrl = "$STREAM_URL?id=$videoId",
            )
        }
    }
}
