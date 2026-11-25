package ai.fam.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.fam.app.R
import ai.fam.app.data.AdviceText
import ai.fam.app.data.AuditDatabase
import ai.fam.app.data.AuditEvent
import ai.fam.app.data.FarmerStore
import ai.fam.app.data.farmerSpeech
import ai.fam.app.data.shownPlantName
import ai.fam.app.data.speechForFarmer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(id: Long?, onBack: () -> Unit, onAdvice: (Long) -> Unit) {
    val event = rememberRecord(id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_capture)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        if (!event.loaded) {
            return@Scaffold
        }
        val record = event.event
        if (record == null) {
            Text(
                stringResource(R.string.record_missing),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (record.thumbnailPath.isNotBlank()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(record.thumbnailPath))
                        .size(320)
                        .build(),
                    contentDescription = stringResource(R.string.cd_thumb),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(160.dp),
                )
            }
            val advice = AdviceText.fromJson(record.adviceJson)
            val leafCare = AdviceText.fromJson(record.leafAdviceJson)?.takeIf { it.useful() }
                ?: advice?.takeIf { record.adviceSource == "efficientnet" && it.useful() }
            val named = shownPlantName(record.displayName, record.label)
            val title = record.leafLabel.ifBlank { named }.ifEmpty { stringResource(R.string.plant_in_photo) }
            if (record.leafLabel.isNotBlank()) {
                Text(
                    stringResource(
                        R.string.result_leaf,
                        record.leafLabel,
                        (record.leafConfidence * 100).toInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (record.leafNext.isNotBlank()) {
                    Text(stringResource(R.string.result_leaf_also, record.leafNext))
                }
            } else if (named.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            if (leafCare != null) {
                Text(stringResource(R.string.result_leaf_steps), style = MaterialTheme.typography.titleMedium)
                CareBlocks(leafCare)
            }
            val appContext = LocalContext.current.applicationContext
            val farmerName = remember(appContext) { FarmerStore(appContext).read().name }
            FarmerListen(
                script = speechForFarmer(farmerName, farmerSpeech(title, leafCare ?: advice)),
                language = record.language,
            )
            Fact(stringResource(R.string.result_severity), severityName(record.severity))
            if (advice != null && advice != leafCare) {
                Text(stringResource(R.string.settings_section_reply), style = MaterialTheme.typography.titleMedium)
                CareBlocks(advice)
            }
            when (baseNote(record.note)) {
                "health" -> Text(stringResource(R.string.note_health))
                "pi_error" -> Text(stringResource(R.string.note_pi_error))
                "health_vision" -> Text(stringResource(R.string.note_health_vision))
                "pi_error_vision" -> Text(stringResource(R.string.note_pi_error_vision))
            }
            if (showsEnglishNote(record.note)) {
                Text(stringResource(R.string.note_english))
            }
            Text(
                "${record.backend} · ${record.latencyMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun severityName(severity: String): String {
    return when (severity) {
        "low" -> stringResource(R.string.severity_low)
        "medium" -> stringResource(R.string.severity_medium)
        "high" -> stringResource(R.string.severity_high)
        else -> severity
    }
}

data class LoadedRecord(val loaded: Boolean, val event: AuditEvent?)

@Composable
fun rememberRecord(id: Long?): LoadedRecord {
    val app = LocalContext.current.applicationContext as Application
    var state by remember(id) { mutableStateOf(LoadedRecord(false, null)) }
    LaunchedEffect(id) {
        val event = if (id == null) {
            null
        } else {
            withContext(Dispatchers.IO) { AuditDatabase.get(app).auditDao().find(id) }
        }
        state = LoadedRecord(true, event)
    }
    return state
}

fun showsEnglishNote(note: String): Boolean {
    return note == "english_note" || note.endsWith("+english")
}

fun baseNote(note: String): String {
    return when {
        note == "english_note" -> ""
        note.endsWith("+english") -> note.removeSuffix("+english")
        else -> note
    }
}
