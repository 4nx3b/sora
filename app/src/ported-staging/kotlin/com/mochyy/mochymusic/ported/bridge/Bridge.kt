// The ArchiveTune types, locals and small helpers the vendored Neiro styles import, mapped onto sora's own.
// Only files in ported/bridge reach into com.mochyy.mochymusic outside ported/.

package com.mochyy.mochymusic.ported.bridge

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.mochyy.mochymusic.extensions.metadata as soraMetadata
import com.mochyy.mochymusic.extensions.move as soraMove
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias PlayerConnection = com.mochyy.mochymusic.playback.PlayerConnection
typealias MediaMetadata = com.mochyy.mochymusic.models.MediaMetadata
typealias FormatEntity = com.mochyy.mochymusic.db.entities.FormatEntity
typealias ArtistEntity = com.mochyy.mochymusic.db.entities.ArtistEntity
typealias Artist = com.mochyy.mochymusic.db.entities.Artist
typealias SleepTimer = com.mochyy.mochymusic.playback.SleepTimer
typealias BottomSheetState = com.mochyy.mochymusic.ui.component.BottomSheetState
typealias BottomSheetPageState = com.mochyy.mochymusic.ui.component.PopupSheetState
typealias LiquidGlassTuning = com.mochyy.mochymusic.ui.component.liquid.LiquidGlassTuning

val LocalPlayerConnection = com.mochyy.mochymusic.LocalPlayerConnection
val LocalDatabase = com.mochyy.mochymusic.LocalDatabase
val LocalSyncUtils = com.mochyy.mochymusic.LocalSyncUtils
val LocalBottomSheetPageState = com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState

val LocalAnimationsDisabled = staticCompositionLocalOf { false }
val LocalStableSystemBarsTopPadding = compositionLocalOf<Dp> { 0.dp }
val LocalLyricsScrollListener = compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalLiquidGlassTuning = compositionLocalOf { LiquidGlassTuning() }

object PreferenceStore {
    fun <T> get(key: Preferences.Key<T>): T? = com.mochyy.mochymusic.utils.PreferencesCache.snapshot.value[key]
}

object AutoMixUiState {
    val mixing = MutableStateFlow(false)
}

object YouTube {
    suspend fun artist(browseId: String) = com.music.innertube.YouTube.artist(browseId)

    // sora's innertube exposes no like count, so the rail hides it rather than showing a made-up number
    suspend fun getLikeCount(@Suppress("UNUSED_PARAMETER") videoId: String): Result<Int?> = Result.success(null)
}

object LyricsEntity {
    const val LYRICS_NOT_FOUND = com.mochyy.mochymusic.db.entities.LyricsEntity.LYRICS_NOT_FOUND

    enum class Source(val value: String) {
        REMOTE("REMOTE"),
        EMBEDDED("EMBEDDED"),
        USER_SELECTION("USER_SELECTION"),
        USER_EDIT("USER_EDIT"),
        AI_TRANSLATION("AI_TRANSLATION"),
    }
}

val com.mochyy.mochymusic.db.entities.LyricsEntity.source: String get() = provider

val MediaItem.metadata: MediaMetadata? get() = soraMetadata

fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int): MutableList<T> = soraMove(fromIndex, toIndex)

val MediaMetadata.isMusicVideo: Boolean get() = isVideoSong

val PlayerConnection.localPlayer: Player get() = player

val com.mochyy.mochymusic.playback.MusicService.crossfadeMixing: StateFlow<Boolean> get() = AutoMixUiState.mixing

fun Player.togglePlayPause() {
    if (playbackState == Player.STATE_IDLE) prepare()
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}

fun makeTimeString(duration: Long?): String = com.mochyy.mochymusic.utils.makeTimeString(duration)

// Neiro's FormatEntity helpers, verbatim
fun FormatEntity.isLossless(): Boolean {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    return rawCodec.contains("FLAC") || rawCodec.contains("ALAC")
}

fun FormatEntity.codecLabel(): String {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    val rawMime = mimeType.substringAfter("/").substringBefore(";").uppercase()

    return when {
        rawCodec.contains("FLAC") || rawCodec.contains("ALAC") -> "Lossless"
        rawCodec.contains("OPUS") -> "OPUS"
        rawCodec.contains("AAC") || rawCodec.contains("MP4A") -> "AAC"
        rawCodec.contains("VORBIS") -> "VORBIS"
        rawMime.contains("AAC") || rawMime.contains("MP4A") -> "AAC"
        rawMime.contains("VORBIS") -> "VORBIS"
        rawMime.isNotBlank() -> rawMime
        else -> rawCodec
    }
}

object LyricsUtils {
    private val LrcLine = Regex("""^\[\d{1,3}:\d{2}([.:]\d{1,3})?].*""")

    fun isTtml(lyrics: String): Boolean = lyrics.trimStart().let { it.startsWith("<") && it.contains("<tt") }

    fun isLineSyncedLrc(lyrics: String): Boolean = lyrics.lineSequence().any { LrcLine.matches(it.trim()) }

    // sora keeps translations beside the lyrics, never inside them
    fun hasTranslation(@Suppress("UNUSED_PARAMETER") lyrics: String): Boolean = false

    fun shouldAutoTranslate(lyrics: String, targetLanguage: String, excludedLanguageCodes: Set<String>): Boolean =
        lyrics.isNotBlank() && targetLanguage.isNotBlank() && targetLanguage !in excludedLanguageCodes
}

class LyricsMenuViewModel : androidx.lifecycle.ViewModel() {
    val translationDismissedMediaIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())

    @Suppress("UNUSED_PARAMETER")
    fun translateLyricsWithAi(mediaMetadata: MediaMetadata, lyrics: String, targetLanguage: String) {
        com.mochyy.mochymusic.lyrics.LyricsTranslationHelper.triggerManualTranslation()
    }
}

enum class VideoAspectRatio {
    FIT,
    CROP,
    STRETCH,
    FILL,
    ;

    fun toExoResizeMode(): Int = when (this) {
        FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        CROP -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
}
