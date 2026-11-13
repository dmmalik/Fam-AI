package ai.fam.app.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.fam.app.R
import ai.fam.app.data.AuditDatabase
import ai.fam.app.data.HistoryRow
import ai.fam.app.data.shownPlantName
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Composable
fun HistoryScreen(padding: PaddingValues, onOpen: (Long) -> Unit) {
    val viewModel: HistoryViewModel = viewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    if (rows.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.history_empty))
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            HistoryItem(row = row, onOpen = { onOpen(row.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onOpen: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(row.thumbnailPath))
                .size(160)
                .build(),
            contentDescription = stringResource(R.string.cd_thumb),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            val named = shownPlantName(row.displayName, row.label)
            Text(
                text = named.ifEmpty { stringResource(R.string.plant_in_photo) },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(severityName(row.severity))
            Text(row.backend, style = MaterialTheme.typography.bodySmall)
            Text(formatAuditTime(row.time), style = MaterialTheme.typography.bodySmall)
        }
    }
}

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    val rows: StateFlow<List<HistoryRow>> = AuditDatabase.get(app).auditDao().history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

fun formatAuditTime(value: String): String {
    return try {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        value
    }
}
