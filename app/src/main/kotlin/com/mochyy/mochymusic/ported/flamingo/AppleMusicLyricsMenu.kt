/*
 * The Apple Music lyrics screen's overflow menu, ported from Neiro's
 * ui/menu/LyricsMenu.kt AnchoredLyricsOverflowMenu (ArchiveTune, © Rukamori,
 * GPL-3.0): the same anchored 220dp popup that opens beside the ⋯ icon — the
 * same spring scale-in from 0.3 pivoting on the icon, the same 180ms fade, the
 * same open-above-the-anchor fallback — carrying the lyrics rows Neiro's menu
 * carries that sora has a tool for: edit, refetch (the provider picker), the
 * sync offset and the translation.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mochyy.mochymusic.ported.flamingo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ported.bridge.LocalLiquidGlassTuning
import com.mochyy.mochymusic.ui.player.menu.LyricsEditorSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsOffsetSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsProviderSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsTranslationSheet
import kotlinx.coroutines.launch

@Composable
fun AnchoredLyricsOverflowMenu(
    iconBoundsInRoot: Rect,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.45f),
) {
    var dismissed by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = !dismissed) {
        dismissed = true
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scaleAnim = remember { Animatable(0.3f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect

        val scaleJob = scope.launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
        val alphaJob = scope.launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(180),
            )
        }
        scaleJob.join()
        alphaJob.join()
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(
                targetValue = 0.3f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        }
        val alphaJob = scope.launch {
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(180),
            )
        }
        scaleJob.join()
        alphaJob.join()

        onDismiss()
    }

    val scale = scaleAnim.value
    val alpha = alphaAnim.value

    var anchorSpaceHeightPx by remember { mutableIntStateOf(0) }
    var popupHeightPx by remember { mutableIntStateOf(0) }
    val verticalOffsetPx = with(density) { 4.dp.toPx() }.toInt()

    fun opensAboveAnchor(): Boolean {
        val neededHeightPx =
            if (popupHeightPx > 0) popupHeightPx else with(density) { 360.dp.toPx() }.toInt()
        return anchorSpaceHeightPx > 0 &&
            iconBoundsInRoot.bottom + verticalOffsetPx + neededHeightPx > anchorSpaceHeightPx
    }

    var showEditor by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var showOffset by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    val anyToolOpen = showEditor || showProvider || showOffset || showTranslation

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { anchorSpaceHeightPx = it.height }
                .background(scrimColor.copy(alpha = scrimColor.alpha * alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!dismissed) dismissed = true
                },
    ) {
        Box(
            modifier =
                Modifier
                    .offset {
                        val popupWidthPx = with(density) { 220.dp.toPx() }.toInt()
                        val horizontalMarginPx = with(density) { 16.dp.toPx() }.toInt()
                        val iconRight = iconBoundsInRoot.right.toInt()
                        val iconBottom = iconBoundsInRoot.bottom.toInt()
                        val x =
                            (iconRight - popupWidthPx)
                                .coerceAtLeast(horizontalMarginPx)
                        val y =
                            if (opensAboveAnchor()) {
                                (iconBoundsInRoot.top - verticalOffsetPx -
                                    (if (popupHeightPx > 0) popupHeightPx else with(density) { 360.dp.toPx() }.toInt()))
                                    .coerceAtLeast(0f)
                                    .toInt()
                            } else {
                                iconBottom + verticalOffsetPx
                            }
                        IntOffset(x = x, y = y)
                    }
                    .widthIn(max = 220.dp)
                    .heightIn(max = 520.dp)
                    .onSizeChanged { popupHeightPx = it.height }
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale

                        val popupWidthPx = 220.dp.toPx()
                        val horizontalMarginPx = 16.dp.toPx()
                        val popupLeftPx =
                            (iconBoundsInRoot.right - popupWidthPx)
                                .coerceAtLeast(horizontalMarginPx)
                        val iconCenterX = (iconBoundsInRoot.left + iconBoundsInRoot.right) / 2f
                        val pivotX =
                            ((iconCenterX - popupLeftPx) / popupWidthPx.coerceAtLeast(1f))
                                .coerceIn(0.02f, 0.98f)
                        this.transformOrigin =
                            TransformOrigin(pivotX, if (opensAboveAnchor()) 1f else 0f)

                        this.shadowElevation = 16.dp.toPx()
                        this.shape = RoundedCornerShape(16.dp)
                        this.clip = false
                    }
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                    },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
            ) {
                @Composable fun row(label: String, icon: Int, onClick: () -> Unit) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClick()
                                if (!dismissed) dismissed = true
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                row(stringResource(R.string.edit), R.drawable.edit) { showEditor = true }
                row(stringResource(R.string.refetch), R.drawable.sync) { showProvider = true }
                row(stringResource(R.string.video_mode_lyrics_offset), R.drawable.lyrics) { showOffset = true }
                row(stringResource(R.string.video_mode_lyrics_translation), R.drawable.translate) { showTranslation = true }
            }
        }
    }

    if (anyToolOpen) {
        // sora's own sheets for the same tools, stacked over the player like
        // Neiro stacks its inline dialogs over the menu
        if (showEditor) LyricsEditorSheet(onDismiss = { showEditor = false })
        if (showProvider) LyricsProviderSheet(onDismiss = { showProvider = false })
        if (showOffset) LyricsOffsetSheet(onDismiss = { showOffset = false })
        if (showTranslation) LyricsTranslationSheet(onDismiss = { showTranslation = false })
    }
}
