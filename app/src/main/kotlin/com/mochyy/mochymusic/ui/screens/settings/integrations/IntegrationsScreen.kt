// mochy → settings → integrations: a row per service, always all of them, tapping one opens its own page

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.gesture.PreviewCard
import com.mochyy.mochymusic.ui.screens.settings.previews.DevicesPreview
import com.mochyy.mochymusic.ui.screens.settings.previews.DiscordPreview
import com.mochyy.mochymusic.ui.screens.settings.previews.ListenBrainzPreview
import com.mochyy.mochymusic.ui.screens.settings.previews.ListenTogetherPreview
import com.mochyy.mochymusic.ui.screens.settings.previews.ScrobblePreview
import com.mochyy.mochymusic.ui.screens.settings.previews.SourcesPreview
import com.mochyy.mochymusic.ui.screens.settings.previews.YouTubeSyncPreview
import com.mochyy.mochymusic.constants.WorkOutGenresKey
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute
import com.mochyy.mochymusic.ui.screens.devices.showDeviceIntegration

const val IntegrationsRoute = "integrations_settings"

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

@Composable
fun IntegrationsScreen(navController: NavController) {
    // device integration is a sheet over whatever page asked for it
    val sheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()

    val (rpc) = rememberPreference(com.mochyy.mochymusic.constants.EnableDiscordRPCKey, false)
    val (listenBrainz) = rememberPreference(com.mochyy.mochymusic.constants.ListenBrainzEnabledKey, false)
    val lastFm = rememberScrobbleConnected(ScrobbleSite.LASTFM)
    val libreFm = rememberScrobbleConnected(ScrobbleSite.LIBREFM)
    val status: @Composable (Boolean) -> String = {
        stringResource(if (it) R.string.preview_status_connected else R.string.preview_status_not_connected)
    }

    SubSettingsScaffold(
        title = stringResource(R.string.integrations),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewCard(
                category = stringResource(if (rpc) R.string.preview_status_sharing else R.string.preview_status_off),
                title = "Discord",
                onClick = { navController.navigate(DiscordRoute) },
            ) { DiscordPreview() }
            PreviewCard(
                category = status(lastFm),
                title = ScrobbleSite.LASTFM.label,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LASTFM)) },
            ) { ScrobblePreview(ScrobbleSite.LASTFM) }
            PreviewCard(
                category = status(libreFm),
                title = ScrobbleSite.LIBREFM.label,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LIBREFM)) },
            ) { ScrobblePreview(ScrobbleSite.LIBREFM) }
            PreviewCard(
                category = status(listenBrainz),
                title = "ListenBrainz",
                onClick = { navController.navigate(ListenBrainzRoute) },
            ) { ListenBrainzPreview() }
            // the genres the stats page shows are asked of Last.fm, artist names only; the
            // switch travels with the scrobbling services that share its account
            var workOutGenres by rememberPreference(WorkOutGenresKey, defaultValue = true)
            IntegrationSwitchRow(
                name = stringResource(R.string.work_out_genres),
                description = stringResource(
                    if (workOutGenres) R.string.work_out_genres_on_desc else R.string.work_out_genres_off_desc,
                ),
                icon = R.drawable.music_note,
                checked = workOutGenres,
                onCheckedChange = { workOutGenres = it },
            )
            PreviewCard(
                category = stringResource(R.string.module_youtube_desc),
                title = stringResource(R.string.module_youtube_name),
                onClick = { navController.navigate(YouTubeSyncRoute) },
            ) { YouTubeSyncPreview() }
            // the listen together rooms, ported from Neiro, live here with the other things that reach out
            PreviewCard(
                category = stringResource(R.string.settings_listen_together_desc),
                title = stringResource(R.string.ported_listen_together),
                onClick = { navController.navigate("listen_together") },
            ) { ListenTogetherPreview() }
            // device integration opens as a sheet rather than its own page
            PreviewCard(
                category = stringResource(R.string.settings_devices_link_desc),
                title = stringResource(R.string.devices_title),
                onClick = { sheets.showDeviceIntegration(devices) },
            ) { DevicesPreview() }
            PreviewCard(
                category = stringResource(R.string.sources_row_desc),
                title = stringResource(R.string.sources),
                onClick = { navController.navigate(SourcesRoute) },
            ) { SourcesPreview() }
        }

        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun rememberScrobbleConnected(site: ScrobbleSite): Boolean {
    val (enabled) = rememberPreference(site.enabledKey, false)
    val (session) = rememberPreference(site.sessionKey, "")
    return enabled && session.isNotBlank()
}
