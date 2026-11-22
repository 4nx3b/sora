// ArchModulesViewModel.kt
// The ArchiveTune modules page's model, ported from ArchiveTune's ModuleSettingsViewModel in
// shape but written against sora: one state, one action at a time, failures mapped to strings
// the page can toast.

package com.mochyy.mochymusic.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.arch.ArchModuleImport
import com.mochyy.mochymusic.arch.ArchModuleUseCases
import com.mochyy.mochymusic.arch.ArchInstalledModule
import com.mochyy.mochymusic.arch.ArchSavedCatalog
import com.mochyy.mochymusic.arch.ModuleHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.modules.ModuleException
import moe.rukamori.archivetune.modules.ModuleFailure
import javax.inject.Inject

/** a pending install, held between "Continue" and the review dialog's Install */
data class ArchPendingInstall(
    val title: String,
    val author: String,
    val version: String,
    val description: String,
    val origin: String,
    val requiredSettings: List<String>,
    val alreadyInstalled: Boolean,
    val import: ArchModuleImport,
)

data class ArchModulesState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val modules: List<ArchInstalledModule> = emptyList(),
    val catalogs: List<ArchSavedCatalog> = emptyList(),
    val health: Map<String, ModuleHealth> = emptyMap(),
    /** the module id whose action is running, "all" for the page-wide ones, null when idle */
    val busy: String? = null,
    @androidx.annotation.StringRes val message: Int? = null,
    val messageArg: String? = null,
    /** an auth URL the page is to open in the browser, then clear */
    val openUri: String? = null,
    val review: ArchPendingInstall? = null,
)

