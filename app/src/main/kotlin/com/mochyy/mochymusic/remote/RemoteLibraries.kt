// Remote libraries, ported from Neiro's webdav/ and smb/ stacks: servers the
// listener points at themselves — a WebDAV URL or an SMB share — whose audio
// files browse and play like any other source. The configs live in sora's
// sealed store; a track is addressed as remote|<server>|<path> so the queue
// row survives restarts and the credentials never ride inside it.

package com.mochyy.mochymusic.remote

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RemoteServer(
    /** stable, short: the listener's name for it */
    val id: String,
    val kind: Kind,
    /** WebDAV: the server's base URL. SMB: the host. */
    val address: String,
    val port: Int = 445,
    /** SMB only: the share name */
    val share: String = "",
    /** SMB only: the path inside the share the library is rooted at */
    val basePath: String = "",
    val username: String = "",
    val password: String = "",
) {
    enum class Kind { WEBDAV, SMB }
}

object RemoteLibraries {

    const val MediaIdPrefix = "remote|"
    const val Scheme = "remote"

    private val ServersKey = stringPreferencesKey("remoteLibraries")
    private val json = Json { ignoreUnknownKeys = true }

    // ── the stored servers ──

    suspend fun servers(context: Context): List<RemoteServer> = withContext(Dispatchers.IO) {
        runCatching {
            context.dataStore.data.first()[ServersKey]
                ?.let { runCatching { json.decodeFromString<List<RemoteServer>>(it) }.getOrNull() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun setServers(context: Context, servers: List<RemoteServer>) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            if (servers.isEmpty()) prefs.remove(ServersKey) else prefs[ServersKey] = json.encodeToString(servers)
        }
    }

    suspend fun upsert(context: Context, server: RemoteServer) {
        val next = servers(context).filterNot { it.id == server.id } + server
        setServers(context, next.sortedBy { it.id })
    }

    suspend fun remove(context: Context, id: String) =
        setServers(context, servers(context).filterNot { it.id == id })

    // ── addressing ──

    fun mediaIdOf(serverId: String, path: String): String =
        MediaIdPrefix + serverId + "|" + Uri.encode(path)

    /** the (serverId, path) a remote media id carries, or null when it is not one */
    fun parseMediaId(mediaId: String): Pair<String, String>? {
        if (!mediaId.startsWith(MediaIdPrefix)) return null
        val rest = mediaId.removePrefix(MediaIdPrefix)
        val sep = rest.indexOf('|')
        if (sep <= 0) return null
        return rest.substring(0, sep) to Uri.decode(rest.substring(sep + 1))
    }

    /** the playable URI for one remote file: the WebDAV URL itself, or an smb:// URI carrying the server id */
    fun uriOf(server: RemoteServer, path: String): Uri = when (server.kind) {
        RemoteServer.Kind.WEBDAV -> {
            val base = if (server.address.endsWith("/")) server.address else server.address + "/"
            Uri.parse(base + path.split("/").joinToString("/") { Uri.encode(it) })
        }
        RemoteServer.Kind.SMB -> Uri.parse("$scheme://${server.id}/${path.trimStart('/')}")
    }

    /** one remote file, as the browse screen found it */
    data class Track(
        val server: RemoteServer,
        /** the path under the server (WebDAV: under the base URL; SMB: under the base path) */
        val path: String,
        val title: String,
        val artist: String?,
        val size: Long,
    ) {
        val mediaId: String get() = mediaIdOf(server.id, path)
    }

    /** `Artist - Title`, or just the title, as Neiro's RemoteSong splits a file name */
    fun splitArtistTitle(base: String): Pair<String?, String?> =
        if (" - " in base) {
            val parts = base.split(" - ", limit = 2)
            parts[0].trim().takeIf(String::isNotBlank) to parts[1].trim().takeIf(String::isNotBlank)
        } else {
            null to base
        }

    fun trackOf(server: RemoteServer, path: String, size: Long = 0): Track {
        val fileName = path.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.').takeIf(String::isNotBlank) ?: fileName
        val (artist, title) = splitArtistTitle(base)
        return Track(server = server, path = path, title = title ?: base, artist = artist, size = size)
    }
}
