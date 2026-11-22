// SettingsPagePreviews.kt
// The little screens on the settings list's doors: one per category and per page that had nothing to show,
// drawn in the Customisation previews' ink on the same frame. Each reads the settings its page writes, runs on
// one clock that stops off screen, behind the player and while a page slides, and holds a settled frame when
// motion is off.

package com.mochyy.mochymusic.ui.screens.settings.previews

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.constants.AppLanguageKey
import com.mochyy.mochymusic.constants.AudioQualityCellularKey
import com.mochyy.mochymusic.constants.AudioQualityWifiKey
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.DefaultTransitionMode
import com.mochyy.mochymusic.constants.EnableDiscordRPCKey
import com.mochyy.mochymusic.constants.ListenBrainzEnabledKey
import com.mochyy.mochymusic.constants.ProxyEnabledKey
import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import com.mochyy.mochymusic.constants.ThumbnailCornerRadiusKey
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.screens.audiolab.rememberLabTime
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.screens.gesture.ArtFill
import com.mochyy.mochymusic.ui.screens.gesture.Bone
import com.mochyy.mochymusic.ui.screens.gesture.BoneDim
import com.mochyy.mochymusic.ui.screens.gesture.Ink
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val SettledT = 1.25f

private val OnLight = Color(0xFF8BE07A)
private val OffLight = Color(0xFF5C5C63)
private val Settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val TwoPi = 6.2831855f

@Composable
internal fun motionReduced(): Boolean {
    val context = LocalContext.current
    val scale = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    }
    return !AppMotion.enabled || scale == 0f
}

@Composable
private fun state(on: Boolean, label: String): Float {
    val reduced = motionReduced()
    val v by animateFloatAsState(if (on) 1f else 0f, if (reduced) tween(0) else tween(300, easing = Settle), label = label)
    return v
}

// the one clock a preview runs on, read only while drawing
@Composable
private fun BoxScope.PreviewCanvas(onDraw: DrawScope.(Float) -> Unit) {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val reduced = motionReduced()
    val time = rememberLabTime(held) { !reduced }
    Canvas(Modifier.fillMaxSize().trackOnScreen(onScreen)) { onDraw(if (reduced) SettledT else time.value) }
}

private fun DrawScope.bar(x: Float, y: Float, w: Float, h: Float, c: Color) =
    drawRoundRect(c, Offset(x, y), Size(w.coerceAtLeast(0f), h), CornerRadius(h / 2f))

private fun DrawScope.light(cx: Float, cy: Float, r: Float, on: Float) {
    if (on > 0.5f) drawCircle(OnLight, r, Offset(cx, cy)) else drawCircle(OffLight, r - 0.75f, Offset(cx, cy), style = Stroke(1.5f))
}

private fun phase(t: Float, period: Float): Float = (t % period) / period

private fun wave(t: Float, period: Float, shift: Float = 0f): Float = sin((t / period + shift) * TwoPi)

private fun ease(x: Float): Float = Settle.transform(x.coerceIn(0f, 1f))

// ---- the six categories ------------------------------------------------------------------

@Composable
internal fun BoxScope.AudioLabCategoryPreview() = PreviewCanvas { t ->
    val rest = listOf(0.36f, 0.62f, 0.46f)
    val gap = size.width / 4f
    val top = size.height * 0.18f
    val bottom = size.height * 0.86f
    rest.forEachIndexed { i, r ->
        val x = gap * (i + 1)
        drawLine(Bone, Offset(x, top), Offset(x, bottom), 3f, StrokeCap.Round)
        val y = top + (bottom - top) * (r + 0.05f * wave(t, 3.6f, i * 0.33f))
        drawLine(Ink, Offset(x, y), Offset(x, bottom), 3f, StrokeCap.Round)
        drawCircle(Ink, size.width * 0.06f, Offset(x, y))
    }
}

@Composable
internal fun BoxScope.PlaybackCategoryPreview() {
    val (mode) = rememberEnumPreference(CrossmixModeKey, DefaultTransitionMode)
    val blend = state(mode != CrossmixMode.OFF, "blend")
    PreviewCanvas { t -> nowPlaying(t, blend) }
}

