// Muzo Data (data.kouzu.in), ported from Neiro's data/sources/MuzoSource.kt: the
// same wire protocol (one X-API-Key header on every JSON endpoint), the same
// three answers (usage check, the lossless stream for one video id) and the same
// failure shapes (Rejected / Unreachable), folded into nulls and states rather
// than thrown because every caller is mid-walk through other sources.

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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * What the Check API call came back with, as Neiro's Sources screen words it: the
 * numbers ride separately from the verdict sentence, and [Ok] is the one case
 * with anything to format.
 */
sealed interface MuzoApiCheck {
    data class Ok(val usageToday: Int, val dailyLimit: Int, val streamLimit: String?) : MuzoApiCheck

    /** The key was refused: 401/403 territory. Not worth retrying with the same key. */
    data class Rejected(val reason: String) : MuzoApiCheck

    /** The server could not be reached at all. Worth retrying later. */
    data class Unreachable(val reason: String) : MuzoApiCheck
}

object MuzoService {

    private const val BASE = "https://data.kouzu.in/api/v1"

    /** The one sentence a keyless state says. */
    const val NO_KEY_PROMPT = "API key required"

    private const val STREAMING_UNLIMITED = "unlimited"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Tighter than the shared client's stream-oriented timeouts, after Neiro's
    // MuzoService: this sits inside the playback resolver's walk, which wants an
    // answer in seconds.
    internal val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build()

    /** One GET done, or null for any failure at all. */
    internal fun fetch(path: String, key: String): Pair<Int, String?>? = runCatching {
        val request = Request.Builder()
            .url("$BASE$path")
            .header("Accept", "application/json")
            .header("X-API-Key", key)
            .build()
        client.newCall(request).execute().use { response ->
            response.code to response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    /** The one error string a refused answer carries, when it says nothing better. */
    private fun reasonOf(code: Int, body: String?): String =
        body?.let { runCatching { json.decodeFromString<ErrorAnswer>(it).error }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: "HTTP $code"

    /** Query-component encoding without the `+`-for-space behaviour URLEncoder adds. */
    internal fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** The key set in Settings, or null while Muzo Data is unconfigured. */
    suspend fun apiKey(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.dataStore.data.first()[MuzoApiKey]?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Saves (or, blank, forgets) the key. */
    suspend fun setApiKey(context: Context, key: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            if (key.isBlank()) prefs.remove(MuzoApiKey) else prefs[MuzoApiKey] = key
        }
    }

    /**
     * Asks `/api/v1/user/usage` what today looks like — the one question the
     * Check row asks, and the one a healthy key answers with numbers.
     */
    suspend fun checkApi(key: String): MuzoApiCheck = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext MuzoApiCheck.Rejected(NO_KEY_PROMPT)
        val (code, body) = fetch("/user/usage", key)
            ?: return@withContext MuzoApiCheck.Unreachable("Failed")
        when {
            code == 200 -> {
                val usage = body?.let {
                    runCatching { json.decodeFromString<UsageAnswer>(it) }.getOrNull()
                }
                // An unparseable success is still a success as far as the key is
                // concerned; reported without the numbers it declined to state.
                usage ?: return@withContext MuzoApiCheck.Ok(0, 0, null)
                MuzoApiCheck.Ok(
                    usageToday = usage.usageToday ?: 0,
                    dailyLimit = usage.dailyLimit ?: 0,
                    streamLimit = usage.streamLimit?.takeIf { it.isNotBlank() },
                )
            }
            // 401 is the documented refusal; 403 kept in case the front door words a bad key that way one day.
            code == 401 || code == 403 -> MuzoApiCheck.Rejected(reasonOf(code, body))
            else -> MuzoApiCheck.Unreachable(reasonOf(code, body))
        }
    }

    /**
     * `/api/v1/stream/lossless` for one video id: the CDN url the player opens.
     * The id is the whole contract — every other source matches by a name and can
     * hand back a different cut of the same song; this one is asked for a video
     * id and answers for that exact recording, which is also why its answer is
     * worth a 24-bit FLAC.
     */
    suspend fun lossless(key: String, videoId: String, path: String = "/stream/lossless"): LosslessAnswer? =
        withContext(Dispatchers.IO) {
            if (key.isBlank() || videoId.isBlank() || MuzoEndpoints.limited()) return@withContext null
            val (code, body) = fetch("$path?id=${encode(videoId)}", key)
                ?: return@withContext null
            if (code == 429) MuzoEndpoints.hitLimit()
            if (code != 200) return@withContext null
            val answer = body?.let {
                runCatching { json.decodeFromString<LosslessAnswer>(it) }.getOrNull()
            } ?: return@withContext null
            val url = answer.streamUrl?.takeIf { it.isNotBlank() }
                ?: answer.url?.takeIf { it.isNotBlank() }
                ?: answer.urls?.firstOrNull { !it.isNullOrBlank() }
            url?.let { answer.copy(url = it, quality = answer.quality ?: answer.bitrate) }
        }

    /** The sentence the settings row shows for a working key. */
    fun usageDetail(check: MuzoApiCheck.Ok): String =
        "${check.usageToday}/${check.dailyLimit} requests today · streaming ${check.streamLimit ?: STREAMING_UNLIMITED}"

    @Serializable
    private data class ErrorAnswer(val error: String? = null)

    @Serializable
    private data class UsageAnswer(
        val status: String? = null,
        val usageToday: Int? = null,
        val dailyLimit: Int? = null,
        val remainingToday: Int? = null,
        val streamLimit: String? = null,
    )

    @Serializable
    data class LosslessAnswer(
        val id: String? = null,
        val format: String? = null,
        val quality: String? = null,
        val cdnBaseUrl: String? = null,
        val cdnInstances: List<String>? = null,
        val url: String? = null,
        val urls: List<String>? = null,
        val streamUrl: String? = null,
        val bitrate: String? = null,
        val cdnInstance: String? = null,
    )
}

/** where the key lives; sora's data store seals its secrets on disk */
val MuzoApiKey = stringPreferencesKey("muzoApiKey")
