package org.jw.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.jw.phone.player.PlayerScreen
import org.jw.phone.ui.CategoriesScreen
import org.jw.phone.ui.CategoryScreen
import org.jw.phone.ui.DownloadsScreen
import org.jw.phone.ui.FavoritesScreen
import org.jw.phone.ui.HomeScreen
import org.jw.phone.ui.JwPhoneTheme
import org.jw.phone.ui.LanguageScreen
import org.jw.phone.ui.SettingsScreen
import org.jw.phone.ui.VideoDetailsSheet
import org.jw.tv.api.MediaItem
import org.jw.tv.ui.MainViewModel

private val TAB_ROUTES = listOf("home", "categories", "favorites", "settings")

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
                PhoneRoot(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Details sheet is hosted here so every screen can open it.
    var detailsVideo by remember { mutableStateOf<MediaItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    // updateCheckMessage doubles as the download completion notice channel.
    LaunchedEffect(viewModel.updateCheckMessage) {
        val msg = viewModel.updateCheckMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUpdateCheckMessage()
        }
    }

    fun play(video: MediaItem, resolution: String?) {
        viewModel.selectedResolution = resolution
        viewModel.activeVideo = video
        detailsVideo = null
        navController.navigate("player")
    }

    val title: String
    val showBack: Boolean
    when {
        currentRoute == null -> { title = "JW TV"; showBack = false }
        currentRoute == "home" -> { title = "JW TV"; showBack = false }
        currentRoute == "categories" -> { title = "Categories"; showBack = false }
        currentRoute == "favorites" -> { title = "Favorites"; showBack = false }
        currentRoute == "settings" -> { title = "Settings"; showBack = false }
        currentRoute.startsWith("category/") -> {
            title = viewModel.currentCategory?.name ?: "Category"; showBack = true
        }
        currentRoute == "language" -> { title = "Select Language"; showBack = true }
        currentRoute == "downloads" -> { title = "Manage Downloads"; showBack = true }
        currentRoute == "player" -> { title = ""; showBack = false }
        else -> { title = "JW TV"; showBack = false }
    }

    Scaffold(
        topBar = {
            if (currentRoute != "player") {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            color = if (currentRoute == "home") MaterialTheme.colorScheme.primary
                            else Color.White
                        )
                    },
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                PhoneNavBar(navController, currentRoute, viewModel.favoritesList.size)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onVideoClick = { detailsVideo = it },
                    onPlayVideo = { video -> play(video, null) }
                )
            }
            composable("categories") {
                CategoriesScreen(
                    viewModel = viewModel,
                    onCategoryClick = { navController.navigate("category/${it.key}") }
                )
            }
            composable("category/{key}") { entry ->
                val key = entry.arguments?.getString("key")
                val category = viewModel.categories.firstOrNull { it.key == key }
                if (category == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    CategoryScreen(
                        viewModel = viewModel,
                        category = category,
                        onVideoClick = { detailsVideo = it }
                    )
                }
            }
            composable("favorites") {
                FavoritesScreen(
                    viewModel = viewModel,
                    onVideoClick = { detailsVideo = it }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenLanguage = { navController.navigate("language") },
                    onOpenDownloads = { navController.navigate("downloads") }
                )
            }
            composable("language") {
                LanguageScreen(
                    viewModel = viewModel,
                    onLanguageChosen = { navController.popBackStack() }
                )
            }
            composable("downloads") {
                DownloadsScreen(viewModel = viewModel)
            }
            composable("player") {
                val video = viewModel.activeVideo
                if (video == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    PlayerScreen(
                        video = video,
                        selectedResolution = viewModel.selectedResolution,
                        onBack = {
                            viewModel.activeVideo = null
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

    detailsVideo?.let { video ->
        VideoDetailsSheet(
            video = video,
            viewModel = viewModel,
            onDismiss = { detailsVideo = null },
            onPlay = { resolution -> play(video, resolution) }
        )
    }

    // ── Self-update dialogs (issue #7) — port of the TV update flow ─────────
    if (viewModel.updateAvailable && !viewModel.updateDialogDismissed) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Column {
                    Text("Update Available", color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A new version is ready to download.",
                        color = Color(0xFF8B949E),
                        fontSize = 13.sp
                    )
                }
            },
            text = {
                Text(
                    "A new version is available for download.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        viewModel.startDownload(viewModel.updateApkUrl)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF0033),
                        contentColor = Color.White
                    )
                ) { Text("Update", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Button(
                    onClick = { viewModel.dismissUpdateDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) { Text("Later", color = Color.White) }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    if (viewModel.isDownloadingUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading Update", color = Color.White) },
            text = {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { viewModel.downloadProgress },
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Downloading: ${(viewModel.downloadProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

@Composable
private fun PhoneNavBar(
    navController: NavHostController,
    currentRoute: String?,
    favoritesCount: Int
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple("home", Icons.Filled.Home, "Home"),
            Triple("categories", Icons.Filled.GridView, "Categories"),
            Triple("favorites", Icons.Filled.Favorite, "Favorites"),
            Triple("settings", Icons.Filled.Settings, "Settings")
        )
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = currentRoute == route ||
                        (route == "categories" && currentRoute?.startsWith("category/") == true),
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
