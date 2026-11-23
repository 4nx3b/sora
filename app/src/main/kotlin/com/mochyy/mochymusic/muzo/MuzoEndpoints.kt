// The rest of Muzo Data's documented API (data.kouzu.in/docs.md, llm.txt): the combined /stream answer, /metadata,
// the /ytm/next and /ytm/autoqueue radio queue, and the open /stream/add ingest queue. A 429 holds every keyed call
// until the quota resets at 00:00 UTC.

package com.mochyy.mochymusic.muzo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request

/** what each Muzo Data endpoint group is allowed to do, set from the plugin's own switches */
object MuzoPrefs {
    @Volatile var stream = true
    @Volatile var lyrics = true
    @Volatile var canvas = true
    @Volatile var metadata = false
    @Volatile var autoqueue = false
    @Volatile var catalogAdd = true
    @Volatile var format = MuzoFormat.Lossless
}

enum class MuzoFormat(val id: String) { Lossless("lossless"), Aac("aac"), Auto("auto") }

data class MuzoStreams(val losslessUrl: String?, val m4aUrl: String?, val canvasUrl: String?)

data class MuzoTrack(val id: String, val title: String, val artists: List<String>, val album: String?, val durationSec: Int, val thumbnail: String?)

data class MuzoQueuePage(val tracks: List<MuzoTrack>, val continuation: String?)

data class MuzoAddResult(val queued: List<String>, val processing: List<String>, val cached: List<String>)

object MuzoEndpoints {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var limitedUntil = 0L

    fun limited(now: Long = System.currentTimeMillis()): Boolean = now < limitedUntil

    fun hitLimit(now: Long = System.currentTimeMillis()) {
        limitedUntil = (now / DayMs + 1) * DayMs
    }

    private fun keyed(path: String, key: String): String? {
        if (key.isBlank() || limited()) return null
        val (code, body) = MuzoService.fetch(path, key) ?: return null
        if (code == 429) hitLimit()
        return body.takeIf { code == 200 }
    }

    suspend fun stream(key: String, id: String): MuzoStreams? = withContext(Dispatchers.IO) {
        keyed("/stream?id=${MuzoService.encode(id)}", key)?.let(::parseStreams)
    }

    suspend fun aac(key: String, id: String): MuzoService.LosslessAnswer? = MuzoService.lossless(key, id, "/stream/aac")

    suspend fun metadata(key: String, videoId: String): MuzoTrack? = withContext(Dispatchers.IO) {
        if (!MuzoPrefs.metadata) return@withContext null
        keyed("/metadata?videoId=${MuzoService.encode(videoId)}", key)?.let { body -> obj(body)?.let { track(it, videoId) } }
    }

    suspend fun next(key: String, id: String, limit: Int = 25): MuzoQueuePage? = withContext(Dispatchers.IO) {
        if (!MuzoPrefs.autoqueue) return@withContext null
        keyed("/ytm/next?id=${MuzoService.encode(id)}&limit=$limit", key)?.let(::parseQueue)
    }

    suspend fun autoqueue(key: String, id: String, limit: Int = 25, exclude: Collection<String> = emptyList(), continuation: String? = null): MuzoQueuePage? =
        withContext(Dispatchers.IO) {
            val path = buildString {
                append("/ytm/autoqueue?id=").append(MuzoService.encode(id)).append("&limit=").append(limit.coerceIn(1, 200))
                exclude.take(MaxExclude).takeIf { it.isNotEmpty() }?.let { append("&exclude=").append(MuzoService.encode(it.joinToString(","))) }
                continuation?.let { append("&continuation=").append(MuzoService.encode(it)) }
            }
            keyed(path, key)?.let(::parseQueue)
        }

    suspend fun addToCatalog(ids: Collection<String>): MuzoAddResult? = withContext(Dispatchers.IO) {
        val batch = ids.filter { it.length == 11 }.distinct().take(MaxAdd)
        if (batch.isEmpty()) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url("https://data.kouzu.in/api/v1/stream/add?id=${MuzoService.encode(batch.joinToString(","))}")
                .header("Accept", "application/json")
                .build()
            MuzoService.client.newCall(request).execute().use { r -> r.body?.string()?.takeIf { r.code == 200 } }
        }.getOrNull()?.let(::parseAdd)
    }

    fun parseStreams(body: String): MuzoStreams? = obj(body)?.let {
        MuzoStreams(it.str("losslessUrl"), it.str("m4aUrl"), it.str("canvasUrl")).takeIf { s -> s.losslessUrl != null || s.m4aUrl != null }
    }

    fun parseQueue(body: String): MuzoQueuePage? = obj(body)?.let { root ->
        val list = (root["tracks"] ?: root["items"]) as? JsonArray ?: return@let null
        MuzoQueuePage(list.mapNotNull { (it as? JsonObject)?.let { t -> track(t, null) } }, root.str("continuation"))
    }

    fun parseAdd(body: String): MuzoAddResult? = obj(body)?.let {
        MuzoAddResult(it.ids("queued"), it.ids("processing"), it.ids("cached"))
    }

    private fun track(o: JsonObject, fallbackId: String?): MuzoTrack? {
        val id = o.str("videoId") ?: o.str("id") ?: fallbackId ?: return null
        val title = o.str("title") ?: return null
        val artists = when (val a = o["artists"] ?: o["artist"]) {
            is JsonArray -> a.mapNotNull { (it as? JsonObject)?.str("name") ?: (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(a.contentOrNull)
            is JsonObject -> listOfNotNull(a.str("name"))
            else -> emptyList()
        }
        val album = when (val a = o["album"]) {
            is JsonObject -> a.str("name") ?: a.str("title")
            is JsonPrimitive -> a.contentOrNull
            else -> null
        }
        val thumbs = o["thumbnails"] as? JsonArray
        val thumb = (thumbs?.lastOrNull() as? JsonObject)?.str("url") ?: o.str("thumbnail")
        return MuzoTrack(id, title, artists, album, seconds(o["duration"] ?: o["durationSeconds"]), thumb)
    }

    private fun seconds(e: JsonElement?): Int {
        val p = e as? JsonPrimitive ?: return 0
        p.intOrNull?.let { return it }
        val parts = p.contentOrNull?.split(':')?.mapNotNull { it.toIntOrNull() } ?: return 0
        return parts.fold(0) { acc, n -> acc * 60 + n }
    }

    private fun obj(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.ids(name: String): List<String> = (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private const val DayMs = 86_400_000L
    private const val MaxAdd = 20
    private const val MaxExclude = 200
}
