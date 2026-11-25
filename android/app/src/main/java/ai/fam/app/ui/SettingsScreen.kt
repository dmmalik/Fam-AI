package ai.fam.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.fam.app.R
import ai.fam.app.data.AuditDatabase
import ai.fam.app.data.DeviceIdStore
import ai.fam.app.data.AppLanguage
import ai.fam.app.data.KnowledgeBase
import ai.fam.app.data.FarmerProfile
import ai.fam.app.data.FarmerStore
import ai.fam.app.data.LeafModel
import ai.fam.app.data.ShareSync
import ai.fam.app.data.LanguageCatalog
import ai.fam.app.data.LanguagePackDownload
import ai.fam.app.data.LanguagePackStore
import ai.fam.app.data.ModelDownload
import ai.fam.app.data.PackFetch
import ai.fam.app.data.PhoneModelFiles
import ai.fam.app.data.PiAddress
import ai.fam.app.data.REGION_IDS
import ai.fam.app.data.SettingsStore
import ai.fam.app.data.TokenStore
import ai.fam.app.data.auditEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUi(
    val mode: String = "local",
    val address: String = PiAddress.DEFAULT,
    val token: String = "",
    val language: String = "en",
    val languages: List<AppLanguage> = emptyList(),
    val regionId: String = "indo_gangetic",
    val regionNames: Map<String, String> = emptyMap(),
    val ready: Boolean = false,
    val saved: Boolean = false,
    val error: Int? = null,
)

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FarmerSection()
        Text(stringResource(R.string.settings_section_answer), style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            selected = state.mode == "local",
            label = stringResource(R.string.settings_local),
            description = "mode-local",
            onClick = { viewModel.setMode("local") },
        )
        ChoiceRow(
            selected = state.mode == "pi",
            label = stringResource(R.string.settings_pi),
            description = "mode-pi",
            onClick = { viewModel.setMode("pi") },
        )
        Text(stringResource(R.string.settings_mode_help))
        if (state.mode == "pi") {
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::setAddress,
                label = { Text(stringResource(R.string.settings_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "field-address" },
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::setToken,
                label = { Text(stringResource(R.string.settings_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "field-token" },
            )
        }
        LeafSection()
        Text(stringResource(R.string.settings_section_reply), style = MaterialTheme.typography.titleMedium)
        ModelBanner(onRetry = viewModel::retryModel)
        Text(stringResource(R.string.settings_section_place), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
        state.languages.forEach { language ->
            ChoiceRow(
                selected = state.language == language.code,
                label = language.autonym,
                description = "language-${language.code}",
                onClick = { viewModel.setLanguage(language.code) },
            )
        }
        val selected = state.languages.firstOrNull { it.code == state.language }
        if (selected != null && !selected.builtin) {
            LanguagePackPanel(selected, viewModel)
        }
        Text(stringResource(R.string.settings_region), style = MaterialTheme.typography.titleMedium)
        REGION_IDS.forEach { id ->
            ChoiceRow(
                selected = state.regionId == id,
                label = state.regionNames[id] ?: id,
                onClick = { viewModel.setRegion(id) },
            )
        }
        Button(
            onClick = viewModel::save,
            enabled = state.ready,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "save-settings" },
        ) {
            Text(stringResource(R.string.settings_save))
        }
        state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (state.saved) {
            Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FarmerSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { FarmerStore(context) }
    var profile by remember { mutableStateOf(store.read()) }
    Text(stringResource(R.string.settings_section_farmer), style = MaterialTheme.typography.titleMedium)
    Text(profile.name.ifBlank { stringResource(R.string.welcome_name) })
    Text(profile.mobile)
    Text(
        stringResource(
            if (profile.role == FarmerProfile.ROLE_REGISTERED) R.string.welcome_register else R.string.welcome_guest,
        ),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = profile.shareTraining,
            onCheckedChange = { checked ->
                if (checked) {
                    val next = profile.copy(shareTraining = true, pendingRemoteDelete = false)
                    store.write(next)
                    profile = store.read()
                } else {
                    ShareSync.withdraw(context)
                    profile = store.read()
                }
            },
        )
        Text(stringResource(R.string.welcome_share))
    }
    Text(stringResource(R.string.welcome_share_help))
}

@Composable
private fun LeafSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val leaf = remember { LeafModel.status(context) }
    Text(stringResource(R.string.settings_section_leaf), style = MaterialTheme.typography.titleMedium)
    if (leaf.present) {
        Text(stringResource(R.string.settings_leaf_body, leaf.classes))
    } else {
        Text(stringResource(R.string.settings_leaf_missing))
    }
    Text(stringResource(R.string.settings_leaf_about))
}

@Composable
fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit, description: String = "") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (description.isEmpty()) {
            Modifier
        } else {
            Modifier.semantics { contentDescription = description }
        },
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun LanguagePackPanel(language: AppLanguage, viewModel: SettingsViewModel) {
    val packs by LanguagePackDownload.state.collectAsStateWithLifecycle()
    val fetch = packs[language.code] ?: PackFetch.Missing
    var confirming by remember { mutableStateOf(false) }
    Text(stringResource(R.string.pack_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.pack_body, language.autonym))
    when (fetch) {
        is PackFetch.Ready -> {
            Text(stringResource(R.string.pack_installed))
            Text(stringResource(R.string.pack_size, PhoneModelFiles.sizeText(fetch.bytes)))
            OutlinedButton(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pack_remove))
            }
        }
        is PackFetch.Running -> {
            if (fetch.total > 0L) {
                LinearProgressIndicator(
                    progress = { (fetch.done.toFloat() / fetch.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val percent = ((100 * fetch.done) / fetch.total).toInt().coerceIn(0, 100)
                Text(stringResource(R.string.pack_downloading, percent))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.pack_downloading_unknown))
            }
        }
        PackFetch.NeedsPi -> Text(stringResource(R.string.pack_needs_pi))
        PackFetch.Waiting -> Text(stringResource(R.string.pack_waiting))
        is PackFetch.Paused -> Text(stringResource(R.string.pack_paused_wifi))
        is PackFetch.Failed -> {
            Text(
                if (fetch.needsPi) {
                    stringResource(R.string.pack_needs_pi)
                } else {
                    stringResource(R.string.pack_failed)
                },
            )
            Button(onClick = viewModel::downloadPack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pack_retry))
            }
        }
        PackFetch.Missing -> {
            Button(onClick = viewModel::downloadPack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pack_download))
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.pack_remove)) },
            text = { Text(stringResource(R.string.pack_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        viewModel.removePack()
                    },
                ) {
                    Text(stringResource(R.string.pack_remove_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.pack_not_now))
                }
            },
        )
    }
}

