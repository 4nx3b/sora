// LyricsSourcesSheet.kt
// Neiro's "Lyrics sources" sheet, on sora's own sources: the providers in the order they're
// asked, a tap on a row switching that source on or off, a drag on its handle moving it, and the
// "prefer syllabic lyrics" switch under the list. The order lands in the same
// lyricsProviderOrder the player has always read; each row's switch is the provider's own
// enable key, so nothing about how lyrics are fetched changes shape.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LyricsPreferSyllabicKey
import com.mochyy.mochymusic.constants.LyricsProviderOrderKey
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.screens.audiolab.ProviderOrder
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberPreference

// one line per source, the way Neiro's sheet reads
private fun sourceDescription(name: String): String = when (name) {
    "BetterLyrics" -> "Apple Music, word-synced"
    "Unison" -> "Community, word-synced"
    "YouLyPlus" -> "LyricsPlus"
    "Paxsenix" -> "PaxSenix, word-synced"
    "LrcRed" -> "Apple timed lines"
    "SimpMusic" -> "SimpMusic"
    "Muzo" -> "Muzo Data"
    "LrcLib" -> "LRCLIB, line-synced"
    "Deezer" -> "Deezer"
    "YouTubeSubtitle" -> "YouTube subtitles"
    "YouTubeMusic" -> "YouTube Music"
    "NetEase" -> "NetEase"
    "QQMusic" -> "QQ Music"
    "Kugou" -> "KuGou"
    "YouTubeCaptions" -> "Auto captions, asked last"
    else -> "From a module"
}

fun PopupSheetState.showLyricsSourcesSheet() {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        LyricsSourcesSheetContent(onDone = { sheet?.dismiss() })
    }
}

@Composable
private fun LyricsSourcesSheetContent(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = context.applicationContext.dataStore
    val (order, setOrder) = rememberPreference(LyricsProviderOrderKey, "")
    val (preferSyllabic, setPreferSyllabic) = rememberPreference(LyricsPreferSyllabicKey, true)

    // a provider's own answer to "are you in the chain", read fresh each time the sheet recomposes
    var enabledTick by remember { mutableStateOf(0) }
    val names = remember(order, enabledTick) {
        (if (order.isBlank()) LyricsProviderRegistry.getDefaultProviderOrder()
        else LyricsProviderRegistry.deserializeProviderOrder(order))
            .filter { it in LyricsProviderRegistry.providerNames }
    }
    val on = stringResource(R.string.lyrics_sources_on)
    val off = stringResource(R.string.lyrics_sources_off)
    fun isEnabled(name: String): Boolean =
        LyricsProviderRegistry.getProviderByName(name)?.isEnabled(context) ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        SheetLabel(stringResource(R.string.lyrics_sources_title))
        Text(
            text = stringResource(R.string.lyrics_sources_desc),
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        // the list scrolls inside the sheet when the sources outgrow it; the handles still drag
        Column(
            modifier = Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ProviderOrder(
                order = names,
                name = { LyricsProviderRegistry.getDisplayName(it) },
                logo = { null },
                usable = { isEnabled(it) },
                status = { name ->
                    if (LyricsProviderRegistry.enableKey(name) == null) {
                        sourceDescription(name)
                    } else {
                        sourceDescription(name) + " · " + (if (isEnabled(name)) on else off)
                    }
                },
                onTap = { name ->
                    val key = LyricsProviderRegistry.enableKey(name)
                    if (key != null) {
                        PreferencesCache.set(store, key, !isEnabled(name))
                        enabledTick++
                    }
                },
                onReorder = { reordered -> setOrder(LyricsProviderRegistry.serializeProviderOrder(reordered)) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.prefer_syllabic),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.prefer_syllabic_desc),
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            AppSwitch(checked = preferSyllabic, onCheckedChange = setPreferSyllabic)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = {
                PreferencesCache.edit(store) { prefs ->
                    prefs.remove(LyricsProviderOrderKey)
                    LyricsProviderRegistry.providerNames.forEach { name ->
                        LyricsProviderRegistry.enableKey(name)?.let { key -> prefs[key] = true }
                    }
                    prefs[LyricsPreferSyllabicKey] = true
                }
                enabledTick++
            }) { Text(stringResource(R.string.reset)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
