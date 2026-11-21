// ActionPromptDialog and the pieces it draws with, taken verbatim from Neiro (archivetune ui/component/Dialog.kt).

package com.mochyy.mochymusic.ported.bridge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.mochyy.mochymusic.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

val LocalUnglassColorScheme: ProvidableCompositionLocal<ColorScheme?> =
    compositionLocalOf { null }


@Composable
fun UnglassedDialogTheme(content: @Composable () -> Unit) {
    val unglassed = LocalUnglassColorScheme.current
    MaterialTheme(
        colorScheme = unglassed ?: MaterialTheme.colorScheme,
        content = content,
    )
}

@Composable
private fun DialogExpressiveEntrance(content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.01f,
            ),
        label = "dialogEntrance",
    )
    Box(
        modifier =
            Modifier.graphicsLayer {
                val p = progress.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                translationY = (1f - p) * 12f
            },
    ) {
        content()
    }
}

@Composable
private fun DialogHeaderRow(
    icon: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (icon != null) {
            CompositionLocalProvider(
                LocalContentColor provides AlertDialogDefaults.iconContentColor,
            ) {
                DialogIconChip(icon)
            }
        }
        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
            ) {
                title()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dialogShowingState = LocalSettingsDialogShowing.current
    DisposableEffect(Unit) {
        dialogShowingState.value = true
        onDispose { dialogShowingState.value = false }
    }

    val menuDialogDismissal = LocalMenuDialogDismissal.current

    Dialog(
        onDismissRequest = {
            menuDialogDismissal?.invoke()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UnglassedDialogTheme {
            KeepStatusBarHiddenInDialog()

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                DialogExpressiveEntrance {
                Surface(
                    modifier = Modifier.heightIn(max = maxHeight),

                    shape = MaterialTheme.shapes.extraLarge,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (titleBar != null) {
                                Row {
                                    titleBar()
                                }
                            } else if (title != null || icon != null) {
                                CompositionLocalProvider(
                                    LocalContentColor provides AlertDialogDefaults.titleContentColor,
                                ) {
                                    DialogHeaderRow(
                                        icon = icon,
                                        title = {
                                            if (title != null) {
                                                Text(
                                                    text = title,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 2,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                )
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            content()
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (onReset != null) {
                                Row(modifier = Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = { onReset() },
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(stringResource(R.string.reset))
                                    }
                                }
                            }

                            if (onCancel != null) {
                                OutlinedButton(
                                    onClick = { onCancel() },
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }

                            FilledTonalButton(
                                onClick = { onConfirm() },
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