private fun DrawScope.nowPlaying(t: Float, blend: Float) {
    val p = phase(t, 6f)
    val side = size.width * 0.56f
    val left = (size.width - side) / 2f
    val top = size.height * 0.12f
    val slide = if (blend > 0f) ease((p - 0.82f) / 0.18f) * blend else 0f
    drawRoundRect(ArtFill, Offset(left, top), Size(side, side), CornerRadius(side * 0.12f), alpha = 1f - slide)
    if (slide > 0f) drawRoundRect(ArtFill, Offset(left + side * (1f - slide) * 0.4f, top), Size(side, side), CornerRadius(side * 0.12f), alpha = slide)
    val y = top + side + size.height * 0.07f
    bar(left, y, side * 0.8f, size.height * 0.035f, Ink)
    bar(left, y + size.height * 0.06f, side * 0.5f, size.height * 0.03f, Bone)
    val py = y + size.height * 0.13f
    bar(left, py, side, 3f, BoneDim)
    bar(left, py, side * p, 3f, Ink)
    val gy = py + size.height * 0.08f
    val cx = size.width / 2f
    val play = ease((p - 0.9f) / 0.1f)
    val g = size.width * 0.05f
    if (play < 0.5f) {
        bar(cx - g * 0.8f, gy - g, g * 0.5f, g * 2f, Ink.copy(alpha = 1f - play * 2f))
        bar(cx + g * 0.3f, gy - g, g * 0.5f, g * 2f, Ink.copy(alpha = 1f - play * 2f))
    } else {
        val tri = Path().apply { moveTo(cx - g * 0.6f, gy - g); lineTo(cx + g, gy); lineTo(cx - g * 0.6f, gy + g); close() }
        drawPath(tri, Ink.copy(alpha = (play - 0.5f) * 2f))
    }
}

@Composable
internal fun BoxScope.AppBehaviourCategoryPreview() {
    val (language) = rememberPreference(AppLanguageKey, SYSTEM_DEFAULT)
    val code = (if (language == SYSTEM_DEFAULT || language.isBlank()) Locale.getDefault().language else language.substringBefore('-'))
        .take(2).uppercase(Locale.ROOT)
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Ink) }
    PreviewCanvas { t ->
        val cx = size.width / 2f
        val cy = size.height * 0.56f
        val lit = phase(t, 2.4f) * 4f
        for (i in 0 until 3) {
            val r = size.width * (0.14f + 0.12f * i)
            val on = if (lit >= i && lit < 3.5f) 1f else 0.25f
            drawArc(Ink.copy(alpha = 0.92f * on), 225f, 90f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round))
        }
        drawCircle(Ink, 4f, Offset(cx, cy))
        val layout = measurer.measure(code, style)
        val w = layout.size.width + size.width * 0.12f
        val h = layout.size.height + size.height * 0.03f
        val x = (size.width - w) / 2f
        val y = size.height * 0.72f
        drawRoundRect(BoneDim, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
        drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2f, y + (h - layout.size.height) / 2f))
    }
}

@Composable
internal fun BoxScope.CustomisationCategoryPreview() {
    val (radius) = rememberPreference(ThumbnailCornerRadiusKey, 25f)
    PreviewCanvas { t ->
        val side = size.width * 0.5f
        val left = (size.width - side) / 2f
        val top = size.height * 0.1f
        val r = side * 0.5f * (radius / 100f).coerceIn(0f, 1f) * (0.5f + 0.5f * (1f + wave(t, 4.8f)) / 2f)
        drawRoundRect(ArtFill, Offset(left, top), Size(side, side), CornerRadius(r))
        val cell = (side - 8f) / 3f
        for (row in 0 until 2) for (col in 0 until 3) {
            val y = top + side + size.height * 0.08f + row * (cell * 0.6f + 6f)
            drawRoundRect(if (row == 0 && col == 0) Bone else BoneDim, Offset(left + col * (cell + 4f), y), Size(cell, cell * 0.6f), CornerRadius(r * 0.3f))
        }
    }
}

@Composable
internal fun BoxScope.StorageCategoryPreview() = PreviewCanvas { t ->
    val w = size.width * 0.72f
    val left = (size.width - w) / 2f
    val y = size.height * 0.36f
    val h = size.height * 0.08f
    val grow = ease(t / 1.2f)
    val shades = listOf(Ink, Bone, BoneDim)
    var x = left
    shades.forEachIndexed { i, c ->
        val seg = w / 3f * grow
        drawRoundRect(c, Offset(x, y), Size((seg - 3f).coerceAtLeast(0f), h), CornerRadius(h / 2f))
        x += seg
        bar(left + 18f, y + h * 2.2f + i * h * 1.4f, w * (0.6f - i * 0.12f), h * 0.6f, BoneDim)
        drawCircle(c, h * 0.35f, Offset(left + 6f, y + h * 2.5f + i * h * 1.4f))
    }
}

