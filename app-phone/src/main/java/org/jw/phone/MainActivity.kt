package org.jw.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jw.phone.ui.JwPhoneTheme
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.UiState

/**
 * Phone entry point. Portrait-first; the player route takes over orientation
 * handling itself (issue #4).
 *
 * NOTE: unlike the TV MainActivity this deliberately does NOT call
 * [MainViewModel.checkForUpdates] — the update metadata currently points at the
 * TV APK. Platform-aware updates are handled in issue #7; until then the phone
 * app must never offer to install a TV build.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JwPhoneTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(viewModel, navController) }
                    // Scaffold stubs — real screens land in issues #3 (browser),
                    // #4 (player). Routes exist now so navigation wiring is done.
                    composable("details") { StubScreen("Details", navController) }
                    composable("player") { StubScreen("Player", navController) }
                    composable("settings") { StubScreen("Settings", navController) }
                    composable("downloads") { StubScreen("Downloads", navController) }
                }
            }
        }
    }
}

/**
 * Scaffold home screen: proves the network → MainViewModel → Compose pipeline
 * end to end. Replaced by the real browse UI in issue #3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(viewModel: MainViewModel, navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JW TV", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.uiState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        viewModel.currentCategory
                            ?.let { viewModel.selectCategory(it) }
                            ?: viewModel.loadRootCategories()
                    }) { Text("Retry") }
                }

                is UiState.Success -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    viewModel.featuredVideo?.let { featured ->
                        item(key = "featured") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "FEATURED",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(featured.title, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    items(
                        items = viewModel.subcategoriesWithMedia,
                        key = { it.key }
                    ) { subcat ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(subcat.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${subcat.videos.size} videos",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StubScreen(title: String, navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$title — arrives with its dedicated issue",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
