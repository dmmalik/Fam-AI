package ai.fam.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.fam.app.R
import ai.fam.app.data.DownloadGate
import ai.fam.app.data.ModelDownload
import ai.fam.app.data.ModelFetch
import ai.fam.app.data.ModelReferenceStore
import ai.fam.app.data.NetLink
import ai.fam.app.data.PhoneModelFiles

@Composable
fun ModelBanner(onRetry: () -> Unit) {
    val fetch by ModelDownload.state.collectAsStateWithLifecycle()
    val link by ModelDownload.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val needsNotice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var allowed by rememberSaveable {
        mutableStateOf(notificationAllowed(context, needsNotice))
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it || notificationAllowed(context, needsNotice)
    }
    val stillFetching = fetch !is ModelFetch.Ready
    LaunchedEffect(stillFetching, allowed) {
        if (needsNotice && !allowed && stillFetching && !asked) {
            asked = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(
                when (link) {
                    NetLink.ONLINE -> R.string.net_connected
                    NetLink.OFFLINE -> R.string.net_offline
                    NetLink.CHECKING -> R.string.net_checking
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        when (val current = fetch) {
            is ModelFetch.Ready -> InstalledModelChoices(current)
            is ModelFetch.Running -> RunningLine(current)
            is ModelFetch.Waiting -> {
                Text(savedOrWaiting(current.done, waiting = true))
                TransferActions(context, onRetry, current.done)
            }
            is ModelFetch.Paused -> {
                Text(pausedText(current))
                TransferActions(context, onRetry, current.done)
            }
            is ModelFetch.Failed -> {
                val waiting = current.offline || link != NetLink.ONLINE
                Text(
                    savedOrWaiting(current.done, waiting),
                    color = if (waiting) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                TransferActions(context, onRetry, current.done)
            }
        }
        if (stillFetching) DownloadChoices(context)
        if (needsNotice && !allowed && stillFetching) {
            Text(stringResource(R.string.notify_download_reason))
            Button(
                onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.notify_download_allow))
            }
        }
    }
}

@Composable
private fun pausedText(paused: ModelFetch.Paused): String {
    val saved = PhoneModelFiles.sizeText(paused.done)
    val withSaved = paused.done > 0L
    return when (paused.gate) {
        DownloadGate.UNMETERED -> if (withSaved) {
            stringResource(R.string.download_paused_wifi, saved)
        } else {
            stringResource(R.string.download_paused_wifi_none)
        }
        DownloadGate.SAVER -> if (withSaved) {
            stringResource(R.string.download_paused_saver, saved)
        } else {
            stringResource(R.string.download_paused_saver_none)
        }
        else -> if (withSaved) {
            stringResource(R.string.download_paused_background, saved)
        } else {
            stringResource(R.string.download_paused_background_none)
        }
    }
}

@Composable
private fun DownloadChoices(context: Context) {
    val choices by ModelDownload.choices.collectAsStateWithLifecycle()
    Text(stringResource(R.string.download_disclosure))
    ChoiceSwitch(
        label = stringResource(R.string.download_background),
        help = stringResource(R.string.download_background_help),
        checked = choices.background,
        onChecked = { ModelDownload.setBackground(context, it) },
    )
    ChoiceSwitch(
        label = stringResource(R.string.download_mobile),
        help = stringResource(R.string.download_mobile_help),
        checked = choices.mobile,
        onChecked = { ModelDownload.setMobile(context, it) },
    )
    if (ModelDownload.dataSaverOn(context)) {
        Text(stringResource(R.string.download_paused_saver_none))
        OutlinedButton(onClick = { openBackgroundDataSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.download_allow_background))
        }
    }
}

@Composable
private fun ChoiceSwitch(label: String, help: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    Text(help, style = MaterialTheme.typography.bodySmall)
}

private fun openBackgroundDataSettings(context: Context) {
    val page = Uri.parse("package:${context.packageName}")
    val system = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, page)
    try {
        context.startActivity(system)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page))
    }
}

