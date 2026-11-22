// Minimal WebDAV client, ported from Neiro's data/webdav/WebDavClient.kt:
// PROPFIND directory listings over OkHttp with HTTP Basic auth. Depth 1 per
// directory with manual recursion rather than `Depth: infinity`, which many
// servers (notably Nextcloud) reject. XML is parsed with the platform DOM
// parser.

package com.mochyy.mochymusic.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

object WebDavClient {

    data class Entry(
        val url: String,
        val displayName: String,
        val isCollection: Boolean,
        val contentType: String? = null,
    )

    /** the audio files a listing shows, by Neiro's rule */
    private val AUDIO_EXTENSIONS =
        setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "aiff", "alac")

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>
""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    private fun clientOf(username: String?, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
        if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
            val credential = okhttp3.Credentials.basic(username.orEmpty(), password.orEmpty())
            builder.addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", credential).build())
            }
        }
        return builder.build()
    }

    private fun baseUrlOf(url: String): String {
        var base = url.trim()
        if (!base.endsWith("/")) base += "/"
        return base
    }

    /**
     * One directory's entries, the caller's URL first (skipped: it is the
     * directory itself), collections before files.
     */
    suspend fun list(
        url: String,
        username: String? = null,
        password: String? = null,
    ): Result<List<Entry>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrlOf(url)
            val request = Request.Builder()
                .url(base)
                .method("PROPFIND", PROPFIND_BODY)
                .header("Depth", "1")
                .header("Accept", "application/xml")
                .build()
            clientOf(username, password).newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                parseMultistatus(base, body)
            }
        }
    }

    private fun parseMultistatus(base: String, xml: String): List<Entry> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        val out = mutableListOf<Entry>()
        for (i in 0 until responses.length) {
            val el = responses.item(i) as? Element ?: continue
            val href = el.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: continue
            val decoded = URLDecoder.decode(href, "UTF-8")
            // the listing's own directory, in either spelling
            if (decoded == base.removeSuffix("/") || decoded == base || decoded.endsWith("/..")) continue
            val absolute = if (decoded.startsWith("http")) decoded else base.trimEnd('/') + "/" + decoded.trimStart('/')
            if (absolute.removeSuffix("/") == base.removeSuffix("/")) continue
            val display = el.getElementsByTagNameNS("DAV:", "displayname").item(0)?.textContent
                ?: decoded.trimEnd('/').substringAfterLast('/')
            val isCollection = el.getElementsByTagNameNS("DAV:", "collection").length > 0
            val contentType = el.getElementsByTagNameNS("DAV:", "getcontenttype").item(0)?.textContent
            out += Entry(url = absolute, displayName = display, isCollection = isCollection, contentType = contentType)
        }
        return out.sortedWith(compareByDescending<Entry> { it.isCollection }.thenBy { it.displayName.lowercase() })
    }
}
