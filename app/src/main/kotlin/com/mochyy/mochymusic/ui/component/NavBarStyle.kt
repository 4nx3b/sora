// NavBarStyle.kt
// The standard bar's own look from Neiro's navigation bar page: the labels under the tabs and the
// glass glow. The liquid glass bar keeps its own shape. Neiro's six dimension dials were removed:
// they never changed anything the bar drew.

package com.mochyy.mochymusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.NavBarGlassGlowIntensityKey
import com.mochyy.mochymusic.constants.NavBarGlassGlowKey
import com.mochyy.mochymusic.constants.NavBarGlowIntensityDefault
import com.mochyy.mochymusic.constants.NavBarShowLabelsKey
import com.mochyy.mochymusic.utils.rememberPreference

@Immutable
data class NavBarStyle(
    val showLabels: Boolean = false,
    // the gap between a tab's icon and its label
    val labelSpacing: Dp = 1.dp,
    // 0 when the glow is off
    val glow: Float = 0f,
)

@Composable
fun rememberNavBarStyle(glassBar: Boolean): NavBarStyle {
    val labels by rememberPreference(NavBarShowLabelsKey, false)
    val glowOn by rememberPreference(NavBarGlassGlowKey, false)
    val glowIntensity by rememberPreference(NavBarGlassGlowIntensityKey, NavBarGlowIntensityDefault)
    val glow = if (glowOn) glowIntensity.coerceIn(0.2f, 2f) else 0f
    return NavBarStyle(showLabels = labels, glow = glow)
}
