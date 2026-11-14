package org.jw.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.formatBytes

/** Manage Downloads — touch port of the TV ManageDownloadsDialog. */
@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshDownloads() }

    val downloads = viewModel.allDownloads
    val unlistedBytes = viewModel.unlistedDownloadBytes
    val usedBytes = viewModel.downloadSizes.values.sum() + unlistedBytes
    var confirmDeleteAll by remember { mutableStateOf(false) }

    if (downloads.isEmpty() && unlistedBytes == 0L) {
        EmptyView("No downloaded videos.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Used ${formatBytes(usedBytes)} · Free ${formatBytes(viewModel.freeStorageBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { confirmDeleteAll = true }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(items = downloads, key = { it.contentId }) { dl ->
            DownloadCard(
                title = dl.title,
                detail = listOfNotNull(
                    dl.quality,
                    viewModel.downloadSizes[dl.contentId]?.let(::formatBytes)
                ).joinToString(" · "),
                onDelete = { viewModel.deleteDownload(dl.contentId) }
            )
        }
        if (unlistedBytes > 0) {
            item(key = "unlisted") {
                DownloadCard(
                    title = "Other files",
                    detail = "Interrupted or replaced downloads · ${formatBytes(unlistedBytes)}",
                    onDelete = { viewModel.deleteUnlistedDownloads() }
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This frees ${formatBytes(usedBytes)}. Videos will stream again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllDownloads()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DownloadCard(title: String, detail: String, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}
