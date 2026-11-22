/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported into sora: the signed-session callback receiver, on the port's own
 * sora://module-session deep link.
 */

package com.mochyy.mochymusic.arch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed interface ArchModuleAuthorizationState {
    data object Loading : ArchModuleAuthorizationState
    data object Success : ArchModuleAuthorizationState
    data object Empty : ArchModuleAuthorizationState
    data object Error : ArchModuleAuthorizationState
}

@HiltViewModel
internal class ArchModuleAuthorizationViewModel @Inject constructor(private val useCases: ArchModuleUseCases) : ViewModel() {
    private val mutableState = MutableStateFlow<ArchModuleAuthorizationState>(ArchModuleAuthorizationState.Empty)
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun complete(callback: String?) {
        if (operation?.isActive == true || mutableState.value == ArchModuleAuthorizationState.Success) return
        mutableState.value = ArchModuleAuthorizationState.Loading
        operation = viewModelScope.launch {
            try {
                requireNotNull(callback)
                useCases.completeAuthorization(callback)
                mutableState.value = ArchModuleAuthorizationState.Success
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ArchModuleAuthorizationState.Error
            }
        }
    }
}

@AndroidEntryPoint
class ArchModuleAuthorizationActivity : ComponentActivity() {
    private val viewModel: ArchModuleAuthorizationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accept(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ArchModuleAuthorizationState.Loading, ArchModuleAuthorizationState.Empty -> Unit
                        ArchModuleAuthorizationState.Success, ArchModuleAuthorizationState.Error -> {
                            Toast.makeText(
                                this@ArchModuleAuthorizationActivity,
                                if (state == ArchModuleAuthorizationState.Success) R.string.arch_modules_session_connected
                                else R.string.arch_modules_session_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                            if (state == ArchModuleAuthorizationState.Success) {
                                startActivity(
                                    Intent(this@ArchModuleAuthorizationActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                )
                            }
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(value: Intent) {
        val callback = value.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()
        value.data = null
        viewModel.complete(callback)
    }
}
