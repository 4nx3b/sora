// The addon system, ported from Neiro's data/sources/addon: an addon is an
// ordinary HTTP server, not code — it answers /manifest.json, /search?q= and
// /stream/{id} with JSON, and nothing is ever executed on this side. The wire
// shapes and the walk position (user-added sources first, after Muzo, before
// the installed modules) are Neiro's; the stored list lives in sora's sealed
// settings store, one URL per line.

package com.mochyy.mochymusic.muzo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AddonSources {

    /** the stored addon addresses, one per line */
    val AddonUrlsKey = stringPreferencesKey("addonSources")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── the wire shapes (Neiro's AddonModels, the fields that are read) ──

    @Serializable
    data class AddonManifest(
        val id: String = "",
        val name: String = "",
        val version: String = "",
        val resources: List<String> = emptyList(),
        val description: String? = null,
    )

    @Serializable
    private data class AddonSearchResponse(val tracks: List<AddonTrack> = emptyList())

    @Serializable
    data class AddonTrack(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        /** Seconds. Read as a double so an addon sending `240.0` is not a parse failure. */
        val duration: Double? = null,
        val artworkURL: String? = null,
    )

    @Serializable
    data class AddonStream(
        val url: String = "",
        /** Free text: `mp3`, `flac`, `dash`. Describes the audio, not the transport. */
        val format: String = "",
        val quality: String = "",
        val streamQuality: String = "",
        val audioQuality: String = "",
        val codec: String? = null,
        /** `none`, `hls` or `dash` — what the URL points at. */
        val manifest: String? = null,
    ) {
        val qualityLabel: String
            get() = quality.ifBlank { streamQuality }.ifBlank { audioQuality }.ifBlank { format }.ifBlank { "audio" }
        val isHls: Boolean get() = manifest.equals("hls", ignoreCase = true)
        val isDash: Boolean get() = manifest.equals("dash", ignoreCase = true)
    }

    // ── the stored list ──

    suspend fun urls(context: Context): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.dataStore.data.first()[AddonUrlsKey].orEmpty()
                .lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
        }.getOrDefault(emptyList())
    }

    suspend fun setUrls(context: Context, urls: List<String>) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            val clean = urls.map(String::trim).filter(String::isNotEmpty).distinct()
            if (clean.isEmpty()) prefs.remove(AddonUrlsKey) else prefs[AddonUrlsKey] = clean.joinToString("\n")
        }
    }

    // ── the protocol ──

    private fun endpoint(base: String, segments: List<String>, params: Map<String, String> = emptyMap()): String? {
        val url = (if (base.endsWith("/")) base.dropLast(1) else base).toHttpUrlOrNull() ?: return null
        val builder = url.newBuilder()
        segments.forEach(builder::addPathSegment)
        params.forEach { (k, v) -> if (v.isNotBlank()) builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private suspend fun <T> fetch(url: String, of: kotlinx.serialization.KSerializer<T>): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
                    .execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = response.body?.string().orEmpty()
                        json.decodeFromString(of, body)
                    }
            }
        }

    suspend fun manifest(base: String): Result<AddonManifest> {
        val url = endpoint(base, listOf("manifest.json")) ?: return Result.failure(IllegalArgumentException("bad address"))
        return fetch(url, AddonManifest.serializer())
    }

    suspend fun search(base: String, query: String): Result<List<AddonTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val url = endpoint(base, listOf("search"), mapOf("q" to trimmed))
            ?: return Result.failure(IllegalArgumentException("bad address"))
        return fetch(url, AddonSearchResponse.serializer()).map { it.tracks }
    }

    /**
     * A playable URL for one of this addon's own track ids. The id goes in as a
     * path segment rather than being interpolated into the string: an addon's
     * ids are whatever its backend uses, and one holding a `/` or a `?` would
     * otherwise rewrite the request into a different endpoint entirely.
     */
    suspend fun stream(base: String, trackId: String): Result<AddonStream> {
        val url = endpoint(base, listOf("stream", trackId))
            ?: return Result.failure(IllegalArgumentException("bad address"))
        return fetch(url, AddonStream.serializer())
    }

    /**
     * The resolver's question: does this addon have the song? Neiro's sources
     * match by name; the port asks the same search and takes the first row
     * whose artist overlaps, so a different cut of the same song still counts.
     */
    suspend fun findTrack(base: String, title: String, artist: String): AddonTrack? {
        val rows = search(base, "$artist $title").getOrNull() ?: return null
        val loweredTitle = title.lowercase()
        val loweredArtist = artist.lowercase()
        return rows.firstOrNull { row ->
            row.title.lowercase().contains(loweredTitle) || loweredArtist in row.artist.lowercase()
        } ?: rows.firstOrNull()
    }
}
