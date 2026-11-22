// CustomisationPortedPreviews.kt
// The little live screens for the settings ported from Neiro: the navigation bar's dials, the mini player and
// bar fold, the app font, the always-on screen and the liquid glass. Drawn from the same kit as the other
// Customisation previews, reading the very settings their rows write, so every toggle and slider shows here.

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mochyy.mochymusic.constants.AodAutoDimmingKey
import com.mochyy.mochymusic.constants.AodBrightnessDefault
import com.mochyy.mochymusic.constants.AodBrightnessKey
import com.mochyy.mochymusic.constants.AodPixelShiftKey
import com.mochyy.mochymusic.constants.AodShowAlbumKey
import com.mochyy.mochymusic.constants.AodShowArtistKey
import com.mochyy.mochymusic.constants.AodShowBatteryKey
import com.mochyy.mochymusic.constants.AodShowClockKey
import com.mochyy.mochymusic.constants.AodShowControlsKey
import com.mochyy.mochymusic.constants.AodShowProgressKey
import com.mochyy.mochymusic.constants.AodShowThumbnailKey
import com.mochyy.mochymusic.constants.AodShowTimeLabelsKey
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.liquid.LocalAppBackdrop
import com.mochyy.mochymusic.ui.component.liquid.LocalLiquidGlass
import com.mochyy.mochymusic.ui.component.liquid.isGlassSupported
import com.mochyy.mochymusic.ui.component.liquid.liquidGlassSurface
import com.mochyy.mochymusic.ui.component.rememberNavBarStyle
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val StepSpec = tween<Float>(700, easing = FastOutSlowInEasing)
private val ChangeSpec = tween<Float>(260)
private const val HoldMs = 900L
private const val RestMs = 500L

// the real bar is 64dp tall; the preview's is this, so the dials' dp values are drawn at the same proportion
private val PreviewBarHeight = 22.dp
private const val BarScale = 22f / 64f

// waits while the picture is off screen or behind another page, so no loop runs where nobody can see it
private suspend fun awaitShown(held: androidx.compose.runtime.State<Boolean>) {
    snapshotFlow { held.value }.first { !it }
}

private fun DrawScope.pill(color: Color, left: Float, top: Float, width: Float, height: Float, alpha: Float = 1f) {
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2f), alpha = alpha)
}

// a small player screen behind the bar: the cover and its two title bars
private fun DrawScope.playerBehind() {
    val side = 10.dp.toPx()
    drawRoundRect(ArtFill, Offset(side, 10.dp.toPx()), Size(size.width - side * 2, 54.dp.toPx()), CornerRadius(8.dp.toPx()))
    pill(Ink, side, 72.dp.toPx(), 42.dp.toPx(), 4.5.dp.toPx())
    pill(Ink, side, 80.5.dp.toPx(), 30.dp.toPx(), 4.5.dp.toPx())
}

