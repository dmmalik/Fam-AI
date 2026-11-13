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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ai.fam.app.data.KnowledgeBase
import ai.fam.app.data.LANGUAGES
import ai.fam.app.data.ModelDownload
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
        Text(stringResource(R.string.settings_mode), style = MaterialTheme.typography.titleMedium)
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
        ModelBanner(onRetry = viewModel::retryModel)
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
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LANGUAGES.forEach { code ->
            ChoiceRow(
                selected = state.language == code,
                label = languageName(code),
                description = "language-$code",
                onClick = { viewModel.setLanguage(code) },
            )
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
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit, description: String = "") {
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
private fun languageName(code: String): String {
    return when (code) {
        "hi" -> stringResource(R.string.lang_hi)
        "sw" -> stringResource(R.string.lang_sw)
        else -> stringResource(R.string.lang_en)
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
                    REGION_IDS.associateWith { kb.regionName(it, current.language) }
                } catch (_: Exception) {
                    emptyMap()
                }
                LoadedSettings(current, token, names)
            }
            _state.update {
                it.copy(
                    mode = loaded.current.mode,
                    address = loaded.current.address,
                    token = loaded.token,
                    language = loaded.current.language,
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
        val language = if (ui.language in LANGUAGES) ui.language else "en"
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
                    REGION_IDS.associateWith { kb.regionName(it, language) }
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
