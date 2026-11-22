// NavigationBarSettingsScreen.kt
// Neiro's Navigation bar page (SettingsSheet's NavigationBarSettingsPage), without the removed
// mini player bar behaviour segment and the six dimension dials: what's left is the glass glow
// and its strength, and the labels under the tabs. AppNavigationBar draws them.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.NavBarGlassGlowIntensityKey
import com.mochyy.mochymusic.constants.NavBarGlassGlowKey
import com.mochyy.mochymusic.constants.NavBarGlowIntensityDefault
import com.mochyy.mochymusic.constants.NavBarShowLabelsKey
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

const val NavigationBarSettingsRoute = "customisation_settings/navigation_bar"

@Composable
fun NavigationBarSettingsScreen(navController: NavController) {
    val (glow, onGlow) = rememberPreference(NavBarGlassGlowKey, false)
    val (labels, onLabels) = rememberPreference(NavBarShowLabelsKey, false)

    SubSettingsScaffold(
        title = stringResource(R.string.navigation_bar_settings_title),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.settings_customisation_category),
    ) {
        SettingsSection(
            stringResource(R.string.navigation_bar_general),
            listOf(
                switchItem(
                    key = "Glass glow",
                    title = stringResource(R.string.navigation_bar_glass_glow),
                    description = stringResource(R.string.navigation_bar_glass_glow_desc),
                    icon = painterResource(R.drawable.tune),
                    checked = glow,
                    onCheckedChange = onGlow,
                ),
                switchItem(
                    key = "Show labels",
                    title = stringResource(R.string.navigation_bar_labels),
                    description = stringResource(R.string.navigation_bar_labels_subtitle),
                    icon = painterResource(R.drawable.tune),
                    checked = labels,
                    onCheckedChange = onLabels,
                ),
            ),
        )
        NavBarFooter(stringResource(R.string.navigation_bar_footer))

        if (glow) {
            SettingsSection(
                null,
                listOf(
                    dialItem(
                        stringResource(R.string.navigation_bar_glass_glow_intensity),
                        NavBarGlassGlowIntensityKey, NavBarGlowIntensityDefault, 0.2f..2f, steps = 17,
                        description = stringResource(R.string.navigation_bar_glass_glow_intensity_desc),
                    ) { "${(it * 100).roundToInt()}%" },
                ),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NavBarFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
    )
}

// a titled slider row with its value on the right, the shape of the Playback page's History duration
@Composable
private fun dialItem(
    title: String,
    key: androidx.datastore.preferences.core.Preferences.Key<Float>,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String? = null,
    format: @Composable (Float) -> String,
): SettingsItem {
    val (value, onValue) = rememberPreference(key, default)
    val shown = format(value.coerceIn(range))
    return SettingsItem(
        title = { ValueTitle(title, shown) },
        description = {
            androidx.compose.foundation.layout.Column {
                if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium)
                DefaultPlayerSeekBarSlider(
                    value = value.coerceIn(range),
                    onValueChange = onValue,
                    valueRange = range,
                    steps = steps,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        icon = painterResource(R.drawable.sliders),
        iconShape = CircleShape,
        searchText = title,
    )
}
