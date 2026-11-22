// mochy → settings → integrations → sources: the horizontal switch pill turns the page between
// the Sources tab — the order songs are looked for in, with a switch per source — and the
// Remote libraries tab, the WebDAV and SMB servers a library can also live on.

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.remote.RemoteLibraries
import com.mochyy.mochymusic.remote.RemoteServer
import com.mochyy.mochymusic.remote.WebDavClient
import com.mochyy.mochymusic.remote.SmbLibrary
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabSegmented
import com.mochyy.mochymusic.ui.screens.audiolab.ProviderOrder
import com.mochyy.mochymusic.ui.screens.audiolab.providerLogo
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import kotlinx.coroutines.launch

const val SourcesRoute = "sources_settings"

@Composable
fun SourcesScreen(
    navController: NavController,
    modules: ModulesViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(0) }
    var servers by remember { mutableStateOf<List<RemoteServer>>(emptyList()) }
    var serverStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { servers = RemoteLibraries.servers(context) }

    fun refresh() {
        scope.launch { servers = RemoteLibraries.servers(context) }
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
            // the horizontal switch pill the page turns on: the sources on one side, the remote libraries on the other
            LabSegmented(
                options = listOf(stringResource(R.string.sources_tab_sources), stringResource(R.string.sources_tab_libraries)),
                selectedIndex = tab,
                onSelect = { tab = it },
                height = 40.dp,
            )

            when (tab) {
                0 -> SourcesTab(
                    modules = modules,
                    onOpenModule = { id -> navController.navigate(com.mochyy.mochymusic.ui.screens.settings.modules.moduleRoute(id)) },
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                        icon = androidx.compose.ui.res.painterResource(R.drawable.cloud),
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
                                    icon = androidx.compose.ui.res.painterResource(R.drawable.add),
                                    iconShape = CircleShape,
                                    onClick = { dialog = "webdav" },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    when (dialog) {
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

// ── the Sources tab ─────────────────────────────────────────────────────────────────────────

/** the try-order card, a switch per source, and a row each for the sources the modules add */
@Composable
private fun SourcesTab(
    modules: ModulesViewModel,
    onOpenModule: (String) -> Unit,
) {
    val entries by modules.entries.collectAsState()
    val order by modules.order.collectAsState()
    val youTubeOn by modules.youTubeEnabled.collectAsState()

    val statusSignIn = stringResource(R.string.aq_status_sign_in)
    val statusSignInAgain = stringResource(R.string.aq_status_sign_in_again)
    val statusOn = stringResource(R.string.aq_status_on)
    val statusOff = stringResource(R.string.aq_status_off_tap)
    fun moduleStatus(entry: ModuleEntry): String = when {
        entry.account is AccountState.SignedOut -> statusSignIn
        entry.account is AccountState.Expired -> statusSignInAgain
        entry.enabled -> statusOn
        else -> statusOff
    }

    // every module that adds audio, on or off: a switched-off source keeps its row and its place
    val sourceModules = entries.filter { it.adds(ModuleFeature.AudioSource) }
    val usable = { id: String ->
        if (id == SourceModules.YouTubeId) youTubeOn
        else sourceModules.any { it.id == id && it.enabled && it.account is AccountState.SignedIn }
    }
    val shownOrder = order.filter { id -> id == SourceModules.YouTubeId || sourceModules.any { it.id == id } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // where a song is looked for first: drag a row by its handle and the others slide out of the way
        ProviderOrder(
            order = shownOrder,
            name = modules::nameOf,
            logo = ::providerLogo,
            usable = usable,
            status = { id ->
                if (id == SourceModules.YouTubeId) {
                    if (youTubeOn) statusOn else statusOff
                } else {
                    sourceModules.firstOrNull { it.id == id }?.let(::moduleStatus)
                }
            },
            onTap = { id ->
                // youtube switches right here; a module opens its own page, where its account lives
                if (id == SourceModules.YouTubeId) {
                    modules.setEnabled(id, !youTubeOn)
                } else {
                    onOpenModule(id)
                }
            },
            onReorder = modules::setVisibleOrder,
        )

        // one switch per source, in the same order; YouTube Music is the one source that's always here
        SettingsGroup(
            title = null,
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                add(
                    SettingsItem(
                        title = { Text("YouTube Music") },
                        descriptionText = if (youTubeOn) statusOn else statusOff,
                        icon = androidx.compose.ui.res.painterResource(R.drawable.provider_youtube_music),
                        iconShape = CircleShape,
                        onClick = { modules.setEnabled(SourceModules.YouTubeId, !youTubeOn) },
                        trailingContent = {
                            AppSwitch(checked = youTubeOn, onCheckedChange = { modules.setEnabled(SourceModules.YouTubeId, it) })
                        },
                    ),
                )
                sourceModules.forEach { entry ->
                    add(
                        SettingsItem(
                            title = { Text(entry.module.info.name) },
                            descriptionText = moduleStatus(entry),
                            icon = androidx.compose.ui.res.painterResource(providerLogo(entry.id) ?: R.drawable.extension),
                            iconShape = CircleShape,
                            onClick = { onOpenModule(entry.id) },
                            trailingContent = {
                                AppSwitch(
                                    checked = entry.enabled,
                                    onCheckedChange = { modules.setEnabled(entry.id, it) },
                                )
                            },
                        ),
                    )
                }
            },
        )

        Text(
            text = stringResource(R.string.sources_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
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
