// The motion every page draws from: cards that press in and lean toward the finger, fold as they pass
// a scroll edge, and change shape instead of swapping. Everything is read in the layer phase, nothing
// loops, and with the app's animations off each piece sits at its end state.

package com.mochyy.mochymusic.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.mochyy.mochymusic.ui.utils.AppMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MotionTokens {
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val fast: Int get() = scaled(150)
    val base: Int get() = scaled(300)
    val reveal: Int get() = scaled(600)
    const val Stagger = 40
    const val MaxStagger = 6

    private fun scaled(ms: Int) = (ms / AppMotion.speed.coerceAtLeast(0.1f)).toInt()
}

fun motionOn(): Boolean = AppMotion.enabled

private const val PressScale = 0.97f
private const val PressTilt = 2f

/** presses in to 0.97 and leans up to 2° toward where the finger landed; never takes the touch from the click */
fun Modifier.pressDepth(): Modifier = composed {
    val press = remember { Animatable(0f) }
    val lean = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            if (!motionOn()) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val side = ((down.position.x / size.width.coerceAtLeast(1)) - 0.5f) * 2f
                scope.launch { press.animateTo(1f, tween(100, easing = MotionTokens.Ease)) }
                scope.launch { lean.animateTo(side.coerceIn(-1f, 1f), tween(100, easing = MotionTokens.Ease)) }
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                scope.launch { press.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
                scope.launch { lean.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
            }
        }
        .graphicsLayer {
            val p = press.value
            if (p == 0f && lean.value == 0f) return@graphicsLayer
            val s = 1f - (1f - PressScale) * p
            scaleX = s
            scaleY = s
            rotationY = lean.value * PressTilt * p
            cameraDistance = 12f * density
        }
}

/** folds the card back, up to [maxDeg], by how much of it has scrolled past the list's top or bottom edge; flat at rest */
fun Modifier.scrollFold(state: LazyListState, key: Any, maxDeg: Float = 8f): Modifier = graphicsLayer {
    if (!motionOn()) return@graphicsLayer
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val top = item.offset - info.viewportStartOffset
    val bottom = info.viewportEndOffset - (item.offset + item.size)
    val fromTop = top < 0
    val out = when {
        fromTop -> -top
        bottom < 0 -> -bottom
        else -> return@graphicsLayer
    }
    val f = (out.toFloat() / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    rotationX = (if (fromTop) maxDeg else -maxDeg) * f
    alpha = 1f - 0.15f * f
    val s = 1f - 0.1f * f
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, if (fromTop) 1f else 0f)
    cameraDistance = 12f * density
}

/**
 * The same fold for a card inside a plain scrolling column: it leans back as it slides under the top bar
 * ([clearTopPx] from the window's top) or past the bottom. Its place is noted at layout, the scroll is read
 * in the layer, so nothing recomposes while scrolling.
 */
fun Modifier.columnFold(scroll: ScrollState, clearTopPx: Float, maxDeg: Float = 8f): Modifier = composed {
    val box = remember { FloatArray(2) }
    onGloballyPositioned {
        val b = it.boundsInWindow()
        box[0] = b.top
        box[1] = b.bottom
    }.graphicsLayer {
        if (!motionOn()) return@graphicsLayer
        scroll.value
        val height = (box[1] - box[0]).coerceAtLeast(1f)
        val viewBottom = clearTopPx + scroll.viewportSize
        val fromTop = box[0] < clearTopPx
        val out = when {
            fromTop -> clearTopPx - box[0]
            box[1] > viewBottom && scroll.viewportSize > 0 -> box[1] - viewBottom
            else -> return@graphicsLayer
        }
        val f = (out / height).coerceIn(0f, 1f)
        rotationX = (if (fromTop) maxDeg else -maxDeg) * f
        alpha = 1f - 0.15f * f
        transformOrigin = TransformOrigin(0.5f, if (fromTop) 1f else 0f)
        cameraDistance = 12f * density
    }
}

/** a hairline under a heading that draws across once, the first time the heading shows */
fun Modifier.ruleIn(color: Color = Color.White.copy(alpha = 0.12f)): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(!motionOn()) }
    val f = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        f.animateTo(1f, tween(MotionTokens.reveal, easing = MotionTokens.Ease))
        played = true
    }
    drawBehind {
        val w = 1.dp.toPx()
        drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width * f.value, size.height - w / 2), strokeWidth = w)
    }
}

