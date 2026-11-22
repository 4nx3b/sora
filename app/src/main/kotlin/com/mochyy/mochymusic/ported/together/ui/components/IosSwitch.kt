/*
 * The Listen Together port's switch, in the shape of Neiro's IosSwitch: every
 * ported settings row toggles through this name. The look is sora's own
 * AppSwitch, so the ported page matches the rest of the app.
 */

package com.mochyy.mochymusic.ported.together.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mochyy.mochymusic.ui.component.AppSwitch

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}
