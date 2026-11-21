/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid glass / backdrop blur effect, ported from SimpMusic
 * (https://github.com/maxrave-dev/SimpMusic) and simplified for the
 * Android-only ArchiveTune build. The original KMP expect/actual
 * pattern is collapsed into a single file because ArchiveTune does
 * not have a JVM/iOS target.
 */

package com.mochyy.mochymusic.ported.bridge

import android.os.SystemClock
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import androidx.compose.ui.graphics.graphicsLayer

typealias PlatformBackdrop = ThrottledLayerBackdrop

@Composable
fun rememberLayerBackdropSettled(@Suppress("UNUSED_PARAMETER") delayMillis: Long = 0L): Boolean = true

@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop =

    rememberThrottledBackdrop(color)

@Composable
fun rememberThrottledBackdrop(
    color: Color,
    minIntervalMillis: Long = ThrottledLayerBackdropDefaultIntervalMillis,
): ThrottledLayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = remember(graphicsLayer, minIntervalMillis, color) {
        ThrottledLayerBackdrop(
            graphicsLayer = graphicsLayer,
            minIntervalMillis = minIntervalMillis,
            contentPrefix = { drawRect(color) },
        )
    }
    DisposableEffect(backdrop) {
        onDispose { backdrop.layerCoordinates = null }
    }
    return backdrop
}

fun Modifier.layerBackdrop(backdrop: Backdrop): Modifier =
    when (backdrop) {
        is ThrottledLayerBackdrop -> throttledLayerBackdrop(backdrop)

        is LayerBackdrop -> this.kyantLayerBackdrop(backdrop)
        else -> this
    }

fun Modifier.glassSource(backdrop: Backdrop): Modifier =
    when (backdrop) {
        is ThrottledLayerBackdrop -> throttledLayerBackdrop(backdrop)
        is LayerBackdrop -> layerBackdrop(backdrop)
        else -> this
    }

internal const val ThrottledLayerBackdropDefaultIntervalMillis = 32L

private val liveRecorderBackdrops =
    java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<ThrottledLayerBackdrop, Boolean>(),
    )

@Stable
class ThrottledLayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val minIntervalMillis: Long,
    internal val contentPrefix: DrawScope.() -> Unit = {},
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    internal var consumerInvalidationTick by mutableStateOf(0)

    internal var recordingInProgress by mutableStateOf(false)

    internal var recordTick by mutableStateOf(0)

    fun requestRecord() {
        recordTick++
    }

    internal fun notifyRecorderAttached() {
        consumerInvalidationTick++
    }

    fun notifyContentRestore() {
        consumerInvalidationTick++
        notifyAllContentRestored()
    }

    private companion object {
        fun notifyAllContentRestored() {
            val snapshots: List<ThrottledLayerBackdrop>
            synchronized(liveRecorderBackdrops) {
                snapshots = liveRecorderBackdrops.keys.toList()
            }
            snapshots.forEach { it.consumerInvalidationTick++ }
        }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = consumerInvalidationTick
        if (recordingInProgress) return
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return

        if (!layerCoordinates.isAttached || !coordinates.isAttached) return
        val offset =
            try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                runCatching {
                    coordinates.positionInWindow() - layerCoordinates.positionInWindow()
                }.getOrNull()
            } ?: return
        withTransform({
            translate(-offset.x, -offset.y)
        }) {
            runCatching { drawLayer(graphicsLayer) }
        }
    }
}

@Composable
fun rememberThrottledLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    minIntervalMillis: Long = ThrottledLayerBackdropDefaultIntervalMillis,
): ThrottledLayerBackdrop = remember(graphicsLayer) {
    ThrottledLayerBackdrop(graphicsLayer, minIntervalMillis)
}

fun Modifier.throttledLayerBackdrop(backdrop: ThrottledLayerBackdrop): Modifier =
    this then ThrottledLayerBackdropElement(backdrop)

private class ThrottledLayerBackdropElement(
    val backdrop: ThrottledLayerBackdrop,
) : ModifierNodeElement<ThrottledLayerBackdropNode>() {
    override fun create() = ThrottledLayerBackdropNode(backdrop)

    override fun update(node: ThrottledLayerBackdropNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "throttledLayerBackdrop"
        properties["backdrop"] = backdrop
        properties["minIntervalMillis"] = backdrop.minIntervalMillis
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThrottledLayerBackdropElement) return false
        return backdrop === other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class ThrottledLayerBackdropNode(
    var backdrop: ThrottledLayerBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    private var lastRecordUptimeMillis = 0L

    override fun onAttach() {
        super.onAttach()
        liveRecorderBackdrops[backdrop] = true
        lastRecordUptimeMillis = 0L
        backdrop.notifyRecorderAttached()
    }

    override fun ContentDrawScope.draw() {

        @Suppress("UNUSED_VARIABLE") val tick = backdrop.recordTick
        val now = SystemClock.uptimeMillis()
        if (now - lastRecordUptimeMillis >= backdrop.minIntervalMillis) {
            lastRecordUptimeMillis = now
            val density = requireDensity()

            val recorded =
                runCatching {
                    backdrop.recordingInProgress = true
                    try {
                        backdrop.graphicsLayer.record(size.toIntSize()) {
                            val previousDensity = drawContext.density
                            drawContext.density = density
                            try {
                                backdrop.contentPrefix(this@draw)
                                this@draw.drawContent()
                            } finally {
                                drawContext.density = previousDensity
                            }
                        }
                    } finally {
                        backdrop.recordingInProgress = false
                    }
                }.isSuccess
            if (recorded && backdrop.graphicsLayer.size == size.toIntSize()) {
                runCatching { drawLayer(backdrop.graphicsLayer) }
                return
            }
        }
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        liveRecorderBackdrops.remove(backdrop)

        if (backdrop.layerCoordinates?.isAttached == false) {
            backdrop.layerCoordinates = null
            backdrop.consumerInvalidationTick++
        }
    }
}
