package org.jw.phone.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jw.phone.BuildConfig
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.formatBytes

/**
 * Settings tab. Port of the TV SettingsDialog: default quality, language,
 * manage downloads, manual update check.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenLanguage: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val qualities = listOf("Auto", "144p", "240p", "360p", "480p", "720p", "1080p", "2160p")
    val context = LocalContext.current
    val settingsVersionCode = remember {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                pkg.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") pkg.versionCode
        } catch (e: Exception) { 1 }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Default Quality",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            qualities.forEach { q ->
                val isSelected = if (q == "Auto") viewModel.defaultQuality == null
                else viewModel.defaultQuality == q
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.updateDefaultQuality(if (q == "Auto") null else q)
                    },
                    label = { Text(q) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = Color(0xFF34495E),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        SettingsRow(
            title = "Media language",
            subtitle = viewModel.selectedLanguageName,
            onClick = onOpenLanguage
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            title = "Subtitle language",
            subtitle = viewModel.subtitleLanguageLabel,
            onClick = onOpenSubtitles
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            title = "Manage Downloads",
            subtitle = "${viewModel.allDownloads.size} downloaded · " +
                formatBytes(viewModel.downloadSizes.values.sum() + viewModel.unlistedDownloadBytes),
            onClick = onOpenDownloads
        )

        Spacer(Modifier.height(28.dp))

        // Self-update (issue #7): phone channel configured in JwPhoneApp.
        Button(
            onClick = {
                viewModel.checkForUpdates(settingsVersionCode, manual = true)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check for Update", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