/** the card turns under the finger while it's dragged across it, then springs flat */
fun Modifier.dragTilt(maxDeg: Float = 8f): Modifier = composed {
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(maxDeg) {
            if (!motionOn()) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val at = event.changes.firstOrNull()?.position ?: break
                    val nx = ((at.x / size.width.coerceAtLeast(1)) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                    val ny = ((at.y / size.height.coerceAtLeast(1)) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                    scope.launch { x.snapTo(-ny * maxDeg) }
                    scope.launch { y.snapTo(nx * maxDeg) }
                } while (event.changes.any { it.pressed })
                scope.launch { x.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 400f)) }
                scope.launch { y.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 400f)) }
            }
        }
        .graphicsLayer {
            if (x.value == 0f && y.value == 0f) return@graphicsLayer
            rotationX = x.value
            rotationY = y.value
            cameraDistance = 12f * density
        }
}

object MotionPolygons {
    val Circle: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 12, radius = 1f) }
    val Squircle: RoundedPolygon by lazy { RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.55f, 0.6f)) }
    val Rounded: RoundedPolygon by lazy { RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.28f, 0.4f)) }
    val Pill: RoundedPolygon by lazy { RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(1f)) }
    val Cookie: RoundedPolygon by lazy {
        RoundedPolygon.star(numVerticesPerRadius = 9, radius = 1f, innerRadius = 0.8f, rounding = CornerRounding(0.35f), innerRounding = CornerRounding(0.35f))
    }
}

@Composable
fun rememberMorph(from: RoundedPolygon, to: RoundedPolygon): Morph = remember(from, to) { Morph(from, to) }

/** 0 at rest, 1 when [target]; damped so the shape never wobbles past */
@Composable
fun morphProgress(target: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (target) 1f else 0f,
    animationSpec = if (motionOn()) spring(dampingRatio = 0.85f, stiffness = 380f) else tween(0),
    label = "morph",
)

/** arrives once, a beat after the item before it; the first six step in, the rest come together */
fun Modifier.stageIn(index: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(!motionOn()) }
    val f = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index.coerceIn(0, MotionTokens.MaxStagger).toLong() * MotionTokens.Stagger)
        f.animateTo(1f, tween(MotionTokens.base, easing = MotionTokens.Ease))
        played = true
    }
    graphicsLayer {
        val v = f.value
        if (v >= 1f) return@graphicsLayer
        alpha = v
        val s = 0.96f + 0.04f * v
        scaleX = s
        scaleY = s
    }
}

/** flips in once around its vertical axis, 90° to flat, for tiles that open a new place */
fun Modifier.flipIn(index: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(!motionOn()) }
    val f = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index.coerceIn(0, MotionTokens.MaxStagger).toLong() * MotionTokens.Stagger)
        f.animateTo(1f, tween(MotionTokens.reveal, easing = MotionTokens.Ease))
        played = true
    }
    graphicsLayer {
        val v = f.value
        if (v >= 1f) return@graphicsLayer
        rotationY = 90f * (1f - v)
        alpha = (v * 2f).coerceAtMost(1f)
        cameraDistance = 12f * density
    }
}

/** fans out by [deg] once on first sight, then settles flat */
fun Modifier.fanOnce(deg: Float): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(!motionOn()) }
    val f = remember { Animatable(if (played) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        f.animateTo(0f, tween(MotionTokens.reveal, easing = MotionTokens.Ease))
        played = true
    }
    graphicsLayer {
        if (f.value == 0f) return@graphicsLayer
        rotationZ = deg * f.value
        val s = 1f - 0.06f * f.value
        scaleX = s
        scaleY = s
    }
}

/** clips to a morph between two polygons, redrawn (never re-laid-out or recomposed) as [progress] moves */
fun Modifier.morphClip(morph: Morph, progress: () -> Float): Modifier = drawWithCache {
    val p = android.graphics.Path()
    val m = android.graphics.Matrix().apply {
        setScale(size.width / 2f, size.height / 2f)
        postTranslate(size.width / 2f, size.height / 2f)
    }
    val path = p.asComposePath()
    onDrawWithContent {
        p.rewind()
        morph.toPath(progress().coerceIn(0f, 1f), p)
        p.transform(m)
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

/** true while a finger rests on it; doesn't take the touch from anything under it */
fun Modifier.onHeld(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onChange(true)
        do {
            val event = awaitPointerEvent()
        } while (event.changes.any { it.pressed })
        onChange(false)
    }
}
