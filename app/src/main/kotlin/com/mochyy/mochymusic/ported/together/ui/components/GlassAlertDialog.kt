/*
 * The Listen Together port's alert dialog, in the shape of Neiro's
 * GlassAlertDialog (the same parameter list the ported screens call it with).
 * The surface is sora's material3 dialog, so the ported page matches the rest
 * of the app.
 */

package com.mochyy.mochymusic.ported.together.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
    )
}
