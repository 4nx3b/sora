// NavBarStyle.kt
// The standard bar's own look from Neiro's navigation bar page: the labels under the tabs, the
// glass glow, and the six dimension dials — width, height, opacity, transparency, label spacing
// and corner radius. Every dial applies to whichever bar is drawing: the frosted standard bar
// takes them as its fill and geometry, the Liquid Glass bar takes them as its shape and the
// alpha of its glass layer. (The dials used to stand down while the glass bar was on, which
// read on a glass-bar phone as "the dials change nothing" — that's the bug this fixes.)

package com.mochyy.mochymusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.mochyy.mochymusic.utils.rememberPreference

@Immutable
data class NavBarStyle(
    val widthFraction: Float = NavBarWidthDefault,
    val heightScale: Float = NavBarHeightDefault,
    val opacity: Float = NavBarOpacityDefault,
    val transparency: Float = NavBarTransparencyDefault,
    val labelSpacing: Dp = NavBarLabelSpacingDefault.dp,
    val cornerRadius: Dp = NavBarCornerRadiusDefault.dp,
    val showLabels: Boolean = false,
    // 0 when the glow is off
    val glow: Float = 0f,
) {
    // how much of the bar's fill shows: Neiro multiplies the two dials
    val surfaceAlpha: Float get() = (opacity * (1f - transparency)).coerceIn(0f, 1f)

    // the extra height the bar takes over sora's 64dp, so the mini player and the pages make room for it
    val extraHeight: Dp get() = NavBarHeight * (heightScale - 1f)
}

@Composable
fun rememberNavBarStyle(): NavBarStyle {
    val width by rememberPreference(NavBarWidthKey, NavBarWidthDefault)
    val height by rememberPreference(NavBarHeightKey, NavBarHeightDefault)
    val opacity by rememberPreference(NavBarOpacityKey, NavBarOpacityDefault)
    val transparency by rememberPreference(NavBarTransparencyKey, NavBarTransparencyDefault)
    val spacing by rememberPreference(NavBarLabelSpacingKey, NavBarLabelSpacingDefault)
    val corner by rememberPreference(NavBarCornerRadiusKey, NavBarCornerRadiusDefault)
    val labels by rememberPreference(NavBarShowLabelsKey, false)
    val glowOn by rememberPreference(NavBarGlassGlowKey, false)
    val glowIntensity by rememberPreference(NavBarGlassGlowIntensityKey, NavBarGlowIntensityDefault)
    val glow = if (glowOn) glowIntensity.coerceIn(0.2f, 2f) else 0f
    return NavBarStyle(
        widthFraction = width.coerceIn(0.5f, 1f),
        heightScale = height.coerceIn(0.8f, 1.4f),
        opacity = opacity.coerceIn(0.2f, 1f),
        transparency = transparency.coerceIn(0f, 0.95f),
        labelSpacing = spacing.coerceIn(0f, 16f).dp,
        cornerRadius = corner.coerceIn(0f, 48f).dp,
        showLabels = labels,
        glow = glow,
    )
}
