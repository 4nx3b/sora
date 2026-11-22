/*
 * The Deezer source, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, deezer/DeezerAudioProvider.kt and DeezerCrypto.kt
 * — GPL-3.0, © Rukamori and the fork's contributors).
 *
 * The credential is the arl cookie of a Deezer account. The gateway opens a
 * session (its api token and license token), a track is matched by ISRC first
 * and by search second, and media.deezer.com hands back a Blowfish-encrypted
 * CDN url — returned as a deezer:// stream uri the app's decrypting reader
 * opens, the same arrangement the fork plays under.
 */

package com.mochyy.mochymusic.module.deezer

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Deezer's catalog in FLAC CD quality through an account's arl cookie */
class DeezerModule : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "Deezer",
        version = "1.0",
        description = "Deezer's catalog in FLAC (CD 16-bit) through an account. Bring the arl cookie of a Deezer account you can sign in to",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var arl: String = ""

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(FormatFlac, "FLAC", "CD quality, 16-bit/44.1 kHz — steps down to MP3 320 on accounts without lossless"),
                StreamOption(FormatMp3_320, "MP3 320", "A third of the data, still clear"),
                StreamOption(FormatMp3_128, "MP3 128", "For slow or metered networks"),
            ),
        ),
    )

    override val fields = listOf(
        ModuleField(
            id = FieldArl,
            label = "ARL cookie",
            kind = FieldKind.Text,
            default = "",
            hint = "the arl cookie from a signed-in deezer.com",
            description = "Sign in on deezer.com and paste the arl cookie out of the browser. The account's own quality is what plays",
        ),
    )

    override fun attach(host: ModuleHost) {
        this.host = host
        arl = host.storage.get("arl").orEmpty().trim()
    }

    override fun onFields(values: Map<String, String>) {
        val given = values[FieldArl].orEmpty().trim()
        if (given != arl) {
            arl = given
            host.storage.set("arl", given)
            session = null
            matches.clear()
            streamCache.clear()
        }
    }

    // the arl is the opt-in, the way the fork's accounts are
    override fun accountState(): AccountState {
        if (arl.isBlank()) return AccountState.SignedOut
        host.storage.get("account")?.let { return AccountState.SignedIn(it) }
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn("ARL set")
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn("ARL set")
    }

    override suspend fun verifyAccount(): AccountState {
        if (arl.isBlank()) return AccountState.SignedOut
        val account = verifyArl(arl)
        return if (account != null) {
            session = null
            host.storage.set("unreachable", null)
            val label = "${account.first} · " + if (account.second) "lossless" else "no lossless, MP3 320 at best"
            host.storage.set("account", label)
            AccountState.SignedIn(label)
        } else {
            session = null
            val reason = "The ARL was rejected by Deezer"
            host.storage.set("unreachable", reason)
            host.storage.set("unreachable_at", System.currentTimeMillis().toString())
            host.storage.set("account", null)
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        session = null
        matches.clear()
        misses.clear()
        streamCache.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (arl.isBlank()) {
            report(DiagnosticStep("Account", false, "No ARL"))
            return ResolveResult.Unavailable("Paste the arl cookie of a Deezer account first")
        }
        // an account without lossless is downgraded inside the ask, by the tier list below
        val format = picks[ChoiceQuality] ?: FormatFlac

        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on Deezer"))
            return ResolveResult.NotFound("Not on Deezer")
        }

        val opened = runCatching { session() }.getOrNull()
        if (opened == null) {
            report(DiagnosticStep("Account", false, "Deezer didn't answer"))
            markUnreachable("Deezer didn't answer")
            return ResolveResult.Unavailable("Deezer didn't answer")
        }
        host.storage.set("unreachable", null)
        report(DiagnosticStep("Account", true, if (opened.lossless) "signed in · lossless" else "signed in"))

        report(DiagnosticStep("Find the song", null, "Searching Deezer…"))
        val match = matches[key] ?: searchTrack(opened, track)
        if (match == null) {
            misses[key] = System.currentTimeMillis()
            report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
            return ResolveResult.NotFound("No matching recording on Deezer")
        }
        matches[key] = match
        report(DiagnosticStep("Find the song", true, "${match.title} · ${match.artist}"))

        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val cacheKey = "$key:$format"
        streamCache[cacheKey]?.let { return it }
        val media = runCatching { requestUrl(opened, match.id, format) }.getOrNull()
        if (media == null) {
            report(DiagnosticStep("Stream", false, "Deezer wouldn't open the song"))
            return ResolveResult.NotFound("Deezer wouldn't open the song")
        }
        val label = when (media.format.uppercase()) {
            FormatFlac -> "FLAC 16/44.1"
            FormatMp3_320 -> "MP3 320"
            FormatMp3_128 -> "MP3 128"
            else -> "Deezer"
        }
        report(DiagnosticStep("Stream", true, label))

        val stream = ResolveResult.Found(
            SourceStream(
                // the encrypted cdn file, handed to the app's decrypting reader by uri
                url = buildStreamUri(media.url, match.id),
                format = StreamFormat.Progressive,
                sourceLabel = "Deezer",
                qualityLabel = label,
                codec = if (media.flac) "FLAC" else null,
                sampleRateHz = if (media.flac) 44_100 else null,
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
        streamCache[cacheKey] = stream
        return stream
    }

    // ── the session, the gateway the fork opens ────────────────────────────────────────────

    internal class Session(
        val apiToken: String,
        val licenseToken: String,
        val lossless: Boolean,
        val establishedAt: Long,
    )

    @Volatile
    private var session: Session? = null

    internal fun session(): Session? {
        session?.let { if (System.currentTimeMillis() - it.establishedAt < SessionTtlMs) return it }
        val body = gateway("deezer.getUserData", "", null) ?: return null
        val results = body["results"] as? JsonObject ?: return null
        val user = results["USER"] as? JsonObject ?: return null
        if (user.long("USER_ID") == 0L) return null
        val options = user["OPTIONS"] as? JsonObject ?: return null
        val apiToken = results.string("checkForm")?.takeIf { it.isNotBlank() } ?: return null
        val licenseToken = options.string("license_token")?.takeIf { it.isNotBlank() } ?: return null
        val lossless = options.bool("web_lossless") == true || options.bool("mobile_lossless") == true
        return Session(apiToken, licenseToken, lossless, System.currentTimeMillis()).also { session = it }
    }

    /** the account's name and whether it carries lossless, from the same call the fork verifies with */
    internal fun verifyArl(arl: String): Pair<String, Boolean>? {
        val body = runCatching { gateway("deezer.getUserData", "", null) }.getOrNull() ?: return null
        val results = body["results"] as? JsonObject ?: return null
        val user = results["USER"] as? JsonObject ?: return null
        if (user.long("USER_ID") == 0L) return null
        val options = user["OPTIONS"] as? JsonObject
        val name = listOfNotNull(user.string("BLOG_NAME"), user.string("FIRSTNAME"), user.string("EMAIL"))
            .firstOrNull { it.isNotBlank() } ?: "Deezer"
        val lossless = options?.bool("web_lossless") == true || options?.bool("mobile_lossless") == true
        return name to lossless
    }

    private fun gateway(method: String, apiToken: String, payload: JsonObject?): JsonObject? {
        val url = "$Gateway?method=${enc(method)}&input=3&api_version=1.0&api_token=${enc(apiToken)}"
        val body = (payload ?: buildJsonObject { }).toString().toByteArray(Charsets.UTF_8)
        val response = runCatching {
            host.http(
                HttpRequest(
                    url,
                    method = "POST",
                    headers = mapOf("User-Agent" to UserAgent, "Cookie" to "arl=$arl", "Accept" to "application/json"),
                    body = body,
                    contentType = "application/json; charset=utf-8",
                ),
            )
        }.getOrNull() ?: return null
        if (!response.ok) return null
        return runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull()
    }

    // ── matching: the isrc first, the search second ────────────────────────────────────────

    internal class Match(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
        val confidence: Float,
    )

    private fun searchTrack(session: Session, track: TrackQuery): Match? {
        val isrc = track.isrc?.takeIf { it.isNotBlank() }
        val candidates = buildList {
            isrc?.let { addAll(lookupByIsrc(it)) }
            addAll(searchMusic(session, track))
        }
        val scored = TrackMatcher.ranked(track, candidates).firstOrNull() ?: return null
        val candidate = candidates.first { it.id == scored.track.id }
        return Match(
            id = candidate.id,
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            durationMs = candidate.durationMs,
            confidence = scored.score,
        )
    }

    private fun lookupByIsrc(isrc: String): List<CatalogTrack> {
        val response = runCatching {
            host.http(HttpRequest("https://api.deezer.com/track/isrc:${enc(isrc)}", headers = mapOf("Accept" to "application/json")))
        }.getOrNull() ?: return emptyList()
        if (!response.ok) return emptyList()
        val obj = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return emptyList()
        if (obj.containsKey("error")) return emptyList()
        val id = obj.long("id")?.takeIf { it > 0 }?.toString() ?: return emptyList()
        val title = obj.string("title")?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(
            CatalogTrack(
                id = id,
                title = title,
                artist = (obj["artist"] as? JsonObject)?.string("name").orEmpty(),
                album = (obj["album"] as? JsonObject)?.string("title"),
                durationMs = obj.long("duration")?.takeIf { it > 0 }?.times(1000),
            ),
        )
    }

    private fun searchMusic(session: Session, track: TrackQuery): List<CatalogTrack> {
        val payload = buildJsonObject {
            put("query", (listOf(TrackMatcher.core(track.title)) + track.artists.take(1)).joinToString(" ").trim())
            put("start", 0)
            put("nb", SearchLimit)
        }
        val body = gateway("search.music", session.apiToken, payload) ?: return emptyList()
        val data = ((body["results"] as? JsonObject)?.get("data") as? JsonArray) ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("SNG_ID")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogTrack(
                id = id,
                title = obj.string("SNG_TITLE").orEmpty(),
                artist = obj.string("ART_NAME").orEmpty(),
                album = obj.string("ALB_TITLE"),
                durationMs = obj.long("DURATION")?.takeIf { it > 0 }?.times(1000),
            )
        }
    }

    // ── the stream: the track token, then the encrypted cdn url ────────────────────────────

    internal class Media(val url: String, val flac: Boolean, val format: String)

    private fun requestUrl(session: Session, trackId: String, format: String): Media? {
        val trackJson = gateway("song.getData", session.apiToken, buildJsonObject { put("sng_id", trackId) }) ?: return null
        val results = trackJson["results"] as? JsonObject ?: return null
        val trackToken = results.string("TRACK_TOKEN")?.takeIf { it.isNotBlank() } ?: return null

        // the tiers at and below the asked one, so a lossless ask on a lossless-less account
        // still gets an answer
        val tiers = FormatTiers.dropWhile { it != format }.ifEmpty { listOf(FormatMp3_320, FormatMp3_128) }
        val payload = buildJsonObject {
            put("license_token", session.licenseToken)
            put("media", buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "FULL")
                        put("formats", buildJsonArray {
                            tiers.forEach { tier ->
                                add(buildJsonObject { put("cipher", "BF_CBC_STRIPE"); put("format", tier) })
                            }
                        })
                    },
                )
            })
            put("track_tokens", buildJsonArray { add(trackToken) })
        }
        val response = runCatching {
            host.http(
                HttpRequest(
                    MediaEndpoint,
                    method = "POST",
                    headers = mapOf("User-Agent" to UserAgent, "Cookie" to "arl=$arl", "Accept" to "application/json"),
                    body = payload.toString().toByteArray(Charsets.UTF_8),
                    contentType = "application/json; charset=utf-8",
                ),
            )
        }.getOrNull() ?: return null
        if (!response.ok) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        val media = (((root["data"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("media") as? JsonArray)
            ?.firstOrNull() as? JsonObject ?: return null
        val source = (media["sources"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val url = source.string("url")?.takeIf { it.isNotBlank() } ?: return null
        val served = media.string("format").orEmpty().ifBlank { format }
        return Media(url, served.equals(FormatFlac, ignoreCase = true), served)
    }

    /** the app-side decrypting reader opens this: the cdn url and the track id it is keyed to */
    internal fun buildStreamUri(url: String, trackId: String): String =
        "deezer://stream?u=${enc(url)}&k=${enc(trackId)}"

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.contentOrNull?.let { it == "true" }

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private val matches = ConcurrentHashMap<String, Match>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val streamCache = ConcurrentHashMap<String, ResolveResult.Found>()

    companion object {
        const val Id = "deezer"
        const val ChoiceQuality = "quality"
        const val FormatFlac = "FLAC"
        const val FormatMp3_320 = "MP3_320"
        const val FormatMp3_128 = "MP3_128"
        const val FieldArl = "arl"
        private const val Gateway = "https://www.deezer.com/ajax/gw-light.php"
        private const val MediaEndpoint = "https://media.deezer.com/v1/get_url"
        private val FormatTiers = listOf(FormatFlac, FormatMp3_320, FormatMp3_128)
        private const val SearchLimit = 10
        private const val SessionTtlMs = 45L * 60 * 1000
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
