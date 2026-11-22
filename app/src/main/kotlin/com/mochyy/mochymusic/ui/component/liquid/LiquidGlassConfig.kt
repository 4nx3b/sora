// Neiro's liquid glass settings, read from sora's DataStore instead of Neiro's AppSettings flows. Everything defaults to off:
// a surface only turns glassy when its own switch and one of the two materials (liquid glass or acrylic) are on.

package com.mochyy.mochymusic.ui.component.liquid

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.AcrylicBlurKey
import com.mochyy.mochymusic.constants.AnimationSpeedKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.LiquidGlassAdaptiveLuminanceKey
import com.mochyy.mochymusic.constants.LiquidGlassBlurRadiusEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassBlurRadiusKey
import com.mochyy.mochymusic.constants.LiquidGlassChromaticAberrationKey
import com.mochyy.mochymusic.constants.LiquidGlassChromaticAmountEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassChromaticAmountKey
import com.mochyy.mochymusic.constants.LiquidGlassClarityEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassClarityKey
import com.mochyy.mochymusic.constants.LiquidGlassCornerRadiusEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassCornerRadiusKey
import com.mochyy.mochymusic.constants.LiquidGlassDepth3DKey
import com.mochyy.mochymusic.constants.LiquidGlassEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassIntensityEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassIntensityKey
import com.mochyy.mochymusic.constants.LiquidGlassMiniPlayerKey
import com.mochyy.mochymusic.constants.LiquidGlassNavBarKey
import com.mochyy.mochymusic.constants.LiquidGlassPerformanceKey
import com.mochyy.mochymusic.constants.LiquidGlassQualityEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassQualityKey
import com.mochyy.mochymusic.constants.LiquidGlassRainbowEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassRainbowKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionAmountEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionAmountKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionHeightEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionHeightKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionWidthEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassRefractionWidthKey
import com.mochyy.mochymusic.constants.LiquidGlassRimEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassRimKey
import com.mochyy.mochymusic.constants.LiquidGlassSaturationEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassSaturationKey
import com.mochyy.mochymusic.constants.LiquidGlassShadowDepthEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassShadowDepthKey
import com.mochyy.mochymusic.constants.LiquidGlassSlidersKey
import com.mochyy.mochymusic.constants.LiquidGlassTintOpacityEnabledKey
import com.mochyy.mochymusic.constants.LiquidGlassTintOpacityKey
import com.mochyy.mochymusic.constants.LiquidGlassTogglesKey
import com.mochyy.mochymusic.constants.LiquidGlassVibrancyKey
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

enum class LiquidGlassIntensity { SUBTLE, STANDARD, VIVID }

/** One tuning value: its key, the switch that lets it count, Neiro's default and its range. */
class LiquidGlassValue(
    val key: Preferences.Key<Float>,
    val enabledKey: Preferences.Key<Boolean>,
    val default: Float,
    val range: ClosedFloatingPointRange<Float>,
)

object LiquidGlassValues {
    val RefractionHeight = LiquidGlassValue(LiquidGlassRefractionHeightKey, LiquidGlassRefractionHeightEnabledKey, 0.30f, 0f..1f)
    val RefractionAmount = LiquidGlassValue(LiquidGlassRefractionAmountKey, LiquidGlassRefractionAmountEnabledKey, 0.75f, 0f..1f)
    val RefractionWidth = LiquidGlassValue(LiquidGlassRefractionWidthKey, LiquidGlassRefractionWidthEnabledKey, LiquidGlassUpstream.REFRACTION_WIDTH_DEFAULT, 0f..1f)
    val BlurRadius = LiquidGlassValue(LiquidGlassBlurRadiusKey, LiquidGlassBlurRadiusEnabledKey, 0.65f, 0f..1f)
    val TintOpacity = LiquidGlassValue(LiquidGlassTintOpacityKey, LiquidGlassTintOpacityEnabledKey, 1.40f, 0f..2f)
    val ShadowDepth = LiquidGlassValue(LiquidGlassShadowDepthKey, LiquidGlassShadowDepthEnabledKey, 0.45f, 0f..1f)
    val CornerRadius = LiquidGlassValue(LiquidGlassCornerRadiusKey, LiquidGlassCornerRadiusEnabledKey, LiquidGlassUpstream.CORNER_RADIUS_DEFAULT, 0f..1f)
    val ChromaticAmount = LiquidGlassValue(LiquidGlassChromaticAmountKey, LiquidGlassChromaticAmountEnabledKey, LiquidGlassUpstream.CHROMATIC_AMOUNT_DEFAULT, 0f..1f)
    val Clarity = LiquidGlassValue(LiquidGlassClarityKey, LiquidGlassClarityEnabledKey, LiquidGlassUpstream.CLARITY_DEFAULT, 0f..1f)
    val Saturation = LiquidGlassValue(LiquidGlassSaturationKey, LiquidGlassSaturationEnabledKey, LiquidGlassUpstream.SATURATION_DEFAULT, 0f..2f)
    val Rainbow = LiquidGlassValue(LiquidGlassRainbowKey, LiquidGlassRainbowEnabledKey, LiquidGlassUpstream.RAINBOW_DEFAULT, 0f..1f)
    val Rim = LiquidGlassValue(LiquidGlassRimKey, LiquidGlassRimEnabledKey, LiquidGlassUpstream.RIM_DEFAULT, 0f..1f)
    val Quality = LiquidGlassValue(LiquidGlassQualityKey, LiquidGlassQualityEnabledKey, LiquidGlassUpstream.QUALITY_DEFAULT, 0.2f..1f)
}