private data class LoadedSettings(
    val current: ai.fam.app.data.AppSettings,
    val token: String,
    val names: Map<String, String>,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val tokens = TokenStore(app)
    private val devices = DeviceIdStore(app)
    private val dao = AuditDatabase.get(app).auditDao()
    private val _state = MutableStateFlow(SettingsUi())
    val state: StateFlow<SettingsUi> = _state

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val current = settings.read()
                val token = tokens.get()
                val names = try {
                    val kb = KnowledgeBase.load(getApplication<Application>().assets)
                    val pack = LanguagePackStore(getApplication()).read(current.language, kb)
                    REGION_IDS.associateWith { kb.regionName(it, current.language, pack) }
                } catch (_: Exception) {
                    emptyMap()
                }
                LanguagePackDownload.refresh(getApplication())
                LoadedSettings(current, token, names)
            }
            _state.update {
                it.copy(
                    mode = loaded.current.mode,
                    address = loaded.current.address,
                    token = loaded.token,
                    language = loaded.current.language,
                    languages = LanguageCatalog.all(getApplication()),
                    regionId = loaded.current.regionId,
                    regionNames = loaded.names,
                    ready = true,
                )
            }
        }
    }

    fun retryModel() {
        ModelDownload.retry(getApplication())
    }

    fun setMode(mode: String) = _state.update { it.copy(mode = mode, saved = false) }
    fun setAddress(value: String) = _state.update { it.copy(address = value, saved = false) }
    fun setToken(value: String) = _state.update { it.copy(token = value, saved = false) }
    fun setLanguage(value: String) = _state.update { it.copy(language = value, saved = false) }

    fun downloadPack() {
        val ui = _state.value
        val saved = settings.read()
        val token = tokens.get()
        val address = PiAddress.parse(saved.address)
        if (token.isBlank() || address == null) {
            LanguagePackDownload.start(getApplication(), ui.language, "", "")
            return
        }
        LanguagePackDownload.start(getApplication(), ui.language, address.origin(), token)
    }

    fun removePack() {
        LanguagePackDownload.remove(getApplication(), _state.value.language)
    }
    fun setRegion(value: String) = _state.update { it.copy(regionId = value, saved = false) }

    fun save() {
        val ui = _state.value
        if (!ui.ready) return
        val parsed = PiAddress.parse(ui.address.trim().ifEmpty { PiAddress.DEFAULT })
        if (parsed == null) {
            _state.update { it.copy(error = R.string.settings_bad_address, saved = false) }
            return
        }
        val mode = if (ui.mode == "pi") "pi" else "local"
        val language = if (ui.languages.any { it.code == ui.language }) ui.language else "en"
        val region = if (ui.regionId in REGION_IDS) ui.regionId else "indo_gangetic"
        val origin = parsed.origin()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val old = settings.read()
                settings.write(
                    ai.fam.app.data.AppSettings(
                        mode = mode,
                        address = origin,
                        language = language,
                        regionId = region,
                    ),
                )
                tokens.set(ui.token)
                val deviceId = devices.get()
                if (old.language != language) {
                    dao.insert(
                        auditEvent(
                            deviceId = deviceId,
                            action = "language_change",
                            language = language,
                            region = region,
                        ),
                    )
                }
                if (old.regionId != region) {
                    dao.insert(
                        auditEvent(
                            deviceId = deviceId,
                            action = "region_change",
                            language = language,
                            region = region,
                        ),
                    )
                }
                if (old.mode != mode) {
                    dao.insert(
                        auditEvent(
                            deviceId = deviceId,
                            action = "mode_change",
                            language = language,
                            region = region,
                            backend = mode,
                        ),
                    )
                }
            }
            val names = withContext(Dispatchers.IO) {
                try {
                    val kb = KnowledgeBase.load(getApplication<Application>().assets)
                    val pack = LanguagePackStore(getApplication()).read(language, kb)
                    REGION_IDS.associateWith { kb.regionName(it, language, pack) }
                } catch (_: Exception) {
                    _state.value.regionNames
                }
            }
            _state.update {
                it.copy(
                    mode = mode,
                    address = origin,
                    language = language,
                    regionId = region,
                    regionNames = names,
                    saved = true,
                    error = null,
                )
            }
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != language) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
            }
        }
    }
}
