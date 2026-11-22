/*
 * SoundCloud's playback headers, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, soundcloud/SoundCloudAudioProvider.kt's
 * addPlaybackHeaders and the player's host-based interceptor — GPL-3.0,
 * © Rukamori and the fork's contributors).
 *
 * The cdn asks where a stream came from: without a browser's User-Agent and
 * soundcloud.com's own Referer and Origin, the sndcdn urls can refuse. An
 * interceptor on the player's http stack adds them for SoundCloud's playback
 * hosts — which is also what makes an hls playlist's segments work, since
 * those are fetched as their own requests that no per-stream header rides.
 * A Range header is only added when the request doesn't carry one, so a seek
 * keeps its own.
 */

package com.mochyy.mochymusic.playback


object SoundCloudPlaybackHeaders {
    private const val BrowserUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private fun isPlaybackHost(host: String): Boolean =
        host == "playback.media-streaming.soundcloud.cloud" || host.endsWith(".sndcdn.com")

    /** the interceptor the player's and the downloader's clients both carry */
    val interceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (!isPlaybackHost(request.url.host)) {
            return@Interceptor chain.proceed(request)
        }
        val builder = request.newBuilder()
            .header("User-Agent", BrowserUserAgent)
            .header("Accept", "audio/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Referer", "https://soundcloud.com/")
            .header("Origin", "https://soundcloud.com")
        if (request.header("Range") == null) {
            builder.header("Range", "bytes=0-")
        }
        chain.proceed(builder.build())
    }
}