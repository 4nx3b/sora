/*
 * The Apple Music player's overflow menu, ported from Neiro's
 * ui/menu/PlayerMenu.kt (ArchiveTune, © Rukamori, GPL-3.0): the same header
 * card, the same 3-column action grid with its 84dp bordered tiles, the same
 * MenuSurfaceSection rows and 0.5dp dividers, the same sleep-timer sheet —
 * drawn inside the vendored BottomSheetMenu Neiro's player opens. The sections
 * whose backends only Neiro has (canvas pickers, speed dial, per-song source
 * override, external downloader) are left out exactly where Neiro's own
 * conditionals leave them out when those features are off.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mochyy.mochymusic.ported.flamingo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.ported.bridge.BottomSheetState
import com.mochyy.mochymusic.ported.bridge.MediaMetadata
import com.mochyy.mochymusic.ported.bridge.MenuSurfaceSection
import com.mochyy.mochymusic.ported.bridge.MenuSectionDivider
import com.mochyy.mochymusic.ported.bridge.NewAction
import com.mochyy.mochymusic.ported.bridge.NewActionGrid
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: androidx.navigation.NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()

    val currentSong = playerConnection.currentSong.collectAsState(null).value
    val download by remember(downloadUtil, mediaMetadata.id) {
        downloadUtil.getDownload(mediaMetadata.id)
    }.collectAsState(initial = null)

    val isLocalMedia = mediaMetadata.id.startsWith("LOCAL_")

    val nowPlayingTitle = mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
    val nowPlayingSubtitle = mediaMetadata.artists.joinToString(separator = " • ") { it.name }

    var showSleepTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showChoosePlaylistDialog) {
        com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog(
            isVisible = true,
            onGetSong = { listOf(mediaMetadata.id) },
            onDismiss = { showChoosePlaylistDialog = false },
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val thumb = mediaMetadata.thumbnailUrl
            if (thumb.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlayingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                if (nowPlayingSubtitle.isNotBlank()) {
                    Text(
                        text = nowPlayingSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 12.dp,
            ),
    ) {
        if (showSleepTimerSheet) {
            item {
                AppleMusicSleepTimerSheet(
                    sleepTimer = playerConnection.service.sleepTimer,
                    onDismiss = {
                        showSleepTimerSheet = false
                        onDismiss()
                    },
                )
            }
        } else {
            item {
                MenuSurfaceSection {
                    NewActionGrid(
                        actions =
                            buildList {
                                if (!isLocalMedia && !mediaMetadata.isPodcast) {
                                    add(
                                        NewAction(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.radio),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            text = stringResource(R.string.start_radio),
                                            onClick = {
                                                playerConnection.startRadioSeamlessly()
                                                onDismiss()
                                            },
                                        ),
                                    )
                                }
                                add(
                                    if (isLocalMedia) {
                                        NewAction(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.share),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            text = stringResource(R.string.share),
                                            onClick = {
                                                com.mochyy.mochymusic.ui.player.share.song.SongShare.open(mediaMetadata)
                                                onDismiss()
                                            },
                                        )
                                    } else {
                                        NewAction(
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.link),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            text = stringResource(R.string.copy_link),
                                            onClick = {
                                                val clipboard =
                                                    context.getSystemService(
                                                        android.content.Context.CLIPBOARD_SERVICE,
                                                    ) as android.content.ClipboardManager
                                                val clip =
                                                    android.content.ClipData.newPlainText(
                                                        context.getString(R.string.copy_link),
                                                        "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                                    )
                                                clipboard.setPrimaryClip(clip)
                                                Toast
                                                    .makeText(
                                                        context,
                                                        R.string.link_copied,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                onDismiss()
                                            },
                                        )
                                    },
                                )
                            },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                MenuSectionDivider()
            }
            item {
                MenuSurfaceSection {
                    Column {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_add),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    showChoosePlaylistDialog = true
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(
                                        if (currentSong?.song?.liked == true) {
                                            R.string.action_remove_like
                                        } else {
                                            R.string.action_like
                                        },
                                    ),
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(
                                        if (currentSong?.song?.liked == true) {
                                            R.drawable.flamingo_np_favorited
                                        } else {
                                            R.drawable.flamingo_np_favorite
                                        },
                                    ),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    playerConnection.toggleLike()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item {
                MenuSectionDivider()
            }
            if (mediaMetadata.artists.any { it.id != null } || mediaMetadata.album != null) {
                item {
                    MenuSurfaceSection {
                        Column {
                            if (mediaMetadata.artists.any { it.id != null }) {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.artist),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("artist/${mediaMetadata.artists.first { it.id != null }.id}")
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            if (mediaMetadata.artists.any { it.id != null } && mediaMetadata.album != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 0.5.dp,
                                )
                            }

                            if (mediaMetadata.album != null) {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.view_album)) },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.album),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("album/${mediaMetadata.album.id}")
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
                item {
                    MenuSectionDivider()
                }
            }
            if (!isLocalMedia) {
                item {
                    MenuSurfaceSection {
                        when (download?.state) {
                            Download.STATE_COMPLETED -> {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.remove_download),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.offline),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                mediaMetadata.id,
                                                false,
                                            )
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                    leadingContent = {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                mediaMetadata.id,
                                                false,
                                            )
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            else -> {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            database.transaction { insert(mediaMetadata) }

                                            coroutineScope.launch {
                                                val downloadRequest =
                                                    DownloadRequest
                                                        .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                                        .setCustomCacheKey(mediaMetadata.id)
                                                        .setData(mediaMetadata.title.toByteArray())
                                                        .build()
                                                DownloadService.sendAddDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    downloadRequest,
                                                    false,
                                                )
                                            }
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
                item {
                    MenuSectionDivider()
                }
            }
            item {
                MenuSurfaceSection {
                    Column {
                        if (isQueueTrigger != true) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.play_next)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_play),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        playerConnection.playNext(mediaMetadata.toMediaItem())
                                        onDismiss()
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )

                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        playerConnection.addToQueue(mediaMetadata.toMediaItem())
                                        onDismiss()
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )
                        }

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.details)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onShowDetailsDialog()
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        if (isQueueTrigger != true) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )

                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.sleep_timer)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.bedtime),
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable { showSleepTimerSheet = true },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
