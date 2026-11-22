// Neiro's font choice (SettingsSheet Font row and chooser): sora's own Inter, SF Pro Display, the device's
// typeface, or a .ttf/.otf of the user's, plus the SF Pro catalog. Applied by ui/theme/ApplyAppFont.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppFont
import com.mochyy.mochymusic.constants.AppFontKey
import com.mochyy.mochymusic.constants.CustomFontNameKey
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.theme.AppFontLoader
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch

const val FontSettingsRoute = "customisation_settings/font"

@Composable
fun FontSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (font, onFont) = rememberEnumPreference(AppFontKey, defaultValue = AppFont.DEFAULT)
    val (customName) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { AppFontLoader.applyPicked(context, uri) }
    }
    val icon = painterResource(R.drawable.tune)

    @Composable
    fun choice(value: AppFont, title: Int, subtitle: String, onClick: () -> Unit = { onFont(value) }) = SettingsItem(
        title = { Text(stringResource(title)) },
        descriptionText = subtitle,
        icon = icon,
        highlightKey = stringResource(title),
        trailingContent = {
            if (font == value) {
                Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        },
        onClick = onClick,
    )

    SubSettingsScaffold(
        title = stringResource(R.string.font),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.settings_customisation_category),
    ) {
        com.mochyy.mochymusic.ui.screens.gesture.PortedPreviewHeader { com.mochyy.mochymusic.ui.screens.gesture.FontPreview(com.mochyy.mochymusic.ui.screens.gesture.rememberFontName()) }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
        SettingsSection(
            stringResource(R.string.font),
            listOf(
                choice(AppFont.DEFAULT, R.string.font_sora_default, stringResource(R.string.font_sora_default_subtitle)),
                choice(AppFont.SF_PRO, R.string.font_sf_pro, stringResource(R.string.font_sf_pro_subtitle)),
                choice(AppFont.SYSTEM, R.string.font_system, stringResource(R.string.font_system_subtitle)),
                choice(
                    AppFont.CUSTOM,
                    R.string.font_custom,
                    customName.ifBlank { stringResource(R.string.custom_font_none_subtitle) },
                    // the file is the choice: picking one copies it in and turns it on, and a bad one changes nothing
                    onClick = { picker.launch(AppFontLoader.supportedMimeTypes) },
                ),
            ),
        )
        SettingsSection(
            null,
            listOf(
                linkItem(
                    key = "SF Pro fonts",
                    title = stringResource(R.string.sf_pro_fonts),
                    description = stringResource(R.string.sf_pro_fonts_subtitle),
                    icon = painterResource(R.drawable.download),
                    onClick = { navController.navigate(FontCatalogRoute) },
                ),
            ),
        )
        Spacer(Modifier.height(40.dp))
    }
}