@HiltViewModel
class ArchModulesViewModel @Inject constructor(
    private val useCases: ArchModuleUseCases,
) : ViewModel() {

    private val busy = MutableStateFlow<String?>(null)
    private val message: MutableStateFlow<Pair<Int, String?>?> = MutableStateFlow(null)
    private val openUri = MutableStateFlow<String?>(null)
    private val review = MutableStateFlow<ArchPendingInstall?>(null)

    val state: StateFlow<ArchModulesState> = combine(
        combine(useCases.state, useCases.health) { registry, health -> registry to health },
        busy,
        message,
        openUri,
        review,
    ) { (registry, health), isBusy, msg, uri, pending ->
        ArchModulesState(
            loading = registry == null,
            enabled = registry?.enabled ?: false,
            modules = registry?.modules ?: emptyList(),
            catalogs = registry?.catalogs ?: emptyList(),
            health = health,
            busy = isBusy,
            message = msg?.first,
            messageArg = msg?.second,
            openUri = uri,
            review = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchModulesState())

    fun consumeMessage() {
        message.value = null
    }

    fun consumeOpenUri() {
        openUri.value = null
    }

    fun dismissReview() {
        review.value = null
    }

    fun setEnabled(on: Boolean) = run("all") {
        useCases.setEnabled(on)
        message.value = (if (on) R.string.arch_modules_enabled else R.string.arch_modules_disabled) to null
    }

    fun setModuleEnabled(id: String, on: Boolean) = run(id, silent = true) {
        useCases.enable(id, on)
    }

    fun move(id: String, up: Boolean) = run(id, silent = true) {
        val ids = state.value.modules.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = if (up) from - 1 else from + 1
        if (from in ids.indices && to in ids.indices) {
            ids.add(to, ids.removeAt(from))
            useCases.reorder(ids)
        }
    }

    fun remove(id: String, catalog: Boolean) = run(id) {
        useCases.remove(id, catalog)
        message.value = R.string.arch_modules_removed to null
    }

    fun check(id: String) = run(id) {
        useCases.check(id)
    }

    fun checkAll() = run("all", silent = true) {
        state.value.modules.forEach { useCases.check(it.id) }
    }

    fun importFile(uri: Uri) = run("all") {
        when (val prepared = useCases.file(uri)) {
            is ArchModuleImport.Provider -> review.value = prepared.toPending(null)
            is ArchModuleImport.Catalog -> {
                useCases.commit(prepared)
                message.value = R.string.arch_modules_catalog_added to null
            }
        }
    }

    fun importUrl(url: String, sha256: String = "") = run("all") {
        when (val prepared = useCases.url(url, sha256)) {
            is ArchModuleImport.Provider -> review.value = prepared.toPending(null)
            is ArchModuleImport.Catalog -> {
                useCases.commit(prepared)
                message.value = R.string.arch_modules_catalog_added to null
            }
        }
    }

    fun addCatalog(url: String) = run("all") {
        val prepared = useCases.catalog(url, null)
        useCases.commit(prepared)
        message.value = R.string.arch_modules_catalog_added to null
    }

    fun refreshCatalog(id: String) = run(id) {
        useCases.refresh(id)
        message.value = R.string.arch_modules_catalog_refresh_toast to null
    }

    fun removeCatalog(id: String) = run(id) {
        useCases.remove(id, catalog = true)
    }

    fun installFromCatalog(url: String, sha256: String) {
        importUrl(url, sha256)
    }

    fun confirmReview() {
        val pending = review.value ?: return
        review.value = null
        run("all") {
            useCases.commit(pending.import)
            message.value = R.string.arch_modules_installed to null
        }
    }

    fun saveSettings(id: String, values: Map<String, String>) = run(id) {
        useCases.save(id, values)
        message.value = R.string.arch_modules_saved to null
    }

    fun saveMetadata(id: String, name: String, author: String, description: String) = run(id) {
        useCases.saveMetadata(id, name, author, description)
        message.value = R.string.arch_modules_saved to null
    }

    fun authorize(id: String) = run(id) {
        val url = useCases.authorize(id)
        if (url == null) {
            message.value = R.string.arch_modules_already_authorized to null
        } else {
            openUri.value = url
        }
    }

    fun checkUpdate(id: String) = run(id) {
        val update = useCases.update(id)
        if (update == null) {
            message.value = R.string.arch_modules_no_update to null
        } else {
            review.value = update.toPending(id)
        }
    }

    private fun ArchModuleImport.Provider.toPending(replacementId: String?): ArchPendingInstall {
        val manifest = document.manifest
        return ArchPendingInstall(
            title = manifest.displayName.ifBlank { manifest.name },
            author = manifest.author,
            version = manifest.version,
            description = manifest.description,
            origin = document.origin.ifBlank { "file" },
            requiredSettings = manifest.settings.filter { it.required }.map { it.label.ifBlank { it.key } },
            alreadyInstalled = replacementId != null || state.value.modules.any {
                it.manifest.id == manifest.id
            },
            import = copy(replacementId = replacementId),
        )
    }

    private fun run(id: String, silent: Boolean = false, block: suspend () -> Unit) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = id
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: ModuleException) {
                if (!silent) message.value = failureMessage(error.failure) to error.diagnosticCode
            } catch (error: Exception) {
                if (!silent) message.value = R.string.arch_modules_action_failed to (error.message ?: error.javaClass.simpleName)
            } finally {
                busy.value = null
            }
        }
    }

    private fun failureMessage(failure: ModuleFailure): Int = when (failure) {
        ModuleFailure.NETWORK -> R.string.arch_modules_network_failed
        ModuleFailure.INVALID_DOCUMENT -> R.string.arch_modules_invalid_document
        ModuleFailure.INTEGRITY -> R.string.arch_modules_integrity_failed
        ModuleFailure.INVALID_CONFIGURATION -> R.string.arch_modules_configuration_failed
        ModuleFailure.UNSUPPORTED -> R.string.arch_modules_unsupported
        ModuleFailure.EXECUTION -> R.string.arch_modules_execution_failed
        ModuleFailure.TIMEOUT -> R.string.arch_modules_timeout
    }
}
