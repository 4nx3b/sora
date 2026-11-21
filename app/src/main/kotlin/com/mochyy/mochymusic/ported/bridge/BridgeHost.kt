// What PortedPlayerHost needs from sora to host the vendored styles: a NavController the styles can navigate on
// (modelled on Neiro's bridge/ArchiveTunePlayerHost.kt BridgeNavController, with sora's style actions behind it)
// and the song's canvas, looked up the way sora's MorphingCover does.

package com.mochyy.mochymusic.ported.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import androidx.navigation.NavGraphNavigator
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CanvasWifiOnlyKey
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlaybackCache
import com.mochyy.mochymusic.ui.player.findCanvas
import com.mochyy.mochymusic.ui.player.normalizeCanvasArtistName
import com.mochyy.mochymusic.ui.player.normalizeCanvasSongTitle
import com.mochyy.mochymusic.ui.player.styles.StyleActions

private class PortedNavController(
    context: Context,
    private val actions: StyleActions,
) : NavController(context) {
    init {
        navigatorProvider.apply {
            addNavigator(NavGraphNavigator(this))
            addNavigator(ComposeNavigator())
            addNavigator(DialogNavigator())
        }
        graph =
            createGraph(startDestination = "player") {
                composable("player") {}
                composable("artist/{id}") {}
                composable("album/{id}") {}
                composable("search") {}
                composable("home") {}
                composable("library") {}
            }
        addOnDestinationChangedListener { _, destination, arguments ->
            when (destination.route) {
                "artist/{id}" -> arguments?.getString("id")?.let(actions.onOpenArtist)
                "album/{id}" -> arguments?.getString("id")?.let(actions.onOpenAlbum)
                "search" -> actions.onOpenRoute("search_input")
                "home" -> actions.onOpenRoute("home")
                "library" -> actions.onOpenRoute("library")
            }
        }
    }
}

@Composable
fun rememberPortedNavController(actions: StyleActions): NavController {
    val context = LocalContext.current
    return remember(actions) { PortedNavController(context, actions) }
}

/** the song's canvas (primary, fallback), or nulls: the same lookup, cache and wifi/data-saver rules as sora's cover */
@Composable
fun rememberPortedCanvas(mediaId: String, title: String, artist: String, durationSec: Int?, enabled: Boolean): Pair<String?, String?> {
    val context = LocalContext.current
    val canvasOn = rememberPreference(CanvasThumbnailAnimationKey, true).value
    val wifiOnly = rememberPreference(CanvasWifiOnlyKey, true).value
    var urls by remember(mediaId) { mutableStateOf<Pair<String?, String?>>(null to null) }
    LaunchedEffect(mediaId, title, artist, canvasOn, wifiOnly, enabled) {
        if (!enabled || !canvasOn || mediaId.isBlank() || title.isBlank() || artist.isBlank()) return@LaunchedEffect
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (wifiOnly && context.getSystemService<android.net.ConnectivityManager>()?.isActiveNetworkMetered == true) return@withContext null
            if (com.mochyy.mochymusic.utils.DataSaver.isActive(context)) return@withContext null
            CanvasArtworkPlaybackCache.get(mediaId)
                ?: run {
                    val t = normalizeCanvasSongTitle(title)
                    val a = normalizeCanvasArtistName(artist)
                    if (t.length < 2 || a.length < 2) return@withContext null
                    findCanvas(context, t, a, durationSec = durationSec)?.also { CanvasArtworkPlaybackCache.put(mediaId, it) }
                }
        } ?: return@LaunchedEffect
        urls = found.preferredAnimationUrl to found.videoUrl
    }
    return urls
}
