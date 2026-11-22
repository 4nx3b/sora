/*
 * The Listen Together port's playback bridge: the two directions between a
 * media3 MediaItem on sora's player and the ported Song model, in the shape of
 * Neiro's PlayerConnection extensions (MediaItem.toSong / Song.toMediaItem),
 * plus the LastPlayed door the queue stash falls back to.
 *
 * Sora's items always carry sora's own MediaMetadata as their tag, and are
 * built through the same MediaMetadata.toMediaItem() the rest of the app uses
 * (song edits, shareable artwork, the resolver's uri scheme included), so a
 * room's tracks play through exactly the same path as the listener's own.
 */

package com.mochyy.mochymusic.ported.together.playback

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ported.together.model.ArtistRef
import com.mochyy.mochymusic.ported.together.model.Song

fun MediaItem.toSong(): Song {
    val md = metadata
    return if (md != null) {
        Song(
            videoId = md.id,
            title = md.title,
            artist = md.artists.joinToString { it.name },
            thumbnailUrl = md.thumbnailUrl,
            durationText = md.duration.takeIf { it > 0 }?.let { seconds -> "%d:%02d".format(seconds / 60, seconds % 60) },
            artistId = md.artists.firstOrNull()?.id,
            artists = md.artists.map { ArtistRef(name = it.name, browseId = it.id) },
            albumId = md.album?.id,
            albumName = md.album?.title,
            isVideo = md.isVideoSong,
            isVideoOrigin = md.isVideoSong,
            setVideoId = md.setVideoId,
            isExplicit = md.explicit,
            localUri = md.localUri,
        )
    } else {
        Song(
            videoId = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        )
    }
}

fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata(
        id = videoId,
        title = title,
        artists = if (artist.isBlank()) {
            emptyList()
        } else {
            listOf(
                MediaMetadata.Artist(
                    id = artistId,
                    name = artist,
                ),
            )
        },
        duration = parseDurationSeconds(durationText),
        thumbnailUrl = thumbnailUrl,
        album = albumId?.let { id -> MediaMetadata.Album(id = id, title = albumName ?: title) },
        setVideoId = setVideoId,
        explicit = isExplicit == true,
        localUri = localUri,
    )
    return metadata.toMediaItem()
}

/** "m:ss" back to whole seconds; 0 when the text never carried a runtime. */
private fun parseDurationSeconds(durationText: String?): Int {
    if (durationText.isNullOrBlank()) return 0
    val parts = durationText.split(":")
    val numbers = parts.mapNotNull { it.trim().toIntOrNull() }
    if (numbers.size != parts.size || numbers.isEmpty()) return 0
    return numbers.reduceRight { acc, total -> acc * 60 + total }
}

/**
 * Neiro's last-played queue persistence, which the personal-queue stash consults
 * when the player is empty at the moment a room is joined. Sora keeps its own
 * persistent queue in its own format and does not expose a snapshot here, so
 * the door answers null: the stash simply has nothing to fall back to in that
 * corner, and the main path — stashing the live queue — is unaffected.
 */
object LastPlayed {
    fun load(): PartyPersonalQueueStash.Snapshot? = null
}
