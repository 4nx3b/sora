// mochy → settings → integrations → sources: two tabs behind one pill. The Add-ons tab holds the
// addon servers and the remote libraries (WebDAV, SMB), ported from Neiro's SourcesScreen in shape
// and behaviour — add by address, test what was added, remove what stopped working. The Sources
// tab is Neiro's source list on sora's own sources: the order songs are looked for in, and a
// switch per source, with YouTube Music always there.

package com.mochyy.mochymusic.ui.screens.settings.integrations

import com.mochyy.mochymusic.ui.screens.settings.previews.SourcesPreview
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.muzo.AddonSources
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
import com.mochyy.mochymusic.ui.component.TextFieldDialog
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(0) }
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
        com.mochyy.mochymusic.ui.screens.gesture.PortedPreviewHeader { SourcesPreview() }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // the horizontal switch pill the page turns on: add-ons on one side, the playback sources on the other
            LabSegmented(
                options = listOf(stringResource(R.string.sources_tab_addons), stringResource(R.string.sources_tab_sources)),
                selectedIndex = tab,
                onSelect = { tab = it },
                height = 40.dp,
            )

            when (tab) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                }

                else -> SourcesTab(
                    modules = modules,
                    onAddPlugin = { dialog = "addon" },
                    onOpenModule = { id -> navController.navigate(com.mochyy.mochymusic.ui.screens.settings.modules.moduleRoute(id)) },
                )
            }
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

// ── the Sources tab ─────────────────────────────────────────────────────────────────────────

/** Neiro's source list on sora's sources: the try-order card, a switch per source, and the add-plugin door */
@Composable
private fun SourcesTab(
    modules: ModulesViewModel,
    onAddPlugin: () -> Unit,
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
                        icon = painterResource(R.drawable.provider_youtube_music),
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
                            icon = painterResource(providerLogo(entry.id) ?: R.drawable.extension),
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
                // the add plugin row: a new addon source joins the same list on the other tab
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.sources_add_plugin)) },
                        descriptionText = stringResource(R.string.sources_add_plugin_desc),
                        icon = painterResource(R.drawable.add),
                        iconShape = CircleShape,
                        onClick = onAddPlugin,
                    ),
                )
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
