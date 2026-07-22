package org.jw.tv.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jw.tv.BuildConfig
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient
import org.jw.tv.data.DownloadEntity
import org.jw.tv.data.WatchProgressEntity

private val SIDEBAR_FULL_DP = 240.dp
private val SIDEBAR_ICON_DP = 70.dp

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VideoBrowserScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sidebarExpanded by remember { mutableStateOf(false) }

    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) SIDEBAR_FULL_DP else SIDEBAR_ICON_DP,
        animationSpec = tween(durationMillis = 250),
        label = "sidebarWidth"
    )

    val heroPlayFocusRequester = remember { FocusRequester() }

    // TvLazyColumn natively scrolls the focused row into view with one
    // canonical animation in BOTH directions — no hand-rolled focus scrolling.
    val listState = rememberTvLazyListState()
    val currentCategoryKey = viewModel.currentCategory?.key
    val isHome = viewModel.currentCategory == null || viewModel.currentCategory?.key == "VideoOnDemand"

    // Featured video from API (available on Home screen & when root response is loaded)
    val featured = viewModel.featuredVideo ?: viewModel.subcategoriesWithMedia.firstOrNull()?.videos?.firstOrNull()

    // Initial focus: land on Hero's "Play Now" button when content loads!
    LaunchedEffect(currentCategoryKey, viewModel.uiState) {
        if (viewModel.uiState is UiState.Success) {
            delay(100)
            try {
                if (featured != null) {
                    heroPlayFocusRequester.requestFocus()
                }
                listState.animateScrollToItem(0, 0)
            } catch (e: Exception) {}
        }
    }

    var selectedVideoForDetails by remember { mutableStateOf<MediaItem?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManageDownloads by remember { mutableStateOf(false) }

    val versionCode = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                info.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") info.versionCode
        } catch (e: Exception) { 1 }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090B0E))) {

        // ── Main content (fixed 70dp inset) ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SIDEBAR_ICON_DP)
        ) {
            TvLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = viewModel.uiState) {
                    is UiState.Loading -> item(key = "loading") {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Color.White) }
                    }

                    is UiState.Error -> item(key = "error") {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = Color.Red, fontSize = 16.sp)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    viewModel.currentCategory
                                        ?.let { viewModel.selectCategory(it) }
                                        ?: viewModel.loadRootCategories()
                                }) { Text("Retry") }
                            }
                        }
                    }

                    is UiState.Success -> {
                        var tvColumnItemCounter = 0

                        // Row 0: Featured Hero Banner (rendered whenever featured != null)
                        if (featured != null) {
                            tvColumnItemCounter++
                            item(key = "hero_banner") {
                                HeroBanner(
                                    video = featured,
                                    playButtonFocusRequester = heroPlayFocusRequester,
                                    onPlay = { selectedVideoForDetails = featured }
                                )
                            }
                        }

                        // Row 1 (or 0 if no hero): Continue Watching Row (Home screen only)
                        if (isHome && viewModel.continueWatchingList.isNotEmpty()) {
                            val continueIndex = tvColumnItemCounter++
                            val isFirstRowBelowHero = featured != null && continueIndex == 1
                            item(key = "continue_watching_row") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                        .then(
                                            if (isFirstRowBelowHero) {
                                                Modifier.focusProperties { up = heroPlayFocusRequester }
                                            } else Modifier
                                        )
                                ) {
                                    Text(
                                        text = "Continue Watching",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                    TvLazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0.08f)
                                    ) {
                                        items(
                                            items = viewModel.continueWatchingList,
                                            key = { it.contentId }
                                        ) { progress ->
                                            ContinueWatchingCard(
                                                progress = progress,
                                                onClick = {
                                                    val mediaItem = try {
                                                        MediatorClient.json.decodeFromString<MediaItem>(progress.rawMediaItemJson)
                                                    } catch (e: Exception) { null }
                                                    if (mediaItem != null) {
                                                        viewModel.activeVideo = mediaItem
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (viewModel.subcategoriesWithMedia.isEmpty() && (!isHome || viewModel.continueWatchingList.isEmpty())) {
                            item(key = "empty_state") {
                                Box(
                                    Modifier.fillMaxWidth().height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No videos found.",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        } else {
                            // Subcategory Rows
                            val baseSubcatIndex = tvColumnItemCounter
                            itemsIndexed(
                                items = viewModel.subcategoriesWithMedia,
                                key = { _, subcat -> subcat.key }
                            ) { idx, subcat ->
                                val rowItemIndex = baseSubcatIndex + idx
                                val isFirstRowBelowHero = featured != null && rowItemIndex == 1

                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                        .then(
                                            if (isFirstRowBelowHero) {
                                                Modifier.focusProperties { up = heroPlayFocusRequester }
                                            } else Modifier
                                        )
                                ) {
                                    Text(
                                        text = subcat.name,
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                    TvLazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0.08f)
                                    ) {
                                        items(
                                            items = subcat.videos,
                                            key = { it.contentId }
                                        ) { video ->
                                            VideoCard(
                                                video = video,
                                                onClick = { selectedVideoForDetails = video },
                                                downloadedIds = viewModel.downloadedIds
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // ── Scrim overlay: darkens the main content behind the expanded sidebar ──
        if (sidebarExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(15f)
            )
        }


        // ── Sidebar ───────────────────────────────────────────────────────────
        // Collapsed: fully transparent.
        // Expanded:  scrim dims content, sidebar draws with its own dark→transparent
        // gradient layered on top.  The gradient uses 0xA0C12 at varying opacities
        // so the dark left edge blocks all content, fades only at the far right.
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .zIndex(30f)
                .then(
                    if (sidebarExpanded)
                        Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF020305),                               // solid near-black left
                                    Color(0xFF020305).copy(alpha = 0.95f),            // near-solid behind text
                                    Color.Transparent                                  // fades at right edge
                                )
                            )
                        )
                    else Modifier
                )
                .onFocusChanged { sidebarExpanded = it.hasFocus }
                .padding(vertical = 24.dp)
        ) {
            // Brand logo (icon at 16dp — matches SidebarItem inner-row 16dp padding)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = Color(0xFFFF0033),
                    modifier = Modifier.size(22.dp)
                )
                if (sidebarExpanded) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "JW TV",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nav items
            TvLazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "nav_favorites") {
                    val isFavSelected = viewModel.currentCategory?.key == "FAVORITES"
                    SidebarItem(
                        icon = Icons.Filled.Favorite,
                        label = "Favorites (${viewModel.favoritesList.size})",
                        selected = isFavSelected,
                        expanded = sidebarExpanded,
                        onClick = {
                            viewModel.selectFavoritesCategory()
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
                items(
                    items = viewModel.categories,
                    key = { it.key }
                ) { category ->
                    SidebarItem(
                        icon = getCategoryIcon(category.key),
                        label = category.name,
                        selected = viewModel.currentCategory?.key == category.key,
                        expanded = sidebarExpanded,
                        onClick = {
                            viewModel.selectCategory(category)
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
            }
            // Bottom: Settings + version string
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SidebarItem(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    selected = false,
                    expanded = sidebarExpanded,
                    onClick = { showSettingsDialog = true }
                )
                if (sidebarExpanded) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${versionCode})",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }

    // ── Overlays ─────────────────────────────────────────────────────────────

    selectedVideoForDetails?.let { video ->
        VideoDetailsDialog(
            video = video,
            viewModel = viewModel,
            onDismiss = { selectedVideoForDetails = null },
            onPlay = { resolution ->
                viewModel.selectedResolution = resolution
                viewModel.activeVideo = video
                selectedVideoForDetails = null
            }
        )
    }
    if (showLanguageDialog) {
        LanguageSelectionDialog(viewModel = viewModel, onDismiss = { showLanguageDialog = false })
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onOpenLanguage = {
                showSettingsDialog = false
                showLanguageDialog = true
            },
            onOpenDownloads = {
                showSettingsDialog = false
                showManageDownloads = true
            }
        )
    }
    if (showManageDownloads) {
        ManageDownloadsDialog(
            viewModel = viewModel,
            onDismiss = { showManageDownloads = false }
        )
    }
    if (viewModel.updateAvailable && !viewModel.updateDialogDismissed) {
        val updateFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try { updateFocusRequester.requestFocus() } catch (e: Exception) {}
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Column {
                    Text("Update Available", color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME} → v3.9.1",
                        color = Color(0xFF8B949E),
                        fontSize = 13.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "A new version is available for download.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "What's new in this update:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• Full download support (save videos to device)\n" +
                        "• Default quality setting\n" +
                        "• Redesigned Settings menu\n" +
                        "• Language picker moved to Settings\n" +
                        "• Simplified sidebar layout\n" +
                        "• Zero border radius on all elements\n" +
                        "• Bug fixes & performance improvements",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        viewModel.startDownload(viewModel.updateApkUrl)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFFFF0033),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFFFF3355),
                        focusedContentColor = Color.White
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    modifier = Modifier.focusRequester(updateFocusRequester)
                ) { Text("Update", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissUpdateDialog() },
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    )
                ) { Text("Later") }
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

    viewModel.updateCheckMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUpdateCheckMessage() },
            title = { Text("App Update Status", color = Color.White) },
            text = { Text(msg, color = Color.LightGray) },
            confirmButton = {
                Button(onClick = { viewModel.clearUpdateCheckMessage() }) { Text("OK") }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

// ── Continue Watching Card (ZERO Scale, 2dp White Border on Focus) ───────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageRequest = remember(progress.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(progress.thumbnailUrl)
            .size(320, 180)
            .build()
    }

    val progressFraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF161B22),
            focusedContainerColor = Color(0xFF161B22)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(0.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F27)),
                    contentScale = ContentScale.Crop
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = Color(0xFFFF0033),
                    trackColor = Color(0x66000000),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                )
            }
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    progress.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Sidebar item (Focused scale = 1.0f, Rounded shape, 8dp padding — zero overlap) ──

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    // ── Highlight: expanded = left-rounded gradient; collapsed = solid pill ──
    val highlightBrush = remember(isFocused, selected) {
        when {
            expanded && isFocused -> Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFFF0033),
                    Color(0xFFFF0033).copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
            expanded && selected -> Brush.horizontalGradient(
                colors = listOf(
                    Color(0xAAFF0033),
                    Color(0xAAFF0033).copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
            else -> null
        }
    }
    val highlightShape = RoundedCornerShape(0.dp)

    // Collapsed: solid red pill flush to left edge, 6dp right margin only
    //             (icon stays at 16dp same as every other item).
    val activeBackground = if (!expanded && (isFocused || selected)) {
        // Pill starts flush at left edge, only right margin so the icon stays
        // at the same 16dp as every other item (no rightward shift).
        Modifier.padding(end = 6.dp)
            .background(
                if (isFocused) Color(0xFFFF0033) else Color(0xAAFF0033),
                RoundedCornerShape(0.dp)
            )
    } else if (expanded && highlightBrush != null) {
        Modifier.background(highlightBrush, highlightShape)
    } else Modifier

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(activeBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconTint = when {
                isFocused || selected -> Color.White
                else                  -> Color(0xFF8B949E)
            }
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(
                    label,
                    color = iconTint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Hero banner ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroBanner(
    video: MediaItem,
    playButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When the Play button is focused, ask the parent list to reveal the ENTIRE
    // hero rect (not just the button) via the native bring-into-view mechanism —
    // same animation as any other focus scroll, no competing coroutine scrolls.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val heroScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.getThumbnailUrl())
                .size(1280, 720)
                .build(),
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F27)),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF090B0E), Color(0xEE090B0E),
                        Color(0x99090B0E), Color.Transparent
                    ),
                    startX = 0f, endX = 1100f
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xAA090B0E), Color(0xFF090B0E)),
                    startY = 100f, endY = 850f
                )
            )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 28.dp)
                .widthIn(max = 540.dp)
        ) {
            Text(
                "FEATURED VIDEO",
                color = Color(0xFFFF0033),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                video.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            val dur = formatDuration(video.duration)
            if (dur.isNotEmpty()) {
                Text("$dur • HD • Video", color = Color(0xFF8B949E), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFFFF0033),
                    contentColor = Color.White,
                    focusedContainerColor = Color(0xFFFF3355),
                    focusedContentColor = Color.White
                ),
                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                border = ButtonDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(3.dp, Color.White),
                        shape = RoundedCornerShape(0.dp)
                    )
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                modifier = Modifier
                    .height(44.dp)
                    .focusRequester(playButtonFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            heroScope.launch {
                                try { bringIntoViewRequester.bringIntoView() } catch (e: Exception) {}
                            }
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text("▶", fontSize = 15.sp, color = Color.White)
                    Text(
                        "Play Now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ── Video card (ZERO Scale, 2dp White Border on Focus) ───────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoCard(
    video: MediaItem,
    onClick: () -> Unit,
    downloadedIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageRequest = remember(video) {
        ImageRequest.Builder(context)
            .data(video.getThumbnailUrl(small = true))
            .size(320, 180)
            .build()
    }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF161B22),
            focusedContainerColor = Color(0xFF161B22)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(0.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F27)),
                    contentScale = ContentScale.Crop
                )
                // Download indicator overlay
                if (video.contentId in downloadedIds) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(0.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "⬇",
                            color = Color(0xFF4FC3F7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val dur = formatDuration(video.duration)
            }
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    video.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── TV-Native Video Details Overlay ──────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoDetailsDialog(
    video: MediaItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onPlay: (selectedResolution: String?) -> Unit
) {
    var chosenResolution by remember { mutableStateOf<String?>(null) }
    val resolutions = remember(video) {
        video.files?.mapNotNull { it.label }?.distinct() ?: emptyList()
    }
    val resolutionsScrollState = rememberScrollState()
    val playButtonFocusRequester = remember { FocusRequester() }
    val isFavorite = video.contentId in viewModel.favoriteContentIds

    LaunchedEffect(Unit) {
        // Pre-select default quality if available
        val def = viewModel.defaultQuality
        if (def != null && def in resolutions) {
            chosenResolution = def
        }
        try { playButtonFocusRequester.requestFocus() } catch (e: Exception) {}
    }
    // behind it. BACK fires onDismissRequest exactly once (dismissOnBackPress).
    // dismissOnClickOutside = false: the only way out is BACK.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
        Surface(
            onClick = {},
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF161B22),
                focusedContainerColor = Color(0xFF161B22)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            modifier = Modifier
                .width(880.dp)
                .height(470.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.getThumbnailUrl())
                            .size(640, 360)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F27)),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient spans the full image width and dissolves exactly
                    // where the controls panel begins.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF161B22)),
                                startX = 300f, endX = 1020f
                            )
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        val dur = formatDuration(video.duration)
                        if (dur.isNotEmpty()) {
                            Text(
                                text = "$dur • HD • Video",
                                color = Color(0xFF8B949E),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Quality / Calidad:", color = Color.LightGray, fontSize = 13.sp)
                        Row(
                            Modifier.horizontalScroll(resolutionsScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { chosenResolution = null },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (chosenResolution == null) Color(0xFF34495E) else Color.Transparent,
                                    contentColor = Color.White,
                                    focusedContainerColor = if (chosenResolution == null) Color(0xFF46637E) else Color(0xFF2A3644),
                                    focusedContentColor = Color.White
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                border = ButtonDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                        shape = RoundedCornerShape(0.dp)
                                    ),
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(0.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                            ) { Text("Auto") }
                            resolutions.forEach { res ->
                                val dq = viewModel.downloadedQualities
                                val isDownloadedRes = "${video.contentId}|${res}" in dq
                                OutlinedButton(
                                    onClick = { chosenResolution = res },
                                    colors = ButtonDefaults.colors(
                                        containerColor = when {
                                            chosenResolution == res -> Color(0xFF34495E)
                                            isDownloadedRes -> Color(0xFF1B3A2D)
                                            else -> Color.Transparent
                                        },
                                        contentColor = Color.White,
                                        focusedContainerColor = if (chosenResolution == res) Color(0xFF46637E) else Color(0xFF2A3644),
                                        focusedContentColor = Color.White
                                    ),
                                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                    border = ButtonDefaults.border(
                                        border = Border(
                                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                            shape = RoundedCornerShape(0.dp)
                                        ),
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = RoundedCornerShape(0.dp)
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                                ) {
                                    Text(if (isDownloadedRes) "$res ✓" else res)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Download progress indicator
                        if (viewModel.isDownloadingVideo) {
                            LinearProgressIndicator(
                                progress = { viewModel.videoDownloadProgress },
                                color = Color(0xFFFF0033),
                                trackColor = Color(0x66000000),
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 1. Play Now — always visible, first button (focused by default)
                            Button(
                                onClick = { onPlay(chosenResolution) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFFFF0033),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color(0xFFFF3355),
                                    focusedContentColor = Color.White
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(3.dp, Color.White),
                                        shape = RoundedCornerShape(0.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                modifier = Modifier
                                    .height(44.dp)
                                    .focusRequester(playButtonFocusRequester)
                            ) {
                                Text("▶ Play Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // 2. Download button (icon only, or "Saved" if already downloaded)
                            val isQualityDownloaded = chosenResolution != null &&
                                "${video.contentId}|${chosenResolution}" in viewModel.downloadedQualities
                            if (chosenResolution != null) {
                                if (isQualityDownloaded) {
                                    OutlinedButton(
                                        onClick = {},
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF1B3A2D),
                                            contentColor = Color(0xFF66BB6A),
                                            focusedContainerColor = Color(0xFF1B3A2D),
                                            focusedContentColor = Color(0xFF66BB6A)
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(
                                                border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                                shape = RoundedCornerShape(0.dp)
                                            ),
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, Color.White),
                                                shape = RoundedCornerShape(0.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("✓ Saved", color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.startVideoDownload(video, chosenResolution!!) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF1E5C8C),
                                            contentColor = Color.White,
                                            focusedContainerColor = Color(0xFF2875B0),
                                            focusedContentColor = Color.White
                                        ),
                                        border = ButtonDefaults.border(
                                            focusedBorder = Border(
                                                border = BorderStroke(3.dp, Color.White),
                                                shape = RoundedCornerShape(0.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("⬇", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                    }
                                }
                            }

                            // 3. Favorite — heart icon only
                            OutlinedButton(
                                onClick = { viewModel.toggleFavorite(video) },
                                colors = ButtonDefaults.colors(
                                    contentColor = Color.White,
                                    focusedContainerColor = Color(0xFF2A3644),
                                    focusedContentColor = Color.White
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                border = ButtonDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                        shape = RoundedCornerShape(0.dp)
                                    ),
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(0.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Text(
                                    if (isFavorite) "♥" else "♡",
                                    color = if (isFavorite) Color(0xFFFF0033) else Color.White,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

// ── Language dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LanguageSelectionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
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

    val focusManager   = androidx.compose.ui.platform.LocalFocusManager.current
    val scrollState    = rememberScrollState()
    // Attached to the first visible list row — dialog opens with list focused,
    // keyboard never auto-opens.  User navigates UP to reach the search field.
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { firstItemFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        androidx.compose.material3.Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.width(560.dp).height(520.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    "Select Language",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                // Search field — user navigates here with DPAD_UP from the list.
                // Intercept DPAD_DOWN only: moves focus into the list below.
                // BACK is NOT intercepted here — when the keyboard is open the
                // platform IME handles BACK itself (closes keyboard, keeps focus
                // on the TextField).  When the keyboard is already closed, BACK
                // reaches DialogProperties.dismissOnBackPress and closes the
                // dialog cleanly.  Consuming BACK here would clearFocus() and
                // leave nothing focused in the dialog, causing DPAD events to
                // leak through to the content behind the modal.
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // onPreviewKeyEvent fires BEFORE BasicTextField's internal
                        // key handler, so DPAD_DOWN is intercepted before the text
                        // field can consume it for cursor movement.
                        // Direct requestFocus() is more reliable than moveFocus()
                        // from inside a text field on Compose TV.
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                try {
                                    firstItemFocus.requestFocus()
                                } catch (_: Exception) {
                                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                                }
                                true
                            } else false
                        },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2B2B2B),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color(0xFFFF0033),
                        unfocusedIndicatorColor = Color(0xFF5A6B7C)
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Popular",
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        popular.forEachIndexed { idx, lang ->
                            LangRow(
                                label = "${lang.vernacular} (${lang.name})",
                                modifier = if (idx == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                            ) {
                                viewModel.changeLanguage(lang.code, lang.vernacular)
                                onDismiss()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "All Languages",
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    filtered.forEachIndexed { idx, lang ->
                        // When search is active (popular section hidden) the first
                        // filtered row gets the focus requester.
                        val isFirst = searchQuery.isNotEmpty() && idx == 0
                        LangRow(
                            label = "${lang.vernacular} (${lang.name})",
                            modifier = if (isFirst) Modifier.focusRequester(firstItemFocus) else Modifier
                        ) {
                            viewModel.changeLanguage(lang.code, lang.vernacular)
                            onDismiss()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LangRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF262626),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF383838),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(0.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

// ── Settings dialog ───────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenLanguage: () -> Unit = {},
    onOpenDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val qualities = listOf("Auto", "144p", "240p", "360p", "480p", "720p", "1080p", "2160p")
    val settingsVersionCode = try {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            pkg.longVersionCode.toInt()
        else
            @Suppress("DEPRECATION") pkg.versionCode
    } catch (e: Exception) { 1 }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(500.dp)
                .background(Color(0xFF1E1E1E))
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(20.dp))

                // Default Quality
                Text(
                    "Default Quality",
                    color = Color(0xFF8B949E),
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
                        OutlinedButton(
                            onClick = {
                                viewModel.updateDefaultQuality(if (q == "Auto") null else q)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF34495E) else Color.Transparent,
                                contentColor = Color.White,
                                focusedContainerColor = if (isSelected) Color(0xFF46637E) else Color(0xFF2A3644),
                                focusedContentColor = Color.White
                            ),
                            border = ButtonDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                    shape = RoundedCornerShape(0.dp)
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(0.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                        ) { Text(q) }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Language
                Text(
                    "Language",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenLanguage,
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A3644),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                ) {
                    Text("Language: ${viewModel.selectedLanguageName}", color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Switch language from the main menu sidebar.",
                    color = Color(0xFF5A6B7C),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))

                // Manage Downloads
                OutlinedButton(
                    onClick = onOpenDownloads,
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A3644),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                ) {
                    Text("Manage Downloads", color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
                // Check for Update
                Button(
                    onClick = {
                        viewModel.checkForUpdates(settingsVersionCode, manual = true)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFFFF0033),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFFFF3355),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp))
                ) { Text("Check for Update") }
                Spacer(Modifier.height(20.dp))

                // Close
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A3644),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close") }
            }
        }
    }
}
// ── Manage Downloads dialog ───────────────────────────────────────────────────

@Composable
fun ManageDownloadsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var downloads by remember { mutableStateOf(viewModel.allDownloads) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(560.dp)
                .height(480.dp)
                .background(Color(0xFF1E1E1E))
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "Manage Downloads",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))

                if (downloads.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No downloaded videos.", color = Color(0xFF8B949E), fontSize = 14.sp)
                    }
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        downloads.forEach { dl ->
                            Surface(
                                onClick = {},
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF262626),
                                    focusedContainerColor = Color(0xFF333333)
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            dl.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Quality: ${dl.quality}",
                                            color = Color(0xFF8B949E),
                                            fontSize = 12.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.deleteDownload(dl.contentId)
                                            downloads = viewModel.allDownloads
                                        },
                                        colors = ButtonDefaults.colors(
                                            contentColor = Color(0xFFFF0033),
                                            focusedContainerColor = Color(0xFF2A3644),
                                            focusedContentColor = Color(0xFFFF0033)
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(
                                                border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                                                shape = RoundedCornerShape(0.dp)
                                            ),
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, Color.White),
                                                shape = RoundedCornerShape(0.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Delete", color = Color(0xFFFF0033), fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A3644),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color(0xFF5A6B7C)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(0.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close") }
            }
        }
    }
}
// ── Utilities ────────────────────────────────────────────────────────────────
fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null || durationSeconds <= 0) return ""
    val total = durationSeconds.toInt()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

fun getCategoryIcon(key: String): ImageVector = when (key) {
    "VODStudio"            -> Icons.Filled.Tv
    "VODChildren"          -> Icons.Filled.ChildCare
    "VODTeenagers"         -> Icons.Filled.PeopleAlt
    "VODFamily"            -> Icons.Filled.PeopleAlt
    "VODProgramsEvents"    -> Icons.Filled.CalendarMonth
    "VODOurActivities"     -> Icons.AutoMirrored.Filled.DirectionsRun
    "VODMinistry"          -> Icons.Filled.Book
    "VODOurOrganization"   -> Icons.Filled.Business
    "VODBible"             -> Icons.Filled.Book
    "VODMovies"            -> Icons.Filled.Movie
    "VODSeries"            -> Icons.Filled.Tv
    "VODMusicVideos"       -> Icons.Filled.MusicNote
    "VODIntExp"            -> Icons.Filled.Mic
    "VODAudioDescriptions" -> Icons.Filled.Accessibility
    else                   -> Icons.Filled.Movie
}
