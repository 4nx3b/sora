/*
 * The Spotify source, ported from Stopify
 * (github.com/Anattana-Labs/Stopify, data/SpotifyWebClient.kt and player/SpotifyAudioPlayer.kt
 * — GPL-3.0, © Shashwat and the project's contributors).
 *
 * The credential is the sp_dc cookie of a Spotify account, read out of the sign-in browser the
 * way the other modules take theirs. The walk is the web player's own: the TOTP-signed access
 * token, the catalog searched with the web player's persisted query, and then the part that is
 * Stopify's breakthrough — this "device" registers with the dealer, asks for a state machine
 * for the matched track, and reads the manifest's file id off the dealer socket. The file id
 * resolves to a cdn url, a Widevine-encrypted mp4 the device's own cdm decrypts while the
 * module trades its challenges with spotify's license server, exactly as Apple Music's streams
 * play here. Free accounts play; downloads can't keep a drm stream, so the download order
 * passes over them the way it does Apple Music's.
 */

package com.mochyy.mochymusic.module.spotify

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Spotify's catalog from its own cdn, the web player's way; free accounts play */
class SpotifyModule : SourceModule, ModuleFields {

    override val info = ModuleInfo(
        id = Id,
        name = "Spotify",
        version = "1.0",
        description = "Spotify's own catalog, streamed from its cdn the way the web player does it — free accounts play. Sign in below, or paste the sp_dc cookie of a signed-in open.spotify.com",
        sourceUrl = "https://github.com/Anattana-Labs/Stopify",
        author = "Anattana Labs (Stopify)",
    )

    override val signIn = SignInSpec(
        startUrl = "https://accounts.spotify.com/?continue=https%3A%2F%2Fopen.spotify.com%2F",
        origins = listOf("https://accounts.spotify.com", "https://open.spotify.com", "https://www.spotify.com"),
        // the session rides open.spotify.com's own requests; its cookie header is the sp_dc
        sessionOrigins = setOf("https://open.spotify.com"),
        desktopSite = true,
        title = "Sign in to Spotify",
        subtitle = "Sign in and let open.spotify.com load; sora reads the session cookie",
    )

    private lateinit var host: ModuleHost

