// mochy → settings → integrations → sources: the addon servers and the remote
// libraries (WebDAV, SMB), ported from Neiro's SourcesScreen in shape and
// behaviour — add by address, test what was added, remove what stopped
// working; an addon's manifest says its name, a remote library's root listing
// says it answers.

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.muzo.AddonSources
import com.mochyy.mochymusic.remote.RemoteLibraries
import com.mochyy.mochymusic.remote.RemoteServer
import com.mochyy.mochymusic.remote.WebDavClient
import com.mochyy.mochymusic.remote.SmbLibrary
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import kotlinx.coroutines.launch

const val SourcesRoute = "sources_settings"

@Composable
fun SourcesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addonUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var addonsStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var servers by remember { mutableStateOf<List<RemoteServer>>(emptyList()) }
    var serverStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        addonUrls = AddonSources.urls(context)
        servers = RemoteLibraries.servers(context)
    }

    fun refresh() {
        scope.launch {
            addonUrls = AddonSources.urls(context)
            servers = RemoteLibraries.servers(context)
        }
    }

    /** an addon's manifest, asked of the address as typed */
    fun testAddon(base: String) {
        scope.launch {
            val manifest = AddonSources.manifest(base)
            addonsStatus = addonsStatus + (
                base to manifest.fold(
                    onSuccess = { "✓ ${it.name.ifBlank { "addon" }} ${it.version}".trim() },
                    onFailure = { it.message ?: "failed" },
                )
                )
        }
    }

    /** a remote library's root listing, the connection's own answer */
    fun testServer(server: RemoteServer) {
        scope.launch {
            val result = when (server.kind) {
                RemoteServer.Kind.WEBDAV -> WebDavClient.list(server.address, server.username, server.password)
                RemoteServer.Kind.SMB -> SmbLibrary.list(server, server.basePath)
            }
            serverStatus = serverStatus + (
                server.id to result.fold(
                    onSuccess = { "✓ ${it.size} entries" },
                    onFailure = { it.message ?: "failed" },
                )
                )
        }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.sources),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── the addon servers: plain HTTP, /manifest.json, /search, /stream ──
            SettingsGroup(
                title = stringResource(R.string.addon_sources),
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    addonUrls.forEach { base ->
                        add(
                            SettingsItem(
                                title = { Text(base.removePrefix("https://").removePrefix("http://"), maxLines = 1) },
                                descriptionText = addonsStatus[base],
                                icon = painterResource(R.drawable.extension),
                                iconShape = CircleShape,
                                onClick = { testAddon(base) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            AddonSources.setUrls(context, addonUrls - base)
                                            refresh()
                                        }
                                    }) { Text(stringResource(R.string.remove)) }
                                },
                            ),
                        )
                    }
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.add_addon_source)) },
                            descriptionText = stringResource(R.string.addon_sources_desc),
                            icon = painterResource(R.drawable.add),
                            iconShape = CircleShape,
                            onClick = { dialog = "addon" },
                        ),
                    )
                },
            )

            // ── the remote libraries: a WebDAV URL or an SMB share ──
            SettingsGroup(
                title = stringResource(R.string.remote_libraries),
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    servers.forEach { server ->
                        add(
                            SettingsItem(
                                title = { Text(server.id) },
                                descriptionText = serverStatus[server.id] ?: server.address,
                                icon = painterResource(R.drawable.cloud),
                                iconShape = CircleShape,
                                onClick = { testServer(server) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            RemoteLibraries.remove(context, server.id)
                                            refresh()
                                        }
                                    }) { Text(stringResource(R.string.remove)) }
                                },
                            ),
                        )
                    }
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.add_remote_library)) },
                            descriptionText = stringResource(R.string.remote_libraries_desc),
                            icon = painterResource(R.drawable.add),
                            iconShape = CircleShape,
                            onClick = { dialog = "webdav" },
                        ),
                    )
                },
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    when (dialog) {
        "addon" -> TextFieldDialog(
            title = { Text(stringResource(R.string.add_addon_source)) },
            placeholder = { Text("https://") },
            isInputValid = { it.startsWith("http://") || it.startsWith("https://") },
            onDone = { url ->
                scope.launch {
                    AddonSources.setUrls(context, addonUrls + url.trim())
                    refresh()
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "webdav" -> RemoteServerDialog(
            initialKind = RemoteServer.Kind.WEBDAV,
            onDone = { server ->
                scope.launch {
                    RemoteLibraries.upsert(context, server)
                    refresh()
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "smb" -> RemoteServerDialog(
            initialKind = RemoteServer.Kind.SMB,
            onDone = { server ->
                scope.launch {
                    RemoteLibraries.upsert(context, server)
                    refresh()
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

/** one server, its kind first, its address and credentials after — Neiro's editor shape */
@Composable
private fun RemoteServerDialog(
    initialKind: RemoteServer.Kind,
    onDone: (RemoteServer) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var id by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    DefaultDialog(onDismiss = onDismiss, title = { Text(stringResource(R.string.add_remote_library)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            listOf(RemoteServer.Kind.WEBDAV, RemoteServer.Kind.SMB).forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { kind = option }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(selected = kind == option, onClick = { kind = option })
                    Text(if (option == RemoteServer.Kind.WEBDAV) "WebDAV" else "SMB", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                if (kind == RemoteServer.Kind.WEBDAV) "https://cloud.example.com/remote.php/dav/files/me/Music"
                else "192.168.1.10",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RemoteField("Name", id) { id = it }
            if (kind == RemoteServer.Kind.WEBDAV) {
                RemoteField("Address", address) { address = it }
            } else {
                RemoteField("Host", address) { address = it }
                RemoteField("Share", share) { share = it }
            }
            RemoteField("Username", username) { username = it }
            RemoteField("Password", password) { password = it }
        }
        TextButton(
            enabled = id.isNotBlank() && address.isNotBlank() &&
                (kind == RemoteServer.Kind.WEBDAV || share.isNotBlank()),
            onClick = {
                onDone(
                    RemoteServer(
                        id = id.trim(),
                        kind = kind,
                        address = address.trim(),
                        share = share.trim(),
                        username = username.trim(),
                        password = password,
                    ),
                )
            },
        ) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun RemoteField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
    }
}
