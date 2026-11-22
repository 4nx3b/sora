/*
 * The Deezer decrypting reader, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, deezer/DeezerCrypto.kt and
 * deezer/DeezerDecryptingDataSource.kt — GPL-3.0, © Rukamori and the fork's
 * contributors; see git history).
 *
 * Media3 DataSource that streams a Deezer CDN file and Blowfish-decrypts it
 * in flight, so the extractor above it sees an ordinary FLAC/MP3 byte stream.
 * The fork's own note carries over: this is deliberately a DataSource rather
 * than a standalone downloader, so the cache, the downloads and the codec
 * reporting all keep working with no Deezer-specific code in those paths.
 */

package com.mochyy.mochymusic.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object DeezerCrypto {
    const val CHUNK_SIZE = 2048
    const val ENCRYPTED_CHUNK_STRIDE = 3
    const val SCHEME = "deezer"

    private const val PARAM_URL = "u"
    private const val PARAM_KEY = "k"
    private const val PARAM_SALT = "s"

    const val DEFAULT_KEY_SALT = "g4el58wc0zvf9na1"

    private val CHUNK_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    fun deriveKey(trackId: String, salt: String = DEFAULT_KEY_SALT): ByteArray {
        val effective = if (salt.length >= 16) salt else DEFAULT_KEY_SALT
        val md5Hex = MessageDigest
            .getInstance("MD5")
            .digest(trackId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return ByteArray(16) { i ->
            (md5Hex[i].code xor md5Hex[i + 16].code xor effective[i].code).toByte()
        }
    }

    fun isEncryptedChunk(chunkIndex: Long): Boolean = chunkIndex % ENCRYPTED_CHUNK_STRIDE == 0L

    fun decryptChunk(chunk: ByteArray, length: Int, key: ByteArray) {
        if (length < 8) return
        val decryptable = length - (length % 8)
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "Blowfish"), IvParameterSpec(CHUNK_IV))
        cipher.doFinal(chunk, 0, decryptable, chunk, 0)
    }

    data class StreamRef(
        val url: Uri,
        val trackId: String,
        val salt: String,
    )

    fun parseUri(uri: Uri): StreamRef? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        val url = uri.getQueryParameter(PARAM_URL)?.takeIf { it.isNotBlank() } ?: return null
        val trackId = uri.getQueryParameter(PARAM_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val salt = uri.getQueryParameter(PARAM_SALT)?.takeIf { it.isNotBlank() } ?: DEFAULT_KEY_SALT
        return StreamRef(Uri.parse(url), trackId, salt)
    }
}

class DeezerDecryptingDataSource(
    private val upstreamFactory: DataSource.Factory,
) : BaseDataSource(true) {
    private var upstream: DataSource? = null
    private var currentUri: Uri? = null
    private var key: ByteArray? = null

    private val chunk = ByteArray(DeezerCrypto.CHUNK_SIZE)

    private var chunkLength = 0
    private var chunkOffset = 0

    private var nextChunkIndex = 0L

    private var bytesRemaining = C.LENGTH_UNSET.toLong()

    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val ref = DeezerCrypto.parseUri(dataSpec.uri)
            ?: throw IOException("Not a Deezer stream URI: ${dataSpec.uri}")
        val realUri = ref.url

        key = DeezerCrypto.deriveKey(ref.trackId, ref.salt)
        currentUri = dataSpec.uri

        val requestedPosition = dataSpec.position
        val alignedPosition = requestedPosition - (requestedPosition % DeezerCrypto.CHUNK_SIZE)
        val discardCount = (requestedPosition - alignedPosition).toInt()
        nextChunkIndex = alignedPosition / DeezerCrypto.CHUNK_SIZE

        val upstreamLength =
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                C.LENGTH_UNSET.toLong()
            } else {
                dataSpec.length + discardCount
            }

        val source = upstreamFactory.createDataSource()
        upstream = source

        val upstreamLengthReported = source.open(
            dataSpec
                .buildUpon()
                .setUri(realUri)
                .setPosition(alignedPosition)
                .setLength(upstreamLength)
                .build(),
        )

        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            upstreamLengthReported == C.LENGTH_UNSET.toLong() -> C.LENGTH_UNSET.toLong()
            else -> (upstreamLengthReported - discardCount).coerceAtLeast(0L)
        }

        opened = true
        transferStarted(dataSpec)

        if (discardCount > 0) discardFully(discardCount)

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        if (chunkOffset >= chunkLength && !fillChunk()) return C.RESULT_END_OF_INPUT

        var available = chunkLength - chunkOffset
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            available = min(available.toLong(), bytesRemaining).toInt()
        }
        val toCopy = min(length, available)
        if (toCopy == 0) return C.RESULT_END_OF_INPUT

        chunk.copyInto(buffer, offset, chunkOffset, chunkOffset + toCopy)
        chunkOffset += toCopy
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    private fun fillChunk(): Boolean {
        val source = upstream ?: return false
        var filled = 0

        while (filled < DeezerCrypto.CHUNK_SIZE) {
            val read = source.read(chunk, filled, DeezerCrypto.CHUNK_SIZE - filled)
            if (read == C.RESULT_END_OF_INPUT) break
            filled += read
        }
        if (filled == 0) return false

        if (filled == DeezerCrypto.CHUNK_SIZE && DeezerCrypto.isEncryptedChunk(nextChunkIndex)) {
            val chunkKey = key ?: return false
            try {
                DeezerCrypto.decryptChunk(chunk, filled, chunkKey)
            } catch (e: Exception) {
                throw IOException("Deezer chunk decryption failed at index $nextChunkIndex", e)
            }
        }

        nextChunkIndex++
        chunkLength = filled
        chunkOffset = 0
        return true
    }

    private fun discardFully(count: Int) {
        var left = count
        while (left > 0) {
            if (chunkOffset >= chunkLength && !fillChunk()) return
            val skip = min(left, chunkLength - chunkOffset)
            chunkOffset += skip
            left -= skip
        }
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream?.responseHeaders ?: emptyMap()

    override fun close() {
        chunkLength = 0
        chunkOffset = 0
        key = null
        currentUri = null
        bytesRemaining = C.LENGTH_UNSET.toLong()
        try {
            upstream?.close()
        } catch (e: IOException) {
            // the fork logs and moves on; so does this
        } finally {
            upstream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = DeezerDecryptingDataSource(upstreamFactory)
    }
}

/**
 * Routes a `deezer://` stream through the decrypting reader and leaves every other uri to the
 * plain factory — the same spot the fork wires it, around the http stack the player and the
 * downloads share.
 */
class DeezerSchemeRouting(private val base: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = Router(base.createDataSource())

    private inner class Router(private val plain: DataSource) : DataSource {
        // makes its own upstream from the factory on every open, so one instance serves many songs
        private val deezer = DeezerDecryptingDataSource(base)
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            plain.addTransferListener(transferListener)
            deezer.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme?.equals(DeezerCrypto.SCHEME, ignoreCase = true) == true) deezer else plain
            active = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }
}