@Composable
private fun TransferActions(context: Context, onResume: () -> Unit, saved: Long) {
    val canRun = ModelDownload.currentGate(context) == DownloadGate.RUN
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    if (saved > 0L) {
        ResumeDownload(onResume, canRun)
    }
    val retryButton: @Composable () -> Unit = {
        Text(stringResource(R.string.settings_model_retry))
    }
    if (saved > 0L) {
        OutlinedButton(
            onClick = { confirmRestart = true },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            retryButton()
        }
    } else {
        Button(
            onClick = { ModelDownload.restart(context) },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            retryButton()
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.settings_model_retry)) },
            text = { Text(stringResource(R.string.download_restart_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestart = false
                        ModelDownload.restart(context)
                    },
                ) {
                    Text(stringResource(R.string.download_restart_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) {
                    Text(stringResource(R.string.model_choice_later))
                }
            },
        )
    }
}

@Composable
private fun ResumeDownload(onResume: () -> Unit, online: Boolean) {
    Button(onClick = onResume, enabled = online, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_model_resume))
    }
}

@Composable
private fun InstalledModelChoices(current: ModelFetch.Ready) {
    val context = LocalContext.current
    val store = remember { ModelReferenceStore(context) }
    val size = PhoneModelFiles.sizeText(current.bytes)
    var referenced by remember(current.name, current.bytes) {
        mutableStateOf(store.matches(current.name, current.bytes))
    }
    var asking by rememberSaveable(current.name, current.bytes) { mutableStateOf(!store.matches(current.name, current.bytes)) }
    var confirmingUninstall by rememberSaveable { mutableStateOf(false) }
    fun keepInstalled() {
        store.remember(current.name, current.bytes)
        referenced = true
        asking = false
    }
    Text(stringResource(R.string.settings_model_present, current.name))
    Text(
        if (referenced) {
            stringResource(R.string.model_referenced, current.name, size)
        } else {
            stringResource(R.string.model_installed_ask, current.name, size)
        },
    )
    Button(onClick = ::keepInstalled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.model_update_keep))
    }
    OutlinedButton(onClick = { confirmingUninstall = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.model_uninstall_app))
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.model_download_title)) },
            text = { Text(stringResource(R.string.model_update_note, current.name, size)) },
            confirmButton = {
                TextButton(onClick = ::keepInstalled) {
                    Text(stringResource(R.string.model_update_keep))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.model_choice_later))
                }
            },
        )
    }
    if (confirmingUninstall) {
        AlertDialog(
            onDismissRequest = { confirmingUninstall = false },
            title = { Text(stringResource(R.string.model_uninstall_app)) },
            text = { Text(stringResource(R.string.model_uninstall_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUninstall = false
                        asking = false
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")),
                        )
                    },
                ) {
                    Text(stringResource(R.string.model_uninstall_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUninstall = false }) {
                    Text(stringResource(R.string.model_choice_later))
                }
            },
        )
    }
}

@Composable
private fun RunningLine(current: ModelFetch.Running) {
    val percent = current.percent
    val done = PhoneModelFiles.sizeText(current.done)
    Text(
        if (percent == null) {
            stringResource(R.string.settings_model_amount_unknown, done)
        } else {
            stringResource(
                R.string.settings_model_amount,
                done,
                PhoneModelFiles.sizeText(current.total),
                percent,
            )
        },
    )
    Text(
        if (current.speedBps <= 0L) {
            stringResource(R.string.settings_model_speed_wait)
        } else {
            stringResource(R.string.settings_model_speed, PhoneModelFiles.speedText(current.speedBps))
        },
    )
    if (percent == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun savedOrWaiting(done: Long, waiting: Boolean): String {
    val saved = PhoneModelFiles.sizeText(done)
    return when {
        waiting && done > 0L -> stringResource(R.string.settings_model_waiting_saved, saved)
        waiting -> stringResource(R.string.settings_model_waiting)
        done > 0L -> stringResource(R.string.settings_model_failed_saved, saved)
        else -> stringResource(R.string.settings_model_failed)
    }
}

private fun notificationAllowed(context: android.content.Context, needsNotice: Boolean): Boolean {
    if (!needsNotice) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
