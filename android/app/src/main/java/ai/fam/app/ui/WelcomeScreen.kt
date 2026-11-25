package ai.fam.app.ui

import android.app.Application
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.fam.app.R
import ai.fam.app.data.FarmerProfile
import ai.fam.app.data.FarmerStore
import ai.fam.app.data.ShareRules
import ai.fam.app.data.ShareSync
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WelcomeScreen(padding: PaddingValues, onDone: () -> Unit) {
    val viewModel: WelcomeViewModel = viewModel()
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.welcome_notice))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.welcome_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text(stringResource(R.string.welcome_mobile)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceRow(
            selected = register,
            label = stringResource(R.string.welcome_register),
            description = "role-register",
            onClick = { register = true },
        )
        Text(stringResource(R.string.welcome_register_help))
        ChoiceRow(
            selected = !register,
            label = stringResource(R.string.welcome_guest),
            description = "role-guest",
            onClick = {
                register = false
                codeSent = false
            },
        )
        Text(stringResource(R.string.welcome_guest_help))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = share, onCheckedChange = { share = it })
            Text(stringResource(R.string.welcome_share))
        }
        Text(stringResource(R.string.welcome_share_help))
        if (register && codeSent) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(8) },
                label = { Text(stringResource(R.string.welcome_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val local = ShareRules.localMobile(mobile)
                if (name.isBlank() || local.isEmpty()) {
                    error = R.string.welcome_mobile_bad
                    return@Button
                }
                busy = true
                error = null
                if (!register) {
                    viewModel.saveGuest(name, local, share)
                    onDone()
                    return@Button
                }
                if (!viewModel.canText()) {
                    error = R.string.welcome_setup_missing
                    busy = false
                    return@Button
                }
                if (!codeSent) {
                    viewModel.sendCode(name, local) { ok ->
                        busy = false
                        if (ok) codeSent = true else error = R.string.welcome_code_failed
                    }
                    return@Button
                }
                viewModel.checkCode(name, local, code, share) { ok ->
                    busy = false
                    if (ok) onDone() else error = R.string.welcome_code_failed
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    when {
                        !register -> R.string.welcome_continue
                        codeSent -> R.string.welcome_check_code
                        else -> R.string.welcome_send_code
                    },
                ),
            )
        }
    }
}

class WelcomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = FarmerStore(app)

    fun canText(): Boolean = ShareSync.key().isNotBlank()

    fun saveGuest(name: String, mobile: String, share: Boolean) {
        store.write(
            FarmerProfile(
                name = name.trim(),
                mobile = mobile,
                role = FarmerProfile.ROLE_GUEST,
                mobileVerified = false,
                noticeAccepted = true,
                shareTraining = share,
                consentAt = Instant.now().toString(),
            ),
        )
    }

    fun sendCode(name: String, mobile: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ShareSync.startRegistration(name, mobile) != null
            }
            done(ok)
        }
    }

    fun checkCode(name: String, mobile: String, code: String, share: Boolean, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { ShareSync.checkRegistration(mobile, code) }
            if (token == null) {
                done(false)
                return@launch
            }
            store.write(
                FarmerProfile(
                    name = name.trim(),
                    mobile = mobile,
                    role = FarmerProfile.ROLE_REGISTERED,
                    mobileVerified = true,
                    sessionToken = token,
                    noticeAccepted = true,
                    shareTraining = share,
                    consentAt = Instant.now().toString(),
                ),
            )
            done(true)
        }
    }
}