// the navigation bar as its page sets it: width, height, corner, fill and labels, a lit tab sliding
// along it and, with the glass glow on, a sheen running across after each slide
@Composable
internal fun NavBarStylePreview() {
    val style = rememberNavBarStyle()
    val labels = animateFloatAsState(if (style.showLabels) 1f else 0f, ChangeSpec, label = "navPreviewLabels")
    val glow = animateFloatAsState(style.glow, ChangeSpec, label = "navPreviewGlow")
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val tab = remember { Animatable(0f) }
    val sheen = remember { Animatable(0f) }
    val motion = AppMotion.enabled
    LaunchedEffect(held, motion) {
        if (!motion) return@LaunchedEffect
        var next = 1
        while (true) {
            awaitShown(held)
            tab.animateTo(next.toFloat(), StepSpec)
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(700, easing = LinearEasing))
            delay(HoldMs)
            next = (next + 1) % 3
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .trackOnScreen(onScreen)
            .damageBoundary()
            .drawBehind {
                playerBehind()
                val margin = 8.dp.toPx()
                val width = (size.width - margin * 2) * style.widthFraction
                val height = PreviewBarHeight.toPx() * style.heightScale
                val left = (size.width - width) / 2f
                val top = size.height - 14.dp.toPx() - height
                val radius = (style.cornerRadius.toPx() * BarScale).coerceAtMost(height / 2f)
                val corner = CornerRadius(radius)
                drawRoundRect(Bone, Offset(left, top), Size(width, height), corner, alpha = style.surfaceAlpha)

                val tabWidth = width / 3f
                val lit = tab.value
                val capsuleWidth = tabWidth * 0.78f
                val capsuleHeight = height - 5.dp.toPx()
                val capsuleLeft = left + tabWidth * lit + (tabWidth - capsuleWidth) / 2f
                drawRoundRect(
                    Color.White,
                    Offset(capsuleLeft, top + (height - capsuleHeight) / 2f),
                    Size(capsuleWidth, capsuleHeight),
                    CornerRadius((radius - 2.5.dp.toPx()).coerceAtLeast(capsuleHeight / 4f)),
                    alpha = 0.9f,
                )

                val labelsBy = labels.value
                val dot = 2.6.dp.toPx()
                val labelHeight = 2.dp.toPx()
                val gap = 1.dp.toPx() + style.labelSpacing.toPx() * BarScale
                val stack = dot * 2 + (gap + labelHeight) * labelsBy
                repeat(3) { i ->
                    val cx = left + tabWidth * (i + 0.5f)
                    val cy = top + (height - stack) / 2f + dot
                    // the tab under the lit capsule turns dark so it reads on white
                    val onCapsule = (1f - kotlin.math.abs(lit - i)).coerceIn(0f, 1f)
                    val color = androidx.compose.ui.graphics.lerp(Ink, FrameFill, onCapsule)
                    drawCircle(color, dot, Offset(cx, cy))
                    if (labelsBy > 0f) {
                        pill(color, cx - 4.dp.toPx(), cy + dot + gap, 8.dp.toPx(), labelHeight, alpha = labelsBy)
                    }
                }

                val glowBy = glow.value
                if (glowBy > 0.01f) {
                    val shape = Path().apply { addRoundRect(RoundRect(left, top, left + width, top + height, corner)) }
                    drawPath(shape, Color.White, alpha = (0.25f * glowBy).coerceAtMost(0.5f), style = Stroke(1.dp.toPx()))
                    val band = width * 0.35f
                    val x = left - band + (width + band) * sheen.value
                    clipPath(shape) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = (0.35f * glowBy).coerceAtMost(0.7f)), Color.Transparent),
                                startX = x,
                                endX = x + band,
                            ),
                            Offset(x, top),
                            Size(band, height),
                        )
                    }
                }
            },
    )
}

// the app's type in the font picked on the Font page: a big Aa breathing from regular to bold, and the font's name
@Composable
internal fun FontPreview(fontName: String) {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val weight = remember { Animatable(0f) }
    val motion = AppMotion.enabled
    LaunchedEffect(held, motion) {
        if (!motion) return@LaunchedEffect
        while (true) {
            awaitShown(held)
            weight.animateTo(1f, StepSpec)
            delay(HoldMs)
            weight.animateTo(0f, StepSpec)
            delay(RestMs)
        }
    }
    // InterFontFamily follows the picked font (and a look's own font), so a new pick crossfades in here
    Crossfade(
        targetState = InterFontFamily,
        modifier = Modifier.fillMaxSize().trackOnScreen(onScreen).damageBoundary(),
        animationSpec = ChangeSpec,
        label = "fontPreview",
    ) { family ->
        FontSample(family, fontName, weight)
    }
}

@Composable
private fun FontSample(family: FontFamily, fontName: String, weight: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val sample = TextStyle(fontFamily = family, fontSize = 40.sp, color = Color.White)
            BasicText("Aa", style = sample.copy(fontWeight = FontWeight.Normal), modifier = Modifier.graphicsLayer { alpha = 1f - weight.value })
            BasicText("Aa", style = sample.copy(fontWeight = FontWeight.Bold), modifier = Modifier.graphicsLayer { alpha = weight.value })
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            fontName,
            style = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Bar(54.dp, 3.dp, BoneDim)
    }
}

