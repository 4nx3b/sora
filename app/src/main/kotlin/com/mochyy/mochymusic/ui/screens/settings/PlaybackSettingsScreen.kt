package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.DontSkipOnFailureKey
import com.mochyy.mochymusic.constants.HistoryDuration
import com.mochyy.mochymusic.constants.PreloadSongsCountKey
import com.mochyy.mochymusic.constants.PreloadSongsDefault
import com.mochyy.mochymusic.constants.PreloadSongsMax
import com.mochyy.mochymusic.constants.MixWithOtherAppsKey
import com.mochyy.mochymusic.constants.PauseOnMute
import com.mochyy.mochymusic.constants.PersistentQueueKey
import com.mochyy.mochymusic.constants.KeepCpuAwakeKey
import com.mochyy.mochymusic.constants.PersistentShuffleAcrossQueuesKey
import com.mochyy.mochymusic.constants.ResumeOnBluetoothConnectKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StopPlaybackOnTaskRemovedKey
import com.mochyy.mochymusic.constants.StreamFailureTimeoutKey
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.screens.settings.modules.ModulesRoute
import com.mochyy.mochymusic.utils.rememberPreference

const val PlaybackSettingsRoute = "playback_settings"

// settings > playback: how music keeps going, shares the speaker, and what happens when a song won't load
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_playback_category),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        // the player's own page and the modules that add sources and looks, entered from here
        SettingsSection(stringResource(R.string.settings_player_pages), playerPageItems(navController).matching(query))
        SettingsSection(stringResource(R.string.settings_section_listening), listeningItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_failures), failureItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_content_preferences), contentItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_local_music), localMusicItems().matching(query))
        SettingsSection(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

// the pages that live under playback: what the open player shows, and the modules that add to the app
@Composable
private fun playerPageItems(navController: NavController): List<SettingsItem> = listOf(
    linkItem(
        key = "Now playing",
        title = stringResource(R.string.settings_now_playing),
        description = stringResource(R.string.settings_now_playing_link_desc),
        icon = painterResource(R.drawable.lyrics),
        onClick = { navController.navigate(NowPlayingSettingsRoute) },
    ),
    linkItem(
        key = "Modules",
        title = stringResource(R.string.settings_modules_category),
        description = stringResource(R.string.settings_modules_link_desc),
        icon = painterResource(R.drawable.library_add),
        onClick = { navController.navigate(ModulesRoute) },
    ),
)

@Composable
private fun listeningItems(): List<SettingsItem> {
    val (stopOnTaskRemoved, onStopOnTaskRemoved) = rememberPreference(StopPlaybackOnTaskRemovedKey, defaultValue = false)
    val (mixWithOtherApps, onMixWithOtherAppsChange) = rememberPreference(MixWithOtherAppsKey, defaultValue = false)
    val (resumeOnBluetooth, onResumeOnBluetoothChange) = rememberPreference(ResumeOnBluetoothConnectKey, defaultValue = false)
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(PauseOnMute, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(PersistentQueueKey, defaultValue = true)
    val (keepCpuAwake, onKeepCpuAwakeChange) = rememberPreference(KeepCpuAwakeKey, defaultValue = false)
    val (persistentShuffle, onPersistentShuffleChange) = rememberPreference(PersistentShuffleAcrossQueuesKey, defaultValue = false)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (historyDuration, onHistoryDurationChange) = rememberPreference(HistoryDuration, defaultValue = 30f)
    val (preloadSongs, onPreloadSongsChange) = rememberPreference(PreloadSongsCountKey, defaultValue = PreloadSongsDefault)
    return listOf(
        switchItem(
            key = "Keep playing even when closed",
            title = stringResource(R.string.playback_keep_playing),
            description = stringResource(R.string.playback_keep_playing_desc),
            detail = stringResource(R.string.playback_keep_playing_detail),
            icon = painterResource(R.drawable.clear_all),
            checked = !stopOnTaskRemoved,
            onCheckedChange = { onStopOnTaskRemoved(!it) },
        ),
        switchItem(
            key = "Play alongside other apps",
            title = stringResource(R.string.playback_mix_other_apps),
            description = stringResource(R.string.playback_mix_other_apps_desc),
            detail = stringResource(R.string.playback_mix_other_apps_detail),
            icon = painterResource(R.drawable.volume_up),
            checked = mixWithOtherApps,
            onCheckedChange = onMixWithOtherAppsChange,
        ),
        switchItem(
            key = "Resume on Bluetooth connection",
            title = stringResource(R.string.playback_resume_bluetooth),
            description = stringResource(R.string.playback_resume_bluetooth_desc),
            icon = painterResource(R.drawable.bluetooth),
            checked = resumeOnBluetooth,
            onCheckedChange = onResumeOnBluetoothChange,
        ),
        switchItem(
            key = "Pause when muted",
            title = stringResource(R.string.pause_music_when_media_is_muted),
            description = stringResource(R.string.pause_music_when_muted_desc),
            icon = painterResource(R.drawable.volume_off),
            checked = pauseOnMute,
            onCheckedChange = onPauseOnMuteChange,
        ),
        switchItem(
            key = "Save Player's last state",
            title = stringResource(R.string.playback_save_state),
            description = stringResource(R.string.playback_save_state_desc),
            detail = stringResource(R.string.playback_save_state_detail),
            icon = painterResource(R.drawable.cached),
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange,
        ),
        // keep the CPU awake while playing, ported from Neiro (ArchiveTune's keep awake)
        switchItem(
            key = "Keep CPU awake",
            title = stringResource(R.string.playback_keep_cpu_awake),
            description = stringResource(R.string.playback_keep_cpu_awake_desc),
            icon = painterResource(R.drawable.bolt),
            checked = keepCpuAwake,
            onCheckedChange = onKeepCpuAwakeChange,
        ),
        // shuffle stays on from one playlist to the next, like spotify, until it's turned off
        switchItem(
            key = "Keep shuffle on",
            title = stringResource(R.string.settings_persistent_shuffle),
            description = stringResource(R.string.settings_persistent_shuffle_desc),
            icon = painterResource(R.drawable.shuffle),
            checked = persistentShuffle,
            onCheckedChange = onPersistentShuffleChange,
        ),
        switchItem(
            key = "Skip Silence",
            title = stringResource(R.string.playback_skip_silence),
            description = stringResource(R.string.playback_skip_silence_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
        ),
        // how long a song plays before it counts in the listening history (ported from Neiro; MusicService already reads it)
        SettingsItem(
            title = { ValueTitle(stringResource(R.string.history_duration), stringResource(R.string.history_duration_value, historyDuration.toInt())) },
            highlightKey = "History duration",
            searchText = stringResource(R.string.history_duration),
            description = {
                DefaultPlayerSeekBarSlider(
                    value = historyDuration,
                    onValueChange = { onHistoryDurationChange(it.toInt().toFloat()) },
                    valueRange = 1f..60f,
                    steps = 58,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.history),
            iconShape = CircleShape,
        ),
        // upcoming songs found and their first seconds cached ahead, so the next one starts at once (Neiro's preload)
        SettingsItem(
            title = {
                ValueTitle(
                    stringResource(R.string.preload_songs),
                    if (preloadSongs == 0) stringResource(R.string.preload_songs_off) else androidx.compose.ui.res.pluralStringResource(R.plurals.preload_songs_value, preloadSongs, preloadSongs),
                )
            },
            highlightKey = "Preload songs",
            searchText = stringResource(R.string.preload_songs),
            description = {
                androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(stringResource(R.string.preload_songs_desc), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                DefaultPlayerSeekBarSlider(
                    value = preloadSongs.toFloat(),
                    onValueChange = { onPreloadSongsChange(it.toInt()) },
                    valueRange = 0f..PreloadSongsMax.toFloat(),
                    steps = PreloadSongsMax - 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
                }
            },
            icon = painterResource(R.drawable.speed),
            iconShape = CircleShape,
        ),
    )
}

// Neiro's content preferences: Home shelves and videos the listener would rather not see
@Composable
private fun contentItems(): List<SettingsItem> {
    val (hideVideos, onHideVideos) = rememberPreference(com.mochyy.mochymusic.constants.HideVideoSongsKey, defaultValue = false)
    val (hideRecents, onHideRecents) = rememberPreference(com.mochyy.mochymusic.constants.HideRecentsKey, defaultValue = false)
    val (hideTopPicks, onHideTopPicks) = rememberPreference(com.mochyy.mochymusic.constants.HideTopPicksKey, defaultValue = false)
    val (hideMoods, onHideMoods) = rememberPreference(com.mochyy.mochymusic.constants.HideHomeMoodsKey, defaultValue = false)
    val (squareVideos, onSquareVideos) = rememberPreference(com.mochyy.mochymusic.constants.SquareVideoThumbnailsKey, defaultValue = false)
    // sora's studio-version switch is Neiro's "Prefer music-only version": a music video plays its song's audio upload
    val (studioForVideos, onStudioForVideos) = rememberPreference(com.mochyy.mochymusic.constants.StudioForVideosKey, defaultValue = false)
    return listOf(
        switchItem(key = "Studio version for music videos", title = stringResource(R.string.settings_studio_for_videos), description = stringResource(R.string.settings_studio_for_videos_desc),
            icon = painterResource(R.drawable.music_note), checked = studioForVideos, onCheckedChange = onStudioForVideos),
        switchItem(key = "Hide videos", title = stringResource(R.string.hide_videos), description = stringResource(R.string.hide_videos_desc),
            icon = painterResource(R.drawable.volume_off), checked = hideVideos, onCheckedChange = onHideVideos),
        switchItem(key = "Hide Recents", title = stringResource(R.string.hide_recents), description = stringResource(R.string.hide_recents_desc),
            icon = painterResource(R.drawable.history), checked = hideRecents, onCheckedChange = onHideRecents),
        switchItem(key = "Hide Top Picks for You", title = stringResource(R.string.hide_top_picks), description = stringResource(R.string.hide_top_picks_desc),
            icon = painterResource(R.drawable.speed), checked = hideTopPicks, onCheckedChange = onHideTopPicks),
        switchItem(key = "Hide Find Your Mood", title = stringResource(R.string.hide_home_moods), description = stringResource(R.string.hide_home_moods_desc),
            icon = painterResource(R.drawable.clear_all), checked = hideMoods, onCheckedChange = onHideMoods),
        switchItem(key = "Square video thumbnails", title = stringResource(R.string.square_video_thumbnails), description = stringResource(R.string.square_video_thumbnails_desc),
            icon = painterResource(R.drawable.library_music), checked = squareVideos, onCheckedChange = onSquareVideos),
    )
}

// Neiro's local music group: the folder songs are read from (sora's own saved folders) and the non-music filter
@Composable
private fun localMusicItems(): List<SettingsItem> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val importer: com.mochyy.mochymusic.viewmodels.LibraryImportViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var folders by remember { mutableStateOf(com.mochyy.mochymusic.importer.LocalFolders.read(context)) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            importer.addFolder(tree)
            folders = com.mochyy.mochymusic.importer.LocalFolders.read(context)
        }
    }
    val (filterNonMusic, onFilterNonMusic) = rememberPreference(com.mochyy.mochymusic.constants.FilterNonMusicAudioKey, defaultValue = false)
    val folderSummary = when (folders.size) {
        0 -> stringResource(R.string.local_music_folder_all)
        1 -> folders.first().lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: stringResource(R.string.local_music_folder_all)
        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.local_music_folder_count, folders.size, folders.size)
    }
    return listOf(
        linkItem(key = "Local music folder", title = stringResource(R.string.local_music_folder), description = folderSummary,
            icon = painterResource(R.drawable.library_music), onClick = { picker.launch(null) }),
        switchItem(key = "Filter non-music audio", title = stringResource(R.string.filter_non_music_audio), description = stringResource(R.string.filter_non_music_audio_desc),
            icon = painterResource(R.drawable.clear_all), checked = filterNonMusic, onCheckedChange = onFilterNonMusic),
    )
}

@Composable
private fun failureItems(): List<SettingsItem> {
    val (dontSkipOnFailure, onDontSkipOnFailureChange) = rememberPreference(DontSkipOnFailureKey, defaultValue = false)
    val (failureTimeout, onFailureTimeoutChange) = rememberPreference(StreamFailureTimeoutKey, defaultValue = 6)
    return listOf(
        switchItem(
            key = "Don't skip on failure",
            title = stringResource(R.string.settings_dont_skip_failure),
            description = stringResource(R.string.settings_dont_skip_failure_desc),
            detail = stringResource(R.string.settings_dont_skip_failure_detail),
            icon = painterResource(R.drawable.skip_next),
            checked = dontSkipOnFailure,
            onCheckedChange = onDontSkipOnFailureChange,
        ),
        SettingsItem(
            title = { ValueTitle(stringResource(R.string.settings_skip_timeout), stringResource(R.string.settings_seconds_short, failureTimeout)) },
            highlightKey = "Skip timeout failure",
            searchText = stringResource(R.string.settings_skip_timeout),
            description = {
                DefaultPlayerSeekBarSlider(
                    value = failureTimeout.toFloat(),
                    onValueChange = { onFailureTimeoutChange(it.toInt()) },
                    valueRange = 5f..20f,
                    steps = 14,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.speed),
            iconShape = CircleShape,
            isVisible = !dontSkipOnFailure,
            isSubOption = true,
        ),
    )
}

// the sound side of playback lives in audio lab, these take people there
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> = listOf(
    linkItem(
        key = "Transition crossfade",
        title = stringResource(R.string.transition_title),
        description = stringResource(R.string.settings_transition_link_desc),
        icon = painterResource(R.drawable.linear_scale),
        onClick = { navController.navigate("crossmix_settings") },
    ),
    linkItem(
        key = "Audio Pass output usb dac",
        title = stringResource(R.string.audiopass_hub_title),
        description = stringResource(R.string.settings_audio_pass_desc),
        icon = rememberVectorPainter(Icons.Rounded.GraphicEq),
        onClick = { navController.navigate("audio_pass") },
    ),
)
