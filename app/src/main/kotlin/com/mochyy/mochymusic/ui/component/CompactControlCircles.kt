// Neiro's compact fold (ui/components/CompactControlCircles.kt): once the tabs fall away, Home and Search
// sit as two glass circles either side of the thinned mini player. Drawn in sora's chrome glass, with the
// Navigation bar page's glow on them as on the bar.

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalGlassState
import com.mochyy.mochymusic.ui.component.liquid.glassGlowOverlay
import com.mochyy.mochymusic.ui.component.liquid.liquidGlassSurface
import com.mochyy.mochymusic.ui.screens.Screens

val CompactCircleSize = 56.dp
val CompactCircleInset = 16.dp

// the pill's side inset once folded: past the circle and an 8dp gap
val CompactPillInset: Dp = CompactCircleInset + CompactCircleSize + 8.dp

@Composable
fun CompactControlCircles(
    fraction: () -> Float,
    leftIsLibrary: Boolean,
    pureBlack: Boolean,
    onLeft: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CompactCircleInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactControlCircle(if (leftIsLibrary) Screens.Library else Screens.Home, pureBlack, onLeft, fraction)
        CompactControlCircle(Screens.Search, pureBlack, onSearch, fraction)
    }
}

@Composable
private fun CompactControlCircle(
    screen: Screens,
    pureBlack: Boolean,
    onClick: () -> Unit,
    fraction: () -> Float,
) {
    val liquid = com.mochyy.mochymusic.ui.component.liquid.LocalLiquidGlass.current
    val liquidCircle = liquid.surface(liquid.navBar)
    val style = rememberNavBarStyle(glassBar = liquidCircle)
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    val icon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdInactive)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(CompactCircleSize)
            .graphicsLayer {
                val f = fraction()
                alpha = f
                scaleX = 0.6f + 0.4f * f
                scaleY = 0.6f + 0.4f * f
                translationY = (1f - f) * 40.dp.toPx()
            }
            .dropShadow(CircleShape, ChromeShadow)
            .clip(CircleShape)
            .drawWithContent {
                drawContent()
                drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
            }
            .then(if (style.glow > 0f) Modifier.glassGlowOverlay(style.glow, CircleShape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        if (liquidCircle) {
            Box(Modifier.fillMaxSize().liquidGlassSurface(CircleShape))
        } else {
            FrostedChromeGlass(state = glassState, pureBlack = pureBlack, accentWash = null, modifier = Modifier.fillMaxSize())
        }
        Icon(
            painter = icon,
            contentDescription = stringResource(screen.titleId),
            tint = Color.White,
            modifier = Modifier.size(if (screen == Screens.Library) 24.dp else 22.dp),
        )
    }
}

// the mini player thins as the bar folds, leaving the circles room; stepped in fifths so the player under
// it recomposes five times per fold rather than every frame, and the frame itself at rest
@Composable
fun FoldedMiniPill(base: MiniPillFrame, fold: () -> Float, content: @Composable () -> Unit) {
    val step by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { (fold() * 5f).roundToInt() } }
    val frame = androidx.compose.runtime.remember(base, step) {
        if (step == 0 || base.merged) base else {
            val f = step / 5f
            MiniPillFrame(
                start = base.start + (CompactPillInset - base.start).coerceAtLeast(0.dp) * f,
                end = base.end + (CompactPillInset - base.end).coerceAtLeast(0.dp) * f,
                merged = base.merged,
                withPrevious = base.withPrevious,
                height = base.height,
            )
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMiniPillFrame provides frame, content = content)
}
