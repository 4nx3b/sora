// SMB remote library, ported from Neiro's data/smb (SmbConnection, SmbClient,
// SmbConfig): the one open session to the configured server, guest rather than
// anonymous login (smbj 0.15 derives SMB3 keys from a session key an anonymous
// login has none of), backslash-joined wire paths, and the recursive walk that
// turns a share into audio rows.

package com.mochyy.mochymusic.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** one configured SMB server, as sora's sealed store holds it */
data class SmbServer(
    val host: String,
    val port: Int = 445,
    val share: String,
    val basePath: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean get() = host.isNotBlank() && share.isNotBlank()
}

object SmbLibrary {

    class SmbException(message: String) : IOException(message)

    private val AUDIO_EXTENSIONS =
        setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "aiff", "alac")

    fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    data class Entry(
        /** Display path with forward slashes, relative to the share. */
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    // ── the one open session, invalidated on every credential change ──

    @Volatile private var current: SmbServer? = null
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var shareHandle: DiskShare? = null

    /** a settings-page server, as the SMB session wants it */
    fun RemoteServer.toSmb(): SmbServer = SmbServer(
        host = address,
        port = port,
        share = share,
        basePath = basePath,
        username = username,
        password = password,
    )

    @Synchronized
    fun invalidate() {
        runCatching { shareHandle?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        shareHandle = null
        connection = null
        client = null
        current = null
    }

    fun <T> withShare(server: SmbServer, block: (DiskShare) -> T): T {
        return try {
            block(open(server))
        } catch (e: IOException) {
            invalidate()
            block(open(server))
        }
    }

    @Synchronized
    private fun open(server: SmbServer): DiskShare {
        if (server == current) {
            shareHandle?.let { if (connection?.isConnected == true) return it }
        }
        invalidate()
        require(server.isConfigured) { "Enter a server and a share" }
        val config = SmbConfig.builder()
            .withTimeout(20, TimeUnit.SECONDS)
            .withReadTimeout(30, TimeUnit.SECONDS)
            .withWriteTimeout(30, TimeUnit.SECONDS)
            .withTransactTimeout(30, TimeUnit.SECONDS)
            .build()
        val newClient = SMBClient(config)
        try {
            val newConnection = newClient.connect(server.host, server.port)
            val session: Session = newConnection.authenticate(authFor(server.username, server.password))
            val diskShare = session.connectShare(server.share) as? DiskShare
                ?: throw SmbException("\"${server.share}\" is not a file share")
            client = newClient
            connection = newConnection
            shareHandle = diskShare
            current = server
            return diskShare
        } catch (e: Throwable) {
            runCatching { newClient.close() }
            throw e
        }
    }

    /**
     * Never anonymous(): smbj 0.15 derives SMB3 keys from a session key an
     * anonymous login has none of, and crashes on servers that do not flag the
     * session as null (Samba) — hierynomus/smbj#872. Guest sends a real NTLMv2
     * exchange and lands on the guest path smbj handles.
     */
    internal fun authFor(username: String, password: String): AuthenticationContext =
        if (username.isBlank() && password.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(username, password.toCharArray(), "")
        }

    /** Display path back to the wire form: backslash-joined, as the wire spells it. */
    fun smbPath(displayPath: String): String =
        displayPath.replace('/', '\\').trim('\\')

    private fun listDir(diskShare: DiskShare, dir: String): List<Entry> =
        diskShare.list(smbPath(dir)).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            Entry(name, isDir, info.endOfFile)
        }

    /** One directory's entries, folders first. */
    suspend fun list(
        server: RemoteServer,
        dir: String,
    ): Result<List<Entry>> = withContext(Dispatchers.IO) {
        val smb = server.toSmb()
        runCatching {
            withShare(smb) { diskShare -> listDir(diskShare, dir) }
        }
    }

    /** the share's audio rows, walked breadth-first like Neiro's listLibrary */
    suspend fun listLibrary(server: RemoteServer, maxFiles: Int = 10_000): Result<List<Entry>> =
        withContext(Dispatchers.IO) {
            val smb = server.toSmb()
            runCatching {
                val audio = ArrayList<Entry>()
                withShare(smb) { diskShare ->
                    val stack = ArrayDeque<String>()
                    stack.add(server.basePath)
                    val visited = HashSet<String>()
                    while (stack.isNotEmpty() && audio.size < maxFiles) {
                        val dir = stack.removeFirst()
                        if (!visited.add(dir.lowercase())) continue
                        for (child in listDir(diskShare, dir)) {
                            val childPath = if (dir.isBlank()) child.path else "$dir/${child.path}"
                            if (child.isDirectory) {
                                if (childPath.lowercase() !in visited) stack.add(childPath)
                            } else if (isAudioFile(child.path)) {
                                audio.add(Entry(childPath, false, child.size))
                            }
                        }
                    }
                }
                audio
            }
        }

    /**
     * Opens [path] (display form) for reading. The handle is the caller's to
     * close — one per player open, so seeks never share a cursor.
     */
    fun openFile(server: RemoteServer, path: String): com.hierynomus.smbj.share.File =
        withShare(server.toSmb()) { diskShare ->
            diskShare.openFile(
                smbPath(path),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        }
}
