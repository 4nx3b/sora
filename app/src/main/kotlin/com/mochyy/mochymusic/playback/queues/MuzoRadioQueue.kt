// A radio from Muzo Data's /ytm/autoqueue: the seed, then songs that follow it, each page excluding what it already gave.

package com.mochyy.mochymusic.playback.queues

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.muzo.MuzoEndpoints
import com.mochyy.mochymusic.muzo.MuzoTrack
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class MuzoRadioQueue(
    private val key: String,
    private val seedId: String,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private val seen = linkedSetOf(seedId)

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val page = MuzoEndpoints.autoqueue(key, seedId, PageSize, exclude = seen) ?: error("Muzo radio is unavailable")
        val tracks = page.tracks.filter { seen.add(it.id) }
        if (tracks.isEmpty()) error("Muzo radio came back empty")
        continuation = page.continuation
        val seed = preloadItem?.toMediaItem()
        Queue.Status(title = null, items = listOfNotNull(seed) + tracks.map { it.toMediaItem() }, mediaItemIndex = 0)
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        val page = MuzoEndpoints.autoqueue(key, seedId, PageSize, exclude = seen, continuation = continuation)
        continuation = page?.continuation
        page?.tracks.orEmpty().filter { seen.add(it.id) }.map { it.toMediaItem() }
    }

    private fun MuzoTrack.toMediaItem(): MediaItem = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
        duration = durationSec,
        thumbnailUrl = thumbnail,
        album = album?.let { MediaMetadata.Album(id = "", title = it) },
    ).toMediaItem()

    private companion object {
        const val PageSize = 25
    }
}