@Immutable
data class LiquidGlassConfig(
    val enabled: Boolean = false,
    val acrylic: Boolean = false,
    val navBar: Boolean = false,
    val miniPlayer: Boolean = false,
    val sliders: Boolean = false,
    val toggles: Boolean = false,
    val performance: Boolean = false,
    val reduceBlur: Boolean = false,
    val reduceMotion: Boolean = false,
    val tuning: LiquidGlassTuning = LiquidGlassTuning(),
) {
    fun surface(on: Boolean): Boolean = on && (enabled || acrylic) && isGlassSupported()

    val anySurface: Boolean get() = surface(navBar) || surface(miniPlayer) || surface(sliders) || surface(toggles)
}

val LocalLiquidGlass = staticCompositionLocalOf { LiquidGlassConfig() }

/** The two materials exclude each other, so both are written in one edit. */
fun setGlassMaterial(context: Context, liquid: Boolean? = null, acrylic: Boolean? = null) {
    PreferencesCache.edit(context.applicationContext.dataStore) {
        if (liquid != null) {
            it[LiquidGlassEnabledKey] = liquid
            if (liquid) it[AcrylicBlurKey] = false
        }
        if (acrylic != null) {
            it[AcrylicBlurKey] = acrylic
            if (acrylic) it[LiquidGlassEnabledKey] = false
        }
    }
}

/** a tuning value while its slider is held, so the glass follows the finger before the value is saved on release */
object LiquidGlassLiveValues {
    val dragging = androidx.compose.runtime.mutableStateMapOf<Preferences.Key<Float>, Float>()
}

@Composable
private fun tuningValue(value: LiquidGlassValue): Float {
    // a slider always counts now; its old per-value switch key is kept so saved settings still read
    val v by rememberPreference(value.key, value.default)
    val live = LiquidGlassLiveValues.dragging[value.key]
    return (live ?: v).coerceIn(value.range)
}

@Composable
fun rememberLiquidGlassConfig(): LiquidGlassConfig {
    val enabled by rememberPreference(LiquidGlassEnabledKey, false)
    val acrylic by rememberPreference(AcrylicBlurKey, false)
    val navBar by rememberPreference(LiquidGlassNavBarKey, false)
    val miniPlayer by rememberPreference(LiquidGlassMiniPlayerKey, false)
    val sliders by rememberPreference(LiquidGlassSlidersKey, false)
    val toggles by rememberPreference(LiquidGlassTogglesKey, false)
    val performance by rememberPreference(LiquidGlassPerformanceKey, false)
    val reduceBlur by rememberPreference(DisableBlurKey, false)
    val speed by rememberPreference(AnimationSpeedKey, 1f)
    val depth3D by rememberPreference(LiquidGlassDepth3DKey, true)
    val chromatic by rememberPreference(LiquidGlassChromaticAberrationKey, true)
    val vibrancy by rememberPreference(LiquidGlassVibrancyKey, true)
    val adaptive by rememberPreference(LiquidGlassAdaptiveLuminanceKey, true)
    val intensityOn by rememberPreference(LiquidGlassIntensityEnabledKey, false)
    val intensity by rememberEnumPreference(LiquidGlassIntensityKey, LiquidGlassIntensity.STANDARD)
    val tuning = LiquidGlassTuning(
        intensity = if (intensityOn) intensity else LiquidGlassIntensity.STANDARD,
        refractionHeightFraction = tuningValue(LiquidGlassValues.RefractionHeight),
        refractionAmountFraction = tuningValue(LiquidGlassValues.RefractionAmount),
        blurFraction = tuningValue(LiquidGlassValues.BlurRadius),
        tintFraction = tuningValue(LiquidGlassValues.TintOpacity),
        shadowFraction = tuningValue(LiquidGlassValues.ShadowDepth),
        depth3D = depth3D,
        chromaticAberration = chromatic,
        backdropVibrancy = vibrancy,
        adaptiveLuminance = adaptive,
        cornerRadius = tuningValue(LiquidGlassValues.CornerRadius),
        chromaticAmount = tuningValue(LiquidGlassValues.ChromaticAmount),
        clarity = tuningValue(LiquidGlassValues.Clarity),
        saturation = tuningValue(LiquidGlassValues.Saturation),
        refractionWidth = tuningValue(LiquidGlassValues.RefractionWidth),
        rainbow = tuningValue(LiquidGlassValues.Rainbow),
        rim = tuningValue(LiquidGlassValues.Rim),
        quality = tuningValue(LiquidGlassValues.Quality),
    )
    return remember(enabled, acrylic, navBar, miniPlayer, sliders, toggles, performance, reduceBlur, speed, tuning) {
        LiquidGlassConfig(enabled, acrylic, navBar, miniPlayer, sliders, toggles, performance, reduceBlur, speed == 0f, tuning)
    }
}