// the always-on screen on black: clock, battery, cover, title and artist, album, progress with its times and the
// controls, each fading with its switch, at the page's brightness, dimming and nudging as auto dim and pixel shift do
@Composable
internal fun AodPreview() {
    val (thumbnail) = rememberPreference(AodShowThumbnailKey, true)
    val (artist) = rememberPreference(AodShowArtistKey, true)
    val (album) = rememberPreference(AodShowAlbumKey, false)
    val (progress) = rememberPreference(AodShowProgressKey, true)
    val (times) = rememberPreference(AodShowTimeLabelsKey, true)
    val (controls) = rememberPreference(AodShowControlsKey, true)
    val (clock) = rememberPreference(AodShowClockKey, true)
    val (battery) = rememberPreference(AodShowBatteryKey, true)
    val brightness by rememberPreference(AodBrightnessKey, AodBrightnessDefault)
    val (autoDim) = rememberPreference(AodAutoDimmingKey, true)
    val (pixelShift) = rememberPreference(AodPixelShiftKey, true)

    val thumbnailBy = animateFloatAsState(if (thumbnail) 1f else 0f, ChangeSpec, label = "aodThumb")
    val artistBy = animateFloatAsState(if (artist) 1f else 0f, ChangeSpec, label = "aodArtist")
    val albumBy = animateFloatAsState(if (album) 1f else 0f, ChangeSpec, label = "aodAlbum")
    val progressBy = animateFloatAsState(if (progress) 1f else 0f, ChangeSpec, label = "aodProgress")
    val timesBy = animateFloatAsState(if (times && progress) 1f else 0f, ChangeSpec, label = "aodTimes")
    val controlsBy = animateFloatAsState(if (controls) 1f else 0f, ChangeSpec, label = "aodControls")
    val clockBy = animateFloatAsState(if (clock) 1f else 0f, ChangeSpec, label = "aodClock")
    val batteryBy = animateFloatAsState(if (battery) 1f else 0f, ChangeSpec, label = "aodBattery")

    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val played = remember { Animatable(0.25f) }
    val dim = remember { Animatable(1f) }
    val shift = remember { Animatable(0f) }
    val motion = AppMotion.enabled
    LaunchedEffect(held, motion, autoDim, pixelShift) {
        dim.snapTo(1f)
        shift.snapTo(0f)
        if (!motion) return@LaunchedEffect
        while (true) {
            awaitShown(held)
            coroutineScope {
                launch { played.animateTo(if (played.value > 0.85f) 0.25f else played.value + 0.2f, StepSpec) }
                if (autoDim) launch { dim.animateTo(0.6f, tween(1_500)); dim.animateTo(1f, tween(1_500)) }
                if (pixelShift) launch { shift.animateTo(if (shift.value > 0f) -1f else 1f, StepSpec) }
            }
            delay(HoldMs)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .trackOnScreen(onScreen)
            .damageBoundary()
            .background(Color.Black)
            .graphicsLayer {
                // the page's brightness, kept readable at its dimmest so the card still shows what is on it
                alpha = (0.3f + 0.7f * brightness.coerceIn(0f, 1f)) * dim.value
                translationX = shift.value * 1.5.dp.toPx()
                translationY = -shift.value * 1.dp.toPx()
            }
            .drawBehind {
                val side = 12.dp.toPx()
                val clockAlpha = clockBy.value
                if (clockAlpha > 0f) {
                    pill(Ink, size.width / 2f - 22.dp.toPx(), 14.dp.toPx(), 18.dp.toPx(), 12.dp.toPx(), clockAlpha)
                    pill(Ink, size.width / 2f + 4.dp.toPx(), 14.dp.toPx(), 18.dp.toPx(), 12.dp.toPx(), clockAlpha)
                    pill(BoneDim, size.width / 2f - 16.dp.toPx(), 30.dp.toPx(), 32.dp.toPx(), 3.dp.toPx(), clockAlpha)
                }
                val batteryAlpha = batteryBy.value
                if (batteryAlpha > 0f) {
                    val bw = 11.dp.toPx()
                    val bh = 6.dp.toPx()
                    val bl = size.width - side - bw
                    drawRoundRect(Ink, Offset(bl, 6.dp.toPx()), Size(bw, bh), CornerRadius(1.5.dp.toPx()), alpha = batteryAlpha, style = Stroke(1.dp.toPx()))
                    drawRect(Ink, Offset(bl + 1.5.dp.toPx(), 7.5.dp.toPx()), Size(bw * 0.55f, bh - 3.dp.toPx()), alpha = batteryAlpha)
                }
                val rowTop = 44.dp.toPx()
                val art = 26.dp.toPx()
                val thumbAlpha = thumbnailBy.value
                if (thumbAlpha > 0f) drawRoundRect(ArtFill, Offset(side, rowTop), Size(art, art), CornerRadius(4.dp.toPx()), alpha = thumbAlpha)
                // the words slide over to the edge when the cover is hidden
                val textLeft = side + (art + 6.dp.toPx()) * thumbAlpha
                pill(Ink, textLeft, rowTop + 3.dp.toPx(), 44.dp.toPx(), 4.5.dp.toPx())
                pill(Bone, textLeft, rowTop + 11.dp.toPx(), 30.dp.toPx(), 3.5.dp.toPx(), artistBy.value)
                pill(BoneDim, textLeft, rowTop + 18.dp.toPx(), 36.dp.toPx(), 3.dp.toPx(), albumBy.value)
                val lineTop = rowTop + art + 12.dp.toPx()
                val progressAlpha = progressBy.value
                if (progressAlpha > 0f) {
                    val lineWidth = size.width - side * 2
                    pill(BoneDim, side, lineTop, lineWidth, 2.dp.toPx(), progressAlpha)
                    pill(Ink, side, lineTop, lineWidth * played.value, 2.dp.toPx(), progressAlpha)
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(side + lineWidth * played.value, lineTop + 1.dp.toPx()), alpha = progressAlpha)
                    val timesAlpha = timesBy.value
                    if (timesAlpha > 0f) {
                        pill(BoneDim, side, lineTop + 6.dp.toPx(), 12.dp.toPx(), 2.5.dp.toPx(), timesAlpha)
                        pill(BoneDim, size.width - side - 12.dp.toPx(), lineTop + 6.dp.toPx(), 12.dp.toPx(), 2.5.dp.toPx(), timesAlpha)
                    }
                }
                val controlsAlpha = controlsBy.value
                if (controlsAlpha > 0f) {
                    val cy = lineTop + 22.dp.toPx()
                    drawCircle(Bone, 3.5.dp.toPx(), Offset(size.width / 2f - 20.dp.toPx(), cy), alpha = controlsAlpha)
                    drawCircle(Ink, 5.5.dp.toPx(), Offset(size.width / 2f, cy), alpha = controlsAlpha)
                    drawCircle(Bone, 3.5.dp.toPx(), Offset(size.width / 2f + 20.dp.toPx(), cy), alpha = controlsAlpha)
                }
            },
    )
}

