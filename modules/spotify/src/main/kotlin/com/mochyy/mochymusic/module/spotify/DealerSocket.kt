/*
 * The Dealer WebSocket, the one part of Stopify's walk with no room in the module host's plain
 * HTTP: Spotify's dealer answers a registered device's play command by pushing the state machine
 * (with the track's manifest) over it. Written against the JDK alone — the module api is kept
 * free of http libraries so a module ships as its own apk — which is just the RFC 6455 upgrade
 * over a TLS socket, framed messages, and pings.
 */

package com.mochyy.mochymusic.module.spotify

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/** one dealer connection: [connect] hands back the Spotify-Connection-Id the registration needs */
internal class DealerSocket(
    private val onMessage: (String) -> Unit,
    private val onDropped: () -> Unit = {},
) {
    @Volatile private var socket: Socket? = null
    @Volatile private var alive = false

    val connected: Boolean get() = alive && socket?.isConnected == true && socket?.isClosed == false

    /**
     * Opens the socket, does the websocket upgrade and returns the dealer's connection id,
     * or null when it couldn't be had. Throws when the dealer answers anything but the upgrade.
     */
    fun connect(host: String, accessToken: String, timeoutMs: Int = 10_000): String? {
        close()
        // an unconnected socket first, so the tcp connect carries a timeout; the sni and the
        // hostname check are set before the tls handshake, the way a browser's would be
        val raw = SSLSocketFactory.getDefault().createSocket() as SSLSocket
        try {
            raw.tcpNoDelay = true
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress(host, 443), timeoutMs)
            raw.sslParameters = raw.sslParameters.apply {
                serverNames = listOf(SNIHostName(host))
            }
            raw.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, raw.session)) {
                throw IOException("dealer certificate isn't for $host")
            }
            raw.soTimeout = 0
        } catch (e: Exception) {
            runCatching { raw.close() }
            return null
        }

        val key = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val handshake = buildString {
            append("GET /?access_token=").append(uriEncode(accessToken)).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: ").append(java.util.Base64.getEncoder().encodeToString(key)).append("\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("Origin: https://open.spotify.com\r\n")
            append("User-Agent: ").append(SpotifyWeb.UserAgent).append("\r\n")
            append("\r\n")
        }

        val out = BufferedOutputStream(raw.getOutputStream())
        out.write(handshake.toByteArray(Charsets.ISO_8859_1))
        out.flush()

        val input = BufferedInputStream(raw.getInputStream())

        // the upgrade answer, headers up to the blank line; the dealer hands its connection id
        // over as a response header (and again inside its first messages, belt and braces)
        val statusLine = readLine(input) ?: throw IOException("dealer closed during upgrade")
        if (!statusLine.startsWith("HTTP/1.1 101")) {
            runCatching { raw.close() }
            throw IOException("dealer refused the upgrade: ${statusLine.trim()}")
        }
        var connectionId: String? = null
        while (true) {
            val line = readLine(input) ?: throw IOException("dealer closed mid-upgrade")
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            if (name == "spotify-connection-id" || name == "x-spotify-connection-id") connectionId = value
        }

        socket = raw
        output = out
        alive = true

        thread(isDaemon = true, name = "spotify-dealer-reader") { readLoop(input, raw) }
        thread(isDaemon = true, name = "spotify-dealer-ping") { pingLoop(raw) }
        return connectionId
    }

    @Volatile private var output: BufferedOutputStream? = null

    fun sendText(text: String) {
        val out = output ?: return
        synchronized(out) {
            runCatching {
                out.write(frame(0x1, text.toByteArray(Charsets.UTF_8)))
                out.flush()
            }.onFailure { drop() }
        }
    }

    fun close() {
        alive = false
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    private fun drop() {
        if (!alive) return
        alive = false
        runCatching { socket?.close() }
        socket = null
        onDropped()
    }

    // ── the reader ────────────────────────────────────────────────────────────────────────────

    private fun readLoop(input: BufferedInputStream, forSocket: Socket) {
        var fragment: StringBuilder? = null
        try {
            while (alive) {
                val head = readExact(input, 2) ?: break
                val opcode = head[0].toInt() and 0x0F
                val fin = (head[0].toInt() and 0x80) != 0
                val masked = (head[1].toInt() and 0x80) != 0
                var length = (head[1].toInt() and 0x7F).toLong()
                if (length == 126L) {
                    val ext = readExact(input, 2) ?: break
                    length = ((ext[0].toLong() and 0xFF) shl 8) or (ext[1].toLong() and 0xFF)
                } else if (length == 127L) {
                    val ext = readExact(input, 8) ?: break
                    length = 0
                    for (b in ext) length = (length shl 8) or (b.toLong() and 0xFF)
                }
                if (length > MaxFrame) break
                val mask = if (masked) readExact(input, 4) else null
                val payload = if (length == 0L) ByteArray(0) else readExact(input, length.toInt()) ?: break
                if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()

                when (opcode) {
                    0x1, 0x2 -> fragment = StringBuilder(payload.toString(Charsets.UTF_8))
                    0x0 -> fragment?.append(payload.toString(Charsets.UTF_8))
                    0x9 -> sendPong(payload) // a ping answers with a pong carrying the same bytes
                    0x8 -> { drop(); return } // the dealer saying goodbye: the next song reconnects
                }
                // a long state machine arrives as pieces; only a final frame is a message
                if (fin && (opcode == 0x1 || opcode == 0x2 || opcode == 0x0)) {
                    fragment?.toString()?.let { if (it.isNotBlank()) onMessage(it) }
                    fragment = null
                }
            }
        } catch (e: Exception) {
            // a dropped dealer is a fact of life on mobile; the next resolve reconnects
        }
        // a reader whose socket was replaced (a reconnect) must not take the new one down
        if (alive && socket === forSocket) drop()
    }

    private fun pingLoop(forSocket: Socket) {
        while (alive && socket === forSocket) {
            Thread.sleep(PingEveryMs)
            if (alive && socket === forSocket) sendText("{\"type\":\"ping\"}")
        }
    }

    private fun sendPong(payload: ByteArray) {
        val out = output ?: return
        synchronized(out) {
            runCatching {
                out.write(frame(0xA, payload))
                out.flush()
            }
        }
    }

    // ── framing ───────────────────────────────────────────────────────────────────────────────

    private fun frame(opcode: Int, payload: ByteArray): ByteArray {
        val random = SecureRandom()
        val mask = ByteArray(4).also { random.nextBytes(it) }
        val masked = ByteArray(payload.size) { (payload[it].toInt() xor mask[it and 3].toInt()).toByte() }
        val head = ByteArrayOutputStream()
        head.write(0x80 or opcode) // fin, always; the dealer's messages never interleave
        when {
            payload.size < 126 -> head.write(0x80 or payload.size)
            payload.size < 65536 -> {
                head.write(0x80 or 126)
                head.write((payload.size shr 8) and 0xFF)
                head.write(payload.size and 0xFF)
            }
            else -> {
                head.write(0x80 or 127)
                repeat(8) { i -> head.write(((payload.size.toLong() shr ((7 - i) * 8)) and 0xFF).toInt()) }
            }
        }
        head.write(mask)
        head.write(masked)
        return head.toByteArray()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) break
            bytes.write(b)
        }
        var line = bytes.toString(Charsets.ISO_8859_1)
        if (line.endsWith("\r")) line = line.dropLast(1)
        return line
    }

    private fun readExact(input: BufferedInputStream, count: Int): ByteArray? {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(out, read, count - read)
            if (n < 0) return null
            read += n
        }
        return out
    }

    private fun uriEncode(value: String): String = buildString {
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39 || c in 0x41..0x5A || c in 0x61..0x7A || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) {
                append(c.toChar())
            } else {
                append('%').append(String.format("%02X", c))
            }
        }
    }

    private companion object {
        // the biggest message the dealer pushes is a state machine's worth of tracks
        private const val MaxFrame = 4 * 1024 * 1024
        private const val PingEveryMs = 30_000L
    }
}
