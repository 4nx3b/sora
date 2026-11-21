// namida's track dialog, ported from its showGeneralPopupDialog (ui/dialogs/
// general_popup_dialog.dart): the same centred Material card (28dp corners, the
// inset margins), the same 0.92 scale-and-fade entrance, the same header (60dp
// artwork with 8dp corners, the title at 17 over the artist at 14 tinted with
// the current colour) and the same compact icon rows — opened by the miniplayer's
// more chip exactly where namida's onMenuOpen opens it. The rows are the ones
// namida's queue-sourced track dialog carries that sora has a backend for.

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTrack
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.offline.DownloadService

/** the track dialog namida's more chip opens: its card, its header, its rows. */
@Composable
fun NamidaTrackMenu(
    actions: com.mochyy.mochymusic.ui.player.styles.StyleActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val palette = LocalNamidaPalette.current
    val track = rememberStyleTrack()
    val metadata = connection.mediaMetadata.collectAsState().value
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current

    val currentSong = connection.currentSong.collectAsState(null).value
    val liked = currentSong?.song?.liked == true
    val download by remember(downloadUtil, metadata?.id) {
        if (metadata?.id != null) downloadUtil.getDownload(metadata.id) else flowOf(null)
    }.collectAsState(initial = null)

    // namida's dialog: the page behind dims, the card comes up from 0.92 scale
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun close() {
        if (dismissed) return
        dismissed = true
        scope.launch {
            appear.animateTo(0f, tween(220))
            onDismiss()
        }
    }

    var showAddToPlaylist by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.5f * appear.value))
            .pointerInput(Unit) {
                detectTapGestures { close() }
            },
        contentAlignment = Alignment.Center,
    ) {
        // namida's Dialog inset: a horizontal margin around a bounded card
        Column(
            modifier = Modifier
                .padding(horizontal = 34.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                }
                .clip(RoundedCornerShape(28.dp))
                .background(lerp(palette.scaffold, palette.currentColor, 0.08f))
                .clickable(enabled = false, onClick = {}),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ── the header: artwork, title, artist, third line ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                ) {
                    Spacer(Modifier.width(16.dp))
                    if (track.artwork != null) {
                        AsyncImage(
                            model = track.artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.namida_sound),
                                contentDescription = null,
                                tint = palette.onSecondaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                            color = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium),
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                lineHeight = 15.sp,
                                fontFamily = NamidaFontFamily,
                                color = palette.currentColor.copy(alpha = 0.31f).compositeOver(palette.textMedium),
                            )
                        }
                        if (track.album.isNotBlank()) {
                            Text(
                                text = track.album,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.5.sp,
                                lineHeight = 13.sp,
                                fontFamily = NamidaFontFamily,
                                color = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }

                // ── the rows, namida's compact icon tiles ──
                val iconTint = palette.currentColor.copy(alpha = 0.47f).compositeOver(palette.textMedium)
                val titleTint = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium)

                @Composable fun row(label: String, icon: Int, closeAfter: Boolean = true, tint: Color = titleTint, iconColor: Color = iconTint, onClick: () -> Unit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable {
                                onClick()
                                if (closeAfter) close()
                            },
                    ) {
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = label,
                            fontSize = 14.5.sp,
                            fontFamily = NamidaFontFamily,
                            color = tint,
                        )
                    }
                }

                if (metadata != null) {
                    row(stringResource(R.string.play_next), R.drawable.namida_play) {
                        connection.playNext(metadata.toMediaItem())
                    }
                    row(stringResource(R.string.add_to_queue), R.drawable.namida_row_vertical) {
                        connection.addToQueue(metadata.toMediaItem())
                    }
                    row(
                        stringResource(if (liked) R.string.action_remove_like else R.string.action_like),
                        if (liked) R.drawable.namida_heart_filled else R.drawable.namida_heart,
                        closeAfter = false,
                    ) {
                        connection.toggleLike()
                    }
                    row(stringResource(R.string.add_to_playlist), R.drawable.namida_maximize_3, closeAfter = false) {
                        showAddToPlaylist = true
                    }
                    if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                        row(
                            stringResource(R.string.remove_download),
                            R.drawable.namida_close_circle,
                            tint = Color(0xFFF44336),
                            iconColor = Color(0xFFF44336),
                        ) {
                            DownloadService.sendRemoveDownload(
                                context, ExoDownloadService::class.java, metadata.id, false,
                            )
                        }
                    } else if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED ||
                        download?.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
                    ) {
                        row(stringResource(R.string.downloading), R.drawable.namida_close_circle) {
                            DownloadService.sendRemoveDownload(
                                context, ExoDownloadService::class.java, metadata.id, false,
                            )
                        }
                    } else if (!metadata.id.startsWith("LOCAL_")) {
                        row(stringResource(R.string.action_download), R.drawable.namida_arrow_down) {
                            database.transaction { insert(metadata) }
                            val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(
                                metadata.id,
                                metadata.id.toUri(),
                            )
                                .setCustomCacheKey(metadata.id)
                                .setData(metadata.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
                        }
                    }
                    row(stringResource(R.string.details), R.drawable.namida_document) {
                        com.mochyy.mochymusic.ui.utils.ShowMediaInfo(metadata.id)
                    }
                }
                metadata?.album?.id?.let { albumId ->
                    row(stringResource(R.string.view_album), R.drawable.namida_video_octagon) {
                        actions.onOpenAlbum(albumId)
                    }
                }
                metadata?.artists?.firstOrNull { it.id != null }?.id?.let { artistId ->
                    row(stringResource(R.string.view_artist), R.drawable.namida_search_normal) {
                        actions.onOpenArtist(artistId)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showAddToPlaylist) {
        com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog(
            isVisible = true,
            onGetSong = { listOfNotNull(metadata?.id) },
            onDismiss = { showAddToPlaylist = false },
        )
    }
}

private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
