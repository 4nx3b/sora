/*
 * The SoundCloud source, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, soundcloud/SoundCloudAudioProvider.kt —
 * GPL-3.0, © Rukamori and the fork's contributors).
 *
 * No account: the client id is scraped off soundcloud.com's own script
 * bundles (rotating, cached twelve hours, re-scraped on a refusal), a track
 * is found by search and matched, and its best transcoding is resolved with
 * the client id and the track's own authorization. Progressive transcodings
 * come back as plain cdn urls; a track with only HLS comes back as its
 * playlist, and the app's player plays it as hls with the same playback
 * headers the fork sends.
 */

package com.mochyy.mochymusic.module.soundcloud

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.NO_ACCOUNT_LABEL
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** user uploads in AAC up to 160 kbps, no account needed */
class SoundCloudModule : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "SoundCloud",
        version = "1.0",
        description = "User uploads in AAC up to 160 kbps and MP3 128, no account needed. Strongest on remixes, sets and self-released music",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(QualityAac160, "AAC 160", "The best SoundCloud serves, HLS when the track has no progressive"),
                StreamOption(QualityAac96, "AAC 96", "A little lighter"),
                StreamOption(QualityMp3128, "MP3 128", "The progressive file most tracks also carry"),
            ),
        ),
    )

    override val fields = listOf(
        ModuleField(
            id = FieldToken,
            label = "OAuth token",
            kind = FieldKind.Text,
            default = "",
            hint = "optional — an oauth token from a signed-in soundcloud.com",
            description = "Optional. Kept for the tracks that need a signed-in listener; the catalog answers without it",
        ),
    )

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        host.storage.set("token", values[FieldToken]?.trim().orEmpty())
    }

    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override suspend fun verifyAccount(): AccountState {
        // the fork's check: a search that answers at all proves the scraped client id
        val clientId = getClientId()
        if (clientId != null) {
            val answered = runCatching {
                host.http(HttpRequest(searchUrl("test", 3, clientId), headers = apiHeaders)).ok
            }.getOrDefault(false)
            if (answered) {
                host.storage.set("unreachable", null)
                return AccountState.SignedIn("$NO_ACCOUNT_LABEL · client id ok")
            }
        }
        val fresh = getClientId(forceRefresh = true)
        val reason = if (fresh == null) "soundcloud.com couldn't be scraped for a client id" else "SoundCloud didn't answer"
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
        return AccountState.Expired(reason)
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
        streamCache.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on SoundCloud"))
            return ResolveResult.NotFound("Not on SoundCloud")
        }

        report(DiagnosticStep("Find the song", null, "Searching SoundCloud…"))
        var clientId = getClientId()
        if (clientId == null) {
            report(DiagnosticStep("Find the song", false, "No client id — soundcloud.com wouldn't scrape"))
            markUnreachable("soundcloud.com couldn't be scraped for a client id")
            return ResolveResult.Unavailable("soundcloud.com couldn't be scraped for a client id")
        }

        val found = matches[key] ?: run {
            val candidates = searchTracks("${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim(), clientId!!)
            TrackMatcher.ranked(track, candidates).firstOrNull()
                ?.let { scored -> candidates.first { it.id == scored.track.id } }
        }
        if (found == null) {
            misses[key] = System.currentTimeMillis()
            report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
            return ResolveResult.NotFound("No matching recording on SoundCloud")
        }
        matches[key] = found
        report(DiagnosticStep("Find the song", true, "${found.title} · ${found.artist}"))

        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val quality = picks[ChoiceQuality] ?: QualityAac160
        streamCache["$key:$quality"]?.let { return it }
        // the search answer is a lite track; the full one carries the transcodings
        val full = found.transcodings?.let { found } ?: fetchFullTrack(found.id, clientId)
        if (full == null) {
            report(DiagnosticStep("Stream", false, "The track couldn't be opened"))
            return ResolveResult.NotFound("The track couldn't be opened on SoundCloud")
        }
        var candidate = selectStreamCandidate(full.transcodings, quality)
        var streamUrl = candidate?.let { resolveTranscodingUrl(it, full.trackAuthorization, clientId) }
        if (candidate == null || streamUrl == null) {
            // a refused client id is the usual reason: scrape a fresh one and ask once more
            clientId = getClientId(forceRefresh = true)
            if (clientId == null) {
                report(DiagnosticStep("Stream", false, "No client id — soundcloud.com wouldn't scrape"))
                return ResolveResult.Unavailable("soundcloud.com couldn't be scraped for a client id")
            }
            candidate = selectStreamCandidate(full.transcodings, quality)
            streamUrl = candidate?.let { resolveTranscodingUrl(it, full.trackAuthorization, clientId) }
        }
        if (candidate == null || streamUrl == null) {
            report(DiagnosticStep("Stream", false, "No playable transcoding"))
            return ResolveResult.NotFound("No playable transcoding on SoundCloud")
        }
        host.storage.set("unreachable", null)
        val label = candidate.label(quality)
        report(DiagnosticStep("Stream", true, label))

        val stream = ResolveResult.Found(
            SourceStream(
                url = streamUrl,
                format = if (candidate.isHls) StreamFormat.Hls else StreamFormat.Progressive,
                // no headers here: the app's player adds SoundCloud's playback headers by host,
                // the fork's arrangement, so hls segments and seeks both behave
                sourceLabel = "SoundCloud",
                qualityLabel = label,
                codec = if (candidate.mimeType.contains("aac") || candidate.mimeType.contains("mp4")) "AAC" else null,
                sampleRateHz = candidate.sampleRate ?: 44_100,
                matched = MatchedTrack(
                    id = full.id,
                    title = full.title,
                    artist = full.artist,
                    album = null,
                    durationMs = full.durationMs,
                    confidence = TrackMatcher.score(track, found.catalog),
                ),
            ),
        )
        streamCache["$key:$quality"] = stream
        return stream
    }

    // ── the client id, scraped off soundcloud.com's own bundles ────────────────────────────

    @Volatile
    private var cachedClientId: String? = null

    @Volatile
    private var clientIdExpiresAt: Long = 0L

    internal fun getClientId(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) cachedClientId?.takeIf { now < clientIdExpiresAt }?.let { return it }
        val html = runCatching {
            host.http(HttpRequest("https://soundcloud.com", headers = mapOf("User-Agent" to BrowserUserAgent)))
        }.getOrNull()?.takeIf { it.ok }?.text ?: ""
        var found = ClientIdRegex.find(html)?.groupValues?.get(1)
        if (found == null) {
            // the id lives in one of the script bundles the page names
            for (scriptUrl in AssetScriptRegex.findAll(html).map { it.value }.distinct()) {
                val js = runCatching {
                    host.http(HttpRequest(scriptUrl, headers = mapOf("User-Agent" to BrowserUserAgent)))
                }.getOrNull()?.takeIf { it.ok }?.text ?: continue
                found = ClientIdRegex.find(js)?.groupValues?.get(1)
                if (found != null) break
            }
        }
        if (found != null) {
            cachedClientId = found
            clientIdExpiresAt = now + ClientIdTtlMs
        } else if (cachedClientId != null) {
            // keep the stale one rather than nothing: it may still answer
            clientIdExpiresAt = now + ClientIdTtlMs
        }
        return found ?: cachedClientId
    }

    // ── the catalog ────────────────────────────────────────────────────────────────────────

    internal class Track(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long?,
        val trackAuthorization: String?,
        val transcodings: JsonArray?,
    ) {
        val catalog: CatalogTrack
            get() = CatalogTrack(id = id, title = title, artist = artist, durationMs = durationMs)
    }

    private fun searchTracks(term: String, clientId: String): List<Track> {
        val response = runCatching {
            host.http(HttpRequest(searchUrl(term, SearchLimit, clientId), headers = apiHeaders))
        }.getOrNull() ?: return emptyList()
        if (!response.ok) return emptyList()
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"] as? JsonArray ?: return emptyList()
        return collection.mapNotNull { (it as? JsonObject)?.toTrack() }
    }

    private fun searchUrl(term: String, limit: Int, clientId: String) =
        "$ApiBase/search/tracks?q=${enc(term)}&limit=${limit.coerceIn(1, 50)}&client_id=${enc(clientId)}&app_locale=en"

    private fun fetchFullTrack(trackId: String, clientId: String): Track? {
        val response = runCatching {
            host.http(HttpRequest("$ApiBase/tracks/$trackId?client_id=${enc(clientId)}&app_locale=en", headers = apiHeaders))
        }.getOrNull() ?: return null
        if (!response.ok) return null
        val obj = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        return obj.toTrack()
    }

    private fun JsonObject.toTrack(): Track? {
        // SNIP is SoundCloud's own "you may not stream this" marker
        if (string("policy")?.equals("SNIP", ignoreCase = true) == true) return null
        val id = string("id") ?: long("id")?.toString() ?: return null
        val title = string("title") ?: return null
        val publisherArtist = (this["publisher_metadata"] as? JsonObject)?.string("artist")
        val userName = (this["user"] as? JsonObject)?.string("username")
        val labelName = string("label_name")
        val artist = listOfNotNull(publisherArtist, userName, labelName).firstOrNull() ?: "Unknown Artist"
        return Track(
            id = id,
            title = title,
            artist = artist,
            durationMs = long("full_duration") ?: long("duration"),
            trackAuthorization = string("track_authorization"),
            transcodings = (this["media"] as? JsonObject)?.get("transcodings") as? JsonArray,
        )
    }

    // ── the transcodings, picked the way the fork ranks them ───────────────────────────────

    internal class StreamCandidate(
        val url: String,
        val mimeType: String,
        val isHls: Boolean,
        val bitrate: Int?,
        val sampleRate: Int?,
    ) {
        fun label(quality: String): String {
            val kbps = (bitrate ?: 0) / 1000
            val codec = when {
                mimeType.contains("aac") || mimeType.contains("mp4") -> "AAC"
                mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3"
                else -> "Audio"
            }
            return if (kbps > 0) "$codec $kbps" else codec
        }
    }

    internal fun selectStreamCandidate(transcodings: JsonArray?, quality: String): StreamCandidate? {
        if (transcodings == null) return null
        val targetBitrate = when (quality) {
            QualityMp3128 -> 128_000
            QualityAac96 -> 96_000
            else -> 160_000
        }
        val targetMimePart = if (quality == QualityMp3128) "mpeg" else "aac"
        class Ranked(val candidate: StreamCandidate, val score: List<Int>)

        val ranked = transcodings.mapNotNull { element ->
            val transcoding = element as? JsonObject ?: return@mapNotNull null
            val format = transcoding["format"] as? JsonObject ?: return@mapNotNull null
            val protocol = format.string("protocol")?.lowercase(Locale.US) ?: return@mapNotNull null
            val mimeType = format.string("mime_type")?.lowercase(Locale.US) ?: return@mapNotNull null
            if (protocol != "progressive" && protocol != "hls") return@mapNotNull null
            if (!mimeType.contains("mpeg") && !mimeType.contains("mp3") && !mimeType.contains("aac") && !mimeType.contains("mp4")) return@mapNotNull null
            val streamUrl = transcoding.string("url") ?: return@mapNotNull null
            val bitrate = transcoding.int("bitrate", "bit_rate") ?: format.int("bitrate", "bit_rate")
            Ranked(
                StreamCandidate(
                    url = streamUrl,
                    mimeType = mimeType,
                    isHls = protocol == "hls",
                    bitrate = bitrate,
                    sampleRate = transcoding.int("sample_rate", "sampleRate", "audio_sample_rate")
                        ?: format.int("sample_rate", "sampleRate", "audio_sample_rate"),
                ),
                listOf(
                    if (protocol == "progressive") 1_000 else 0,
                    when {
                        bitrate == targetBitrate && mimeType.contains(targetMimePart) -> 400
                        bitrate == targetBitrate -> 300
                        else -> 0
                    },
                    if (mimeType.contains(targetMimePart)) 200 else 0,
                    if (protocol == "hls" && quality != QualityMp3128) 150 else 0,
                    bitrate ?: 0,
                ),
            )
        }
        return ranked.maxWithOrNull(compareByDescending<Ranked> { it.score[0] }
            .thenByDescending { it.score[1] }
            .thenByDescending { it.score[2] }
            .thenByDescending { it.score[3] }
            .thenByDescending { it.score[4] })?.candidate
    }

    private fun resolveTranscodingUrl(candidate: StreamCandidate, trackAuthorization: String?, clientId: String): String? {
        val url = candidate.url.substringBefore("?") +
            "?client_id=${enc(clientId)}" +
            (trackAuthorization?.takeIf { it.isNotBlank() }?.let { "&track_authorization=${enc(it)}" } ?: "")
        val response = runCatching { host.http(HttpRequest(url, headers = apiHeaders)) }.getOrNull() ?: return null
        if (!response.ok) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        return root.string("url")?.takeIf { it.startsWith("http") }
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(vararg names: String): Int? {
        for (name in names) {
            val value = (this[name] as? JsonPrimitive)?.contentOrNull ?: continue
            value.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private val matches = ConcurrentHashMap<String, Track>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val streamCache = ConcurrentHashMap<String, ResolveResult.Found>()

    companion object {
        const val Id = "soundcloud"
        const val ChoiceQuality = "quality"
        const val QualityAac160 = "aac160"
        const val QualityAac96 = "aac96"
        const val QualityMp3128 = "mp3128"
        const val FieldToken = "token"
        private const val ApiBase = "https://api-v2.soundcloud.com"
        private const val SearchLimit = 20
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val ClientIdTtlMs = 12L * 60 * 60 * 1000
        private val AssetScriptRegex = Regex("""https://[a-z0-9-]+\.sndcdn\.com/[^"'<>!]+\.js""")
        private val ClientIdRegex = Regex("""client_?id["']?\s*[:=]\s*["']([A-Za-z0-9]{20,})["']""", RegexOption.IGNORE_CASE)
        internal const val BrowserUserAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
        private val apiHeaders = mapOf("User-Agent" to BrowserUserAgent, "Accept" to "application/json")
    }
}
