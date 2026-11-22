// Neiro's AOD page (SettingsSheet AodSettingsPage): what the always-on playing screen shows and how it holds
// the panel. Read by aod/AodDreamService and aod/AodScreen.

package com.mochyy.mochymusic.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AodAutoDimmingKey
import com.mochyy.mochymusic.constants.AodAutoTimerSecondsKey
import com.mochyy.mochymusic.constants.AodBrightnessDefault
import com.mochyy.mochymusic.constants.AodBrightnessKey
import com.mochyy.mochymusic.constants.AodEnabledKey
import com.mochyy.mochymusic.constants.AodPixelShiftKey
import com.mochyy.mochymusic.constants.AodShowAlbumKey
import com.mochyy.mochymusic.constants.AodShowArtistKey
import com.mochyy.mochymusic.constants.AodShowBatteryKey
import com.mochyy.mochymusic.constants.AodShowClockKey
import com.mochyy.mochymusic.constants.AodShowControlsKey
import com.mochyy.mochymusic.constants.AodShowProgressKey
import com.mochyy.mochymusic.constants.AodShowThumbnailKey
import com.mochyy.mochymusic.constants.AodShowTimeLabelsKey
import com.mochyy.mochymusic.constants.AodTouchLockKey
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

const val AodSettingsRoute = "now_playing_settings/aod"

@Composable
fun AodSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val icon = painterResource(R.drawable.tune)

    @Composable
    fun toggle(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean, title: Int, desc: Int? = null): SettingsItem {
        val (on, set) = rememberPreference(key, default)
        return switchItem(
            key = stringResource(title),
            title = stringResource(title),
            description = desc?.let { stringResource(it) } ?: "",
            icon = icon,
            checked = on,
            onCheckedChange = set,
        )
    }

    val (brightness, onBrightness) = rememberPreference(AodBrightnessKey, AodBrightnessDefault)
    val (timer, onTimer) = rememberPreference(AodAutoTimerSecondsKey, 0)

    SubSettingsScaffold(
        title = stringResource(R.string.aod_title),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.settings_customisation_category),
    ) {
        com.mochyy.mochymusic.ui.screens.gesture.PortedPreviewHeader { com.mochyy.mochymusic.ui.screens.gesture.AodPreview() }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
        SettingsSection(
            stringResource(R.string.aod_title),
            listOf(
                toggle(AodEnabledKey, false, R.string.aod_enabled, R.string.aod_enabled_desc),
                SettingsItem(
                    title = { Text(stringResource(R.string.aod_open_system)) },
                    descriptionText = stringResource(R.string.aod_open_system_desc),
                    icon = icon,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                ),
            ),
        )
        SettingsSection(
            stringResource(R.string.aod_section_show),
            listOf(
                toggle(AodShowThumbnailKey, true, R.string.aod_show_thumbnail),
                toggle(AodShowArtistKey, true, R.string.aod_show_artist),
                toggle(AodShowAlbumKey, false, R.string.aod_show_album),
                toggle(AodShowProgressKey, true, R.string.aod_show_progress),
                toggle(AodShowTimeLabelsKey, true, R.string.aod_show_time_labels),
                toggle(AodShowControlsKey, true, R.string.aod_show_controls),
                toggle(AodShowClockKey, true, R.string.aod_show_clock),
                toggle(AodShowBatteryKey, true, R.string.aod_show_battery),
            ),
        )
        SettingsSection(
            stringResource(R.string.aod_section_screen),
            listOf(
                SettingsItem(
                    title = { ValueTitle(stringResource(R.string.aod_brightness), "${(brightness * 100).roundToInt()}%") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = brightness,
                            onValueChange = onBrightness,
                            valueRange = 0.01f..1f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = icon,
                ),
                toggle(AodAutoDimmingKey, true, R.string.aod_auto_dimming, R.string.aod_auto_dimming_desc),
                SettingsItem(
                    title = {
                        ValueTitle(
                            stringResource(R.string.aod_auto_timer),
                            if (timer == 0) stringResource(R.string.aod_auto_timer_off) else stringResource(R.string.aod_auto_timer_value, timer / 60),
                        )
                    },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = (timer / 60).toFloat(),
                            onValueChange = { onTimer(it.roundToInt() * 60) },
                            valueRange = 0f..120f,
                            steps = 23,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = icon,
                ),
                toggle(AodTouchLockKey, false, R.string.aod_touch_lock, R.string.aod_touch_lock_desc),
                toggle(AodPixelShiftKey, true, R.string.aod_pixel_shift, R.string.aod_pixel_shift_desc),
            ),
        )
    }
}
