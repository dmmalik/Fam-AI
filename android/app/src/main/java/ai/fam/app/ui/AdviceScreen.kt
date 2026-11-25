package ai.fam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.fam.app.R
import ai.fam.app.data.AdviceText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdviceScreen(id: Long?, onBack: () -> Unit) {
    val loaded = rememberRecord(id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_open_advice)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded.loaded) return@Scaffold
        val record = loaded.event
        val advice = record?.adviceJson?.let { AdviceText.fromJson(it) }
        val leafCare = record?.leafAdviceJson?.let { AdviceText.fromJson(it) }?.takeIf { it.useful() }
        if (record == null || (advice == null && leafCare == null)) {
            Text(
                stringResource(R.string.advice_missing),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                record.leafLabel.ifBlank { record.displayName.ifBlank { record.label } },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (leafCare != null) {
                Text(stringResource(R.string.result_leaf_steps), style = MaterialTheme.typography.titleMedium)
                CareBlocks(leafCare)
            }
            if (advice != null && advice != leafCare) {
                Text(stringResource(R.string.settings_section_reply), style = MaterialTheme.typography.titleMedium)
                CareBlocks(advice)
            }
            if (showsEnglishNote(record.note)) {
                Text(stringResource(R.string.note_english))
            }
        }
    }
}

@Composable
fun CareBlocks(advice: AdviceText) {
    if (advice.summary.isNotBlank()) {
        AdviceBlock(stringResource(R.string.advice_summary), listOf(advice.summary))
    }
    if (advice.immediateActions.isNotEmpty()) {
        AdviceBlock(stringResource(R.string.advice_immediate), advice.immediateActions)
    }
    if (advice.treatment.isNotEmpty()) {
        AdviceBlock(stringResource(R.string.advice_treatment), advice.treatment)
    }
    if (advice.prevention.isNotEmpty()) {
        AdviceBlock(stringResource(R.string.advice_prevention), advice.prevention)
    }
    if (advice.whenToSeekHelp.isNotBlank()) {
        AdviceBlock(stringResource(R.string.advice_help), listOf(advice.whenToSeekHelp))
    }
}

@Composable
fun AdviceBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        lines.forEach { line ->
            Text(if (lines.size == 1) line else "• $line")
        }
    }
}
