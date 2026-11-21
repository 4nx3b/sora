// Reads smb:// tracks for ExoPlayer, straight off the share — ported from
// Neiro's playback/SmbDataSource.kt. The URI's authority is the server id,
// its path the display path inside the share; seeking is close-and-reopen per
// the DataSource contract, so each open takes its own file handle and reads
// carry absolute offsets. Failures invalidate the pooled session on the way
// out: a dropped connection that is kept looks exactly like a dead server on
// every retry after the first.

package com.mochyy.mochymusic.remote

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.runBlocking
import java.io.IOException

class SmbDataSource(private val context: Context) : BaseDataSource(/* isNetwork = */ true) {

    private var file: com.hierynomus.smbj.share.File? = null
    private var position = 0L
    private var endPosition = 0L
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        require(uri.scheme == RemoteLibraries.Scheme) { "SmbDataSource cannot open ${uri.scheme}" }
        val serverId = uri.authority ?: throw IOException("No server in $uri")
        val path = uri.path?.trim('/') ?: throw IOException("No path in $uri")
        val server = runBlocking {
            RemoteLibraries.servers(context).firstOrNull { it.id == serverId }
        } ?: throw IOException("Server \"$serverId\" is gone")
        try {
            val handle = SmbLibrary.openFile(server, path)
            val length = handle.length
            position = dataSpec.position.coerceAtLeast(0)
            endPosition = if (dataSpec.length != -1L) {
                (position + dataSpec.length).coerceAtMost(length)
            } else {
                length
            }.coerceAtLeast(position)
            file = handle
            openedUri = uri
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            SmbLibrary.invalidate()
            throw IOException("Could not open SMB file: ${e.message}", e)
        }
        transferStarted(dataSpec)
        return endPosition - position
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val handle = file ?: return -1
        if (position >= endPosition) return -1
        val capped = minOf(readLength.toLong(), endPosition - position).toInt()
        val read = try {
            // One call, absolute offset — the handle holds no position of its own.
            handle.read(buffer, position, offset, capped)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            SmbLibrary.invalidate()
            throw IOException("SMB read failed: ${e.message}", e)
        }
        if (read <= 0) return -1
        position += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        openedUri = null
        runCatching { file?.close() }
        file = null
        transferEnded()
    }

    /** Routes smb:// (by way of remote://) to [SmbDataSource] and everything else to [downstream]. */
    class RoutingFactory(
        private val context: Context,
        private val downstream: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(downstream.createDataSource())

        private inner class RoutingDataSource(private val inner: DataSource) : DataSource {
            private val smb = SmbDataSource(context)
            private var active: DataSource = inner

            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                inner.addTransferListener(transferListener)
                smb.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                active = if (dataSpec.uri.scheme == RemoteLibraries.Scheme) smb else inner
                return active.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                active.read(buffer, offset, length)

            override fun getUri(): Uri? = active.uri

            override fun close() = active.close()
        }
    }
}