@Composable
internal fun BoxScope.IntegrationsCategoryPreview() {
    val (discord) = rememberPreference(EnableDiscordRPCKey, false)
    val (lastfm) = rememberPreference(ScrobbleSite.LASTFM.enabledKey, false)
    val (listenBrainz) = rememberPreference(ListenBrainzEnabledKey, false)
    val on = listOf(state(discord, "d"), state(lastfm, "l"), state(listenBrainz, "b"))
    PreviewCanvas { t ->
        val c = Offset(size.width / 2f, size.height * 0.5f)
        val reach = size.width * 0.32f
        on.forEachIndexed { i, lit ->
            val a = (-90f + i * 120f) * PI.toFloat() / 180f
            val n = Offset(c.x + cos(a) * reach, c.y + sin(a) * reach)
            drawLine(Bone, c, n, 2f)
            val f = phase(t + i * 0.8f, 2.4f)
            drawCircle(Ink.copy(alpha = 0.3f + 0.6f * lit), 3f, Offset(c.x + (n.x - c.x) * f, c.y + (n.y - c.y) * f))
            drawCircle(BoneDim, size.width * 0.09f, n)
            light(n.x, n.y, size.width * 0.045f, lit)
        }
        drawCircle(Ink, size.width * 0.08f, c)
    }
}

// ---- the page doors ------------------------------------------------------------------------

@Composable
internal fun BoxScope.ModulesPreview() = PreviewCanvas { t ->
    val side = size.width * 0.3f
    val gap = size.width * 0.06f
    val left = (size.width - side * 2 - gap) / 2f
    val top = size.height * 0.2f
    val p = phase(t, 4f)
    val lift = if (p < 0.5f) ease(p / 0.25f) else 1f - ease((p - 0.5f) / 0.25f)
    for (i in 0 until 4) {
        val x = left + (i % 2) * (side + gap)
        val y = top + (i / 2) * (side + gap)
        val up = if (i == 3) lift else 0f
        drawRoundRect(if (i == 3) Ink.copy(alpha = 0.35f + 0.5f * up) else Bone, Offset(x, y - up * side * 0.25f), Size(side, side), CornerRadius(side * 0.22f))
    }
}

@Composable
internal fun BoxScope.NowPlayingPreview() {
    val (canvas) = rememberPreference(CanvasThumbnailAnimationKey, true)
    val shimmer = state(canvas, "canvas")
    PreviewCanvas { t ->
        val side = size.width * 0.34f
        val left = size.width * 0.12f
        val top = size.height * 0.1f
        drawRoundRect(ArtFill, Offset(left, top), Size(side, side), CornerRadius(side * 0.15f))
        if (shimmer > 0f) {
            val sx = left + side * (phase(t, 3f) * 1.6f - 0.3f)
            drawRect(Ink.copy(alpha = 0.18f * shimmer), Offset(sx.coerceIn(left, left + side), top), Size(side * 0.18f, side))
        }
        val lineH = size.height * 0.11f
        val scroll = ease(phase(t, 3.2f) / 0.35f) * lineH
        val y0 = top + side + size.height * 0.12f - scroll
        for (i in 0 until 4) {
            val y = y0 + i * lineH
            if (y < top + side + 4f || y > size.height) continue
            val active = i == 1
            bar(left, y, size.width * (if (i % 2 == 0) 0.7f else 0.55f), size.height * 0.04f, if (active) Ink else Bone)
        }
    }
}

@Composable
internal fun BoxScope.InternetPreview() {
    val (wifi) = rememberPreference(AudioQualityWifiKey, "AUTO")
    val (cell) = rememberPreference(AudioQualityCellularKey, "AUTO")
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 9.sp, color = Ink) }
    val labels = remember(wifi, cell) { listOf(wifi, cell).map { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } } }
    PreviewCanvas { t ->
        val mobile = phase(t, 4.8f) >= 0.5f
        val cx = size.width / 2f
        val cy = size.height * 0.5f
        if (!mobile) {
            for (i in 0 until 3) {
                val r = size.width * (0.12f + 0.1f * i)
                drawArc(Ink, 225f, 90f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round))
            }
        } else {
            for (i in 0 until 4) {
                val h = size.height * (0.06f + 0.05f * i)
                bar(cx - size.width * 0.15f + i * size.width * 0.09f, cy - h, size.width * 0.05f, h, Ink)
            }
        }
        val layout = measurer.measure(labels[if (mobile) 1 else 0], style)
        val w = layout.size.width + 16f
        val h = layout.size.height + 6f
        val x = (size.width - w) / 2f
        val y = size.height * 0.64f
        drawRoundRect(BoneDim, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
        drawText(layout, topLeft = Offset(x + 8f, y + 3f))
    }
}

