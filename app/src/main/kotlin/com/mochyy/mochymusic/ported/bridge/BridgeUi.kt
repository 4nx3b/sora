// The composables the vendored Neiro styles call that sora already has: each one hands over to sora's own
// (menus, add-to-playlist, media info, queue rows, canvas, lyrics, drift, preferences, liquid glass).

package com.mochyy.mochymusic.ported.bridge

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavController
import com.mochyy.mochymusic.ui.player.driftArtworkSlot as soraDriftArtworkSlot

// ---- preferences

@Composable
fun <T> rememberPreference(key: Preferences.Key<T>, defaultValue: T): MutableState<T> =
    com.mochyy.mochymusic.utils.rememberPreference(key, defaultValue)

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(key: Preferences.Key<String>, defaultValue: T): MutableState<T> =
    com.mochyy.mochymusic.utils.rememberEnumPreference(key, defaultValue)

@Composable
fun rememberLowDataModeActive(): Boolean = com.mochyy.mochymusic.utils.rememberDataSaverActive()

// ---- liquid glass

@Composable
fun rememberLiquidGlassEnabled(): Boolean = com.mochyy.mochymusic.ui.component.liquid.LocalLiquidGlass.current.enabled

// ---- drift: the one artwork flight every sora style shares

fun Modifier.driftArtworkSlot(active: Boolean = true, cornerDp: Float): Modifier =
    soraDriftArtworkSlot(corner = cornerDp.dp, active = active)

// ---- cast: sora has none, so the output button falls through to the system output picker

class NewAction(val onClick: () -> Unit)

@Composable
fun rememberCastPlayerMenuAction(@Suppress("UNUSED_PARAMETER") renderSheet: Boolean = true): NewAction? = null

// ---- menus. sora's player menu is a sheet over the whole player, so the vendored menu calls ask the host
// to open it there instead of nesting it inside Neiro's own menu sheet.

@Stable
class PortedMenus {
    var playerMenuOpen by mutableStateOf(false)
    var lyricsMenuOpen by mutableStateOf(false)
}

val LocalPortedMenus = staticCompositionLocalOf { PortedMenus() }

@Composable
fun PlayerMenu(
    @Suppress("UNUSED_PARAMETER") mediaMetadata: MediaMetadata,
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    @Suppress("UNUSED_PARAMETER") playerBottomSheetState: BottomSheetState,
    @Suppress("UNUSED_PARAMETER") isQueueTrigger: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onShowDetailsDialog: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val menus = LocalPortedMenus.current
    LaunchedEffect(Unit) {
        menus.playerMenuOpen = true
        onDismiss()
    }
}

@Composable
fun AnchoredLyricsOverflowMenu(
    @Suppress("UNUSED_PARAMETER") iconBoundsInRoot: Rect,
    @Suppress("UNUSED_PARAMETER") lyricsProvider: () -> com.mochyy.mochymusic.db.entities.LyricsEntity?,
    @Suppress("UNUSED_PARAMETER") mediaMetadataProvider: () -> MediaMetadata,
    @Suppress("UNUSED_PARAMETER") lyricsSyncOffset: Int,
    @Suppress("UNUSED_PARAMETER") onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") backdrop: com.kyant.backdrop.Backdrop? = null,
    @Suppress("UNUSED_PARAMETER") showTranslationActions: Boolean = true,
) {
    val menus = LocalPortedMenus.current
    LaunchedEffect(Unit) {
        menus.lyricsMenuOpen = true
        onDismiss()
    }
}

