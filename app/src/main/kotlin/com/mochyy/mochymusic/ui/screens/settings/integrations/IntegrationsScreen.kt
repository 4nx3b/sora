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
import com.mochyy.mochymusic.constants.WorkOutGenresKey
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute

const val IntegrationsRoute = "integrations_settings"

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

@Composable
fun IntegrationsScreen(navController: NavController) {
    // device integration is a sheet over whatever page asked for it
    val sheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()

    SubSettingsScaffold(
        title = stringResource(R.string.integrations),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegrationRow(
                name = "Discord",
                description = stringResource(R.string.integration_discord_desc),
                icon = R.drawable.integration_discord,
                onClick = { navController.navigate(DiscordRoute) },
            )
            IntegrationRow(
                name = ScrobbleSite.LASTFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LASTFM.label),
                icon = R.drawable.integration_lastfm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LASTFM)) },
            )
            IntegrationRow(
                name = ScrobbleSite.LIBREFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LIBREFM.label),
                icon = R.drawable.integration_librefm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LIBREFM)) },
            )
            IntegrationRow(
                name = "ListenBrainz",
                description = stringResource(R.string.integration_listenbrainz_desc),
                icon = R.drawable.integration_librefm,
                onClick = { navController.navigate(ListenBrainzRoute) },
            )
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
            IntegrationRow(
                name = stringResource(R.string.module_youtube_name),
                description = stringResource(R.string.module_youtube_desc),
                icon = R.drawable.provider_youtube_music,
                iconBackground = YouTubeMusicRed,
                onClick = { navController.navigate(YouTubeSyncRoute) },
            )
            // the listen together rooms, ported from Neiro, live here with the other things that reach out
            IntegrationRow(
                name = stringResource(R.string.ported_listen_together),
                description = stringResource(R.string.settings_listen_together_desc),
                icon = R.drawable.group,
                onClick = { navController.navigate("listen_together") },
            )
            // device integration opens as a sheet rather than its own page
            IntegrationRow(
                name = stringResource(R.string.devices_title),
                description = stringResource(R.string.settings_devices_link_desc),
                icon = R.drawable.cast_connected,
                onClick = { sheets.showDeviceIntegration(devices) },
            )
            IntegrationRow(
                name = stringResource(R.string.sources),
                description = stringResource(R.string.sources_row_desc),
                icon = R.drawable.extension,
                onClick = { navController.navigate(SourcesRoute) },
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
