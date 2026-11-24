// CatalogResultsSheet.kt
// Force change's search: what one source's catalogue has for the playing song, each match with its picture and
// what its stream is, and a tap plays that exact match from that source.

package com.mochyy.mochymusic.ui.player.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.module.CatalogHit
import com.mochyy.mochymusic.module.CatalogSearch
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val SearchTimeoutMs = 15_000L
private val Figures = TextStyle(fontFeatureSettings = "tnum")
private val ChipFill = Color.White.copy(alpha = 0.10f)
private val LosslessFill = Color(0xFF30D158).copy(alpha = 0.22f)

/** a listed match, and for a source without its own list search the stream its one match already found */
@Immutable
private class Match(val hit: CatalogHit, val stream: SourceStream?)

private sealed interface Results {
    data object Looking : Results
    data class Listed(val matches: List<Match>) : Results
    data class Failed(val reason: String) : Results
}

@Composable
internal fun CatalogResultsSheet(
    providerId: String,
    providerName: String,
    module: SourceModule,
    metadata: MediaMetadata?,
    modules: SourceModules,
    onPicked: (SourceStream) -> Unit,
    onDismiss: () -> Unit,
) {
    var results by remember(providerId) { mutableStateOf<Results>(Results.Looking) }
    var picking by remember(providerId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // one lookup per open, off the main thread, never repeated by recomposition
    LaunchedEffect(providerId, metadata?.id) {
        val track = metadata?.let(::trackOf) ?: run {
            results = Results.Failed("nothing is playing")
            return@LaunchedEffect
        }
        results = withContext(Dispatchers.IO) {
            withTimeoutOrNull(SearchTimeoutMs) {
                runCatching { search(module, track, modules.playbackPicks(providerId, module), metadata) }
                    .getOrElse { Results.Failed(it.message ?: it.javaClass.simpleName) }
            } ?: Results.Failed("timed out")
        }
    }

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        InfoColumn {
            InfoSheetTitle(stringResource(R.string.pc_force_results_title, providerName))
            when (val r = results) {
                Results.Looking -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                is Results.Failed -> Note(stringResource(R.string.pc_force_results_failed, r.reason))
                is Results.Listed -> if (r.matches.isEmpty()) {
                    Note(stringResource(R.string.pc_force_results_none))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(r.matches, key = { it.hit.id }, contentType = { "match" }) { match ->
                            MatchRow(
                                match = match,
                                fallbackArt = metadata?.thumbnailUrl,
                                busy = picking == match.hit.id,
                                onClick = {
                                    if (picking != null) return@MatchRow
                                    picking = match.hit.id
                                    scope.launch {
                                        val stream = match.stream ?: withContext(Dispatchers.IO) {
                                            withTimeoutOrNull(SearchTimeoutMs) {
                                                runCatching {
                                                    (module as CatalogSearch).resolveHit(match.hit, modules.playbackPicks(providerId, module))
                                                }.getOrNull()
                                            }
                                        }.let { (it as? ResolveResult.Found)?.stream }
                                        picking = null
                                        if (stream != null) onPicked(stream)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun trackOf(metadata: MediaMetadata) = TrackQuery(
    title = metadata.title,
    artists = metadata.artists.map { it.name },
    album = metadata.album?.title,
    durationMs = metadata.duration.takeIf { it > 0 }?.times(1000L),
    videoId = metadata.id,
)

// a source with its own list search answers with its list; any other gives the one match its normal lookup finds
private suspend fun search(module: SourceModule, track: TrackQuery, picks: Map<String, String>, metadata: MediaMetadata): Results {
    if (module is CatalogSearch) {
        return Results.Listed(module.searchCatalog(track, 10).distinctBy { it.id }.map { Match(it, null) })
    }
    return when (val r = module.resolve(track, picks)) {
        is ResolveResult.Found -> Results.Listed(listOf(Match(hitOf(r.stream, metadata), r.stream)))
        is ResolveResult.NotFound -> Results.Listed(emptyList())
        is ResolveResult.Unavailable -> Results.Failed(r.reason)
    }
}

private fun hitOf(stream: SourceStream, metadata: MediaMetadata): CatalogHit {
    val codec = stream.codec
    return CatalogHit(
        id = stream.matched.id,
        title = stream.matched.title,
        artist = stream.matched.artist,
        album = stream.matched.album,
        durationMs = stream.matched.durationMs ?: metadata.duration.takeIf { it > 0 }?.times(1000L),
        artworkUrl = metadata.thumbnailUrl,
        codec = codec,
        sampleRateHz = stream.sampleRateHz ?: 0,
        lossless = codec != null && (codec.contains("flac", true) || codec.contains("alac", true)),
        qualityLabel = stream.qualityLabel,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MatchRow(match: Match, fallbackArt: String?, busy: Boolean, onClick: () -> Unit) {
    val hit = match.hit
    val context = LocalContext.current
    val art = hit.artworkUrl ?: fallbackArt
    val request = remember(art) { ImageRequest.Builder(context).data(art).size(96).build() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.title,
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                hit.durationMs?.takeIf { it > 0 }?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(clock(it), color = InfoMuted, fontSize = 12.sp, style = Figures)
                }
            }
            Text(
                listOfNotNull(hit.artist.takeIf { it.isNotBlank() }, hit.album?.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = InfoMuted,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val chips = chipsOf(hit)
            if (chips.isNotEmpty() || hit.lossless) {
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hit.lossless) Chip(stringResource(R.string.pc_force_results_lossless), LosslessFill)
                    chips.forEach { Chip(it, ChipFill) }
                }
            }
        }
        if (busy) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Chip(text: String, fill: Color) {
    Text(
        text,
        color = Color.White,
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        style = Figures,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fill).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, color = InfoMuted, fontFamily = InterFontFamily, fontSize = 14.sp, modifier = Modifier.padding(vertical = 18.dp))
}

private fun chipsOf(hit: CatalogHit): List<String> = buildList {
    hit.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    if (hit.sampleRateHz > 0) {
        val khz = hit.sampleRateHz / 1000f
        val rate = if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
        add(if (hit.bitDepth > 0) "$rate / ${hit.bitDepth}-bit" else rate)
    } else if (hit.bitDepth > 0) {
        add("${hit.bitDepth}-bit")
    }
    if (hit.bitrateKbps > 0) add("${hit.bitrateKbps} kbps")
    sizeOf(hit)?.let { add(it) }
    hit.qualityLabel?.takeIf { it.isNotBlank() && none { chip -> chip.equals(it, true) } }?.let { add(it) }
}

// the size the source reports, else roughly what its bitrate over the song's length comes to
private fun sizeOf(hit: CatalogHit): String? {
    hit.sizeBytes?.takeIf { it > 0 }?.let { return megabytes(it) }
    val ms = hit.durationMs ?: return null
    if (hit.bitrateKbps <= 0 || ms <= 0) return null
    return "~" + megabytes(hit.bitrateKbps * 1000L / 8 * ms / 1000)
}

private fun megabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576f)

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
