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
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackQuery
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
            ModuleField(
                id = FormatField,
                label = "Stream format",
                kind = FieldKind.Select,
                options = listOf(
                    StreamOption(MuzoFormat.Lossless.id, "Lossless", "/stream/lossless: the FLAC master, 1 credit"),
                    StreamOption(MuzoFormat.Aac.id, "AAC", "/stream/aac: the M4A, 1 credit"),
                    StreamOption(MuzoFormat.Auto.id, "Auto", "/stream: FLAC when there is one, else the M4A, 1 credit"),
                ),
                default = MuzoFormat.Lossless.id,
            ),
            toggle(StreamField, "Streams", "/stream, /stream/lossless and /stream/aac", true),
            toggle(LyricsField, "Lyrics", "/lyrics, word and syllable timed, 1 credit", true),
            toggle(CanvasField, "Canvas", "/canvas, the looping cover video, 1 credit", true),
            toggle(MetadataField, "Metadata", "/metadata, title, artists, album and art, 1 credit", false),
            toggle(AutoqueueField, "Radio queue", "/ytm/autoqueue and /ytm/next, 0.25 credit a page", false),
            toggle(CatalogField, "Add played songs", "/stream/add, free and keyless: songs you play join the lossless catalog", true),
        )

    private fun toggle(id: String, label: String, description: String, on: Boolean) =
        ModuleField(id = id, label = label, kind = FieldKind.Toggle, default = on.toString(), description = description)

    override fun attach(host: ModuleHost) {
        key = runBlocking(Dispatchers.IO) { MuzoService.apiKey(context) }.orEmpty()
    }

    override fun onFields(values: Map<String, String>) {
        fun on(id: String, default: Boolean) = values[id]?.toBooleanStrictOrNull() ?: default
        MuzoPrefs.stream = on(StreamField, true)
        MuzoPrefs.lyrics = on(LyricsField, true)
        MuzoPrefs.canvas = on(CanvasField, true)
        MuzoPrefs.metadata = on(MetadataField, false)
        MuzoPrefs.autoqueue = on(AutoqueueField, false)
        MuzoPrefs.catalogAdd = on(CatalogField, true)
        MuzoPrefs.format = MuzoFormat.entries.firstOrNull { it.id == values[FormatField] } ?: MuzoFormat.Lossless
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
        if (key.isBlank()) return ResolveResult.Unavailable("add your Muzo Data API key")
        if (!MuzoPrefs.stream) return ResolveResult.NotFound("streams are switched off")
        val videoId = track.videoId ?: return ResolveResult.NotFound("needs a YouTube video id")
        val (url, quality, codec) = when (MuzoPrefs.format) {
            MuzoFormat.Lossless -> MuzoService.lossless(key, videoId)?.let { a -> a.url?.let { Triple(it, a.quality, codecOf(a.format, "FLAC")) } }
            MuzoFormat.Aac -> MuzoEndpoints.aac(key, videoId)?.let { a -> a.url?.let { Triple(it, a.quality, codecOf(a.format, "AAC")) } }
            MuzoFormat.Auto -> MuzoEndpoints.stream(key, videoId)?.let { s ->
                s.losslessUrl?.let { Triple(it, "lossless", "FLAC") } ?: s.m4aUrl?.let { Triple(it, "AAC", "AAC") }
            }
        } ?: return ResolveResult.NotFound("not on Muzo Data")
        return ResolveResult.Found(
            SourceStream(
                url = url,
                format = StreamFormat.Progressive,
                // the CDN binds its file URLs to the key they were minted for
                headers = mapOf("Authorization" to "Bearer $key"),
                sourceLabel = "Muzo Data",
                qualityLabel = quality?.takeIf { it.isNotBlank() } ?: "lossless",
                matched = MatchedTrack(
                    id = videoId,
                    title = track.title,
                    artist = track.artists.joinToString(),
                    album = track.album,
                    durationMs = track.durationMs,
                    confidence = 1f,
                ),
                codec = codec,
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

    private fun codecOf(format: String?, fallback: String): String =
        format?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { if (it.equals("mp4", true) || it.equals("m4a", true)) "AAC" else it.uppercase() } ?: fallback

    companion object {
        const val Id = "muzo"
        const val KeyField = "api_key"
        const val FormatField = "muzo_format"
        const val StreamField = "muzo_stream"
        const val LyricsField = "muzo_lyrics"
        const val CanvasField = "muzo_canvas"
        const val MetadataField = "muzo_metadata"
        const val AutoqueueField = "muzo_autoqueue"
        const val CatalogField = "muzo_catalog_add"
        private const val KeySet = "API key set"
    }
}