@Composable
internal fun BoxScope.ProxyPreview() {
    val (proxy) = rememberPreference(ProxyEnabledKey, false)
    val via = state(proxy, "proxy")
    PreviewCanvas { t ->
        val y = size.height * 0.45f
        val a = Offset(size.width * 0.16f, y)
        val relay = Offset(size.width * 0.5f, y - size.height * 0.16f * via)
        val b = Offset(size.width * 0.84f, y)
        drawLine(Bone, a, relay, 2f)
        drawLine(Bone, relay, b, 2f)
        val f = ease(phase(t, 2.6f) / 0.8f)
        val dot = if (f < 0.5f) Offset(a.x + (relay.x - a.x) * f * 2f, a.y + (relay.y - a.y) * f * 2f)
        else Offset(relay.x + (b.x - relay.x) * (f - 0.5f) * 2f, relay.y + (b.y - relay.y) * (f - 0.5f) * 2f)
        drawRoundRect(Ink, Offset(a.x - size.width * 0.05f, a.y - size.width * 0.08f), Size(size.width * 0.1f, size.width * 0.16f), CornerRadius(4f))
        if (via > 0f) drawRoundRect(Ink.copy(alpha = via), Offset(relay.x - size.width * 0.06f, relay.y - size.width * 0.06f), Size(size.width * 0.12f, size.width * 0.12f), CornerRadius(3f))
        drawCircle(Ink, size.width * 0.08f, b, style = Stroke(2.5f))
        drawLine(Ink, Offset(b.x - size.width * 0.08f, b.y), Offset(b.x + size.width * 0.08f, b.y), 1.5f)
        drawCircle(Ink, 4f, dot)
        light(size.width / 2f, size.height * 0.78f, size.width * 0.04f, via)
    }
}

@Composable
internal fun BoxScope.BackupPreview() = PreviewCanvas { t ->
    val p = phase(t, 4.4f)
    val boxW = size.width * 0.5f
    val boxL = (size.width - boxW) / 2f
    val boxT = size.height * 0.5f
    val drop = ease(p / 0.45f)
    val cardW = boxW * 0.6f
    val cardY = size.height * 0.08f + (boxT - size.height * 0.08f) * drop * 0.8f
    drawRoundRect(Bone, Offset((size.width - cardW) / 2f, cardY), Size(cardW, cardW * 1.1f), CornerRadius(5f), alpha = 1f - drop * 0.6f)
    drawRoundRect(FrameCut, Offset(boxL - 2f, boxT - 2f), Size(boxW + 4f, size.height * 0.3f + 4f))
    drawRoundRect(Ink, Offset(boxL, boxT), Size(boxW, size.height * 0.3f), CornerRadius(6f), style = Stroke(3f))
    val tick = ease((p - 0.5f) / 0.25f)
    if (tick > 0f) {
        val c = Offset(size.width / 2f, boxT + size.height * 0.15f)
        val s = size.width * 0.08f
        val path = Path().apply { moveTo(c.x - s, c.y); lineTo(c.x - s * 0.3f, c.y + s * 0.7f); lineTo(c.x + s, c.y - s * 0.6f) }
        clipRectWidth(c.x - s, c.x - s + s * 2f * tick) { drawPath(path, Ink, style = Stroke(3f, cap = StrokeCap.Round)) }
    }
}

private val FrameCut = com.mochyy.mochymusic.ui.screens.gesture.FrameFill

private inline fun DrawScope.clipRectWidth(from: Float, to: Float, block: DrawScope.() -> Unit) =
    androidx.compose.ui.graphics.drawscope.clipRect(left = from, top = 0f, right = to, bottom = size.height, block = block)

@Composable
internal fun BoxScope.DiscordPreview() {
    val (rpc) = rememberPreference(EnableDiscordRPCKey, false)
    val on = state(rpc, "rpc")
    PreviewCanvas { t -> presence(t, on) }
}

