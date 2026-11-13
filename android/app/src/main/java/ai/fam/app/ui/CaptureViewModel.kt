package ai.fam.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.fam.app.R
import ai.fam.app.data.DiagnoseOutcome
import ai.fam.app.data.DiagnoseRepository
import ai.fam.app.data.SettingsStore
import ai.fam.app.data.TokenStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CaptureUi(
    val crop: String? = null,
    val working: Boolean = false,
    val piConfigured: Boolean = false,
    val error: Int? = null,
    val openId: Long? = null,
)

class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DiagnoseRepository(app)
    private val settings = SettingsStore(app)
    private val tokens = TokenStore(app)
    private val _state = MutableStateFlow(CaptureUi())
    val state: StateFlow<CaptureUi> = _state

    init {
        refreshMode()
    }

    fun setCrop(crop: String?) {
        _state.update { it.copy(crop = crop) }
    }

    fun onFile(file: File, crop: String?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null, crop = crop) }
        viewModelScope.launch {
            val outcome = repository.diagnoseFile(file, crop)
            apply(outcome)
        }
    }

    fun onSample(crop: String?) {
        if (_state.value.working) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dest = File(getApplication<Application>().cacheDir, "sample_leaf.jpg")
                getApplication<Application>().assets.open("sample_leaf.jpg").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest
            }
            onFile(file, crop)
        }
    }

    fun onGallery(uri: Uri, crop: String?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null, crop = crop) }
        viewModelScope.launch {
            val outcome = repository.diagnoseUri(uri, crop)
            apply(outcome)
        }
    }

    fun consumeOpen() {
        _state.update { it.copy(openId = null) }
        refreshMode()
    }

    private fun apply(outcome: DiagnoseOutcome) {
        when (outcome) {
            is DiagnoseOutcome.Ready -> _state.update { it.copy(working = false, openId = outcome.id, error = null) }
            DiagnoseOutcome.Unreadable -> _state.update { it.copy(working = false, error = R.string.error_image) }
            DiagnoseOutcome.ModelMissing -> _state.update { it.copy(working = false, error = R.string.error_model_missing) }
            DiagnoseOutcome.ModelFailed -> _state.update { it.copy(working = false, error = R.string.error_model) }
        }
    }

    private fun refreshMode() {
        viewModelScope.launch {
            val configured = withContext(Dispatchers.IO) {
                val current = settings.read()
                current.mode == "pi" && tokens.get().isNotBlank()
            }
            _state.update { it.copy(piConfigured = configured) }
        }
    }
}
