/*
 * The Qobuz source, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, qobuz/QobuzAudioProvider.kt, QobuzToken.kt and
 * SourceInputParsing.kt — GPL-3.0, © Rukamori and the fork's contributors).
 *
 * Two kinds of backend, tokens first: a Qobuz account (user_auth_token with
 * its app id and secret, signed the way the official client signs — an md5 of
 * the request's own fields), or a community instance proxying the catalog.
 * The stream is the direct API's getFileUrl answer, with sample-only answers
 * passed over, exactly the fork's walk.
 */

package com.mochyy.mochymusic.module.qobuz

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
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Qobuz's catalog in FLAC up to 24-bit/192 kHz, through an account or a community instance */
class QobuzModule : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "Qobuz",
        version = "1.0",
        description = "Qobuz's catalog in FLAC up to 24-bit/192 kHz. Bring an account (its user_auth_token, app id and secret) or a community instance",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var tokens: List<QobuzToken> = emptyList()

    @Volatile
    private var instances: List<String> = emptyList()

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(FormatLossless, "Lossless", "FLAC 16-bit/44.1 kHz"),
                StreamOption(FormatHiRes, "Hi-Res", "FLAC 24-bit up to 96 kHz"),
                StreamOption(FormatMax, "Hi-Res Max", "FLAC 24-bit up to 192 kHz"),
                // the id data saver looks for by name, so it lands on the mp3 tier and not the
                // second flac one
                StreamOption(OptionSaver, "MP3 320", "What data saver steps down to"),
            ),
        ),
    )

    override val fields = listOf(
        ModuleField(
            id = FieldTokens,
            label = "Accounts",
            kind = FieldKind.Text,
            default = "",
            hint = "token … / app_id: … / app_secret: … — one account per block",
            description = "Paste a Qobuz account: its user_auth_token, app_id and app_secret, separated by lines. More than one account can be pasted",
        ),
        ModuleField(
            id = FieldInstances,
            label = "Instances",
            kind = FieldKind.Text,
            default = "",
            hint = "https://instance.example — one URL per line",
            description = "Community instances proxying the Qobuz catalog. Asked after the accounts",
        ),
    )

    override fun attach(host: ModuleHost) {
        this.host = host
        tokens = QobuzToken.listFromJson(host.storage.get("tokens"))
        instances = host.storage.get("instances").orEmpty().lines().mapNotNull(::normalizeInstanceUrl)
    }

    override fun onFields(values: Map<String, String>) {
        val parsed = SourceInputParsing.parseQobuzTokens(values[FieldTokens].orEmpty())
        if (parsed.isNotEmpty() || values[FieldTokens].orEmpty().isBlank()) {
            tokens = parsed
            host.storage.set("tokens", QobuzToken.listToJson(parsed))
        }
        val chain = values[FieldInstances].orEmpty().lines().mapNotNull(::normalizeInstanceUrl)
        instances = chain
        host.storage.set("instances", chain.joinToString("\n"))
        streamCache.clear()
    }

    // an account or an instance is the opt-in: with neither there is nothing to ask
    override fun accountState(): AccountState {
        if (tokens.isEmpty() && instances.isEmpty()) return AccountState.SignedOut
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(accountLabel())
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(accountLabel())
    }

    override suspend fun verifyAccount(): AccountState {
        val first = tokens.firstOrNull()
        if (first != null) {
            when (verifyToken(first)) {
                Health.HEALTHY -> {
                    host.storage.set("unreachable", null)
                    return AccountState.SignedIn(
                        accountLabel() + " · " + (first.label.ifBlank { first.userId }.ifBlank { "account" } + " is premium"),
                    )
                }
                Health.PREVIEW_ONLY -> {
                    host.storage.set("unreachable", null)
                    return AccountState.SignedIn(accountLabel() + " · previews only, not premium")
                }
                else -> {
                    // the token is down to the instances behind it
                }
            }
        }
        if (instances.isNotEmpty()) {
            val reachable = instances.count { checkInstance(it) != null }
            if (reachable > 0) {
                host.storage.set("unreachable", null)
                return AccountState.SignedIn("$reachable of ${instances.size} instances reachable")
            }
            val reason = "No Qobuz backend answered"
            host.storage.set("unreachable", reason)
            host.storage.set("unreachable_at", System.currentTimeMillis().toString())
            return AccountState.Expired(reason)
        }
        return if (first != null) AccountState.Expired("The account didn't answer") else AccountState.SignedOut
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (tokens.isEmpty() && instances.isEmpty()) {
            report(DiagnosticStep("Account", false, "No account or instance"))
            return ResolveResult.Unavailable("Add a Qobuz account or an instance first")
        }

        val formatId = when (picks[ChoiceQuality]) {
            FormatHiRes -> 7
            FormatMax -> 27
            OptionSaver -> 5
            else -> 6
        }
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on Qobuz"))
            return ResolveResult.NotFound("Not on Qobuz")
        }

        report(DiagnosticStep("Find the song", null, "Searching Qobuz…"))
        val cached = matches[key]
        val match = cached ?: searchAll(track)
        if (match == null) {
            misses[key] = System.currentTimeMillis()
            report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
            return ResolveResult.NotFound("No matching recording on Qobuz")
        }
        if (cached == null) matches[key] = match
        report(DiagnosticStep("Find the song", true, "${match.title} · ${match.artist}"))

        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val streamCacheKey = "$key:$formatId"
        streamCache[streamCacheKey]?.let { return it }
        val download = downloadAll(match, formatId)
        if (download == null) {
            report(DiagnosticStep("Stream", false, "No backend could open the song"))
            return ResolveResult.NotFound("No Qobuz backend could open the song")
        }
        host.storage.set("unreachable", null)
        report(DiagnosticStep("Stream", true, download.label))

        val stream = ResolveResult.Found(
            SourceStream(
                url = download.url,
                format = StreamFormat.Progressive,
                sourceLabel = "Qobuz",
                qualityLabel = download.label.substringAfter("Qobuz ").ifBlank { qualityLabel(formatId) },
                codec = if (download.isFlac) "FLAC" else null,
                sampleRateHz = if (download.isFlac) nominalSampleRate(formatId) else 44_100,
                matched = MatchedTrack(
                    id = match.id,
                    title = match.title,
                    artist = match.artist,
                    album = match.album,
                    durationMs = match.durationMs,
                    confidence = match.confidence,
                ),
            ),
        )
        streamCache[streamCacheKey] = stream
        return stream
    }

    // ── the search: every backend asked, the best match overall taken ──────────────────────

    internal class Match(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
        val confidence: Float,
        val backendId: String,
    )

    private fun searchAll(track: TrackQuery): Match? {
        val searchQuery = "${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim()
        var best: Match? = null
        for (backend in orderedBackends()) {
            val items = runCatching { backend.search(searchQuery) }.getOrNull() ?: continue
            val candidates = items.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val id = item.trackId() ?: return@mapNotNull null
                val title = item.string("title") ?: return@mapNotNull null
                val artist = (item["performer"] as? JsonObject)?.string("name")
                    ?: item.string("artist")
                    ?: (item["album"] as? JsonObject)?.let { (it["artist"] as? JsonObject)?.string("name") }
                    ?: ""
                if (artist.isBlank() && track.artists.isNotEmpty()) return@mapNotNull null
                CatalogTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = (item["album"] as? JsonObject)?.string("title"),
                    durationMs = item.long("duration")?.times(1000L),
                )
            }
            val scored = TrackMatcher.ranked(track, candidates).firstOrNull() ?: continue
            val candidate = candidates.first { it.id == scored.track.id }
            if (best == null || scored.score > best!!.confidence) {
                best = Match(
                    id = candidate.id,
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    durationMs = candidate.durationMs,
                    confidence = scored.score,
                    backendId = backend.id,
                )
            }
        }
        return best
    }

    // ── the stream: the backends in order, the first non-preview answer wins ───────────────

    internal class Download(val url: String, val label: String, val isFlac: Boolean)

    private fun downloadAll(match: Match, formatId: Int): Download? {
        val backends = orderedBackends().filter { it.id == match.backendId } + orderedBackends().filter { it.id != match.backendId }
        for (backend in backends) {
            val download = runCatching { backend.download(match.id, formatId) }.getOrNull() ?: continue
            return Download(
                url = download.url,
                label = "Qobuz ${qualityLabel(formatId)}",
                isFlac = download.isFlac,
            )
        }
        return null
    }

    private class Backend(
        val id: String,
        val search: (String) -> JsonArray?,
        val download: (String, Int) -> DownloadResult?,
    )

    private fun orderedBackends(): List<Backend> =
        tokens.map { token ->
            Backend(
                id = "token:${token.id}",
                search = { q -> searchItemsDirect(token, q) },
                download = { id, fmt -> fetchDownloadDirect(token, id, fmt) },
            )
        } + instances.map { base ->
            Backend(
                id = base,
                search = { q -> searchItems(base, q) },
                download = { id, fmt -> fetchDownload(base, id, fmt) },
            )
        }

    // ── the official API, signed the way the client signs ──────────────────────────────────

    private fun directHeaders(token: QobuzToken) = mapOf(
        "User-Agent" to UserAgent,
        "Accept" to "application/json",
        "X-App-Id" to token.appId,
        "X-User-Auth-Token" to token.token,
    )

    private fun searchItemsDirect(token: QobuzToken, query: String): JsonArray? {
        val url = "$ApiBase/track/search?query=${enc(query)}&limit=$SearchLimit&app_id=${enc(token.appId)}"
        val response = runCatching { host.http(HttpRequest(url, headers = directHeaders(token))) }.getOrNull() ?: return null
        if (!response.ok || response.text.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        return root.findTrackItems()
    }

    private fun fetchDownloadDirect(token: QobuzToken, trackId: String, formatId: Int): DownloadResult? {
        val response = runCatching { host.http(HttpRequest(downloadUrl(token, trackId, formatId), headers = directHeaders(token))) }.getOrNull() ?: return null
        if (!response.ok || response.text.isBlank()) return null
        return parseDownload(response.text)
    }

    /** the one signature Qobuz asks for: md5 of the request's own fields, in this exact order */
    internal fun downloadUrl(token: QobuzToken, trackId: String, formatId: Int): String {
        val ts = System.currentTimeMillis() / 1000L
        val sig = md5("trackgetFileUrlformat_id${formatId}intentstreamtrack_id$trackId$ts${token.appSecret}")
        return "$ApiBase/track/getFileUrl" +
            "?request_ts=$ts&request_sig=$sig&track_id=${enc(trackId)}" +
            "&format_id=$formatId&intent=stream&app_id=${enc(token.appId)}"
    }

    private fun parseDownload(body: String): DownloadResult? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val streamUrl = root.string("url")?.takeIf { it.startsWith("http") }
            ?: root.findStreamUrl()
            ?: return null
        val isPreview = root.bool("sample") == true || root.looksLikePreview()
        if (isPreview) return null
        val apiMime = root.string("mime_type")
        val isFlac = apiMime?.contains("flac", true) == true ||
            streamUrl.contains(".flac", true) ||
            (apiMime?.contains("mp3", true) != true && !streamUrl.contains(".mp3", true))
        return DownloadResult(streamUrl, isFlac)
    }

    // ── the community instances ────────────────────────────────────────────────────────────

    private val instanceHeaders = mapOf("User-Agent" to UserAgent, "Accept" to "application/json")

    private fun searchItems(base: String, query: String): JsonArray? {
        val url = "$base/api/get-music?q=${enc(query)}&offset=0"
        val response = runCatching { host.http(HttpRequest(url, headers = instanceHeaders)) }.getOrNull() ?: return null
        if (!response.ok || response.text.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        return root.findTrackItems()
    }

    private fun fetchDownload(base: String, trackId: String, formatId: Int): DownloadResult? {
        val url = "$base/api/download-music?track_id=${enc(trackId)}&quality=$formatId"
        val response = runCatching { host.http(HttpRequest(url, headers = instanceHeaders)) }.getOrNull() ?: return null
        if (!response.ok || response.text.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        val streamUrl = root.findStreamUrl() ?: return null
        if (root.looksLikePreview()) return null
        val isFlac = streamUrl.contains(".flac", true) || !streamUrl.contains(".mp3", true)
        return DownloadResult(streamUrl, isFlac)
    }

    // ── the health probes the fork's check runs ────────────────────────────────────────────

    internal enum class Health { HEALTHY, PREVIEW_ONLY, UNREACHABLE }

    internal fun verifyToken(token: QobuzToken): Health {
        val account = fetchTokenAccountHealth(token) ?: return Health.UNREACHABLE
        if (!hasValidAppSecret(token)) return Health.UNREACHABLE
        return if (account) Health.HEALTHY else Health.PREVIEW_ONLY
    }

    private fun fetchTokenAccountHealth(token: QobuzToken): Boolean? {
        val url = "$ApiBase/user/get?app_id=${enc(token.appId)}&user_auth_token=${enc(token.token)}"
        val response = runCatching { host.http(HttpRequest(url, headers = directHeaders(token))) }.getOrNull() ?: return null
        if (!response.ok || response.text.isBlank()) return null
        val normalized = response.text.lowercase(Locale.US)
        val valid = normalized.contains("\"id\"") || normalized.contains("credential")
        if (!valid) return null
        return Regex("""lossless|hi-res|hires|studio|sublime|"format_id"\s*:\s*(6|7|27)""").containsMatchIn(normalized)
    }

    private fun hasValidAppSecret(token: QobuzToken): Boolean {
        val response = runCatching {
            host.http(HttpRequest(downloadUrl(token, ProbeTrackId, ProbeFormatId), headers = directHeaders(token)))
        }.getOrNull() ?: return false
        val body = response.text.lowercase(Locale.US)
        return !body.contains("invalid request signature") && !body.contains("invalidrequestsignature")
    }

    /** the fork's instance check: any http answer under half a second's wait counts as live */
    internal fun checkInstance(base: String): Long? {
        val started = System.currentTimeMillis()
        val response = runCatching { host.http(HttpRequest(base, headers = instanceHeaders)) }.getOrNull()
        return if (response != null && response.code in 200..499) System.currentTimeMillis() - started else null
    }

    // ── the json walk the fork's responses need ────────────────────────────────────────────

    internal class DownloadResult(val url: String, val isFlac: Boolean)

    private fun JsonObject.findTrackItems(): JsonArray? {
        val data = (this["data"] as? JsonObject) ?: this
        return (data["tracks"] as? JsonObject)?.let { it["items"] as? JsonArray }
            ?: data["items"] as? JsonArray
            ?: data["tracks"] as? JsonArray
            ?: this["items"] as? JsonArray
            ?: this["tracks"] as? JsonArray
    }

    private fun JsonElement.findStreamUrl(): String? {
        if (this !is JsonObject) return null
        for (key in listOf("url", "downloadUrl", "download_url", "stream_url", "streamUrl", "link")) {
            string(key)?.takeIf { it.startsWith("http") }?.let { return it }
        }
        (this["data"] as? JsonObject)?.findStreamUrl()?.let { return it }
        for (value in values) {
            when (value) {
                is JsonObject -> value.findStreamUrl()?.let { return it }
                is JsonPrimitive -> {
                    val text = value.contentOrNull ?: continue
                    if (text.startsWith("http") && (text.contains(".flac", true) || text.contains(".mp3", true))) return text
                }
                else -> {}
            }
        }
        return null
    }

    private fun JsonObject.looksLikePreview(): Boolean {
        val data = (this["data"] as? JsonObject) ?: this
        if (data.bool("sample") == true || data.bool("preview") == true) return true
        val type = data.string("type") ?: return false
        return type.equals("preview", true) || type.equals("sample", true)
    }

    private fun JsonObject.trackId(): String? =
        string("id") ?: long("id")?.toString() ?: string("track_id")

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.contentOrNull?.let { it == "true" }

    private fun accountLabel(): String =
        listOfNotNull(
            tokens.takeIf { it.isNotEmpty() }?.let { "${it.size} account${if (it.size == 1) "" else "s"}" },
            instances.takeIf { it.isNotEmpty() }?.let { "${it.size} instance${if (it.size == 1) "" else "s"}" },
        ).joinToString(" · ")

    private fun qualityLabel(formatId: Int): String = when (formatId) {
        27 -> "Hi-Res Max FLAC ≤192 kHz"
        7 -> "Hi-Res FLAC ≤96 kHz"
        5 -> "MP3 320"
        else -> "FLAC 16/44.1"
    }

    private fun nominalSampleRate(formatId: Int): Int = when (formatId) {
        27 -> 192_000
        7 -> 96_000
        else -> 44_100
    }

    private fun md5(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private val matches = ConcurrentHashMap<String, Match>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val streamCache = ConcurrentHashMap<String, ResolveResult.Found>()

    companion object {
        const val Id = "qobuz"
        const val ChoiceQuality = "quality"
        const val FormatLossless = "6"
        const val FormatHiRes = "7"
        const val FormatMax = "27"
        const val OptionSaver = "high"
        const val FieldTokens = "tokens"
        const val FieldInstances = "instances"
        private const val ApiBase = "https://www.qobuz.com/api.json/0.2"
        private const val SearchLimit = 10
        private const val ProbeTrackId = "5966783"
        private const val ProbeFormatId = 5
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val UserAgent = "sora-android"
    }
}
