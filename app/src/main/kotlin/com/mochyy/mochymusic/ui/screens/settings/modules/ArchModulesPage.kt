// ArchModulesPage.kt
// The ArchiveTune tab of the modules page, ported from ArchiveTune's ModulesSettings and
// ModuleDetailsSettings: the master switch, the installed list with its priority order, the
// catalogs and their entries, the review before an install, and one details page per module
// with its configuration, its diagnostics and its maintenance actions.

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.arch.ArchInstalledModule
import com.mochyy.mochymusic.arch.ArchSavedCatalog
import com.mochyy.mochymusic.arch.ModuleCheckStatus
import com.mochyy.mochymusic.arch.ModuleHealth
import com.mochyy.mochymusic.arch.archQualityTags
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ArchModulesState
import com.mochyy.mochymusic.viewmodels.ArchModulesViewModel
import moe.rukamori.archivetune.modules.ModuleSetting
import moe.rukamori.archivetune.modules.ModuleSettingType
import java.text.DateFormat
import java.util.Date

/** the tab the modules page shows when its pill reads "archivetune" */
@Composable
fun ArchModulesPage(viewModel: ArchModulesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // the inner pages: a module's details, or one catalog's entries
    var openModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var openCatalogId by rememberSaveable { mutableStateOf<String?>(null) }

    // a message the page toasts once
    LaunchedEffect(state.message) {
        state.message?.let { id ->
            Toast.makeText(context, state.messageArg?.let { context.getString(id, it) } ?: context.getString(id), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    // a signed session's auth page, opened in the browser; the deep link brings the answer back
    LaunchedEffect(state.openUri) {
        state.openUri?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            viewModel.consumeOpenUri()
        }
    }

    val openModule = state.modules.firstOrNull { it.id == openModuleId }
    val openCatalog = state.catalogs.firstOrNull { it.id == openCatalogId }

    BackHandler(enabled = openModule != null || openCatalog != null) {
        openModuleId = null
        openCatalogId = null
    }

    when {
        openModule != null -> ArchModuleDetails(
            module = openModule,
            health = state.health[openModule.id],
            busy = state.busy == openModule.id,
            viewModel = viewModel,
            onBack = { openModuleId = null },
        )
        openCatalog != null -> ArchCatalogDetails(
            catalog = openCatalog,
            installedIds = state.modules.map { it.manifest.id }.toSet(),
            busy = state.busy == "all",
            viewModel = viewModel,
            onBack = { openCatalogId = null },
        )
        else -> ArchModulesList(state, viewModel, onOpenModule = { openModuleId = it }, onOpenCatalog = { openCatalogId = it })
    }

    state.review?.let { pending ->
        DefaultDialog(
            onDismiss = { viewModel.dismissReview() },
            title = { Text(stringResource(R.string.arch_modules_install_review)) },
        ) {
            Text(pending.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                pending.version.ifBlank { "—" } + " · " + pending.author.ifBlank { "—" },
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            Text(pending.description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                stringResource(R.string.arch_modules_origin, pending.origin),
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (pending.requiredSettings.isNotEmpty()) {
                Text(
                    stringResource(R.string.arch_modules_required_settings, pending.requiredSettings.joinToString()),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                stringResource(R.string.arch_modules_install_warning),
                fontSize = 11.sp, color = Color(0xFFFFB340),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.dismissReview() }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { viewModel.confirmReview() }) {
                    Text(
                        stringResource(if (pending.alreadyInstalled) R.string.arch_modules_replace else R.string.arch_modules_install),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── the list ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArchModulesList(
    state: ArchModulesState,
    viewModel: ArchModulesViewModel,
    onOpenModule: (String) -> Unit,
    onOpenCatalog: (String) -> Unit,
) {
    var urlDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    val enabledCount = state.modules.count { it.enabled }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // the master switch, with the page's whole story in one line
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.arch_modules_use)) },
                    descriptionText = stringResource(R.string.arch_modules_use_desc, enabledCount, state.modules.size),
                    icon = painterResource(R.drawable.extension),
                    iconShape = CircleShape,
                    onClick = { viewModel.setEnabled(!state.enabled) },
                    trailingContent = { AppSwitch(checked = state.enabled, onCheckedChange = { viewModel.setEnabled(it) }) },
                ),
            ),
        )

        // the installed modules, in the order they'd be asked
        if (state.modules.isNotEmpty()) {
            ArchSectionTitle(stringResource(R.string.arch_modules_installed))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = state.modules.mapIndexed { index, module ->
                    val busy = state.busy == module.id
                    SettingsItem(
                        title = { Text("${index + 1} · " + (module.metadata?.name ?: module.manifest.displayName.ifBlank { module.manifest.name })) },
                        descriptionText = checkStatusText(state.health[module.id], busy),
                        icon = painterResource(R.drawable.extension),
                        iconShape = CircleShape,
                        onClick = { onOpenModule(module.id) },
                        trailingContent = {
                            AppSwitch(checked = module.enabled, onCheckedChange = { viewModel.setModuleEnabled(module.id, it) })
                        },
                    )
                },
            )
        }

        // how modules arrive: a file, an address, or a catalog that lists them
        ArchSectionTitle(stringResource(R.string.arch_modules_add))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.arch_modules_add_url)) },
                    descriptionText = stringResource(R.string.arch_modules_add_url_desc),
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    onClick = { urlDialog = "module" },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.arch_modules_import_file)) },
                    descriptionText = stringResource(R.string.arch_modules_import_file_desc),
                    icon = painterResource(R.drawable.download),
                    iconShape = CircleShape,
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.arch_modules_add_catalog)) },
                    descriptionText = stringResource(R.string.arch_modules_add_catalog_desc),
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                    onClick = { urlDialog = "catalog" },
                ),
            ),
        )

        // the catalogs added, each one browsable
        if (state.catalogs.isNotEmpty()) {
            ArchSectionTitle(stringResource(R.string.arch_modules_catalogs))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = state.catalogs.map { catalog ->
                    SettingsItem(
                        title = { Text(catalog.name.ifBlank { catalog.url.removePrefix("https://").removePrefix("http://") }) },
                        descriptionText = stringResource(R.string.arch_modules_catalog_entries, catalog.entries.size),
                        icon = painterResource(R.drawable.cloud),
                        iconShape = CircleShape,
                        onClick = { onOpenCatalog(catalog.id) },
                        trailingContent = {
                            TextButton(onClick = { removing = catalog.id }) { Text(stringResource(R.string.remove)) }
                        },
                    )
                },
            )
        }

        Text(
            text = stringResource(R.string.arch_modules_page_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    when (urlDialog) {
        "module" -> TextFieldDialog(
            title = { Text(stringResource(R.string.arch_modules_add_url)) },
            placeholder = { Text("https://…/provider.arch") },
            isInputValid = { it.startsWith("http://") || it.startsWith("https://") },
            onDone = { url ->
                viewModel.importUrl(url.trim())
                urlDialog = null
            },
            onDismiss = { urlDialog = null },
        )
        "catalog" -> TextFieldDialog(
            title = { Text(stringResource(R.string.arch_modules_add_catalog)) },
            placeholder = { Text("https://…/catalog.json") },
            isInputValid = { it.startsWith("http://") || it.startsWith("https://") },
            onDone = { url ->
                viewModel.addCatalog(url.trim())
                urlDialog = null
            },
            onDismiss = { urlDialog = null },
        )
    }

    removing?.let { catalogId ->
        DefaultDialog(
            onDismiss = { removing = null },
            title = { Text(stringResource(R.string.arch_modules_remove_catalog_title)) },
        ) {
            Text(stringResource(R.string.arch_modules_remove_catalog_desc), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    viewModel.removeCatalog(catalogId)
                    removing = null
                }) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

// ── one module's details ────────────────────────────────────────────────────────────────────

@Composable
private fun ArchModuleDetails(
    module: ArchInstalledModule,
    health: ModuleHealth?,
    busy: Boolean,
    viewModel: ArchModulesViewModel,
    onBack: () -> Unit,
) {
    val manifest = module.manifest
    // the working copy of the settings; saved as one when it changed
    var values by remember(module.id, module.revision) { mutableStateOf(module.configuration) }
    var removing by remember { mutableStateOf(false) }
    var editingMetadata by remember { mutableStateOf(false) }
    val dirty = values != module.configuration
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // the identity card: name, version, author, origin, the quality tags, and the switch
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(module.metadata?.name ?: manifest.displayName.ifBlank { manifest.name }) },
                    descriptionText = listOfNotNull(
                        manifest.version.takeIf { it.isNotBlank() },
                        (module.metadata?.author ?: manifest.author).takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { module.origin },
                    icon = painterResource(R.drawable.extension),
                    iconShape = CircleShape,
                    onClick = { },
                    trailingContent = { AppSwitch(checked = module.enabled, onCheckedChange = { viewModel.setModuleEnabled(module.id, it) }) },
                ),
            ),
        )
        if (manifest.description.isNotBlank() || module.metadata?.description?.isNotBlank() == true) {
            Text(
                module.metadata?.description?.takeIf { it.isNotBlank() } ?: manifest.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        val tags = archQualityTags(manifest.labels, manifest.settings)
        if (tags.isNotEmpty()) {
            Text(
                tags.joinToString(" · ") { stringResource(if (it == com.mochyy.mochymusic.arch.ArchQualityTag.HI_RES) R.string.arch_modules_quality_hi_res else R.string.arch_modules_quality_max) },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // its place in the order the modules are asked in
        ArchSectionTitle(stringResource(R.string.arch_modules_position))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                stringResource(R.string.arch_modules_position_value, viewModel.state.value.modules.indexOfFirst { it.id == module.id } + 1),
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.move(module.id, up = true) }) { Text("↑") }
            TextButton(onClick = { viewModel.move(module.id, up = false) }) { Text("↓") }
        }

        // the module's own settings, as its manifest declares them
        if (manifest.settings.isNotEmpty()) {
            ArchSectionTitle(stringResource(R.string.arch_modules_configuration))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = manifest.settings.map { field -> settingRow(module, field, values[field.key] ?: field.defaultJson) { value -> values = values + (field.key to value) } },
            )
            if (dirty) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { values = module.configuration }) { Text(stringResource(R.string.reset)) }
                    TextButton(onClick = { viewModel.saveSettings(module.id, values) }) {
                        Text(stringResource(R.string.arch_modules_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // the last check, and what it said
        health?.check?.let { check ->
            ArchSectionTitle(stringResource(R.string.arch_modules_diagnostics))
            Text(
                checkStatusText(ModuleHealth(check, null), busy) +
                    (check.code.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // maintenance: check, authorize, update, edit, remove
        ArchSectionTitle(stringResource(R.string.arch_modules_actions))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.arch_modules_check)) },
                        icon = painterResource(R.drawable.speed),
                        iconShape = CircleShape,
                        enabled = !busy,
                        onClick = { viewModel.check(module.id) },
                    ),
                )
                if (manifest.authentication) {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.arch_modules_authorize)) },
                            descriptionText = stringResource(R.string.arch_modules_authorize_desc),
                            icon = painterResource(R.drawable.key),
                            iconShape = CircleShape,
                            enabled = !busy,
                            onClick = { viewModel.authorize(module.id) },
                        ),
                    )
                }
                if (module.updateUrl.isNotBlank()) {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.arch_modules_check_update)) },
                            icon = painterResource(R.drawable.update),
                            iconShape = CircleShape,
                            enabled = !busy,
                            onClick = { viewModel.checkUpdate(module.id) },
                        ),
                    )
                }
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.arch_modules_edit_metadata)) },
                        icon = painterResource(R.drawable.edit),
                        iconShape = CircleShape,
                        onClick = { editingMetadata = true },
                    ),
                )
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.arch_modules_remove), color = Color(0xFFFF5A5F)) },
                        icon = painterResource(R.drawable.delete),
                        iconShape = CircleShape,
                        onClick = { removing = true },
                    ),
                )
            },
        )

        // where the module came from, and its own links
        if (manifest.homepage.isNotBlank() || manifest.repository.isNotBlank()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    if (manifest.homepage.isNotBlank()) {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.arch_modules_homepage)) },
                                descriptionText = manifest.homepage,
                                icon = painterResource(R.drawable.link),
                                iconShape = CircleShape,
                                onClick = { runCatching { uriHandler.openUri(manifest.homepage) } },
                            ),
                        )
                    }
                    if (manifest.repository.isNotBlank()) {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.arch_modules_repository)) },
                                descriptionText = manifest.repository,
                                icon = painterResource(R.drawable.link),
                                iconShape = CircleShape,
                                onClick = { runCatching { uriHandler.openUri(manifest.repository) } },
                            ),
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(40.dp))
    }

    if (removing) {
        DefaultDialog(
            onDismiss = { removing = false },
            title = { Text(stringResource(R.string.arch_modules_remove_module_title)) },
        ) {
            Text(stringResource(R.string.arch_modules_remove_module_desc), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    viewModel.remove(module.id, catalog = false)
                    removing = false
                    onBack()
                }) { Text(stringResource(R.string.remove), color = Color(0xFFFF5A5F)) }
            }
        }
    }

    if (editingMetadata) {
        var name by remember { mutableStateOf(module.metadata?.name ?: manifest.displayName.ifBlank { manifest.name }) }
        var author by remember { mutableStateOf(module.metadata?.author ?: manifest.author) }
        var description by remember { mutableStateOf(module.metadata?.description ?: manifest.description) }
        DefaultDialog(
            onDismiss = { editingMetadata = false },
            title = { Text(stringResource(R.string.arch_modules_edit_metadata)) },
        ) {
            ArchField(stringResource(R.string.arch_modules_metadata_name), name) { name = it }
            ArchField(stringResource(R.string.arch_modules_metadata_author), author) { author = it }
            ArchField(stringResource(R.string.arch_modules_metadata_description), description) { description = it }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { editingMetadata = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.saveMetadata(module.id, name, author, description)
                        editingMetadata = false
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/** one declared setting, as the row its type asks to be */
@Composable
private fun settingRow(
    module: ArchInstalledModule,
    field: ModuleSetting,
    value: String,
    onChange: (String) -> Unit,
): SettingsItem = when (field.type) {
    ModuleSettingType.BOOLEAN -> SettingsItem(
        title = { Text(field.label.ifBlank { field.key }) },
        descriptionText = field.description.takeIf { it.isNotBlank() },
        icon = painterResource(R.drawable.tune),
        iconShape = CircleShape,
        onClick = { onChange(if (value == "true") "false" else "true") },
        trailingContent = { AppSwitch(checked = value == "true", onCheckedChange = { onChange(if (it) "true" else "false") }) },
    )
    ModuleSettingType.SELECT -> SettingsItem(
        title = { Text(field.label.ifBlank { field.key }) },
        descriptionText = field.description.takeIf { it.isNotBlank() },
        icon = painterResource(R.drawable.tune),
        iconShape = CircleShape,
        onClick = { },
        trailingContent = {
            LabSelect(
                options = field.options.map { it.label.ifBlank { it.valueJson } },
                selectedIndex = field.options.indexOfFirst { it.valueJson == value }.takeIf { it >= 0 } ?: 0,
                onSelect = { index -> onChange(field.options[index].valueJson) },
                contentDescription = field.label,
            )
        },
    )
    else -> SettingsItem(
        title = { Text(field.label.ifBlank { field.key }) },
        descriptionText = field.description.takeIf { it.isNotBlank() },
        icon = painterResource(R.drawable.tune),
        iconShape = CircleShape,
        onClick = { },
        trailingContent = {
            Box(Modifier.width(150.dp)) {
                ArchField(
                    label = "",
                    value = remember(value) { plainJson(value) },
                    onValueChange = { typed ->
                        onChange(
                            when (field.type) {
                                // the store keeps JSON: text is quoted, numbers are as typed
                                ModuleSettingType.PASSWORD, ModuleSettingType.TEXT -> org.json.JSONObject.quote(typed)
                                else -> typed
                            },
                        )
                    },
                    password = field.secret,
                    number = field.type == ModuleSettingType.NUMBER,
                )
            }
        },
    )
}

/** "null" and quoted json read as the plain text they were typed as */
private fun plainJson(value: String): String = when {
    value == "null" -> ""
    value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
    else -> value
}

@Composable
private fun ArchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    number: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (number) {
                androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            } else {
                androidx.compose.foundation.text.KeyboardOptions.Default
            },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

// ── one catalog's entries ───────────────────────────────────────────────────────────────────

@Composable
private fun ArchCatalogDetails(
    catalog: ArchSavedCatalog,
    installedIds: Set<String>,
    busy: Boolean,
    viewModel: ArchModulesViewModel,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ArchSectionTitle(catalog.name.ifBlank { catalog.url })
        Text(
            stringResource(
                R.string.arch_modules_catalog_refreshed,
                catalog.entries.size,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(catalog.refreshedAtMs)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TextButton(onClick = { viewModel.refreshCatalog(catalog.id) }) {
            Text(stringResource(R.string.arch_modules_refresh))
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = catalog.entries.map { entry ->
                SettingsItem(
                    title = { Text(entry.name.ifBlank { entry.id }) },
                    descriptionText = listOfNotNull(
                        entry.version.takeIf { it.isNotBlank() },
                        entry.author.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { entry.downloadUrl?.removePrefix("https://") ?: "" },
                    icon = painterResource(R.drawable.extension),
                    iconShape = CircleShape,
                    enabled = entry.supported,
                    onClick = {
                        if (entry.supported && !busy && entry.downloadUrl != null) {
                            viewModel.installFromCatalog(entry.downloadUrl, entry.sha256)
                        }
                    },
                    trailingContent = {
                        Text(
                            when {
                                !entry.supported -> stringResource(R.string.arch_modules_unsupported)
                                entry.id in installedIds -> stringResource(R.string.arch_modules_already_installed)
                                else -> stringResource(R.string.arch_modules_install)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                !entry.supported -> Color.White.copy(alpha = 0.4f)
                                entry.id in installedIds -> Color.White.copy(alpha = 0.6f)
                                else -> Color(0xFF4CD964)
                            },
                        )
                    },
                )
            },
        )
        Spacer(Modifier.height(40.dp))
    }
}

// ── the small pieces ────────────────────────────────────────────────────────────────────────

@Composable
private fun ArchSectionTitle(title: String) {
    Text(
        text = title,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.White,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun checkStatusText(health: ModuleHealth?, busy: Boolean): String {
    val check = health?.check
    return when {
        busy -> stringResource(R.string.arch_modules_checking)
        check == null -> stringResource(R.string.arch_modules_not_checked)
        else -> when (check.status) {
            ModuleCheckStatus.READY -> stringResource(R.string.arch_modules_ready)
            ModuleCheckStatus.CONFIGURATION -> stringResource(R.string.arch_modules_needs_configuration)
            ModuleCheckStatus.UNAVAILABLE -> stringResource(R.string.arch_modules_unavailable)
            ModuleCheckStatus.TIMEOUT -> stringResource(R.string.arch_modules_timeout)
            else -> stringResource(R.string.arch_modules_failed)
        }
    }
}
