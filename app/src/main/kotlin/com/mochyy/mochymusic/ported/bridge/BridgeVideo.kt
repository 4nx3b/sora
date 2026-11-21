// Neiro's video artwork names (VideoArtworkState, rememberVideoArtworkState, VideoArtworkSurface) on top of
// sora's own YouTube video session. Neiro's VideoArtworkPlayer sits on ArchiveTune's stream stack, so the
// ported styles get sora's player behind the same names instead. With no video found, every state reads
// "not playing", and the styles keep showing the artwork.

package com.mochyy.mochymusic.ported.bridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.ui.player.SongVideo
import com.mochyy.mochymusic.ui.player.rememberSongVideo
import com.mochyy.mochymusic.ui.player.video.DriveYouTubeVideo
import com.mochyy.mochymusic.ui.player.video.YouTubeVideoSession
import com.mochyy.mochymusic.ui.player.video.YouTubeVideoSurface
import com.mochyy.mochymusic.ui.player.video.rememberYouTubeVideoSession

// auto quality the inline video asks for: the artwork slot is never bigger than a phone's width
private const val InlineVideoAutoCap = 1080

// how many times the links may go stale before the style gives up on the video and shows the artwork
private const val GiveUpAfterFailures = 3

@Stable
class VideoArtworkState internal constructor(
    internal val video: SongVideo,
    internal val session: YouTubeVideoSession,
) {
    internal var isPlaying by mutableStateOf(false)
    private var resyncs by mutableIntStateOf(0)

    val streamUrl: String? get() = video.videoId
    val isVideoReady: Boolean get() = session.hasFrame
    val isResolvingUrl: Boolean get() = !session.hasFrame && !hasPlaybackFailed
    val isResyncing: Boolean get() = session.hasFrame && session.isStalled
    val hasPlaybackFailed: Boolean get() = session.failures >= GiveUpAfterFailures + resyncs * GiveUpAfterFailures
    val videoAspectRatio: Float? get() = session.aspect.takeIf { it > 0f }

    fun requestResync(position: Long, isPlaying: Boolean) {
        // Neiro's signature, verbatim call shape; sora's video session drives itself,
        // so the resync only counts towards the give-up budget.
        resyncs++
    }
}

// the video for the song on the player, or null when it has none (or the style is only a preview)
@Composable
fun rememberVideoArtworkState(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    enabled: Boolean,
): VideoArtworkState? {
    val video = rememberSongVideo(
        mediaId = mediaMetadata.id,
        title = mediaMetadata.title,
        artist = mediaMetadata.artists.joinToString { it.name },
        isVideoSong = mediaMetadata.isVideoSong,
        enabled = enabled,
    ) ?: return null
    if (!enabled) return null
    val session = rememberYouTubeVideoSession(video.videoId)
    DriveYouTubeVideo(session, video, isPlaying, autoCap = InlineVideoAutoCap)
    val state = remember(video, session) { VideoArtworkState(video, session) }
    state.isPlaying = isPlaying
    return state
}

@Composable
fun VideoArtworkSurface(
    state: VideoArtworkState,
    @Suppress("UNUSED_PARAMETER") resizeMode: Int,
    @Suppress("UNUSED_PARAMETER") ambientMode: Boolean,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (!state.isVideoReady && thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        YouTubeVideoSurface(
            session = state.session,
            isPlaying = state.isPlaying,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
