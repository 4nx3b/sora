/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported into sora: the pieces of ArchiveTune's module stack that carry the settings page —
 * the diagnostics a module's row and details show, the HTTP client the modules talk through,
 * and the quality-tag reading of a manifest. The playback resolution half of
 * ModulePlaybackUseCases stays unported; sora plays from its own source modules.
 */

package com.mochyy.mochymusic.arch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import moe.rukamori.archivetune.modules.ModuleFailure
import moe.rukamori.archivetune.modules.ModuleSetting
import moe.rukamori.archivetune.modules.parseJsonValue
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ModuleCheckStatus { READY, CONFIGURATION, UNAVAILABLE, FAILED, NO_MATCH, RESOLVED, TIMEOUT }

enum class ModuleResolutionPhase { SEARCH, MATCH, STREAM }

enum class ModuleMatchRejection { NO_RESULTS, METADATA, TITLE, ARTIST, RECORDING, DURATION, EXPLICIT }

data class ModuleDiagnostic(
    val status: ModuleCheckStatus,
    val atMs: Long,
    val elapsedMs: Long,
    val failure: ModuleFailure? = null,
    val revision: String? = null,
    val phase: ModuleResolutionPhase? = null,
    val candidateCount: Int = 0,
    val rejection: ModuleMatchRejection? = null,
    val upstream: String = "",
    val variant: String = "",
    val code: String = "",
)

data class ModuleHealth(val check: ModuleDiagnostic?, val playback: ModuleDiagnostic?)

@Singleton
class ArchModuleDiagnostics @Inject constructor() {
    private val mutableState = MutableStateFlow<Map<String, ModuleDiagnostic>>(emptyMap())
    private val mutableChecks = MutableStateFlow<Map<String, ModuleDiagnostic>>(emptyMap())
    val state = mutableState.asStateFlow()
    val checks = mutableChecks.asStateFlow()

    fun record(id: String, diagnostic: ModuleDiagnostic) {
        mutableState.update { it + (id to diagnostic) }
    }

    fun recordCheck(id: String, diagnostic: ModuleDiagnostic) {
        mutableChecks.update { it + (id to diagnostic) }
    }
}

/** ArchiveTune's module HTTP talks through the app's proxy settings; sora's port keeps a plain client. */
internal object ArchNetworkPolicy {
    fun client(streaming: Boolean): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

enum class ArchQualityTag { HI_RES, MAX }

fun archQualityTags(labels: List<String>, settings: List<ModuleSetting> = emptyList()): List<ArchQualityTag> {
    val declarations = labels + settings.filter { field ->
        !field.secret && QUALITY_FIELD.containsMatchIn(field.key + " " + field.label)
    }.flatMap { field ->
        field.options.flatMap { option -> listOf(option.label, qualityValue(option.valueJson)) } + qualityValue(field.defaultJson)
    }
    val normalized = declarations.map { it.lowercase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ') }
    return buildList {
        if (normalized.any(HI_RES::containsMatchIn)) add(ArchQualityTag.HI_RES)
        if (normalized.any(MAX_QUALITY::containsMatchIn)) add(ArchQualityTag.MAX)
    }
}

private fun qualityValue(value: String): String = try {
    parseJsonValue(value) as? String ?: ""
} catch (_: org.json.JSONException) {
    ""
}

private val QUALITY_FIELD = Regex("quality|resolution|format", RegexOption.IGNORE_CASE)
private val HI_RES = Regex("""\b(?:hi(?:gh)?\s*res(?:olution)?|24\s*bit)\b""")
private val MAX_QUALITY = Regex("""\bmax(?:imum)?(?:\s+quality)?\b""")
