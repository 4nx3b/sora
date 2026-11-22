// NavigationBarSettingsScreen.kt
// Neiro's Navigation bar page (SettingsSheet's NavigationBarSettingsPage): the glass glow and
// its strength, the labels under the tabs, and the six dimension dials — which now shape both
// the standard bar and the Liquid Glass bar, so every dial moves something on screen.

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
import com.mochyy.mochymusic.constants.NavBarCornerRadiusDefault
import com.mochyy.mochymusic.constants.NavBarCornerRadiusKey
import com.mochyy.mochymusic.constants.NavBarGlassGlowIntensityKey
import com.mochyy.mochymusic.constants.NavBarGlassGlowKey
import com.mochyy.mochymusic.constants.NavBarGlowIntensityDefault
import com.mochyy.mochymusic.constants.NavBarHeightDefault
import com.mochyy.mochymusic.constants.NavBarHeightKey
import com.mochyy.mochymusic.constants.NavBarLabelSpacingDefault
import com.mochyy.mochymusic.constants.NavBarLabelSpacingKey
import com.mochyy.mochymusic.constants.NavBarOpacityDefault
import com.mochyy.mochymusic.constants.NavBarOpacityKey
import com.mochyy.mochymusic.constants.NavBarShowLabelsKey
import com.mochyy.mochymusic.constants.NavBarTransparencyDefault
import com.mochyy.mochymusic.constants.NavBarTransparencyKey
import com.mochyy.mochymusic.constants.NavBarWidthDefault
import com.mochyy.mochymusic.constants.NavBarWidthKey
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
        com.mochyy.mochymusic.ui.screens.gesture.PortedPreviewHeader { com.mochyy.mochymusic.ui.screens.gesture.NavBarStylePreview() }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
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

        val atDefaults = near(rememberPreference(NavBarWidthKey, NavBarWidthDefault).value, NavBarWidthDefault) &&
            near(rememberPreference(NavBarHeightKey, NavBarHeightDefault).value, NavBarHeightDefault) &&
            near(rememberPreference(NavBarOpacityKey, NavBarOpacityDefault).value, NavBarOpacityDefault) &&
            near(rememberPreference(NavBarTransparencyKey, NavBarTransparencyDefault).value, NavBarTransparencyDefault) &&
            near(rememberPreference(NavBarLabelSpacingKey, NavBarLabelSpacingDefault).value, NavBarLabelSpacingDefault) &&
            near(rememberPreference(NavBarCornerRadiusKey, NavBarCornerRadiusDefault).value, NavBarCornerRadiusDefault)
        val resetters = listOf(
            rememberPreference(NavBarWidthKey, NavBarWidthDefault),
            rememberPreference(NavBarHeightKey, NavBarHeightDefault),
            rememberPreference(NavBarOpacityKey, NavBarOpacityDefault),
            rememberPreference(NavBarTransparencyKey, NavBarTransparencyDefault),
            rememberPreference(NavBarLabelSpacingKey, NavBarLabelSpacingDefault),
            rememberPreference(NavBarCornerRadiusKey, NavBarCornerRadiusDefault),
        )
        val defaults = listOf(
            NavBarWidthDefault, NavBarHeightDefault, NavBarOpacityDefault,
            NavBarTransparencyDefault, NavBarLabelSpacingDefault, NavBarCornerRadiusDefault,
        )
        val percent: @Composable (Float) -> String = { "${(it * 100).roundToInt()}%" }
        val dp: @Composable (Float) -> String = { stringResource(R.string.navigation_bar_dp_value, it.roundToInt()) }

        SettingsSection(
            stringResource(R.string.navigation_bar_dimensions),
            listOf(
                dialItem(stringResource(R.string.navigation_bar_width), NavBarWidthKey, NavBarWidthDefault, 0.5f..1f, steps = 9, format = percent),
                dialItem(stringResource(R.string.navigation_bar_height), NavBarHeightKey, NavBarHeightDefault, 0.8f..1.4f, steps = 11, format = percent),
                dialItem(stringResource(R.string.navigation_bar_opacity), NavBarOpacityKey, NavBarOpacityDefault, 0.2f..1f, steps = 15, format = percent),
                dialItem(stringResource(R.string.navigation_bar_transparency), NavBarTransparencyKey, NavBarTransparencyDefault, 0f..0.95f, steps = 18, format = percent),
                dialItem(stringResource(R.string.navigation_bar_label_spacing), NavBarLabelSpacingKey, NavBarLabelSpacingDefault, 0f..16f, steps = 15, format = dp),
                dialItem(stringResource(R.string.navigation_bar_corner_radius), NavBarCornerRadiusKey, NavBarCornerRadiusDefault, 0f..48f, steps = 47, format = dp),
                SettingsItem(
                    title = { Text(stringResource(R.string.navigation_bar_reset_dimensions)) },
                    icon = painterResource(R.drawable.restore),
                    iconShape = CircleShape,
                    onClick = { if (!atDefaults) resetters.forEachIndexed { i, pref -> pref.value = defaults[i] } },
                    enabled = !atDefaults,
                    searchText = "Reset dimensions",
                ),
            ),
        )
        NavBarFooter(stringResource(R.string.navigation_bar_dimensions_footer))
        Spacer(Modifier.height(40.dp))
    }
}

private fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.001f

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
