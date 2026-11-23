// Muzo Data as a built-in plugin: one video id answered with the lossless FLAC for exactly that recording.
// The key lives where MuzoService always kept it, so Muzo's lyrics and canvas read the same one.

package com.mochyy.mochymusic.muzo

import android.content.Context
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.utils.DataSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MuzoModule(private val context: Context) : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "Muzo Data",
        version = "1.0",
        description = context.getString(R.string.muzo_data_desc),
        author = "kouzu",
    )

    @Volatile private var key = ""

    // the saved key is the field's default, so the registry's first hand-over gives it back rather than wiping it
    override val fields: List<ModuleField>
        get() = listOf(
            ModuleField(
                id = KeyField,
                label = context.getString(R.string.muzo_api_key),
                kind = FieldKind.Text,
                default = key,
                hint = context.getString(R.string.muzo_api_key_hint),
                description = context.getString(R.string.muzo_api_key_subtitle),
            ),
        )

    override fun attach(host: ModuleHost) {
        key = runBlocking(Dispatchers.IO) { MuzoService.apiKey(context) }.orEmpty()
    }

    override fun onFields(values: Map<String, String>) {
        val given = values[KeyField].orEmpty().trim()
        if (given == key) return
        key = given
        runBlocking(Dispatchers.IO) { MuzoService.setApiKey(context, given) }
    }

    // the key is the opt-in: without one Muzo is skipped and never asked
    override fun accountState(): AccountState =
        if (key.isBlank()) AccountState.SignedOut else AccountState.SignedIn(KeySet)

    override suspend fun verifyAccount(): AccountState {
        if (key.isBlank()) return AccountState.SignedOut
        return when (val check = MuzoService.checkApi(key)) {
            is MuzoApiCheck.Ok -> AccountState.SignedIn(MuzoService.usageDetail(check))
            is MuzoApiCheck.Rejected -> AccountState.Expired(check.reason)
            is MuzoApiCheck.Unreachable -> AccountState.SignedIn(KeySet)
        }
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult {
        // a capped connection never pays lossless bytes
        if (DataSaver.isActive(context)) return ResolveResult.Unavailable("data saver is on")
        if (key.isBlank()) return ResolveResult.Unavailable("add your Muzo Data API key")
        val videoId = track.videoId ?: return ResolveResult.NotFound("needs a YouTube video id")
        val answer = MuzoService.lossless(key, videoId) ?: return ResolveResult.NotFound("not on Muzo Data")
        val url = answer.url ?: return ResolveResult.NotFound("not on Muzo Data")
        return ResolveResult.Found(
            SourceStream(
                url = url,
                format = StreamFormat.Progressive,
                // the CDN binds its file URLs to the key they were minted for
                headers = mapOf("Authorization" to "Bearer $key"),
                sourceLabel = "Muzo Data",
                qualityLabel = answer.quality?.takeIf { it.isNotBlank() } ?: "lossless",
                matched = MatchedTrack(
                    id = videoId,
                    title = track.title,
                    artist = track.artists.joinToString(),
                    album = track.album,
                    durationMs = track.durationMs,
                    confidence = 1f,
                ),
                codec = (answer.format ?: "flac").uppercase(),
            ),
        )
    }

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (key.isBlank()) {
            report(DiagnosticStep("API key", false, "No API key"))
            return ResolveResult.Unavailable("add your Muzo Data API key")
        }
        report(DiagnosticStep("API key", true, KeySet))
        report(DiagnosticStep("Lookup", null, "Asking Muzo Data…"))
        val result = resolve(track, picks)
        when (result) {
            is ResolveResult.Found -> report(DiagnosticStep("Lookup", true, "${result.stream.codec} · ${result.stream.qualityLabel}"))
            is ResolveResult.NotFound -> report(DiagnosticStep("Lookup", false, result.reason))
            is ResolveResult.Unavailable -> report(DiagnosticStep("Lookup", false, result.reason))
        }
        return result
    }

    companion object {
        const val Id = "muzo"
        const val KeyField = "api_key"
        private const val KeySet = "API key set"
    }
}
