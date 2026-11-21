// The Muzo Data API key editor, ported from Neiro's ui/screens/MuzoApiKeyEditor.kt:
// the same test-before-save contract (a key is only stored once it has actually
// answered, and the answer it gives is the quota line, which is more use than a
// bare "connected") and the same blank-key-means-forget affordance.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.muzo.MuzoApiCheck
import com.mochyy.mochymusic.muzo.MuzoService
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.OutlinedTextInput
import kotlinx.coroutines.launch

@Composable
fun MuzoApiKeyDialog(
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // a draft of the stored key, seeded once: re-seeding from the store would
    // overwrite typing the first time anything else touched it
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }

    // resolved here rather than in the coroutine, because strings can only be
    // asked for in composition
    val rejected = stringResource(R.string.muzo_api_rejected)
    val unreachable = stringResource(R.string.source_unreachable)

    /** the usage question with the key as typed, then the verdict — the quota line, not "connected" */
    val test: (onGood: () -> Unit) -> Unit = { onGood ->
        val key = input.text.trim()
        if (key.isBlank()) {
            status = rejected.format(MuzoService.NO_KEY_PROMPT) to false
        } else {
            busy = true
            status = null
            scope.launch {
                val check = runCatching { MuzoService.checkApi(key) }
                    .getOrElse { MuzoApiCheck.Unreachable(it.message ?: "Failed") }
                busy = false
                status = when (check) {
                    is MuzoApiCheck.Ok -> MuzoService.usageDetail(check) to true
                    is MuzoApiCheck.Rejected -> rejected.format(check.reason) to false
                    is MuzoApiCheck.Unreachable -> unreachable.format(check.reason) to false
                }
                if (check is MuzoApiCheck.Ok) onGood()
            }
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.muzo_api_key)) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.muzo_api_key_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = input,
                onValueChange = {
                    input = it
                    // a result describes the key it was run against, so editing it clears the result
                    status = null
                },
                placeholder = { Text(stringResource(R.string.muzo_api_key_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            status?.let { (line, good) ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { test {} },
                    enabled = !busy && input.text.isNotBlank(),
                ) {
                    Text(stringResource(R.string.test))
                }
                Button(
                    onClick = {
                        test {
                            scope.launch {
                                MuzoService.setApiKey(context, input.text.trim())
                                onSaved()
                            }
                        }
                    },
                    enabled = !busy && input.text.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        MuzoService.setApiKey(context, "")
                        onSaved()
                    }
                },
            ) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}