// the host draws sora's player menu over the whole style when a vendored menu asked for it
@Composable
fun PortedMenuLayer(menus: PortedMenus) {
    if (menus.playerMenuOpen || menus.lyricsMenuOpen) {
        val fromLyrics = menus.lyricsMenuOpen
        com.mochyy.mochymusic.ui.player.menu.PlayerActionMenu(
            onDismiss = {
                menus.playerMenuOpen = false
                menus.lyricsMenuOpen = false
            },
            onEditPlayer = {},
            fromLyrics = fromLyrics,
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onAddComplete: ((Int, List<String>) -> Unit)? = null,
) {
    com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = { _ -> onGetSong() },
        onDismiss = onDismiss,
    )
}

@Composable
fun ShowMediaInfo(videoId: String) = com.mochyy.mochymusic.ui.utils.ShowMediaInfo(videoId)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    @Suppress("UNUSED_PARAMETER") shouldLoadImage: Boolean = true,
    @Suppress("UNUSED_PARAMETER") showActiveContainer: Boolean = true,
    @Suppress("UNUSED_PARAMETER") textColorOverride: Color? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    com.mochyy.mochymusic.ui.component.MediaMetadataListItem(
        mediaMetadata = mediaMetadata,
        modifier = modifier,
        isSelected = isSelected,
        isActive = isActive,
        isPlaying = isPlaying,
        trailingContent = trailingContent,
    )
}

// ---- canvas artwork (Spotify canvas loops) through sora's player

class CanvasLoopSync

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") resizeMode: Int = 0,
    @Suppress("UNUSED_PARAMETER") maxVideoEdgePx: Int = 0,
    @Suppress("UNUSED_PARAMETER") loopSyncLeader: CanvasLoopSync? = null,
    @Suppress("UNUSED_PARAMETER") loopSyncFollower: CanvasLoopSync? = null,
    visible: Boolean = true,
    @Suppress("UNUSED_PARAMETER") videoAlpha: Float = 1f,
    onPlaybackAvailabilityChange: ((Boolean) -> Unit)? = null,
    onFirstFrameRendered: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") refreshEpoch: Int = 0,
) {
    if (!visible) return
    com.mochyy.mochymusic.ui.player.CanvasArtworkPlayer(
        primaryUrl = primaryUrl,
        fallbackUrl = fallbackUrl,
        isPlaying = isPlaying,
        modifier = modifier,
        onReadyChange = { ready ->
            onPlaybackAvailabilityChange?.invoke(ready)
            if (ready) onFirstFrameRendered?.invoke()
        },
    )
}

// ---- lyrics: the one piece shared with sora on purpose, so every style sings with sora's lyric animations

@Composable
fun LyricsEnhanced(
    @Suppress("UNUSED_PARAMETER") sliderPositionProvider: () -> Long? = { null },
    @Suppress("UNUSED_PARAMETER") lyricsSyncOffset: Int = 0,
    @Suppress("UNUSED_PARAMETER") translationVisibleOverride: Boolean? = null,
    @Suppress("UNUSED_PARAMETER") phoneticVisibleOverride: Boolean? = null,
    textColorOverride: Color? = null,
    singleActiveLine: Boolean = false,
    textSizeOverride: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (singleActiveLine) {
        val lyrics = com.mochyy.mochymusic.ui.player.styles.rememberStyleLyrics(readings = true)
        com.mochyy.mochymusic.ui.player.styles.ExpressiveLyrics(
            lines = lyrics.lines,
            position = com.mochyy.mochymusic.ui.player.styles.rememberLivePosition(),
            color = textColorOverride ?: Color.White,
            baseSize = (textSizeOverride ?: 22f).sp,
            modifier = modifier,
        )
        return
    }
    BoxWithConstraints(modifier) {
        com.mochyy.mochymusic.ui.player.LyricsScreen(
            onClose = {},
            screenHeight = maxHeight,
            contentBottomInset = 0.dp,
            showHeader = false,
            onCoverBackdrop = true,
        )
    }
}

// ---- the locals and enum Neiro's vendored menu sheet and back handler read

enum class BackAnimationStyle { DEFAULT, PREDICTIVE }

val LocalGlassMenuContent = staticCompositionLocalOf { false }
val LocalLiquidGlassBackdrop = androidx.compose.runtime.compositionLocalOf<com.kyant.backdrop.Backdrop?> { null }
val LocalMenuGlassBackdrop = androidx.compose.runtime.compositionLocalOf<com.kyant.backdrop.Backdrop?> { null }