private fun DrawScope.presence(t: Float, on: Float) {
    val dim = 0.4f + 0.6f * on
    val w = size.width * 0.84f
    val left = (size.width - w) / 2f
    val top = size.height * 0.26f
    drawRoundRect(BoneDim, Offset(left, top), Size(w, size.height * 0.4f), CornerRadius(10f))
    val art = size.height * 0.2f
    drawRoundRect(ArtFill, Offset(left + 8f, top + 10f), Size(art, art), CornerRadius(6f), alpha = dim)
    bar(left + art + 16f, top + 12f, w * 0.4f, size.height * 0.035f, Ink.copy(alpha = 0.92f * dim))
    bar(left + art + 16f, top + 12f + size.height * 0.06f, w * 0.3f, size.height * 0.03f, Bone)
    val py = top + art + 20f
    bar(left + 8f, py, w - 16f, 3f, Bone)
    bar(left + 8f, py, (w - 16f) * (if (on > 0.5f) phase(t, 6f) else 0.3f), 3f, Ink.copy(alpha = 0.92f * dim))
    light(left + w - 10f, top - 10f, size.width * 0.035f, on)
}

@Composable
internal fun BoxScope.ScrobblePreview(site: ScrobbleSite) {
    val (enabled) = rememberPreference(site.enabledKey, false)
    val (session) = rememberPreference(site.sessionKey, "")
    ScrobbleSketch(enabled && session.isNotBlank())
}

@Composable
internal fun BoxScope.ListenBrainzPreview() {
    val (enabled) = rememberPreference(ListenBrainzEnabledKey, false)
    ScrobbleSketch(enabled)
}

@Composable
private fun BoxScope.ScrobbleSketch(connected: Boolean) {
    val on = state(connected, "scrobble")
    PreviewCanvas { t ->
        val p = phase(t, 3.6f)
        val left = size.width * 0.16f
        val rowH = size.height * 0.1f
        val listTop = size.height * 0.38f
        val fall = ease(p / 0.4f)
        val noteY = size.height * 0.08f + (listTop - size.height * 0.08f) * fall
        drawCircle(Ink, size.width * 0.05f, Offset(size.width / 2f, noteY), alpha = 1f - fall * 0.7f)
        drawLine(Ink.copy(alpha = 1f - fall * 0.7f), Offset(size.width / 2f + size.width * 0.05f, noteY), Offset(size.width / 2f + size.width * 0.05f, noteY - size.height * 0.1f), 2.5f)
        for (i in 0 until 3) {
            val y = listTop + i * rowH
            bar(left, y, size.width * (0.68f - i * 0.1f), rowH * 0.4f, if (i == 0) Ink.copy(alpha = 0.3f + 0.62f * fall) else Bone)
        }
        val tally = size.width * 0.68f * (0.3f + 0.4f * ease((p - 0.4f) / 0.4f)) * (0.4f + 0.6f * on)
        bar(left, listTop + rowH * 3.4f, tally, 4f, Ink)
        light(size.width * 0.86f, size.height * 0.1f, size.width * 0.035f, on)
    }
}

@Composable
internal fun BoxScope.YouTubeSyncPreview() = PreviewCanvas { t ->
    val colW = size.width * 0.28f
    val top = size.height * 0.32f
    for (side in 0 until 2) {
        val x = if (side == 0) size.width * 0.1f else size.width * 0.62f
        for (i in 0 until 4) bar(x, top + i * size.height * 0.1f, colW * (if (i % 2 == 0) 1f else 0.7f), size.height * 0.04f, if (i == 0) Ink else Bone)
    }
    val c = Offset(size.width / 2f, size.height * 0.16f)
    val r = size.width * 0.12f
    rotate(phase(t, 3f) * 360f, c) {
        drawArc(Ink, 200f, 250f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2.5f, cap = StrokeCap.Round))
        val tip = Offset(c.x + r * cos(90f * PI.toFloat() / 180f), c.y + r * sin(90f * PI.toFloat() / 180f))
        drawCircle(Ink, 3.5f, tip)
    }
}

