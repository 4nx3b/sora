// CanvasCheckDialog.kt
// Neiro's "Canvas check": one question, asked of every provider at once — do you answer at all?
// Each provider is asked for a song it is known to carry; a call that finishes says the service is
// reachable whatever it found, a timeout or a network error says it isn't. Spotify answers only
// when its session is connected.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.SpotifyCanvasKey
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.applecanvas.AppleMusicCanvasProvider
import com.mochyy.mochymusic.canvas.MonochromeApiCanvas
import com.mochyy.mochymusic.arch.ArchQualityTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import com.mochyy.mochymusic.utils.get

/** what one provider's check came to */
private enum class CheckState { RUNNING, REACHABLE, UNREACHABLE, NOT_CONNECTED }

private const val ProbeTimeoutMs = 15_000L
// a song every canvas provider indexes; only the call matters, not the answer
private const val ProbeSong = "Sunflower"
private const val ProbeArtist = "Post Malone"

@Composable
fun CanvasCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val spotifyConnected = remember { context.dataStore[SpotifyCanvasKey] ?: false }
    var states by remember {
        mutableStateOf(
            listOf(
                "Apple Music" to CheckState.RUNNING,
                "Tidal" to CheckState.RUNNING,
                "Spotify" to if (spotifyConnected) CheckState.RUNNING else CheckState.NOT_CONNECTED,
                "Muzo Data" to CheckState.RUNNING,
            ),
        )
    }

    LaunchedEffect(Unit) {
        val answers = coroutineScope {
            listOf(
                async(Dispatchers.IO) {
                    "Apple Music" to runCatching {
                        withTimeoutOrNull(ProbeTimeoutMs) {
                            AppleMusicCanvasProvider.getBySongArtist(ProbeSong, ProbeArtist)
                        }
                    }.fold(
                        onSuccess = { CheckState.REACHABLE },
                        onFailure = { CheckState.UNREACHABLE },
                    )
                },
                async(Dispatchers.IO) {
                    "Tidal" to runCatching {
                        withTimeoutOrNull(ProbeTimeoutMs) {
                            MonochromeApiCanvas.getBySongArtist(ProbeSong, ProbeArtist)
                        }
                    }.fold(
                        onSuccess = { CheckState.REACHABLE },
                        onFailure = { CheckState.UNREACHABLE },
                    )
                },
                async(Dispatchers.IO) {
                    if (!spotifyConnected) return@async "Spotify" to CheckState.NOT_CONNECTED
                    "Spotify" to runCatching {
                        withTimeoutOrNull(ProbeTimeoutMs) {
                            com.mochyy.mochymusic.canvas.SpotifyCanvasProvider
                                .getBySongArtist(context, ProbeSong, ProbeArtist, null)
                        }
                    }.fold(
                        onSuccess = { CheckState.REACHABLE },
                        onFailure = { CheckState.UNREACHABLE },
                    )
                },
                async(Dispatchers.IO) {
                    "Muzo Data" to runCatching {
                        withTimeoutOrNull(ProbeTimeoutMs) {
                            com.mochyy.mochymusic.canvas.MuzoCanvasProvider.getByVideoId("kJQP7kiw5Fk", ProbeSong, ProbeArtist)
                        }
                    }.fold(
                        onSuccess = { CheckState.REACHABLE },
                        onFailure = { CheckState.UNREACHABLE },
                    )
                },
            ).awaitAll()
        }
        states = states.map { (name, current) ->
            val answer = answers.firstOrNull { it.first == name }?.second
            name to (answer ?: if (current == CheckState.NOT_CONNECTED) CheckState.NOT_CONNECTED else CheckState.UNREACHABLE)
        }
    }

    DefaultDialog(onDismiss = onDismiss, title = { Text(stringResource(R.string.canvas_check)) }) {
        Text(
            text = stringResource(R.string.canvas_check_desc),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        Column {
            states.forEach { (name, state) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                ) {
                    Text(
                        text = name,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when (state) {
                            CheckState.RUNNING -> stringResource(R.string.canvas_check_running)
                            CheckState.REACHABLE -> stringResource(R.string.canvas_check_reachable)
                            CheckState.UNREACHABLE -> stringResource(R.string.canvas_check_unreachable)
                            CheckState.NOT_CONNECTED -> stringResource(R.string.canvas_check_not_connected)
                        },
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = when (state) {
                            CheckState.RUNNING -> Color.White.copy(alpha = 0.5f)
                            CheckState.REACHABLE -> Color(0xFF4CD964)
                            else -> Color(0xFFFF5A5F)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
