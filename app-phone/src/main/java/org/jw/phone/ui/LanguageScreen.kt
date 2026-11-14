package org.jw.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jw.tv.api.SubtitleLookup
import org.jw.tv.ui.MainViewModel

/**
 * Language picker — touch port of the TV LanguageSelectionDialog: search field
 * on top, Popular section, then all languages. Selection applies immediately
 * and pops back (same changeLanguage path as TV).
 */
@Composable
fun LanguageScreen(
    viewModel: MainViewModel,
    onLanguageChosen: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val popularCodes = listOf("E", "S", "T", "U", "K", "F", "X", "I")

    val popular = remember(viewModel.languagesList) {
        viewModel.languagesList.filter { it.code in popularCodes }
    }
    val filtered = remember(searchQuery, viewModel.languagesList) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) viewModel.languagesList
        else viewModel.languagesList.filter {
            it.name.lowercase().contains(q) || it.vernacular.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search...", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color(0xFF5A6B7C)
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (searchQuery.isEmpty()) {
                item(key = "popular_header") {
                    LanguageHeader("Popular")
                }
                items(items = popular, key = { "popular_${it.code}" }) { lang ->
                    LanguageRow("${lang.vernacular} (${lang.name})") {
                        viewModel.changeLanguage(lang.code, lang.vernacular)
                        onLanguageChosen()
                    }
                }
                item(key = "all_header") {
                    LanguageHeader("All Languages")
                }
            }
            items(items = filtered, key = { it.code }) { lang ->
                LanguageRow("${lang.vernacular} (${lang.name})") {
                    viewModel.changeLanguage(lang.code, lang.vernacular)
                    onLanguageChosen()
                }
            }
        }
    }
}

@Composable
private fun LanguageHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun LanguageRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp)
    )
}

/**
 * Preferred subtitle language: Off, the video's own, used-before languages
 * and English, then every language with subtitles. Shown over every video
 * that exists in that language (the player's CC button can still change it).
 */
@Composable
fun SubtitleLanguageScreen(
    viewModel: MainViewModel,
    onChosen: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val shortcuts = viewModel.subtitleShortcuts
    val all = remember(viewModel.languagesList) { SubtitleLookup.subtitleLanguages(viewModel.languagesList) }
    val pinned = remember(all, shortcuts) { shortcuts.mapNotNull { code -> all.firstOrNull { it.code == code } } }
    val filtered = remember(searchQuery, all) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) all
        else all.filter { it.name.lowercase().contains(q) || it.vernacular.lowercase().contains(q) }
    }
    val current = viewModel.subtitleLanguage
    fun choose(code: String?) {
        viewModel.updateSubtitleLanguage(code)
        onChosen()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search...", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color(0xFF5A6B7C)
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (searchQuery.isEmpty()) {
                item(key = "quick_header") { LanguageHeader("Quick picks") }
                item(key = "off") { LanguageRow(checked("Off", current == MainViewModel.SUBTITLES_OFF)) { choose(MainViewModel.SUBTITLES_OFF) } }
                item(key = "same") { LanguageRow(checked("Same as video", current == null)) { choose(null) } }
                items(items = pinned, key = { "pinned_${it.code}" }) { lang ->
                    LanguageRow(checked("${lang.vernacular} (${lang.name})", current == lang.code)) { choose(lang.code) }
                }
                item(key = "all_header") { LanguageHeader("All Languages") }
            }
            items(items = filtered, key = { it.code }) { lang ->
                LanguageRow(checked("${lang.vernacular} (${lang.name})", current == lang.code)) { choose(lang.code) }
            }
        }
    }
}

private fun checked(label: String, selected: Boolean) = if (selected) "$label  ✓" else label
