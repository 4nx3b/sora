// Neiro's SF Pro fonts page (SettingsSheet SfProFontsPage): the sf-pro.kouzu.in catalog grouped by family;
// tapping a font downloads it once and makes it the app's font, the same as a picked file.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppFont
import com.mochyy.mochymusic.constants.AppFontKey
import com.mochyy.mochymusic.constants.CustomFontNameKey
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.theme.AppFontLoader
import com.mochyy.mochymusic.ui.theme.SfProFontCatalog
import com.mochyy.mochymusic.ui.theme.SfProFontPreview
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch

const val FontCatalogRoute = "customisation_settings/font/sf_pro"

@Composable
fun FontCatalogScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (font) = rememberEnumPreference(AppFontKey, defaultValue = AppFont.DEFAULT)
    val (appliedName) = rememberPreference(CustomFontNameKey, defaultValue = "")
    var catalog by remember { mutableStateOf<List<SfProFontCatalog.FontEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf<String?>(null) }
    // bumped when a font lands in the cache, so its row redraws its specimen in the font itself
    var cachedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        loading = true
        catalog = SfProFontCatalog.fetchCatalog()
        loading = false
    }

    SubSettingsScaffold(
        title = stringResource(R.string.sf_pro_fonts),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.font),
    ) {
        val fonts = catalog
        when {
            loading -> SettingsSection(
                null,
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.sf_pro_loading)) },
                        trailingContent = { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) },
                    ),
                ),
            )
            fonts == null -> {
                SettingsSection(
                    null,
                    listOf(
                        SettingsItem(
                            title = { Text(stringResource(R.string.sf_pro_error)) },
                            descriptionText = stringResource(R.string.sf_pro_error_subtitle),
                            icon = painterResource(R.drawable.refresh),
                            onClick = { attempt++ },
                        ),
                    ),
                )
                Text(
                    stringResource(R.string.sf_pro_error_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            else -> fonts.groupBy { it.family ?: it.name }.forEach { (family, entries) ->
                SettingsSection(
                    family,
                    entries.map { entry ->
                        val applied = font == AppFont.CUSTOM && entry.name == appliedName
                        SettingsItem(
                            title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            description = {
                                val family = remember(entry.url, cachedTick) { SfProFontPreview.fontFamilyFor(context, entry) }
                                val spec = remember(entry.url) { SfProFontPreview.previewSpec(entry) }
                                Text(
                                    "AaBbCcDd 0123456789",
                                    fontFamily = family,
                                    fontWeight = spec.fontWeight,
                                    fontStyle = spec.fontStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            highlightKey = entry.name,
                            trailingContent = {
                                when {
                                    downloading == entry.name -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    applied -> Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            },
                            enabled = downloading == null,
                            onClick = onClick@{
                                if (downloading != null) return@onClick
                                downloading = entry.name
                                scope.launch {
                                    val ok = SfProFontPreview.ensureDownloaded(context, entry)
                                    if (ok) {
                                        cachedTick++
                                        AppFontLoader.applyFile(context, SfProFontPreview.previewFile(context, entry), entry.name)
                                    } else {
                                        android.widget.Toast.makeText(context, R.string.sf_pro_error, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    downloading = null
                                }
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
