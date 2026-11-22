// NonMusicFilter.kt
// Neiro's "Filter non-music audio" (data/LocalMediaRepository): the phone-wide scan leaves out clips under
// 30 seconds, WAV files, voice notes, recordings and system sounds, so only songs are offered. Off unless turned on.

package com.mochyy.mochymusic.importer

import android.os.Build
import android.provider.MediaStore
import java.util.Locale

object NonMusicFilter {
    const val MinDurationMs = 30_000L

    private val WavMimeTypes = setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")


    private val nonMusicPathSegments = listOf(
        "/alarms/", "/notifications/", "/ringtones/", "/podcasts/", "/audiobooks/",
        "/recordings/", "/voice recorder/", "/sound_recorder/", "/call_rec/", "/whatsapp voice notes/",
    )

    // a duration of 0 or none is the scanner failing to read one, not a short clip, so it falls through to the name checks
    fun selection(): String = buildString {
        append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        append(" AND (${MediaStore.Audio.Media.DURATION} >= ? OR ${MediaStore.Audio.Media.DURATION} <= 0 OR ${MediaStore.Audio.Media.DURATION} IS NULL)")
        append(" AND ${MediaStore.Audio.Media.IS_ALARM} = 0")
        append(" AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
        append(" AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) append(" AND ${MediaStore.Audio.Media.IS_PODCAST} = 0")
    }

    fun selectionArgs(): Array<String> = arrayOf(MinDurationMs.toString())

    // playability is LocalFileNames' call; this only drops what Neiro calls non-music. No extension to read means the name says nothing
    fun isEligible(durationMs: Long, displayName: String, path: String?, mimeType: String? = null): Boolean {
        if (durationMs in 1 until MinDurationMs) return false
        val fileName = path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: displayName
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension == "wav" || (extension.isEmpty() && mimeType?.lowercase(Locale.ROOT) in WavMimeTypes)) return false
        val normalized = path?.replace('\\', '/')?.lowercase(Locale.ROOT) ?: return true
        return nonMusicPathSegments.none(normalized::contains)
    }
}
