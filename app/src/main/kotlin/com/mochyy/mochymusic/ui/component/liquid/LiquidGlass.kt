/*
 * The glass rendering itself is the vendored AndroidLiquidGlass backdrop
 * module (github.com/guozhiqiang123/AndroidLiquidGlass, Kyant's fork —
 * Apache-2.0; see backdrop/build.gradle.kts) whose com.kyant.backdrop
 * package owns backdrop recording and material rendering. This file is
 * the integration glue, adapted from EchoMusicApp/Echo-Music's
 * GlassEffectConfig / Modifier.liquidGlass (GPL-3.0), cut down from Echo's
 * full per-component/vibrancy-slider config to the single on/off switch
 * BitChord exposes in Settings. It is used by the floating nav bar and,
 * while enabled, the app-wide floating top controls.
 */

package com.mochyy.mochymusic.ui.component.liquid

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp

val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

private const val VIBRANCY = 1f
private val BLUR_RADIUS_DP = LiquidGlassDefaults.TabsBlur.value
private val LENS_HEIGHT = LiquidGlassDefaults.TabsRefractionHeight.value / LENS_MAX_DP
private val LENS_AMOUNT = LiquidGlassDefaults.TabsRefractionAmount.value / LENS_MAX_DP
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = LiquidGlassDefaults.TabsContainerAlpha

data class LiquidGlassTuning(
    val intensity: LiquidGlassIntensity = LiquidGlassIntensity.STANDARD,
    val refractionHeightFraction: Float = LiquidGlassValues.RefractionHeight.default,
    val refractionAmountFraction: Float = LiquidGlassValues.RefractionAmount.default,
    val blurFraction: Float = LiquidGlassValues.BlurRadius.default,
    val tintFraction: Float = LiquidGlassValues.TintOpacity.default,
    val shadowFraction: Float = LiquidGlassValues.ShadowDepth.default,
    val depth3D: Boolean = true,
    val chromaticAberration: Boolean = true,
    val backdropVibrancy: Boolean = true,
    val adaptiveLuminance: Boolean = true,
    val cornerRadius: Float = LiquidGlassUpstream.CORNER_RADIUS_DEFAULT,
    val chromaticAmount: Float = LiquidGlassUpstream.CHROMATIC_AMOUNT_DEFAULT,
    val clarity: Float = LiquidGlassUpstream.CLARITY_DEFAULT,
    val saturation: Float = LiquidGlassUpstream.SATURATION_DEFAULT,
    val refractionWidth: Float = LiquidGlassUpstream.REFRACTION_WIDTH_DEFAULT,
    val rainbow: Float = LiquidGlassUpstream.RAINBOW_DEFAULT,
    val rim: Float = LiquidGlassUpstream.RIM_DEFAULT,
    val quality: Float = LiquidGlassUpstream.QUALITY_DEFAULT,
) {
    private val presetRefraction: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.55f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.45f
        }

    private val presetBlur: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.70f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.30f
        }

    val refractionHeightFactor: Float =
        (refractionHeightFraction / LiquidGlassValues.RefractionHeight.default) * presetRefraction

    val refractionAmountFactor: Float =
        (refractionAmountFraction / LiquidGlassValues.RefractionAmount.default) * presetRefraction

    val blurFactor: Float = (blurFraction / LiquidGlassValues.BlurRadius.default) * presetBlur

    val tintFactor: Float = tintFraction / LiquidGlassValues.TintOpacity.default

    val shadowFactor: Float = shadowFraction / LiquidGlassValues.ShadowDepth.default

    val vibrancyFactor: Float = if (backdropVibrancy) 1f else 0f

    val adaptiveLuminanceFactor: Float = if (adaptiveLuminance) 1f else 0.44f

    val cornerFactor: Float = LiquidGlassUpstream.cornerFactor(cornerRadius)
    val clarityBlurFactor: Float = LiquidGlassUpstream.clarityBlurFactor(clarity)
    val clarityTintFactor: Float = LiquidGlassUpstream.clarityTintFactor(clarity)
    val qualityFactor: Float = LiquidGlassUpstream.clarityResolutionFactor(clarity) * quality
    val refractionWidthFactor: Float = LiquidGlassUpstream.refractionWidthFactor(refractionWidth)
    val dispersionStrength: Float = LiquidGlassUpstream.dispersionStrength(chromaticAmount, rainbow)
}

internal const val ACRYLIC_BLUR_DP = 32f
internal const val ACRYLIC_NOISE = 0.12f
internal const val ACRYLIC_TINT_ALPHA = 0.72f

private const val GLASS_RESOLUTION_SCALE = 0.33f

private const val FROST_BLUR_RADIUS_DP = 16f
private const val FROST_RESOLUTION_SCALE = 0.25f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = DARK_INDICATOR_OPACITY)
    }

