// namida player style — the player itself, ported 1:1 from namida's
// packages/miniplayer_base.dart: the same colour-tinted panel card, the same top
// row (collapse arrow, index/total over the album name, the more chip), the
// artwork riding namida's geometry with swipe parallax, the waveform seek bar
// with drag-up-to-cancel, the controls (prev, the gradient play/pause circle,
// next) centred the way namida centres them, the bottom-left buttons row
// (repeat, lyrics, queue) and the track info with its like button — all placed
// by NamidaGeometry, namida's own math.

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.StyleFrame
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.rememberDurationMs
import com.mochyy.mochymusic.ui.player.styles.rememberLivePosition
import com.mochyy.mochymusic.ui.player.styles.rememberStyleLyrics
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTrack
import com.mochyy.mochymusic.ui.player.styles.styleInsets
import kotlin.math.abs
import kotlin.math.roundToInt

/** namida's _kTopBottomMargin. */
private val TopBottomMargin = 8.dp

/** namida's kSiParallax: how far the incoming artwork follows before it lands. */
private const val ImageParallax = 1.15f

@Composable
fun NamidaPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val connection = LocalPlayerConnection.current
    val seed = LocalArtworkColor.current
    val palette = rememberNamidaPalette(seed)
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val durationMs = rememberDurationMs()
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val playing = transport?.isPlaying == true
    val sheet = LocalPlayerBottomSheetState.current
    val lyrics = rememberStyleLyrics(readings = !preview)
    val queueItems = connection?.queueItems?.collectAsState()?.value
    val windowIndex = connection?.currentWindowIndex?.collectAsState()?.value ?: -1
    val metadata = connection?.mediaMetadata?.collectAsState()?.value
    val liked = metadata?.liked == true
    val repeatMode = connection?.repeatMode?.collectAsState()?.value

    // namida's expansion parameter: 1 = the player, 2 = the queue page sliding over it
    var pageTarget by remember { mutableFloatStateOf(1f) }
    val p by animateFloatAsState(
        targetValue = pageTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "namidaP",
    )
    var lyricsOpen by remember { mutableStateOf(false) }
    var draggingWave by remember { mutableStateOf(false) }
    var dragUpCancel by remember { mutableFloatStateOf(0f) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    CompositionLocalProvider(LocalNamidaPalette provides palette) {
        StyleFrame(
            preview, modifier,
            StyleTones(
                played = palette.currentColor,
                remaining = palette.waveBehind,
                ink = palette.textLarge,
                inkSoft = palette.textMedium,
                chip = palette.secondaryContainer,
            ),
            background = {
                // namida's scaffold behind the panel card
                Box(Modifier.fillMaxSize().background(palette.scaffold))
            },
        ) { landscape, menuOpen, setMenuOpen ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val insets = styleInsets(landscape)
                val geometry = remember { NamidaLayout() }
                geometry.update(
                    p.toDouble(),
                    NamidaLayout.Inputs(
                        screenWidth = maxWidth.value.toDouble(),
                        maxOffset = maxHeight.value.toDouble(),
                        topInset = insets.top.value.toDouble(),
                        bottomInset = insets.bottom.value.toDouble(),
                        navBarHeight = insets.bottom.value.toDouble(),
                        imageAspectRatio = 1.0,
                    ),
                )

                // ── the panel card: namida's rounded, colour-tinted panel ──
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(geometry.panelHeight.dp)
                        .graphicsLayer {
                            shape = RoundedCornerShape(
                                topStart = geometry.panelTopRadius.dp,
                                topEnd = geometry.panelTopRadius.dp,
                            )
                            clip = true
                        }
                        .drawBehind {
                            drawRect(palette.panelBackground)
                            drawRect(
                                Brush.verticalGradient(
                                    0f to palette.panelGradientTop,
                                    1f to palette.panelGradientBottom,
                                ),
                            )
                        },
                )

                // ── the top row: collapse, index/total + album, more ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(top = insets.top + TopBottomMargin)
                        .padding(horizontal = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.namida_arrow_down_2),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !preview) { pageTarget = 1f; sheet?.collapseSoft() }
                            .padding(8.dp),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !preview && metadata?.album?.id != null) {
                                metadata?.album?.id?.let(actions.onOpenAlbum)
                            }
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "${windowIndex + 1}/${queueItems?.size ?: 0}",
                            color = palette.onSecondaryContainer.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W500,
                            fontFamily = NamidaFontFamily,
                        )
                        Text(
                            text = track.album.ifBlank { track.title },
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = palette.onSecondaryContainer.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.namida_more),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(palette.secondaryContainer.copy(alpha = 0.2f))
                            .clickable(enabled = !preview) { setMenuOpen(!menuOpen) }
                            .padding(8.dp),
                    )
                }

                // ── the artwork: namida's image slot, swipeable with parallax ──
                val slot = geometry.imageSlot(1.0)
                var swipeX by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(slot.left.roundToPx(), slot.vOffset.dp.roundToPx()) }
                        .size(slot.width, slot.height)
                        .pointerInput(preview, metadata?.id) {
                            if (preview) return@pointerInput
                            detectTapGestures(
                                onLongPress = { if (lyrics.lines.isNotEmpty()) lyricsOpen = true },
                            )
                        }
                        .pointerInput(preview, metadata?.id) {
                            if (preview) return@pointerInput
                            detectDragGestures(
                                onDragEnd = {
                                    if (abs(swipeX) > size.width / 3) {
                                        if (swipeX < 0) connection?.seekToNext() else connection?.skipToPreviousSong()
                                    }
                                    swipeX = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                swipeX = (swipeX + dragAmount.x).coerceIn(-size.width * ImageParallax, size.width * ImageParallax)
                            }
                        },
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(NamidaArtworkRadius))
                            .graphicsLayer {
                                translationX = swipeX
                                alpha = 1f - abs(swipeX) / (size.width * ImageParallax)
                            },
                    )
                    if (swipeX != 0f) {
                        AsyncImage(
                            model = track.artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(NamidaArtworkRadius))
                                .graphicsLayer {
                                    translationX = if (swipeX < 0) {
                                        size.width * ImageParallax + swipeX
                                    } else {
                                        -size.width * ImageParallax + swipeX
                                    }
                                    alpha = abs(swipeX) / (size.width * ImageParallax)
                                },
                        )
                    }
                }

                // ── the waveform seek bar, with namida's drag-up-to-cancel ──
                val waveformHeight = (64.0 * geometry.waveformYScale).dp
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(0, geometry.vOffsetWaveform.dp.roundToPx()) }
                        .fillMaxWidth()
                        .height(waveformHeight)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    NamidaWaveform(
                        positionMs = seekPreviewMs ?: position.positionMs(),
                        durationMs = durationMs,
                        dragging = draggingWave,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(preview, durationMs) {
                                if (preview || durationMs <= 0) return@pointerInput
                                detectDragGestures(
                                    onDragStart = {
                                        draggingWave = true
                                        dragUpCancel = 0f
                                    },
                                    onDragEnd = {
                                        draggingWave = false
                                        if (dragUpCancel < 5f) {
                                            seekPreviewMs?.let { connection?.seekTo(it) }
                                        }
                                        seekPreviewMs = null
                                        dragUpCancel = 0f
                                    },
                                    onDragCancel = {
                                        draggingWave = false
                                        seekPreviewMs = null
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    // namida: five notches of upward drag cancels the seek
                                    dragUpCancel -= dragAmount.y * 0.1f
                                    if (dragUpCancel > 5f) {
                                        seekPreviewMs = null
                                        return@detectDragGestures
                                    }
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    seekPreviewMs = (fraction * durationMs).toLong()
                                }
                            },
                    )
                }

                // ── position and duration (NText small 13), riding the waveform ──
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(0, (geometry.vOffsetWaveform + 64.0 * geometry.waveformYScale).dp.roundToPx()) }
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp + 12.dp)
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    val posText = seekPreviewMs?.let { previewMs ->
                        val diff = previewMs - position.positionMs()
                        val sign = if (diff < 0) "" else "+"
                        sign + namidaFormatMillis(abs(diff))
                    } ?: namidaFormatMillis(position.positionMs())
                    Text(posText, color = palette.textSmall, fontSize = 13.sp, fontWeight = FontWeight.W400, fontFamily = NamidaFontFamily)
                    Text(namidaFormatMillis(durationMs), color = palette.textSmall, fontSize = 13.sp, fontWeight = FontWeight.W400, fontFamily = NamidaFontFamily)
                }

                // ── the controls: prev, the play/pause circle, next — centred like namida ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset { IntOffset(0, geometry.vOffsetControls.dp.roundToPx()) }
                        .padding(end = geometry.buttonsRightPadding.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.namida_previous),
                        contentDescription = null,
                        tint = palette.secondary,
                        modifier = Modifier
                            .size((geometry.nextPrevIconSize + geometry.nextPrevIconPadding * 2).dp)
                            .clickable(enabled = !preview) { connection?.skipToPreviousSong() }
                            .padding(geometry.nextPrevIconPadding.dp),
                    )
                    val iconSize = NamidaPlayPauseMetrics.iconSize(p.toDouble())
                    val boxSize = NamidaPlayPauseMetrics.boxSize(iconSize)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(boxSize.dp)
                            .shadow(
                                8.dp, CircleShape,
                                ambientColor = palette.playShadow,
                                spotColor = palette.playShadow,
                            )
                            .clip(CircleShape)
                            .background(
                                // namida's gradient: the current colour into its grey blend, 0 -> 0.7 stops
                                Brush.linearGradient(
                                    colors = palette.playGradient,
                                    start = androidx.compose.ui.geometry.Offset.Zero,
                                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f),
                                ),
                            )
                            .clickable(enabled = !preview) {
                                connection?.run { if (playing) pause() else play() }
                            },
                    ) {
                        Icon(
                            painter = painterResource(if (playing) R.drawable.namida_pause else R.drawable.namida_play),
                            contentDescription = null,
                            tint = palette.playIcon,
                            modifier = Modifier.size(iconSize.dp),
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.namida_next),
                        contentDescription = null,
                        tint = palette.secondary,
                        modifier = Modifier
                            .size((geometry.nextPrevIconSize + geometry.nextPrevIconPadding * 2).dp)
                            .clickable(enabled = !preview) { connection?.seekToNext() }
                            .padding(geometry.nextPrevIconPadding.dp),
                    )
                }

                // ── the bottom-left buttons row: repeat, lyrics, queue ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = TopBottomMargin)
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    val repeatIcon = when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.namida_repeate_one
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> R.drawable.namida_repeat
                        else -> R.drawable.namida_repeate_music
                    }
                    Icon(
                        painter = painterResource(repeatIcon),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !preview) {
                                connection?.repeatMode?.let { flow ->
                                    flow.value = when (repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                    }
                                }
                            }
                            .padding(8.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.namida_document),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !preview && lyrics.lines.isNotEmpty()) { lyricsOpen = true }
                            .padding(8.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.namida_row_vertical),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !preview) { pageTarget = if (pageTarget > 1.5f) 1f else 2f }
                            .padding(8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }

                // ── the track info: title, second line, the like button ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(0, geometry.vOffsetTrackInfo.dp.roundToPx()) }
                        .fillMaxWidth(0.78f)
                        .height(geometry.trackInfoBoxHeight.dp)
                        .padding(start = 12.dp + 42.dp)
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            track.title,
                            maxLines = if (track.artist.isNotBlank()) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            color = palette.textMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                        )
                        if (track.artist.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textMedium,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                fontFamily = NamidaFontFamily,
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(if (liked) R.drawable.namida_heart_filled else R.drawable.namida_heart),
                        contentDescription = null,
                        tint = if (liked) palette.currentColor.copy(alpha = 0.75f) else palette.secondary.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !preview) { connection?.toggleLike() }
                            .padding(3.dp),
                    )
                }

                // ── the queue page, sliding up over the player (p > 1) ──
                if (p > 1.02f) {
                    NamidaQueue(
                        visibleFraction = (p - 1f).coerceIn(0f, 1f),
                        actions = actions,
                        preview = preview,
                        onCollapse = { pageTarget = 1f },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // ── the lyrics page, over everything ──
                if (lyricsOpen) {
                    NamidaLyrics(
                        fullscreen = true,
                        onDismiss = { lyricsOpen = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** namida's milliSecondsLabel: h:mm:ss or m:ss. */
internal fun namidaFormatMillis(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
