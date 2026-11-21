// MuzoLyricsProvider.kt
//
// Lyrics from Muzo Data (data.kouzu.in), ported from Neiro's MuzoLyrics (shared module): the one
// provider keyed on the video id itself, so it can never hand back a different edit of the song.
// Its syllable runs carry word-internal timing — the sweep lights "e" before "nough" — and a run's
// own trailing space is its word boundary, the exact spacing convention sora's word-merge was
// written to read: consecutive timed pieces with nothing between them in the line text merge into
// one word timed start-to-end, while the pieces themselves stay as the syllables the karaoke
// styles sweep. The answer is emitted as rich-sync LRC, so the same parser every provider's
// output goes through turns it into words and syllables.
//
// The API key is read live from the settings cache at each call — Neiro's pushed-in volatile,
// with the push points removed because sora's PreferencesCache already keeps every preference
// current without them: a key typed into the Muzo Data editor takes effect on the next request.
// Keyless lyrics were also live when Neiro was written (and still are today): the key rides the
// request whenever one is set, and a refusal either way is simply a miss the chain steps over.

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.mochyy.mochymusic.muzo.MuzoApiKey
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.followAppProxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object MuzoLyricsProvider : LyricsProvider {
    override val name = "Muzo"

    // The credential is the opt-in for the lossless source, not for this provider: lyrics answer
    // keyless today, so the provider is always in the chain and the key only makes it surer.
    override fun isEnabled(context: Context): Boolean = true

    private const val BASE = "https://data.kouzu.in/api/v1/lyrics"

    // Shorter instrumental breaks aren't worth interrupting the line for — Neiro's MIN_GAP_MS,
    // which sora's own interlude constant (MinInterludeGapMs) also says as 4_000.
    private const val MinGapMs = 4_000L

    // The same tight timeouts Neiro's lyric clients sit behind: this rides a fan-out with other
    // providers still to be asked, so it wants an answer in seconds.
    private val client = OkHttpClient.Builder()
        .followAppProxy()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext Result.failure(IllegalStateException("no id"))
        val request = Request.Builder()
            .url("$BASE?id=$id")
            .header("Accept", "application/json")
            .apply {
                // Sent whenever one is set, exactly as Neiro's lyricsGetBearer does; a blank key
                // asks keyless, which the service answers the same way today.
                PreferencesCache.snapshot.value[MuzoApiKey]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("unreachable"))
        val response = runCatching { json.decodeFromString<MuzoResponse>(body) }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("unparseable"))
        if (response.isEmpty || response.lines.isNullOrEmpty()) {
            return@withContext Result.failure(IllegalStateException("empty"))
        }
        val lrc = response.toLrc()
        if (lrc.isBlank()) Result.failure(IllegalStateException("no lines")) else Result.success(lrc)
    }

    /** [mm:ss.SSS] with the milliseconds sora's rich-sync parser reads at full width. */
    private fun stamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val minutes = total / 60_000
        val seconds = (total % 60_000) / 1_000
        val millis = total % 1_000
        return "%02d:%02d.%03d".format(minutes, seconds, millis)
    }

    /**
     * One response as rich-sync LRC. Each syllable run becomes one timed piece carrying its own
     * trailing space, so runs of one word arrive glued ("e" "nough ") and the parser merges them
     * into the word while keeping the pieces as its syllables.
     *
     * Instrumental stretches are marked the way an LRC file marks them, a bare "♪" line — the
     * shape of Neiro's blank [LyricLine] gap markers, which its page draws as a note: the marker
     * sits at the *sung* end of the line before it (start + durationMs, Neiro's hasKnownEnd), so
     * the last word's own end — which the parser takes from the next line's stamp — lands where
     * the singing stops rather than several seconds later at the next line's due time, and the
     * break is only drawn where the line says when it stopped, never stamp-to-stamp.
     */
    private fun MuzoResponse.toLrc(): String {
        // one emitted line: its stamp, its text, and where its singing stops (null = unsaid)
        data class Out(val stampMs: Long, val text: String, val sungEndMs: Long?)

        val out = lines
            .orEmpty()
            .mapNotNull { line ->
                val start = line.atMs ?: return@mapNotNull null
                val runs = line.syllables.orEmpty().mapNotNull { syllable ->
                    val text = syllable.text ?: return@mapNotNull null
                    val at = syllable.atMs ?: return@mapNotNull null
                    Triple(at, at + (syllable.durationMs ?: 0L), text)
                }
                when {
                    runs.isNotEmpty() -> {
                        // the line's stamp is the earlier of its own and its first run's, the
                        // same start Neiro's mapping takes (minOf(start, first word's start))
                        val stampMs = minOf(start, runs.first().first)
                        val text = buildString {
                            append('[').append(stamp(stampMs)).append(']')
                            runs.forEach { (at, _, run) ->
                                append('<').append(stamp(at)).append('>').append(run)
                            }
                        }
                        Out(stampMs, text, line.durationMs?.takeIf { it > 0 }?.let { start + it })
                    }
                    // A line-synced answer from here is no better than LRCLIB's, but it is still
                    // better than nothing — the same call Neiro's mapping makes on its plain LRC.
                    !line.text.isNullOrBlank() ->
                        Out(start, "[${stamp(start)}]${line.text.trim()}", line.durationMs?.takeIf { it > 0 }?.let { start + it })
                    else -> null
                }
            }
            .sortedBy { it.stampMs }
        if (out.isEmpty()) return ""

        return buildString {
            // the run-up to the first line gets its own break (Neiro: LyricLine(0L, ""))
            if (out.first().stampMs >= MinGapMs) append('[').append(stamp(0L)).append("]♪\n")
            out.forEachIndexed { index, line ->
                append(line.text).append('\n')
                val next = out.getOrNull(index + 1) ?: return@forEachIndexed
                // only a line that says when its singing stopped can open a break, and the note
                // appears the moment the vocal ends (Neiro: silence >= MIN_GAP_MS && end > start)
                val end = line.sungEndMs ?: return@forEachIndexed
                val silence = next.stampMs - end
                if (silence >= MinGapMs && end > line.stampMs) {
                    append('[').append(stamp(end)).append("]♪\n")
                }
            }
        }
    }

    @Serializable
    private data class MuzoResponse(
        val id: String? = null,
        val videoId: String? = null,
        val name: String? = null,
        val artist: String? = null,
        val provider: String? = null,
        val synced: Boolean? = null,
        val isEmpty: Boolean = false,
        val hasWordSync: Boolean? = null,
        val plain: String? = null,
        val lines: List<MuzoLine>? = null,
    )

    @Serializable
    private data class MuzoLine(
        val atMs: Long? = null,
        val durationMs: Long? = null,
        val text: String? = null,
        val syllables: List<MuzoSyllable>? = null,
    )

    @Serializable
    private data class MuzoSyllable(
        val atMs: Long? = null,
        val durationMs: Long? = null,
        /** As emitted: carries the trailing space that ends its word. */
        val text: String? = null,
    )
}
