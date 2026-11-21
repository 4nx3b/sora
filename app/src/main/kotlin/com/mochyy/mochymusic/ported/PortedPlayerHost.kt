// Hosts Neiro's Apple Music (flamingo) and TikTok styles inside sora's player sheet, modelled on Neiro's
// archivetuneplayer ui/player/Player.kt (the FlamingoPlayerContent / TikTokPlayerContent call sites): the same
// state collected from the player, the same locals provided, Neiro's own menu sheet and page sheet drawn over it.

package com.mochyy.mochymusic.ported

import android.content.res.Configuration
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.ported.bridge.BottomSheetMenu
import com.mochyy.mochymusic.ported.bridge.BottomSheetPage
import com.mochyy.mochymusic.ported.bridge.BottomSheetPageState
import com.mochyy.mochymusic.ported.bridge.LocalBottomSheetPageState
import com.mochyy.mochymusic.ported.bridge.LocalLiquidGlassTuning
import com.mochyy.mochymusic.ported.bridge.LocalMenuState
import com.mochyy.mochymusic.ported.bridge.LocalStableSystemBarsTopPadding
import com.mochyy.mochymusic.ported.bridge.LocalVideoArtworkState
import com.mochyy.mochymusic.ported.bridge.MenuState
import com.mochyy.mochymusic.ported.bridge.rememberPortedCanvas
import com.mochyy.mochymusic.ported.bridge.rememberPortedNavController
import com.mochyy.mochymusic.ported.bridge.rememberVideoArtworkState
import com.mochyy.mochymusic.ported.flamingo.FlamingoPlayerContent
import com.mochyy.mochymusic.ported.theme.PortedTypography
import com.mochyy.mochymusic.ported.theme.SFProDisplay
import com.mochyy.mochymusic.ported.tiktok.TikTokPlayerContent
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.liquid.LocalLiquidGlass
import com.mochyy.mochymusic.ui.player.MusicVolume
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.rememberDurationMs
import com.mochyy.mochymusic.ui.player.styles.rememberLivePosition
import kotlinx.coroutines.delay

@Composable
fun PortedPlayerHost(style: PlayerStyle, actions: StyleActions, modifier: Modifier, preview: Boolean) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = PortedTypography) {
        val connection = LocalPlayerConnection.current
        val sheet = LocalPlayerBottomSheetState.current
        val metadata = connection?.mediaMetadata?.collectAsState()?.value
        if (preview || connection == null || sheet == null || metadata == null) {
            PortedStillFrame(style, metadata?.thumbnailUrl, metadata?.title, metadata?.artists?.joinToString { it.name }, modifier)
            return@MaterialTheme
        }

        val context = LocalContext.current
        val playbackState by connection.playbackState.collectAsState()
        val isPlaying by connection.isPlaying.collectAsState()
        val canSkipPrevious by connection.canSkipPrevious.collectAsState()
        val canSkipNext by connection.canSkipNext.collectAsState()
        val currentSong by connection.currentSong.collectAsState(null)
        val currentFormat by connection.currentFormat.collectAsState(null)
        val isLoading = playbackState == Player.STATE_BUFFERING
        val live = rememberLivePosition()
        val duration = rememberDurationMs()
        var sliderPosition by remember { mutableStateOf<Long?>(null) }
        val onSlide: (Long) -> Unit = { sliderPosition = it }
        val onSlideDone: () -> Unit = {
            sliderPosition?.let { connection.player.seekTo(it) }
            sliderPosition = null
        }

        val open = sheet.isExpanded
        val navController = rememberPortedNavController(actions)
        val menuState = remember { MenuState() }
        val pageState = remember { BottomSheetPageState() }
        val (canvasPrimary, canvasFallback) = rememberPortedCanvas(
            mediaId = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString { it.name },
            durationSec = metadata.duration.takeIf { it > 0 },
            enabled = open,
        )
        val video = rememberVideoArtworkState(metadata, isPlaying, enabled = open)
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Neiro reads the status bar's shown height (its KeepStatusBarHiddenInDialog keeps the
        // bar hidden without changing the number); the cutout is unioned in because with the bar
        // hidden the window draws into the camera strip (drawUnderCutout SHORT_EDGES) and the
        // cutout inset is the one that never lies about where the notch is.
        val stableTopInset = maxOf(
            com.mochyy.mochymusic.ui.utils.stableStatusBarsAsTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
        )
        CompositionLocalProvider(
            LocalMenuState provides menuState,
            LocalBottomSheetPageState provides pageState,
            LocalVideoArtworkState provides video,
            LocalLiquidGlassTuning provides LocalLiquidGlass.current.tuning,
            LocalStableSystemBarsTopPadding provides stableTopInset,
        ) {
            Box(modifier.fillMaxSize()) {
                when (style) {
                    PlayerStyle.TIKTOK -> {
                        // Neiro's TikTok takes the position as a value; it ticks only while the sheet is open
                        var position by remember { mutableLongStateOf(connection.player.currentPosition) }
                        LaunchedEffect(open, isPlaying) {
                            position = connection.player.currentPosition
                            while (open && isPlaying) {
                                delay(200)
                                position = connection.player.currentPosition
                            }
                        }
                        TikTokPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = connection,
                            navController = navController,
                            state = sheet,
                            menuState = menuState,
                            bottomSheetPageState = pageState,
                            canvasPrimaryUrl = canvasPrimary,
                            canvasFallbackUrl = canvasFallback,
                            onSeek = onSlide,
                            onSeekFinished = onSlideDone,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        val volume = remember { MusicVolume(context.getSystemService(AudioManager::class.java)) }
                        FlamingoPlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            positionProvider = { live.positionMs() },
                            duration = duration,
                            playerConnection = connection,
                            navController = navController,
                            state = sheet,
                            bottomSheetPageState = pageState,
                            currentSongLiked = currentSong?.song?.liked == true,
                            volume = volume.shown,
                            onVolumeChange = {
                                volume.drag(it)
                                volume.release()
                            },
                            canvasPrimaryUrl = canvasPrimary,
                            canvasFallbackUrl = canvasFallback,
                            currentFormat = currentFormat,
                            contentBottomPadding = 0.dp,
                            onSliderValueChange = onSlide,
                            onSliderValueChangeFinished = onSlideDone,
                            modifier = Modifier.fillMaxSize(),
                            landscape = landscape,
                        )
                    }
                }
                BottomSheetMenu(state = menuState)
                BottomSheetPage(state = pageState)
            }
        }
    }
}

// the layout selector's card: a still frame in the style's own type, starting no video, canvas or frame loop
@Composable
private fun PortedStillFrame(style: PlayerStyle, artwork: String?, title: String?, artist: String?, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp),
                alpha = 0.6f,
            )
        }
        Column(Modifier.align(if (style == PlayerStyle.TIKTOK) Alignment.BottomStart else Alignment.Center).padding(24.dp)) {
            if (artwork != null && style != PlayerStyle.TIKTOK) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = title.orEmpty(),
                color = Color.White,
                fontFamily = SFProDisplay,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                text = artist.orEmpty(),
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = SFProDisplay,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
