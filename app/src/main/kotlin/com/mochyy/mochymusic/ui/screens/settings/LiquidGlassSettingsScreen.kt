// Neiro's liquid glass settings on one page, each with its own switch; acrylic blur and liquid glass exclude each other.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavController
import com.mochyy.mochymusic.ui.component.sliderDragScrollLock
import com.mochyy.mochymusic.constants.AcrylicBlurKey
import com.mochyy.mochymusic.constants.LiquidGlassAdaptiveLuminanceKey
import com.mochyy.mochymusic.constants.LiquidGlassChromaticAberrationKey
import com.mochyy.mochymusic.constants.LiquidGlassDepth3DKey
import com.mochyy.mochymusic.constants.LiquidGlassEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassMiniPlayerKey
import com.mochyy.mochymusic.constants.LiquidGlassNavBarKey
import com.mochyy.mochymusic.constants.LiquidGlassPerformanceKey
import com.mochyy.mochymusic.constants.LiquidGlassSlidersKey
import com.mochyy.mochymusic.constants.LiquidGlassTogglesKey
import com.mochyy.mochymusic.constants.LiquidGlassVibrancyKey
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SwitchPreference
import com.mochyy.mochymusic.ui.component.liquid.LiquidGlassValue
import com.mochyy.mochymusic.ui.component.liquid.LiquidGlassValues
import com.mochyy.mochymusic.ui.component.liquid.isGlassSupported
import com.mochyy.mochymusic.ui.component.liquid.setGlassMaterial
import com.mochyy.mochymusic.utils.rememberPreference

const val LiquidGlassSettingsRoute = "customisation_settings/liquid_glass"

@Composable
fun LiquidGlassSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val liquid by rememberPreference(LiquidGlassEnabledKey, false)
    val acrylic by rememberPreference(AcrylicBlurKey, false)
    val supported = isGlassSupported()
    SubSettingsScaffold(
        title = "Liquid glass",
        onBack = { navController.navigateUp() },
        backLabel = "Customisation",
    ) {
        com.mochyy.mochymusic.ui.screens.gesture.PortedPreviewHeader { com.mochyy.mochymusic.ui.screens.gesture.LiquidGlassPreview() }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!supported) Text("Liquid glass needs Android 12 or newer.", modifier = Modifier.padding(horizontal = 16.dp))
            SwitchPreference(
                title = { Text("Liquid glass") },
                description = if (acrylic) "Off while acrylic blur is on" else null,
                checked = liquid,
                onCheckedChange = { setGlassMaterial(context, liquid = it) },
                isEnabled = supported,
            )
            SwitchPreference(
                title = { Text("Acrylic blur") },
                description = "Frosted blur without the lens. Turns liquid glass off",
                checked = acrylic,
                onCheckedChange = { setGlassMaterial(context, acrylic = it) },
                isEnabled = supported,
            )
            val material = supported && (liquid || acrylic)
            BoolRow("Navigation bar", LiquidGlassNavBarKey, false, material)
            BoolRow("Miniplayer", LiquidGlassMiniPlayerKey, false, material)
            BoolRow("Sliders", LiquidGlassSlidersKey, false, material)
            BoolRow("Toggles", LiquidGlassTogglesKey, false, material)
            val tune = supported && liquid
            ValueRow("Refraction height", LiquidGlassValues.RefractionHeight, tune)
            ValueRow("Refraction amount", LiquidGlassValues.RefractionAmount, tune)
            ValueRow("Refraction width", LiquidGlassValues.RefractionWidth, tune)
            ValueRow("Blur radius", LiquidGlassValues.BlurRadius, tune)
            ValueRow("Tint opacity", LiquidGlassValues.TintOpacity, tune)
            ValueRow("Shadow depth", LiquidGlassValues.ShadowDepth, tune)
            ValueRow("Corner radius", LiquidGlassValues.CornerRadius, tune)
            ValueRow("Chromatic amount", LiquidGlassValues.ChromaticAmount, tune)
            ValueRow("Clarity", LiquidGlassValues.Clarity, tune)
            ValueRow("Saturation", LiquidGlassValues.Saturation, tune)
            ValueRow("Rainbow", LiquidGlassValues.Rainbow, tune)
            ValueRow("Rim", LiquidGlassValues.Rim, tune)
            ValueRow("Quality", LiquidGlassValues.Quality, tune)
            BoolRow("3D depth", LiquidGlassDepth3DKey, true, tune)
            BoolRow("Chromatic aberration", LiquidGlassChromaticAberrationKey, true, tune)
            BoolRow("Backdrop vibrancy", LiquidGlassVibrancyKey, true, tune)
            BoolRow("Adaptive luminance", LiquidGlassAdaptiveLuminanceKey, true, tune)
            BoolRow("Performance mode", LiquidGlassPerformanceKey, false, tune)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BoolRow(title: String, key: Preferences.Key<Boolean>, default: Boolean, enabled: Boolean) {
    val (on, setOn) = rememberPreference(key, default)
    SwitchPreference(title = { Text(title) }, checked = on, onCheckedChange = setOn, isEnabled = enabled)
}

@Composable
private fun ValueRow(title: String, value: LiquidGlassValue, enabled: Boolean) {
    val (on, setOn) = rememberPreference(value.enabledKey, false)
    val (v, setV) = rememberPreference(value.key, value.default)
    SwitchPreference(title = { Text(title) }, checked = on, onCheckedChange = setOn, isEnabled = enabled)
    AnimatedVisibility(on && enabled) {
        val live = com.mochyy.mochymusic.ui.component.liquid.LiquidGlassLiveValues.dragging
        androidx.compose.runtime.DisposableEffect(value.key) { onDispose { live.remove(value.key) } }
        val shown = (live[value.key] ?: v).coerceIn(value.range)
        val onChange: (Float) -> Unit = { live[value.key] = it }
        val onFinished = {
            live.remove(value.key)?.let(setV)
            Unit
        }
        val glass = com.mochyy.mochymusic.ui.component.liquid.LocalLiquidGlass.current
        if (glass.surface(glass.sliders)) {
            com.mochyy.mochymusic.ui.component.liquid.LiquidGlassSlider(
                value = shown,
                onValueChange = onChange,
                valueRange = value.range,
                onValueChangeFinished = onFinished,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .sliderDragScrollLock(),
            )
        } else {
            Slider(
                value = shown,
                onValueChange = onChange,
                onValueChangeFinished = onFinished,
                valueRange = value.range,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .sliderDragScrollLock(),
            )
        }
    }
}
