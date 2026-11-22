// DeviceMusicScanner.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.constants.FilterNonMusicAudioKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.flow.first
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** a song found in the phone's storage, before it is imported */
@Immutable
data class DeviceSong(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    /** the album picture android keeps for the file, if it has one */
    val artworkUri: String?,
)

/** finds the music already on the phone, once the user lets mochy read its storage */
object DeviceMusicScanner {
    /** android 13 split audio out of the general storage permission */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** every song on the phone, newest first, minus the uris in alreadyImported, only what android marks as music so ringtones and voice notes stay out */
    suspend fun scan(context: Context, alreadyImported: Set<String>): List<DeviceSong> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
        )
        val filterNonMusic = context.dataStore.data.first()[FilterNonMusicAudioKey] ?: false
        val songs = ArrayList<DeviceSong>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                if (filterNonMusic) NonMusicFilter.selection() else "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                if (filterNonMusic) NonMusicFilter.selectionArgs() else null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                @Suppress("DEPRECATION") val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    // wma, aiff and the like would come in and never play, so they aren't offered
                    if (!LocalFileNames.canPlay(cursor.getString(nameColumn).orEmpty(), cursor.getString(mimeColumn))) continue
                    if (filterNonMusic && !NonMusicFilter.isEligible(
                            cursor.getLong(durationColumn),
                            cursor.getString(nameColumn).orEmpty(),
                            if (pathColumn >= 0) cursor.getString(pathColumn) else null,
                            cursor.getString(mimeColumn),
                        )
                    ) continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    if (uri.toString() in alreadyImported) continue
                    songs += DeviceSong(
                        uri = uri,
                        title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown song",
                        // android writes "<unknown>" when a file has no artist tag
                        artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                        durationMs = cursor.getLong(durationColumn),
                        artworkUri = ContentUris.withAppendedId(AlbumArtUri, cursor.getLong(albumColumn)).toString(),
                    )
                }
            }
        } catch (e: Exception) {
            // a storage provider that refuses the query shouldn't take the library down with it
            Timber.w(e, "Scanning the phone for music failed")
        }
        songs
    }

    private val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
}