private const val DARK_INDICATOR_OPACITY = 0.14f

private fun Modifier.glassRim(shape: Shape): Modifier = drawBackdrop(
    backdrop = emptyBackdrop(),
    shape = { shape },
    effects = {},
    highlight = { Highlight.Default },
    shadow = null,
)

@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    if (!LocalLiquidGlass.current.enabled || !isGlassSupported()) return this
    return remember(shape) { glassRim(shape) }
}

@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,

    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) return this
    val config = LocalLiquidGlass.current
    if (config.reduceBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val acrylic = config.acrylic
    val glassPerformance = config.performance
    val tuning = config.tuning
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val resolutionScale = ((if (glassPerformance) FROST_RESOLUTION_SCALE else GLASS_RESOLUTION_SCALE) * tuning.qualityFactor).coerceIn(0.1f, 1f)
    val blurRadiusDp = if (acrylic) {
        ACRYLIC_BLUR_DP
    } else {
        (if (glassPerformance) FROST_BLUR_RADIUS_DP else BLUR_RADIUS_DP) * tuning.blurFactor * tuning.clarityBlurFactor
    }
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP * tuning.refractionHeightFactor * tuning.refractionWidthFactor).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP * tuning.refractionAmountFactor).dp.toPx() } * resolutionScale
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint
        ?: themeTint.copy(alpha = (SURFACE_OPACITY * tuning.tintFactor * tuning.clarityTintFactor * tuning.adaptiveLuminanceFactor).coerceIn(0f, 1f))
    val acrylicTint = MaterialTheme.colorScheme.surface.copy(alpha = ACRYLIC_TINT_ALPHA)
    val glassShape = remember(shape, tuning.cornerFactor) { scaledCorners(shape, tuning.cornerFactor) }
    val highlightSpec = remember(tuning.rim) { Highlight.Default.copy(alpha = tuning.rim.coerceIn(0f, 1f)) }

    // acrylic replaces the lens: frosted blur, the surface tint and grain, nothing refracted or lit
    if (acrylic) {
        return drawBackdrop(
            backdrop = backdrop,
            shape = { glassShape },
            effects = { blur(blurPx) },
            highlight = null,
            shadow = null,
            onDrawSurface = {
                drawRect(color = acrylicTint, size = size)
                drawRect(brush = acrylicGrainBrush(), size = size, alpha = ACRYLIC_NOISE)
            },
            exportedBackdrop = exportedBackdrop,
            samplingScale = { _, _ -> resolutionScale },
        )
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { glassShape },
        effects = {
            if (tuning.vibrancyFactor > 0f || tuning.saturation != 1f) {
                colorControls(saturation = (1f + 0.5f * VIBRANCY * tuning.vibrancyFactor) * tuning.saturation)
            }
            blur(blurPx)
            if (!glassPerformance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = tuning.depth3D,
                    chromaticAberration = tuning.chromaticAberration,
                    chromaticStrength = tuning.dispersionStrength,
                )
            }
        },
        highlight = { highlightSpec },
        shadow = { Shadow.Default.copy(radius = Shadow.Default.radius * tuning.shadowFactor) },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        samplingScale = { _, _ -> resolutionScale },
    )
}

fun Modifier.glassGlowOverlay(
    strength: Float,
    shape: Shape,
): Modifier = drawWithContent {
    if (strength <= 0.01f) {
        drawContent()
        return@drawWithContent
    }
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokePx = GlowEdgeStrokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val insetSize = Size(size.width - strokePx, size.height - strokePx)
    val insetOutline = shape.createOutline(insetSize, layoutDirection, this)

    translate(halfStroke, halfStroke) {
        drawOutline(
            outline = insetOutline,
            brush = Brush.verticalGradient(
                0f to GlowRimStart,
                1f to GlowRimEnd,
            ),
            alpha = strength,
            style = Stroke(strokePx),
        )
    }

    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0f to GlowSheenTop,
            0.45f to GlowSheenMid,
            1f to GlowSheenEnd,
        ),
        alpha = strength,
        style = Fill,
    )

    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0.72f to Color.Transparent,
            1f to GlowBottomEnd,
        ),
        alpha = strength * 0.7f,
        style = Fill,
    )
}

private val GlowEdgeStrokeWidth = 0.75.dp

private val GlowRimStart = Color.White.copy(alpha = 0.27f)
private val GlowRimEnd = Color.White.copy(alpha = 0.02f)
private val GlowSheenTop = Color.White.copy(alpha = 0.10f)
private val GlowSheenMid = Color.Transparent
private val GlowSheenEnd = Color.White.copy(alpha = 0.05f)
private val GlowBottomEnd = Color.White.copy(alpha = 0.14f)

@Composable
fun Modifier.liquidGlassSurface(shape: CornerBasedShape): Modifier = liquidGlass(shape).liquidGlassRim(shape)
