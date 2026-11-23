/*
 * The web player's own endpoints, as Stopify walks them
 * (github.com/Anattana-Labs/Stopify, data/SpotifyWebClient.kt and SpotifyAuthService.kt):
 * the TOTP-signed access token behind the sp_dc cookie, the Pathfinder search, the dealer
 * registration and play command that answer with the track's manifest, storage-resolve's cdn
 * url, and the Widevine license trade.
 */

package com.mochyy.mochymusic.module.spotify

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.ModuleHost
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal object SpotifyWeb {
    const val UserAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"
    private const val AppPlatform = "WebPlayer"
    private const val AppVersion = "1.3.6.121.g2b2ea800fe12"
    private const val ClientToken =
        "AAGCCRiw7WlW3Oy33uN5L1dI2knSs986nqFaI1YTbq66MFLs0oeinjyYUbv2zo9+9kleXdls6EnujpM6OElxp5zJtj+0ZP9RU9I0ApWvFeXJwZRYmCVMWBngB3AN6Ux1imVm+8ImYf9OvVc3mJJBjxLH4S3k77qOsXB6EekBL9hZQgx8umkPOLZZEfolSOP8wURVuQX5sZjnwIfPmLf/8ydY/LeeqBGTvkyZwV4InPyVxMlOI7DQViCkvmXQ2ZkpQASm2NrJ40wdF+BcpIDZV0YA+czHR2bx2K+tTKIaNMa7GUTB/By4+ls8IO6TL4zLlLoKGWnXa8N0xzthzHW9B2zdsW/kTD7JttelP1GHt/1lDuiRvvqtp2WGx6ZeArHvStJTLeO+sZHGs6IxJBY="

    private const val TokenEndpoint = "https://open.spotify.com/api/token"
    private const val ServerTimeEndpoint = "https://open.spotify.com/api/server-time"
    private const val NuancesGist = "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
    private const val LegacyTokenEndpoint = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
    private const val Pathfinder = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val RegisterDevice = "https://gae2-spclient.spotify.com/track-playback/v1/devices"
    private const val ClusterMember = "https://gae2-spclient.spotify.com/connect-state/v1/devices/hobs_"
    private const val StorageResolve = "https://gae2-spclient.spotify.com/storage-resolve/v2/files/audio/interactive/10"
    private const val LicenseEndpoint = "https://gae2-spclient.spotify.com/widevine-license/v1/audio/license"
    private const val DealerHost = "dealer.spotify.com"
    private const val SpClientHost = "gae2-spclient.spotify.com"

    // the persisted-query hash of the web player's searchTopResultsList, the one Stopify sends
    private const val SearchHash = "dd78eaff943eba629ed70ee25517b9cea0dcaa41193e2592ae2727660b21892c"

    private val json = Json { ignoreUnknownKeys = true }

    /** the headers every spclient call carries, as the web player sends them */
    fun clientHeaders(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "client-token" to ClientToken,
        "app-platform" to AppPlatform,
        "spotify-app-version" to AppVersion,
        "User-Agent" to UserAgent,
        "Origin" to "https://open.spotify.com",
        "Referer" to "https://open.spotify.com/",
    )

    /** the headers the audio cdn and the license server ask for */
    fun playbackHeaders() = mapOf(
        "User-Agent" to UserAgent,
        "Origin" to "https://open.spotify.com",
        "Referer" to "https://open.spotify.com/",
        "app-platform" to AppPlatform,
    )

    class AccessToken(val token: String, val anonymous: Boolean, val expiresAtMs: Long)

    /**
     * The access token of the signed-in web player: the TOTP secret of the week (a community
     * gist, the same one Stopify reads) signed against the dealer's own clock, sent with the
     * sp_dc cookie. Falls back to the unsigned endpoint, as Stopify does.
     */
    fun accessToken(host: ModuleHost, spDc: String, spKey: String, forceRefresh: Boolean = false): AccessToken? {
        CachedToken?.let { if (!forceRefresh && !it.anonymous && System.currentTimeMillis() < it.expiresAtMs) return it }
        runCatching { tokenViaTotp(host, spDc, spKey) }.getOrNull()?.let {
            CachedToken = it
            return it
        }
        runCatching { tokenViaLegacyEndpoint(host, spDc, spKey) }.getOrNull()?.let {
            CachedToken = it
            return it
        }
        return null
    }

    @Volatile private var CachedToken: AccessToken? = null

    fun forgetToken() {
        CachedToken = null
    }

    private fun tokenViaTotp(host: ModuleHost, spDc: String, spKey: String): AccessToken? {
        // 1. the rotating TOTP secrets, highest version wins
        val gist = host.http(HttpRequest(NuancesGist, headers = mapOf("User-Agent" to UserAgent, "Accept" to "application/json")))
        if (!gist.ok) return null
        val gistBody = json.parseToJsonElement(gist.text) as? JsonObject ?: return null
        val files = gistBody["files"] as? JsonObject ?: return null
        val (_, file) = files.entries.firstOrNull() ?: return null
        val content = ((file as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull ?: return null
        val nuances = json.parseToJsonElement(content) as? JsonArray ?: return null
        var secret = ""
        var version = 0L
        for (element in nuances) {
            val obj = element as? JsonObject ?: continue
            val v = obj.long("v") ?: 0L
            if (v >= version) {
                version = v
                secret = obj.string("s") ?: ""
            }
        }
        if (secret.isBlank()) return null

        // 2. the server's clock, so the code is the one spotify expects
        val serverTimeSec = runCatching {
            val time = host.http(HttpRequest(ServerTimeEndpoint, headers = mapOf("User-Agent" to UserAgent)))
            (json.parseToJsonElement(time.text) as? JsonObject)?.long("serverTime")
        }.getOrNull() ?: (System.currentTimeMillis() / 1000L)

        // 3. the token, signed
        val totp = totp(secret, serverTimeSec)
        val cookie = if (spKey.isNotBlank()) "sp_dc=$spDc; sp_key=$spKey" else "sp_dc=$spDc"
        val body = host.http(
            HttpRequest(
                "$TokenEndpoint?reason=transport&productType=web-player&totp=$totp&totpServer=$totp&totpVer=$version",
                headers = mapOf("User-Agent" to UserAgent, "Cookie" to cookie, "Accept" to "application/json"),
            ),
        )
        if (!body.ok) return null
        val obj = json.parseToJsonElement(body.text) as? JsonObject ?: return null
        val token = obj.string("accessToken")?.takeIf { it.isNotBlank() } ?: return null
        val anonymous = obj.primitive("isAnonymous")?.contentOrNull == "true"
        if (anonymous) return null // the cookie was refused; an anonymous token can't play anything
        val expiresAt = obj.long("accessTokenExpirationTimestampMs")
            ?: (System.currentTimeMillis() + 3_600_000L)
        return AccessToken(token, false, expiresAt - 60_000L)
    }

    private fun tokenViaLegacyEndpoint(host: ModuleHost, spDc: String, spKey: String): AccessToken? {
        val cookie = if (spKey.isNotBlank()) "sp_dc=$spDc; sp_key=$spKey" else "sp_dc=$spDc"
        val body = host.http(
            HttpRequest(
                LegacyTokenEndpoint,
                headers = mapOf(
                    "User-Agent" to UserAgent,
                    "Cookie" to cookie,
                    "Accept" to "application/json",
                    "app-platform" to AppPlatform,
                    "spotify-app-version" to AppVersion,
                ),
            ),
        )
        if (!body.ok) return null
        val obj = json.parseToJsonElement(body.text) as? JsonObject ?: return null
        val token = obj.string("accessToken")?.takeIf { it.isNotBlank() } ?: return null
        if (obj.primitive("isAnonymous")?.contentOrNull == "true") return null
        val expiresAt = obj.long("accessTokenExpirationTimestampMs")
            ?: (System.currentTimeMillis() + 3_600_000L)
        return AccessToken(token, false, expiresAt - 60_000L)
    }

    /** the six-digit HMAC-SHA1 TOTP the token endpoint asks for, off the base32 secret */
    private fun totp(base32Secret: String, serverTimeSec: Long): String {
        val key = base32Decode(base32Secret)
        var step = serverTimeSec / 30L
        val time = ByteArray(8)
        for (i in 7 downTo 0) {
            time[i] = (step and 0xFF).toByte()
            step = step shr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(time)
        val offset = hash.last().toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        return String.format(java.util.Locale.US, "%06d", code % 1_000_000)
    }

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")
        val output = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    // ── the catalog ───────────────────────────────────────────────────────────────────────────

    /** the web player's own search, the tracks it would list first */
    fun searchTracks(host: ModuleHost, token: String, query: String, limit: Int = 20): List<CatalogTrack> {
        val payload = buildJsonObject {
            put(
                "variables",
                buildJsonObject {
                    put("query", query)
                    put("limit", limit)
                    put("offset", 0)
                    put("numberOfTopResults", limit)
                    put("includeArtistHasConcertsField", false)
                    put("includeAudiobooks", true)
                    put("includeAuthors", true)
                    put("includePreReleases", true)
                    put("includeAlbumPreReleases", false)
                    put("includeEpisodeContentRatingsV2", true)
                    put("isPrefix", null as String?)
                    put("sectionFilters", buildJsonArray { add("GENERIC"); add("VIDEO_CONTENT") })
                },
            )
            put("operationName", "searchTopResultsList")
            put(
                "extensions",
                buildJsonObject {
                    put("persistedQuery", buildJsonObject { put("version", 1); put("sha256Hash", SearchHash) })
                },
            )
        }
        val response = host.http(
            HttpRequest(
                Pathfinder,
                method = "POST",
                headers = clientHeaders(token),
                body = payload.toString().toByteArray(Charsets.UTF_8),
                contentType = "application/json;charset=UTF-8",
            ),
        )
        if (!response.ok) return emptyList()
        val root = json.parseToJsonElement(response.text) as? JsonObject ?: return emptyList()
        val items = (((((root["data"] as? JsonObject)?.get("searchV2") as? JsonObject)
            ?.get("topResultsV2") as? JsonObject)?.get("itemsV2")) as? JsonArray) ?: return emptyList()
        val tracks = mutableListOf<CatalogTrack>()
        for (element in items) {
            val item = ((element as? JsonObject)?.get("item") as? JsonObject) ?: continue
            if (item.string("__typename") != "TrackResponseWrapper") continue
            val data = item["data"] as? JsonObject ?: continue
            val id = data.string("id")?.takeIf { it.isNotBlank() } ?: continue
            val playable = ((data["playability"] as? JsonObject)?.primitive("playable")?.contentOrNull ?: "true") != "false"
            val artists = (((data["artists"] as? JsonObject)?.get("items")) as? JsonArray)
                ?.mapNotNull { ((it as? JsonObject)?.get("profile") as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) }
                .orEmpty()
            val album = ((data["albumOfTrack"] as? JsonObject)?.string("name"))
            val duration = ((data["duration"] as? JsonObject)?.long("totalMilliseconds"))
            if (playable) {
                tracks.add(
                    CatalogTrack(
                        id = id,
                        title = data.string("name").orEmpty(),
                        artist = artists.joinToString(", "),
                        album = album,
                        durationMs = duration?.takeIf { it > 0 },
                    ),
                )
            }
        }
        return tracks
    }

    // ── the manifest dance ────────────────────────────────────────────────────────────────────

    /**
     * Registers this "device" with the dealer: the websocket's connection id is what ties the
     * two sides together, exactly the registration Stopify's client makes.
     */
    fun registerDevice(host: ModuleHost, token: String, deviceId: String, connectionId: String): Boolean {
        val payload = buildJsonObject {
            put(
                "device",
                buildJsonObject {
                    put("brand", "spotify")
                    put(
                        "capabilities",
                        buildJsonObject {
                            put("change_volume", true)
                            put("enable_play_token", true)
                            put("supports_file_media_type", true)
                            put("play_token_lost_behavior", "pause")
                            put("disable_connect", false)
                            put("audio_podcasts", true)
                            put("video_playback", false)
                            put("manifest_formats", buildJsonArray { add("file_ids_mp4"); add("file_ids_mp4_dual") })
                            put("supports_preferred_media_type", true)
                            put("supports_playback_offsets", true)
                            put("supports_playback_speed", true)
                        },
                    )
                    put("device_id", deviceId)
                    put("device_type", "computer")
                    put("metadata", buildJsonObject { })
                    put("model", "web_player")
                    put("name", "Sora")
                    put("platform_identifier", "web_player osx 10.15.7;chrome 155.0.0.0;desktop")
                    put("is_group", false)
                    put("is_public", true)
                    put("correlation_id", "9167488b53cb6529d696cc87a46a8235")
                    put("client_version", "harmony:4.80.0-3cdf02c91")
                },
            )
            put("outro_endcontent_snooping", false)
            put("connection_id", connectionId)
            put("client_version", "harmony:4.80.0-3cdf02c91")
            put("volume", 65535)
        }
        val response = host.http(
            HttpRequest(
                RegisterDevice,
                method = "POST",
                headers = clientHeaders(token),
                body = payload.toString().toByteArray(Charsets.UTF_8),
                contentType = "application/json;charset=UTF-8",
            ),
        )
        return response.ok
    }

    /** puts this device in the connect cluster, so the dealer starts pushing its state */
    fun joinCluster(host: ModuleHost, token: String, deviceId: String, connectionId: String): Boolean {
        val payload = buildJsonObject {
            put("member_type", "CONNECT_STATE")
            put(
                "device",
                buildJsonObject {
                    put(
                        "device_info",
                        buildJsonObject {
                            put("can_play", true)
                            put("volume", 65535)
                            put("name", "Sora")
                            put("device_id", deviceId)
                            put("device_type", "COMPUTER")
                            put("device_software_version", "1.3.5")
                            put("spirc_version", "3.2.6")
                            put(
                                "capabilities",
                                buildJsonObject {
                                    put("can_be_player", true)
                                    put("gaia_eq_connect_id", true)
                                    put("supports_command_request", true)
                                    put("is_observable", true)
                                    put("volume_steps", 64)
                                    put("supported_types", buildJsonArray { add("audio/episode"); add("audio/track") })
                                },
                            )
                        },
                    )
                },
            )
        }
        val response = host.http(
            HttpRequest(
                ClusterMember + deviceId,
                method = "PUT",
                headers = clientHeaders(token) + mapOf("x-spotify-connection-id" to connectionId),
                body = payload.toString().toByteArray(Charsets.UTF_8),
                contentType = "application/json;charset=UTF-8",
            ),
        )
        return response.ok
    }

    /**
     * The play command that asks the backend to build a state machine for this one track; the
     * manifest it answers with arrives on the dealer websocket, where the module's session is
     * waiting for it.
     */
    fun requestManifest(host: ModuleHost, token: String, deviceId: String, trackUri: String): Boolean {
        val commandId = java.util.UUID.randomUUID().toString().replace("-", "")
        val payload = buildJsonObject {
            put(
                "command",
                buildJsonObject {
                    put(
                        "context",
                        buildJsonObject {
                            put("uri", trackUri)
                            put("url", "context://$trackUri")
                            put("metadata", buildJsonObject { })
                        },
                    )
                    put(
                        "play_origin",
                        buildJsonObject {
                            put("feature_identifier", "auto_play_on_load")
                            put("feature_version", "web-player_2026-10-09_1791504000000_d873ee9db135")
                            put("referrer_identifier", "search")
                        },
                    )
                    put(
                        "options",
                        buildJsonObject {
                            put("license", "tft")
                            put("skip_to", buildJsonObject { })
                            put("player_options_override", buildJsonObject { })
                        },
                    )
                    put(
                        "logging_params",
                        buildJsonObject {
                            put("page_instance_ids", buildJsonArray { })
                            put("interaction_ids", buildJsonArray { })
                            put("command_id", commandId)
                        },
                    )
                    put("endpoint", "play")
                },
            )
        }
        val response = host.http(
            HttpRequest(
                "https://$SpClientHost/connect-state/v1/player/command/from/$deviceId/to/$deviceId",
                method = "POST",
                headers = clientHeaders(token),
                body = payload.toString().toByteArray(Charsets.UTF_8),
                contentType = "application/json;charset=UTF-8",
            ),
        )
        return response.ok
    }

    /** one dealer message, every payload of it: the file ids a state machine carries */
    fun fileIdsOf(message: String): Map<String, String> {
        val root = runCatching { json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return emptyMap()
        val found = mutableMapOf<String, String>()
        for (payloadElement in (root["payloads"] as? JsonArray ?: emptyList())) {
            // a payload is the state machine itself, or the same json base64-encoded
            val payloadText = when (payloadElement) {
                is JsonObject -> payloadElement.toString()
                is JsonPrimitive -> payloadElement.contentOrNull?.let { decodeBase64Json(it) } ?: continue
                else -> continue
            }
            val payload = runCatching { json.parseToJsonElement(payloadText) as? JsonObject }.getOrNull() ?: continue
            val machine = payload["state_machine"] as? JsonObject ?: continue
            for (trackElement in (machine["tracks"] as? JsonArray ?: emptyList())) {
                val track = trackElement as? JsonObject ?: continue
                val metadata = track["metadata"] as? JsonObject ?: continue
                val uri = metadata.string("uri")?.takeIf { it.startsWith("spotify:track:") } ?: continue
                val manifest = track["manifest"] as? JsonObject ?: continue
                val fileId = (((manifest["file_ids_mp4"] as? JsonArray)
                    ?.firstOrNull() as? JsonObject)?.string("file_id"))?.takeIf(String::isNotBlank) ?: continue
                found[uri] = fileId
                found[uri.removePrefix("spotify:track:")] = fileId
            }
        }
        return found
    }

    private fun decodeBase64Json(text: String): String? {
        val trimmed = text.trim()
        // a payload straight as json, or the same json base64-encoded — the dealer sends both
        if (trimmed.startsWith("{")) return trimmed
        return runCatching {
            val bytes = java.util.Base64.getDecoder().decode(trimmed)
            val decoded = String(bytes, Charsets.UTF_8).trim()
            decoded.takeIf { it.startsWith("{") }
        }.getOrNull()
    }

    /** the cdn url a file id plays from */
    fun storageResolve(host: ModuleHost, token: String, fileId: String): String? {
        val response = host.http(
            HttpRequest(
                "$StorageResolve/$fileId?version=10000000&product=9&platform=39&alt=json",
                headers = clientHeaders(token),
            ),
        )
        if (!response.ok) return null
        val root = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        return (root["cdnurl"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull { it.isNotBlank() }
    }

    /** a cdm challenge traded for a widevine license, on the player's drm thread */
    fun license(host: ModuleHost, token: String, challenge: ByteArray): ByteArray =
        host.http(
            HttpRequest(
                LicenseEndpoint,
                method = "POST",
                headers = clientHeaders(token),
                body = challenge,
                contentType = "application/octet-stream",
            ),
        ).body

    fun dealerHost() = DealerHost

    // ── small json helpers over kotlinx.serialization ─────────────────────────────────────────

    internal fun JsonObject.string(name: String): String? = primitive(name)?.contentOrNull

    internal fun JsonObject.long(name: String): Long? =
        primitive(name)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    internal fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive
}

/** a catalog track, shaped for the api's matcher */
internal data class CatalogTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
) {
    fun toApi() = com.mochyy.mochymusic.module.CatalogTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
    )
}
