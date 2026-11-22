// YouTubeSubtitleLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.music.innertube.YouTube
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    // Must match the registry key, not the display name: this string is what gets persisted on
    // LyricsEntity.provider and later fed back through LyricsProviderRegistry, which only knows
    // the un-spaced form. See LyricsProviderRegistry.getDisplayName for the pretty version.
    override val name = "YouTubeSubtitle"

    override fun isEnabled(context: Context) = context.dataStore[com.mochyy.mochymusic.constants.EnableYouTubeSubtitleLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
