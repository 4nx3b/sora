// The artwork drift, ported from Neiro's BITCHORD player (archivetuneplayer bridge/DriftDock.kt).
// Neiro flies a second AsyncImage and animates its corners on their own clock, which is why its
// thumbnail lags. Here the mini player's own cover (MorphingCover's art box, already loaded) flies
// to the style's artwork slot: its rect and corner are both read from the sheet's progress in the
// same layout and graphicsLayer pass, so size and shape change on the same frame as the finger.
// The art box is recorded into a GraphicsLayer and drawn by DriftCopyOverlay, which sits above the
// style, so the flying cover is never hidden under the style's own background.

package com.mochyy.mochymusic.ui.player

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

internal const val DriftRestAt = 0.999f

fun driftRect(mini: Rect, open: Rect, t: Float, travelPx: Float): Rect {
    val p = t.coerceIn(0f, 1f)
    if (travelPx <= 0f) return lerp(mini, open, p)
    val ride = ((mini.top - open.top) / travelPx).coerceIn(0f, 1f)
    val target = open.translate(Offset(0f, (1f - p) * travelPx * ride))
    return lerp(mini, target, p)
}

/** One per player style: where that style's artwork rests, and the shared cover's recorded copy. */
class ArtworkDriftSlot(val progress: () -> Float, val enabled: Boolean) {
    private var origin: LayoutCoordinates? = null
    private var slot: LayoutCoordinates? = null
    private var art: LayoutCoordinates? = null
    private var overlay: LayoutCoordinates? = null
    private var owner: Any? = null
    internal var copy: GraphicsLayer? = null

    /** the style's artwork at rest, in the shared cover's coordinates (both ride the same sheet layer) */
    private var armed = false
    var rect: Rect? = null
        get() {
            val r = field?.takeIf { enabled } ?: return null
            if (!armed && progress() >= DriftRestAt) armed = true
            return r.takeIf { armed }
        }
        private set
    var cornerPx = 0f
        private set

    fun showsCopy(): Boolean = progress() < DriftRestAt && rect != null

    private fun measure() {
        val o = origin?.takeIf { it.isAttached } ?: return
        val s = slot?.takeIf { it.isAttached } ?: return
        o.localBoundingBoxOf(s, clipBounds = false).takeIf { it.width > 0f && it.height > 0f }?.let { rect = it }
    }

    internal fun reportSlot(key: Any, coords: LayoutCoordinates, corner: Float) {
        owner = key
        slot = coords
        cornerPx = corner
        measure()
    }

    internal fun release(key: Any) {
        if (owner !== key) return
        owner = null
        slot = null
        rect = null
        armed = false
    }

    internal fun reportOrigin(coords: LayoutCoordinates) {
        origin = coords
        measure()
    }

    internal fun reportArt(coords: LayoutCoordinates) {
        art = coords
    }

    internal fun reportOverlay(coords: LayoutCoordinates) {
        overlay = coords
    }

    internal fun copyOffset(): Offset? {
        val o = overlay?.takeIf { it.isAttached } ?: return null
        val a = art?.takeIf { it.isAttached } ?: return null
        return o.localPositionOf(a, Offset.Zero)
    }
}

val LocalArtworkDriftSlot = staticCompositionLocalOf<ArtworkDriftSlot?> { null }

@Composable
fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) > 0f
    }
}

/** The style's artwork box reports where it rests, and hides while the shared cover is flying in its place. */
fun Modifier.driftArtworkSlot(corner: Dp, active: Boolean = true): Modifier = composed {
    val slot = LocalArtworkDriftSlot.current
    if (slot == null || !active) return@composed Modifier
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val key = remember { Any() }
    DisposableEffect(slot) { onDispose { slot.release(key) } }
    Modifier
        .onGloballyPositioned { slot.reportSlot(key, it, cornerPx) }
        .graphicsLayer { alpha = if (slot.showsCopy()) 0f else 1f }
}

internal fun Modifier.driftOrigin(slot: ArtworkDriftSlot?): Modifier =
    if (slot == null) this else onGloballyPositioned { slot.reportOrigin(it) }

/** On the shared cover's art box: while flying, its drawing goes into a layer the overlay draws above the style. */
internal fun Modifier.driftCopySource(slot: ArtworkDriftSlot?): Modifier =
    if (slot == null) this else composed {
        val layer = rememberGraphicsLayer()
        DisposableEffect(slot, layer) {
            slot.copy = layer
            onDispose { if (slot.copy === layer) slot.copy = null }
        }
        Modifier
            .onGloballyPositioned { slot.reportArt(it) }
            .drawWithContent {
                if (slot.showsCopy()) {
                    layer.record { this@drawWithContent.drawContent() }
                } else {
                    drawContent()
                }
            }
    }

/** Drawn above the style on the sheet: the shared cover while it flies between the mini player and the style's slot. */
@Composable
fun DriftCopyOverlay(slot: ArtworkDriftSlot) {
    Spacer(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { slot.reportOverlay(it) }
            .drawBehind {
                if (!slot.showsCopy()) return@drawBehind
                val layer = slot.copy ?: return@drawBehind
                val at = slot.copyOffset() ?: return@drawBehind
                translate(at.x, at.y) { drawLayer(layer) }
            },
    )
}
