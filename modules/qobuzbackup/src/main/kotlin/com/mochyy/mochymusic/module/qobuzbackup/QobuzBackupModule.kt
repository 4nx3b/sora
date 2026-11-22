/*
 * The Qobuz backup resolver, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, qobuz/QobuzBackupProvider.kt — GPL-3.0,
 * © Rukamori and the fork's contributors; see git history).
 *
 * A community mirror that answers a YouTube video id with a lossless stream:
 * /api/search by name, /api/stream by id, then a 42-byte Range probe of each
 * mirror URL the resolver names, the first live one winning. The endpoint
 * chain is whatever the user set plus the built-in default, with the same
 * three-strikes/ten-minute cooldown the fork gives it.
 */

package com.mochyy.mochymusic.module.qobuzbackup

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.NO_ACCOUNT_LABEL
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** lossless streams off a community mirror of YouTube ids, keyed to no account at all */
class QobuzBackupModule : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "Qobuz backup",
        version = "1.0",
        description = "Lossless streams from a community mirror that answers a YouTube id. No account, and independent of the Qobuz source: switched, ordered and asked on its own",
    )

    override val adds = setOf(com.mochyy.mochymusic.module.ModuleFeature.AudioSource)

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }

    // the endpoints people add on purpose, ahead of the built-in one
    @Volatile
    private var customEndpoints: List<String> = emptyList()

    override val fields = listOf(
        ModuleField(
            id = FieldEndpoints,
            label = "Backup resolver endpoints",
            kind = FieldKind.Text,
            default = "",
            hint = "https://mirror.example — one URL per line",
            description = "Add or replace the backup mirror URLs, one per line. The community default is built in",
        ),
    )

    override fun attach(host: ModuleHost) {
        this.host = host
        customEndpoints = host.storage.get("endpoints").orEmpty().lines().mapNotNull(::normalizeEndpoint)
    }

    override fun onFields(values: Map<String, String>) {
        val chain = values[FieldEndpoints].orEmpty().lines().mapNotNull(::normalizeEndpoint)
        customEndpoints = chain
        host.storage.set("endpoints", chain.joinToString("\n"))
    }

    // no account of its own: the mirror either answers or it doesn't, and a total failure is
    // marked down for ten minutes so it stops costing every song a timeout
    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override suspend fun verifyAccount(): AccountState {
        // the fork's check: a known video id the mirror should be able to answer
        val resolved = resolveStream(ProbeVideoId)
        return if (resolved != null) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · mirror reachable")
        } else {
            val reason = "No live endpoint in the chain (${endpointChain().size} configured)"
            host.storage.set("unreachable", reason)
            host.storage.set("unreachable_at", System.currentTimeMillis().toString())
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on the mirror"))
            return ResolveResult.NotFound("Not on the Qobuz backup mirror")
        }

        report(DiagnosticStep("Find the song", null, "Searching the mirror…"))
        val candidate = matches[key] ?: run {
            val found = searchCandidates("${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim(), 12)
            val best = TrackMatcher.ranked(track, found.map { it.catalog }).firstOrNull()
                ?.let { scored -> found.first { it.catalog === scored.track } }
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on the mirror")
            }
            matches[key] = best
            best
        }
        report(DiagnosticStep("Find the song", true, "${candidate.catalog.title} · ${candidate.catalog.artist}"))

        report(DiagnosticStep("Stream", null, "Asking the mirror for its streams…"))
        val resolved = resolveStream(candidate.videoId)
        if (resolved == null) {
            report(DiagnosticStep("Stream", false, "No live mirror had the song"))
            return ResolveResult.NotFound("No live mirror had the song")
        }
        host.storage.set("unreachable", null)
        report(
            DiagnosticStep(
                "Stream",
                true,
                listOfNotNull(
                    if (resolved.isLossless) "FLAC" else null,
                    resolved.sampleRate?.let { r -> resolved.bitDepth?.let { b -> "${b}/$r" } },
                ).joinToString(" · ").ifBlank { "stream opened" },
            ),
        )

        return ResolveResult.Found(
            SourceStream(
                url = resolved.uri,
                format = com.mochyy.mochymusic.module.StreamFormat.Progressive,
                headers = streamHeaders,
                sourceLabel = "Qobuz backup",
                qualityLabel = resolved.qualityLabel,
                codec = if (resolved.isLossless) "FLAC" else null,
                sampleRateHz = resolved.sampleRate,
                matched = MatchedTrack(
                    id = candidate.videoId,
                    title = candidate.catalog.title,
                    artist = candidate.catalog.artist,
                    album = candidate.catalog.album,
                    durationMs = resolved.durationMs ?: candidate.catalog.durationMs,
                    confidence = TrackMatcher.score(track, candidate.catalog),
                ),
            ),
        )
    }

    // ── the ported resolver ────────────────────────────────────────────────────────────────

    internal class Candidate(val catalog: CatalogTrack, val videoId: String)

    internal class Resolved(
        val uri: String,
        val isLossless: Boolean,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val durationMs: Long? = null,
    ) {
        val qualityLabel: String
            get() = if (isLossless) {
                listOfNotNull(bitDepth, sampleRate).joinToString("/").let { if (it.isBlank()) "FLAC" else "FLAC $it" }
            } else {
                "Lossy mirror"
            }
    }

    internal fun searchCandidates(query: String, limit: Int): List<Candidate> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || limit <= 0) return emptyList()

        val fetchLimit = limit.coerceAtLeast(24).coerceAtMost(60)
        val rows = queryVariants(trimmed)
            .firstNotNullOfOrNull { variant -> fetchSearch(variant, fetchLimit).takeIf { it.isNotEmpty() } }
            .orEmpty()
        return rows.take(limit)
    }

    private fun fetchSearch(query: String, limit: Int): List<Candidate> {
        for (base in activeEndpoints()) {
            val response = runCatching {
                host.http(
                    HttpRequest(
                        url = "$base/api/search?q=${enc(query)}&limit=$limit",
                        headers = requestHeaders,
                    ),
                )
            }.getOrNull()
            if (response == null || !response.ok) {
                recordFailure(base)
                continue
            }
            recordSuccess(base)
            val candidates = parseSearchResponse(response.text, limit)
            if (candidates.isNotEmpty()) return candidates
        }
        return emptyList()
    }

    private fun parseSearchResponse(body: String, limit: Int): List<Candidate> {
        if (body.isBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Candidate>()
        for (element in array) {
            if (out.size >= limit) break
            val item = element as? JsonObject ?: continue
            val videoId = item.string("id")?.trim().orEmpty()
            if (!VideoIdRegex.matches(videoId)) continue
            val title = item.string("name")?.trim().orEmpty()
            if (title.isEmpty()) continue
            val artist = item.string("artists")?.trim()?.takeIf { it.isNotEmpty() }
            out.add(
                Candidate(
                    catalog = CatalogTrack(id = videoId, title = title, artist = artist ?: ""),
                    videoId = videoId,
                ),
            )
        }
        return out
    }

    internal fun resolveStream(videoId: String): Resolved? {
        val id = videoId.trim()
        if (!VideoIdRegex.matches(id)) return null

        for (base in activeEndpoints()) {
            val mirrors = fetchMirrorCandidates(id, base)
            if (mirrors.isEmpty()) {
                recordFailure(base)
                continue
            }
            val resolved = mirrors.firstNotNullOfOrNull(::probeMirror)
            if (resolved != null) {
                recordSuccess(base)
                return resolved
            }
            recordFailure(base)
        }
        return null
    }

    private fun fetchMirrorCandidates(videoId: String, base: String): List<String> {
        val response = runCatching {
            host.http(HttpRequest("$base/api/stream?id=$videoId", headers = requestHeaders))
        }.getOrNull() ?: return emptyList()
        if (!response.ok || response.text.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return emptyList()
        return listOf("lossless", "flac", "url", "canvas_url", "video_url")
            .mapNotNull { root.string(it)?.takeIf(String::isNotBlank) }
            .distinct()
    }

    private fun probeMirror(url: String): Resolved? = runCatching {
        val response = host.http(
            HttpRequest(
                url,
                headers = requestHeaders + ("Range" to "bytes=0-${FlacStreamInfo.REQUIRED_BYTES - 1}"),
            ),
        )
        if (!response.ok) return@runCatching null
        val contentType = response.header("Content-Type")?.lowercase().orEmpty()
        if (contentType.contains("json") || contentType.contains("html")) return@runCatching null
        if (contentType.isNotBlank() &&
            !contentType.startsWith("audio/") &&
            !contentType.startsWith("video/") &&
            !contentType.contains("octet-stream")
        ) {
            return@runCatching null
        }
        val info = FlacStreamInfo.parse(response.body)
        val isFlac = info != null || contentType.contains("flac")
        Resolved(
            uri = url,
            isLossless = isFlac,
            sampleRate = info?.sampleRate,
            bitDepth = info?.bitDepth,
            durationMs = info?.durationMs,
        )
    }.getOrNull()

    // ── the endpoint chain, with the fork's cooldown ───────────────────────────────────────

    internal fun endpointChain(): List<String> = (customEndpoints + DefaultEndpoint).distinct()

    /** the fork's rule: https only, and the legacy mirror host rewritten to the current one */
    private fun normalizeEndpoint(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val https = if (trimmed.startsWith("http://")) "https://" + trimmed.removePrefix("http://") else trimmed
        if (!https.startsWith("https://")) return null
        val host = https.removePrefix("https://").substringBefore("/")
        if (!host.contains('.')) return null
        return https.replace("mlc-ytify.kouzu.in", "mls.kouzu.in")
    }

    private fun activeEndpoints(): List<String> {
        val now = System.currentTimeMillis()
        return endpointChain().filter { (cooldownUntil[it] ?: 0L) < now }
    }

    private fun recordFailure(endpoint: String) {
        val count = failureCounts.getOrPut(endpoint) { AtomicInteger(0) }.incrementAndGet()
        if (count >= FailureThreshold) {
            cooldownUntil[endpoint] = System.currentTimeMillis() + CooldownMs
        }
    }

    private fun recordSuccess(endpoint: String) {
        failureCounts.remove(endpoint)
        cooldownUntil.remove(endpoint)
    }

    private fun queryVariants(query: String): List<String> {
        val words = query.split(WhitespaceRegex).filter { it.isNotBlank() }
        if (words.size < 2) return listOf(query)
        return buildList {
            add(query)
            for (kept in words.size - 1 downTo 1) {
                add(words.take(kept).joinToString(" "))
                add(words.takeLast(kept).joinToString(" "))
            }
        }.distinct().filter { it.length >= 2 }.take(MaxQueryVariants)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private val matches = ConcurrentHashMap<String, Candidate>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    companion object {
        const val Id = "qobuzbackup"
        const val FieldEndpoints = "endpoints"
        private const val DefaultEndpoint = "https://mls.kouzu.in"
        private const val ProbeVideoId = "dQw4w9WgXcQ"
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val FailureThreshold = 3
        private const val CooldownMs = 10L * 60 * 1000
        private const val MaxQueryVariants = 6
        private val VideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
        private val WhitespaceRegex = Regex("\\s+")
        // the mirror's own house rule: the request says where it comes from
        private val requestHeaders = mapOf(
            "User-Agent" to "sora-android",
            "Accept" to "application/json",
            "x-request-source" to "muzo",
        )
        private val streamHeaders = mapOf("User-Agent" to "sora-android")
    }
}
