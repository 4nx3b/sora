// DownloadPacer.kt
// The ceiling the Downloads settings put on the queue: 0 and every download comes in as fast as
// the line allows, a kilobytes-per-second figure and all of them together are held to it, however
// many run at once. The pacer hands out the budget in small slices from a shared token bucket, and
// the interceptor wraps each response body so the thread reading it (media3's own download thread)
// waits for its share before handing bytes back.

package com.mochyy.mochymusic.utils

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

class DownloadPacer {
    /** bytes per second every download connection shares; 0 means no ceiling at all */
    @Volatile
    var bytesPerSecond: Long = 0L

    private val lock = Any()
    // a second's worth of budget at most, refilled by real time passing
    private var tokens = 0.0
    private var lastNs = 0L

    /** waits until [bytes] fit inside the shared budget */
    fun acquire(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val rate = bytesPerSecond
            if (rate <= 0L) return
            // a slice is at most a fiftieth of a second's budget, so many downloads share smoothly
            val slice = (rate / 20L).coerceAtLeast(1L).coerceAtMost(remaining)
            var waited = false
            synchronized(lock) {
                val now = System.nanoTime()
                if (lastNs != 0L) {
                    tokens = (tokens + (now - lastNs) / 1e9 * rate).coerceAtMost(rate.toDouble())
                } else {
                    tokens = rate.toDouble()
                }
                lastNs = now
                if (tokens >= slice) {
                    tokens -= slice
                    remaining -= slice
                } else {
                    waited = true
                }
            }
            if (waited) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }
}

/** paces every response body read through [pacer]; the request itself is untouched */
class DownloadSpeedLimitInterceptor(private val pacer: DownloadPacer) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        return response.newBuilder()
            .body(PacedBody(body, pacer))
            .build()
    }
}

private class PacedBody(
    private val inner: ResponseBody,
    private val pacer: DownloadPacer,
) : ResponseBody() {
    override fun contentType() = inner.contentType()
    override fun contentLength() = inner.contentLength()

    override fun source(): BufferedSource =
        object : ForwardingSource(inner.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) pacer.acquire(read)
                return read
            }
        }.buffer()
}
