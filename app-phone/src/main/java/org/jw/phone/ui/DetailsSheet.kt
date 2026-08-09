package org.jw.phone.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.jw.tv.api.MediaItem
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.formatDuration

/**
 * Video details as a modal bottom sheet — touch port of the TV
 * VideoDetailsDialog: quality chips, Play, per-quality Download, Favorite,
 * and continue-watching progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailsSheet(
    video: MediaItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onPlay: (selectedResolution: String?) -> Unit
) {
    var chosenResolution by remember { mutableStateOf<String?>(null) }
    val resolutions = remember(video) {
        video.files?.mapNotNull { it.label }?.distinct() ?: emptyList()
    }
    val isFavorite = video.contentId in viewModel.favoriteContentIds

    LaunchedEffect(Unit) {
        // Priority: downloaded quality > user default > Auto (same as TV).
        val downloadedRes = resolutions.firstOrNull {
            "${video.contentId}|${it}" in viewModel.downloadedQualities
        }
        if (downloadedRes != null) {
            chosenResolution = downloadedRes
        } else {
            val def = viewModel.defaultQuality
            if (def != null && def in resolutions) chosenResolution = def
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.getThumbnailUrl())
                        .size(640, 360)
                        .build(),
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Color(0xFF1A1F27), RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                video.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val dur = formatDuration(video.duration)
            if (dur.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "$dur • HD • Video",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            // Continue Watching progress
            val progFrac = viewModel.watchProgressMap[video.contentId]
            if (progFrac != null && progFrac > 0.01f) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progFrac },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66000000),
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Resume from ${formatDuration((video.duration ?: 0.0) * progFrac.toDouble())}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Quality / Calidad:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allQualities = listOf(null) + resolutions
                allQualities.forEach { res ->
                    val label = res ?: "Auto"
                    val isSelected =
                        if (res == null) chosenResolution == null else chosenResolution == res
                    val isDownloadedRes = res != null &&
                            "${video.contentId}|${res}" in viewModel.downloadedQualities
                    FilterChip(
                        selected = isSelected,
                        onClick = { chosenResolution = res },
                        label = { Text(if (isDownloadedRes) "$label ✓" else label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = Color.White,
                            selectedContainerColor = Color(0xFF34495E),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (viewModel.isDownloadingVideo) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { viewModel.videoDownloadProgress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66000000),
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onPlay(chosenResolution) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("▶ Play Now", fontWeight = FontWeight.Bold)
                }

                val isQualityDownloaded = chosenResolution != null &&
                        "${video.contentId}|${chosenResolution}" in viewModel.downloadedQualities
                if (chosenResolution != null) {
                    if (isQualityDownloaded) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("✓ Saved", color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.startVideoDownload(video, chosenResolution!!)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E5C8C),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("⬇", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.toggleFavorite(video) },
                    modifier = Modifier.size(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        if (isFavorite) "♥" else "♡",
                        color = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}
