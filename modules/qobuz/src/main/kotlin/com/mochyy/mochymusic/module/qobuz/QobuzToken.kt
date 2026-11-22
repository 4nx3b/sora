/*
 * A Qobuz account, ported from ArchiveTune
 * (github.com/4nx3b/ArchiveTune, qobuz/QobuzToken.kt and SourceInputParsing.kt
 * — GPL-3.0, © Rukamori and the fork's contributors). The token is the
 * user_auth_token with the app id and secret it was issued for; the paste
 * parser takes a block of "token … / app_id: … / app_secret: …" lines and
 * makes as many accounts out of it as it finds.
 */

package com.mochyy.mochymusic.module.qobuz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class QobuzToken(
    val token: String,
    val userId: String = "",
    val appId: String,
    val appSecret: String,
    val label: String = "",
    val subscription: String = "",
) {
    val id: String get() = token.take(12)

    fun toJson(): JsonObject = buildJsonObject {
        put("token", token)
        put("userId", userId)
        put("appId", appId)
        put("appSecret", appSecret)
        put("label", label)
        put("subscription", subscription)
    }

    companion object {
        fun fromJson(obj: JsonObject): QobuzToken? {
            val token = obj.string("token")?.trim().orEmpty()
            val appId = obj.string("appId")?.trim().orEmpty()
            val appSecret = obj.string("appSecret")?.trim().orEmpty()
            if (token.isEmpty() || appId.isEmpty() || appSecret.isEmpty()) return null
            return QobuzToken(
                token = token,
                userId = obj.string("userId")?.trim().orEmpty(),
                appId = appId,
                appSecret = appSecret,
                label = obj.string("label")?.trim().orEmpty(),
                subscription = obj.string("subscription")?.trim().orEmpty(),
            )
        }

        fun listToJson(tokens: List<QobuzToken>): String =
            buildJsonArray { tokens.forEach { add(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<QobuzToken> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { (it as? JsonObject)?.let(::fromJson) }
        }
    }
}

internal object SourceInputParsing {
    private val Separators = Regex("[\\s,;]+")

    fun parseUrls(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        return input
            .split(Separators)
            .asSequence()
            .map { it.trim().trim('"', '\'', '<', '>', '(', ')', ',') }
            .filter { it.isNotEmpty() }
            .mapNotNull(::normalizeUrl)
            .filter { seen.add(it.lowercase()) }
            .toList()
    }

    fun normalizeUrl(raw: String): String? {
        var url = raw
        if (!url.contains("://")) url = "https://$url"
        val host = url.substringAfter("://").substringBefore("/")
        if (!host.contains('.')) return null
        return url.trimEnd('/')
    }

    fun parseQobuzTokens(input: String): List<QobuzToken> {
        if (input.isBlank()) return emptyList()
        val blocks = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        var pendingLabel = ""

        fun startBlock() {
            current = mutableMapOf<String, String>().also {
                if (pendingLabel.isNotEmpty()) it["label"] = pendingLabel
                pendingLabel = ""
                blocks.add(it)
            }
        }

        for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val token = matchField(line, "token")
            val userId = matchField(line, "user id", "user_id", "userid", "user")
            val subscription = matchField(line, "subscription", "plan")
            val appId = AppIdRegex.find(line)?.groupValues?.get(1)
            val appSecret = AppSecretRegex.find(line)?.groupValues?.get(1)

            when {
                token != null -> {
                    startBlock()
                    current?.put("token", token)
                }
                userId != null -> current?.put("userId", userId)
                subscription != null -> current?.put("subscription", subscription)
                !line.contains(FieldSeparatorRegex) && !line.contains("app_") ->
                    pendingLabel = line.take(40)
                else -> {}
            }

            if (appId != null) current?.put("appId", appId)
            if (appSecret != null) current?.put("appSecret", appSecret)
        }

        return blocks.mapNotNull { block ->
            QobuzToken(
                token = block["token"].orEmpty(),
                userId = block["userId"].orEmpty(),
                appId = block["appId"].orEmpty(),
                appSecret = block["appSecret"].orEmpty(),
                label = block["label"].orEmpty(),
                subscription = block["subscription"].orEmpty(),
            ).takeIf { it.token.isNotEmpty() && it.appId.isNotEmpty() && it.appSecret.isNotEmpty() }
        }
    }

    private fun matchField(line: String, vararg names: String): String? {
        val lower = line.lowercase()
        for (name in names) {
            if (!lower.startsWith(name)) continue
            val after = line.substring(name.length)
            val value = after.dropWhile { it == ' ' || it in "➠→➔⇒:=\t" }.trim()
            if (value.isNotEmpty()) return value.trim('"', '\'')
        }
        return null
    }

    private val AppIdRegex = Regex("app_id:?\\s*([0-9]+)", RegexOption.IGNORE_CASE)
    private val AppSecretRegex = Regex("app_secret:?\\s*([a-f0-9]{16,})", RegexOption.IGNORE_CASE)
    private val FieldSeparatorRegex = Regex("[➠→➔⇒:]")
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun normalizeInstanceUrl(raw: String): String? = SourceInputParsing.normalizeUrl(raw)