private val GlassPillShape = RoundedCornerShape(50)

// colour drifting under a pill drawn with the app's own liquid glass (its tuning, acrylic and all), recorded into
// the preview's own backdrop so the glass never samples the page it sits on; frosted when glass is off or unsupported
@Composable
internal fun LiquidGlassPreview() {
    val config = LocalLiquidGlass.current
    val glassOn = (config.enabled || config.acrylic) && isGlassSupported()
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val drift = remember { Animatable(0f) }
    val motion = AppMotion.enabled
    LaunchedEffect(held, motion) {
        if (!motion) return@LaunchedEffect
        var target = 1f
        while (true) {
            awaitShown(held)
            drift.animateTo(target, StepSpec)
            delay(HoldMs)
            target = if (target > 0f) -1f else 1f
        }
    }
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().trackOnScreen(onScreen).damageBoundary()) {
        // only the colours sit in the recorded layer; the pill is their sibling so it never captures itself
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = drift.value * 18.dp.toPx() }
                    .drawBehind {
                        drawRect(FrameFill)
                        val side = 9.dp.toPx()
                        drawRoundRect(PreviewArt, Offset(side, 8.dp.toPx()), Size(size.width - side * 2, 56.dp.toPx()), CornerRadius(8.dp.toPx()))
                        drawRoundRect(PreviewTile, Offset(side, 72.dp.toPx()), Size(42.dp.toPx(), 42.dp.toPx()), CornerRadius(6.dp.toPx()))
                        drawRoundRect(PreviewArt, Offset(size.width - side - 42.dp.toPx(), 80.dp.toPx()), Size(42.dp.toPx(), 34.dp.toPx()), CornerRadius(6.dp.toPx()))
                    },
            )
        }
        CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .size(width = 92.dp, height = 26.dp)
                    .then(if (glassOn) Modifier.liquidGlassSurface(GlassPillShape) else Modifier.background(Bone, GlassPillShape)),
            ) {
                Box(Modifier.align(Alignment.Center).size(width = 34.dp, height = 4.dp).background(Ink, GlassPillShape))
            }
        }
    }
}

// a ported page's own live preview under its title: the same card and little screen the Customisation cards use
@Composable
internal fun PortedPreviewHeader(
    modifier: Modifier = Modifier,
    second: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    preview: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(StyleCardHeight)
            .clip(CardShape)
            .background(com.mochyy.mochymusic.ui.screens.audiolab.LabColors.Card),
    ) {
        if (second == null) {
            PreviewFrame(modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp), content = preview)
        } else {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize(),
            ) {
                PreviewFrame(width = PairPreviewWidth, modifier = Modifier.padding(start = 18.dp), content = preview)
                PreviewFrame(width = PairPreviewWidth, content = second)
            }
        }
    }
}

private val PairPreviewWidth = 120.dp