@Composable
internal fun BoxScope.ListenTogetherPreview() = PreviewCanvas { t ->
    val y = size.height * 0.44f
    val r = size.width * 0.12f
    val a = Offset(size.width * 0.2f, y)
    val b = Offset(size.width * 0.8f, y)
    drawCircle(ArtFill, r, a)
    drawCircle(ArtFill, r, b)
    val from = a.x + r + 6f
    val to = b.x - r - 6f
    val n = 9
    for (i in 0 until n) {
        val x = from + (to - from) * i / (n - 1)
        val h = size.height * (0.04f + 0.08f * (0.5f + 0.5f * wave(t, 2.4f, i / n.toFloat())))
        drawLine(Ink, Offset(x, y - h), Offset(x, y + h), 3f, StrokeCap.Round)
    }
    bar(a.x - r, y + r + 10f, r * 2, 4f, Bone)
    bar(b.x - r, y + r + 10f, r * 2, 4f, Bone)
}

@Composable
internal fun BoxScope.DevicesPreview() = PreviewCanvas { t ->
    val c = Offset(size.width / 2f, size.height * 0.62f)
    drawRoundRect(Ink, Offset(c.x - size.width * 0.07f, c.y - size.width * 0.12f), Size(size.width * 0.14f, size.width * 0.24f), CornerRadius(5f), style = Stroke(2.5f))
    for (i in 0 until 3) {
        val f = phase(t + i * 0.8f, 2.4f)
        val r = size.width * (0.18f + 0.22f * f)
        drawArc(Ink.copy(alpha = 0.92f * (1f - f)), 230f, 80f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}

@Composable
internal fun BoxScope.SourcesPreview() = PreviewCanvas { t ->
    val p = phase(t, 4.8f)
    val swap = ease((p - 0.35f) / 0.3f) - ease((p - 0.8f) / 0.2f)
    val pillH = size.height * 0.1f
    val left = size.width * 0.14f
    val top = size.height * 0.24f
    val step = pillH * 1.6f
    val order = listOf(0f, 1f, 2f)
    order.forEachIndexed { i, base ->
        val slot = when (i) { 0 -> base + swap; 1 -> base - swap; else -> base }
        val y = top + slot * step
        drawRoundRect(if (i == 0) Ink.copy(alpha = 0.35f) else BoneDim, Offset(left, y), Size(size.width * 0.72f, pillH), CornerRadius(pillH / 2f))
        bar(left + pillH * 0.4f, y + pillH * 0.35f, size.width * (0.34f - i * 0.05f), pillH * 0.3f, if (i == 0) Ink else Bone)
    }
}

@Composable
internal fun BoxScope.AudioPassPreview() = PreviewCanvas { t ->
    val p = phase(t, 4f)
    val plug = ease(p / 0.4f) - ease((p - 0.85f) / 0.15f)
    val y = size.height * 0.48f
    val dacW = size.width * 0.3f
    val dacX = size.width * 0.62f
    drawRoundRect(Ink, Offset(dacX, y - size.height * 0.12f), Size(dacW, size.height * 0.24f), CornerRadius(6f), style = Stroke(2.5f))
    val headX = size.width * 0.12f + (dacX - size.width * 0.22f) * plug
    drawLine(Bone, Offset(0f, y), Offset(headX, y), 3f)
    drawRoundRect(Ink, Offset(headX, y - size.height * 0.04f), Size(size.width * 0.1f, size.height * 0.08f), CornerRadius(2f))
    light(dacX + dacW / 2f, y + size.height * 0.24f, size.width * 0.035f, if (plug > 0.95f) 1f else 0f)
}

@Composable
internal fun BoxScope.TransitionsPreview() {
    val (mode) = rememberEnumPreference(CrossmixModeKey, DefaultTransitionMode)
    val on = state(mode != CrossmixMode.OFF, "transitions")
    PreviewCanvas { t ->
        val w = size.width * 0.76f
        val left = (size.width - w) / 2f
        val top = size.height * 0.24f
        val h = size.height * 0.44f
        val out = Path().apply { moveTo(left, top); cubicTo(left + w * 0.5f, top, left + w * 0.5f, top + h, left + w, top + h) }
        val inn = Path().apply { moveTo(left, top + h); cubicTo(left + w * 0.5f, top + h, left + w * 0.5f, top, left + w, top) }
        drawPath(out, Ink.copy(alpha = 0.4f + 0.52f * on), style = Stroke(3f, cap = StrokeCap.Round))
        drawPath(inn, Bone.copy(alpha = 0.2f + 0.6f * on), style = Stroke(3f, cap = StrokeCap.Round))
        val x = left + w * phase(t, 5f)
        drawLine(Ink, Offset(x, top - 6f), Offset(x, top + h + 6f), 1.5f)
        light(size.width / 2f, top + h + size.height * 0.12f, size.width * 0.035f, on)
    }
}