    @Volatile private var spDc = ""
    @Volatile private var spKey = ""
    private val deviceId by lazy {
        host.storage.get("device_id") ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
            host.storage.set("device_id", it)
        }
    }

    override val fields: List<ModuleField>
        get() = listOf(
            ModuleField(
                id = FieldSpDc,
                label = "sp_dc cookie",
                kind = FieldKind.Text,
                default = spDc,
                hint = "optional — the sp_dc cookie from a signed-in open.spotify.com",
                description = "The sign-in above fills this on its own; paste it by hand only if the browser sign-in can't be used",
            ),
        )

    override fun attach(host: ModuleHost) {
        this.host = host
        spDc = host.storage.get("sp_dc").orEmpty().trim()
        spKey = host.storage.get("sp_key").orEmpty().trim()
    }

    override fun onFields(values: Map<String, String>) {
        val given = values[FieldSpDc].orEmpty().trim()
        // an empty field is "nothing pasted", never an instruction to forget the cookie the
        // sign-in browser left: a paste is the only thing it changes, the sign-out clears
        if (given.isBlank()) return
        if (given == spDc) return
        spDc = given
        spKey = ""
        host.storage.set("sp_dc", given)
        host.storage.set("sp_key", null)
        host.storage.set("account", null)
        SpotifyWeb.forgetToken()
        dealer.reset()
        matches.clear()
        misses.clear()
        streamCache.clear()
    }

    override fun onSignInHeader(name: String, value: String, url: String): Boolean {
        if (name.trim().lowercase() != "cookie") return spDc.isNotBlank()
        var dc: String? = null
        var key: String? = null
        for (part in value.split(";")) {
            val p = part.trim()
            when {
                p.startsWith("sp_dc=", ignoreCase = true) -> dc = p.substringAfter("=").trim()
                p.startsWith("sp_key=", ignoreCase = true) -> key = p.substringAfter("=").trim()
            }
        }
        if (dc.isNullOrBlank()) return spDc.isNotBlank()
        spDc = dc
        spKey = key.orEmpty()
        host.storage.set("sp_dc", dc)
        host.storage.set("sp_key", key?.takeIf { it.isNotBlank() })
        SpotifyWeb.forgetToken()
        dealer.reset()
        return true
    }

    // the cookie is the opt-in
    override fun accountState(): AccountState {
        if (spDc.isBlank()) return AccountState.SignedOut
        host.storage.get("account")?.let { return AccountState.SignedIn(it) }
        val reason = host.storage.get("unreachable")
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (reason != null && System.currentTimeMillis() - markedAt < UnreachableRetryMs) {
            AccountState.Expired(reason)
        } else {
            AccountState.SignedIn(SessionOk)
        }
    }

    override suspend fun verifyAccount(): AccountState {
        if (spDc.isBlank()) return AccountState.SignedOut
        val token = SpotifyWeb.accessToken(host, spDc, spKey, forceRefresh = true)
        return if (token != null) {
            host.storage.set("unreachable", null)
            host.storage.set("account", SessionOk)
            AccountState.SignedIn(SessionOk)
        } else {
            dealer.reset()
            val reason = "The sp_dc cookie was rejected by Spotify — sign in again"
            host.storage.set("unreachable", reason)
            host.storage.set("unreachable_at", System.currentTimeMillis().toString())
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        spDc = ""
        spKey = ""
        host.storage.set("sp_dc", null)
        host.storage.set("sp_key", null)
        host.storage.set("account", null)
        host.storage.set("unreachable", null)
        SpotifyWeb.forgetToken()
        dealer.reset()
        matches.clear()
        misses.clear()
        streamCache.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (spDc.isBlank()) {
            report(DiagnosticStep("Account", false, "Not signed in"))
            return ResolveResult.Unavailable("Sign in to Spotify first")
        }

        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on Spotify"))
            return ResolveResult.NotFound("Not on Spotify")
        }

        report(DiagnosticStep("Account", null, "Opening the session…"))
        val token = SpotifyWeb.accessToken(host, spDc, spKey)
        if (token == null) {
            report(DiagnosticStep("Account", false, "The session was refused"))
            markUnreachable("The sp_dc cookie was rejected by Spotify")
            return ResolveResult.Unavailable("The Spotify session was refused — sign in again")
        }
        host.storage.set("unreachable", null)
        report(DiagnosticStep("Account", true, SessionOk))

        report(DiagnosticStep("Find the song", null, "Searching Spotify…"))
        val match = matches[key] ?: searchMatch(token.token, track)
        if (match == null) {
            misses[key] = System.currentTimeMillis()
            report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
            return ResolveResult.NotFound("No matching recording on Spotify")
        }
        matches[key] = match
        report(DiagnosticStep("Find the song", true, "${match.title} · ${match.artist}"))

        streamCache[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.second < StreamTtlMs) return cached.first
            streamCache.remove(key)
        }

        report(DiagnosticStep("Stream", null, "Asking the dealer for the manifest…"))
        val stream = withTimeoutOrNull(ManifestTimeoutMs) { resolveStream(token.token, match) }
        if (stream == null) {
            // the dealer can be momentary; a later song (or retry) reconnects and tries again
            report(DiagnosticStep("Stream", false, "The dealer didn't answer with the manifest"))
            dealer.reset()
            return ResolveResult.Unavailable("Spotify's dealer didn't answer — try again")
        }
        report(DiagnosticStep("Stream", true, "AAC · widevine"))
        val found = ResolveResult.Found(stream)
        streamCache[key] = found to System.currentTimeMillis()
        return found
    }

    // ── the search and the match ──────────────────────────────────────────────────────────────

    internal class Match(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
        val confidence: Float,
    )

    private fun searchMatch(token: String, track: TrackQuery): Match? {
        val query = (listOf(track.title) + track.artists.take(2)).joinToString(" ").trim()
        val candidates = runCatching { SpotifyWeb.searchTracks(host, token, query) }.getOrDefault(emptyList())
        val scored = TrackMatcher.ranked(track, candidates.map { it.toApi() }).firstOrNull() ?: return null
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

    // ── the manifest, the cdn url and the drm ─────────────────────────────────────────────────

    private suspend fun resolveStream(token: String, match: Match): SourceStream? {
        val fileId = dealer.fileIdFor(token, match.id) ?: return null
        val url = SpotifyWeb.storageResolve(host, token, fileId) ?: return null
        return SourceStream(
            url = url,
            format = StreamFormat.Progressive,
            headers = SpotifyWeb.playbackHeaders(),
            drm = StreamDrm.Widevine(licenseServer(), emptyList()),
            sourceLabel = "Spotify",
            qualityLabel = "AAC",
            codec = "AAC",
            matched = MatchedTrack(
                id = match.id,
                title = match.title,
                artist = match.artist,
                album = match.album,
                durationMs = match.durationMs,
                confidence = match.confidence,
            ),
        )
    }

    /** the license trade happens when the player asks, so the token is fetched then, fresh */
    private fun licenseServer(): LicenseServer = LicenseServer { challenge ->
        val token = SpotifyWeb.accessToken(host, spDc, spKey)?.token ?: throw java.io.IOException("No Spotify session for the license")
        SpotifyWeb.license(host, token, challenge)
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    /**
     * The dealer half: one socket, registered once, reused for every song. A dropped socket
     * resets it; the next song registers again.
     */
    private inner class DealerSession {
        @Volatile private var socket: DealerSocket? = null
        @Volatile private var registered = false
        private val fileIds = ConcurrentHashMap<String, String>()
        private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
        private val lock = Any()

        private fun onDealerMessage(message: String) {
            for ((uri, fileId) in SpotifyWeb.fileIdsOf(message)) {
                fileIds[uri] = fileId
                val id = uri.removePrefix("spotify:track:")
                fileIds[id] = fileId
                listOf(uri, id).forEach { key -> pending.remove(key)?.complete(fileId) }
            }
        }

        private fun onDealerDropped() {
            synchronized(lock) { registered = false }
        }

        fun reset() {
            synchronized(lock) {
                runCatching { socket?.close() }
                socket = null
                registered = false
                pending.values.forEach { it.cancel() }
                pending.clear()
            }
        }

        /** the file id for a track: the cached one, or the one a fresh play command brings */
        suspend fun fileIdFor(token: String, trackId: String): String? {
            fileIds[trackId]?.let { return it }
            synchronized(lock) {
                fileIds[trackId]?.let { return it }
                if (!registered) {
                    val connId = connect(token) ?: return null
                    if (!SpotifyWeb.registerDevice(host, token, deviceId, connId)) return null
                    SpotifyWeb.joinCluster(host, token, deviceId, connId)
                    registered = true
                }
            }
            val deferred = pending.getOrPut(trackId) { CompletableDeferred() }
            try {
                if (!SpotifyWeb.requestManifest(host, token, deviceId, "spotify:track:$trackId")) return null
                val fileId = withTimeoutOrNull(ManifestWaitMs) { deferred.await() }
                if (fileId != null) fileIds[trackId] = fileId
                return fileId
            } finally {
                pending.remove(trackId, deferred)
            }
        }

        private fun connect(token: String): String? {
            runCatching { socket?.close() }
            val sock = DealerSocket(onMessage = ::onDealerMessage, onDropped = ::onDealerDropped)
            socket = sock
            val connId = runCatching { sock.connect(SpotifyWeb.dealerHost(), token) }.getOrNull()
            if (connId == null) socket = null
            return connId
        }
    }

    private val dealer = DealerSession()

    private val matches = ConcurrentHashMap<String, Match>()
    private val misses = ConcurrentHashMap<String, Long>()
    // a resolved stream is only good until the cdn's signature runs out, so it isn't kept long
    private val streamCache = ConcurrentHashMap<String, Pair<ResolveResult.Found, Long>>()

    companion object {
        const val Id = "spotify"
        const val FieldSpDc = "sp_dc"
        private const val SessionOk = "Web player session"
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        // the whole walk shares the source walk's twelve-second budget
        private const val ManifestTimeoutMs = 9_000L
        private const val ManifestWaitMs = 8_000L
        // spotify's cdn urls are signed for about an hour; fifteen minutes is safely inside it
        private const val StreamTtlMs = 15L * 60 * 1000
    }
}
