package org.jw.tv.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.zIndex
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jw.tv.BuildConfig
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient
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

    val sidebarFirstFocus = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()
    val currentCategoryKey = viewModel.currentCategory?.key

    // Initial focus on app launch / category change: land directly on Hero's "Play Now" button!
    LaunchedEffect(currentCategoryKey, viewModel.uiState) {
        if (viewModel.uiState is UiState.Success) {
            delay(100)
            try {
                heroPlayFocusRequester.requestFocus()
                listState.scrollToItem(0, 0)
            } catch (e: Exception) {}
        }
    }

    var selectedVideoForDetails by remember { mutableStateOf<MediaItem?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val versionCode = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                info.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") info.versionCode
        } catch (e: Exception) { 1 }
    }

    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun handleRowFocus(rowIndex: Int) {
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            try {
                if (rowIndex <= 1) {
                    listState.scrollToItem(0, 0)
                } else {
                    listState.animateScrollToItem(rowIndex, -100)
                }
            } catch (e: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090B0E))) {

        // ── Main content (fixed 70dp inset) ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SIDEBAR_ICON_DP)
        ) {
            LazyColumn(
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

                        // Row 0: Featured Hero Banner
                        val featured = viewModel.featuredVideo
                            ?: viewModel.subcategoriesWithMedia.firstOrNull()?.videos?.firstOrNull()
                        if (featured != null) {
                            val heroIndex = tvColumnItemCounter++
                            item(key = "hero_banner") {
                                HeroBanner(
                                    video = featured,
                                    playButtonFocusRequester = heroPlayFocusRequester,
                                    onPlay = { selectedVideoForDetails = featured },
                                    onFocused = { handleRowFocus(heroIndex) }
                                )
                            }
                        }

                        // Row 1 (or 0 if no hero): Continue Watching Row
                        val isHome = viewModel.currentCategory == null || viewModel.currentCategory?.key == "VideoOnDemand"
                        if (isHome && viewModel.continueWatchingList.isNotEmpty()) {
                            val continueIndex = tvColumnItemCounter++
                            item(key = "continue_watching_row") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                        .focusProperties { up = heroPlayFocusRequester }
                                        .onFocusChanged { if (it.hasFocus) handleRowFocus(continueIndex) }
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
                                val isFirstVideoRow = rowItemIndex == 1

                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                        .then(
                                            if (isFirstVideoRow) {
                                                Modifier.focusProperties { up = heroPlayFocusRequester }
                                            } else Modifier
                                        )
                                        .onFocusChanged { if (it.hasFocus) handleRowFocus(rowItemIndex) }
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
                                                onClick = { selectedVideoForDetails = video }
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

        // ── Sidebar (Animated Width) ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .zIndex(30f)
                .background(Color(0xF00D1117))
                .onFocusChanged { sidebarExpanded = it.hasFocus }
                .padding(vertical = 24.dp)
        ) {
            // Brand logo
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
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
                item(key = "nav_home") {
                    val isHome = viewModel.currentCategory == null ||
                            viewModel.currentCategory?.key == "VideoOnDemand"
                    SidebarItem(
                        icon = Icons.Filled.Home,
                        label = "Home",
                        selected = isHome,
                        expanded = sidebarExpanded,
                        focusRequester = sidebarFirstFocus,
                        onClick = {
                            viewModel.loadRootCategories()
                            coroutineScope.launch {
                                try { heroPlayFocusRequester.requestFocus() } catch (e: Exception) {}
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                }
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

            // Bottom: Language + Update + version string
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SidebarItem(
                    icon = Icons.Filled.Language,
                    label = viewModel.selectedLanguageName,
                    selected = false,
                    expanded = sidebarExpanded,
                    onClick = { showLanguageDialog = true }
                )
                SidebarItem(
                    icon = Icons.Filled.Refresh,
                    label = "Check for Update",
                    selected = false,
                    expanded = sidebarExpanded,
                    onClick = { viewModel.checkForUpdates(versionCode, manual = true) }
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

    if (viewModel.updateAvailable && !viewModel.updateDialogDismissed) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text("Update Available", color = Color.White) },
            text = {
                Text(
                    "A new version of JW TV Library is available. Would you like to update now?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        viewModel.startDownload(viewModel.updateApkUrl, context)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFFE50914),
                        focusedContainerColor = Color(0xFFFF1E27)
                    )
                ) { Text("Update") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissUpdateDialog() }) { Text("Later") }
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

    // Explicit scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f) forces ZERO scaling/zoom!
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF161B22),
            focusedContainerColor = Color(0xFF161B22)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
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
                    color = Color(0xFFE50914),
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

// ── Sidebar item ─────────────────────────────────────────────────────────────

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
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x22FFFFFF) else Color.Transparent,
            focusedContainerColor = Color(0x33FFFFFF)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color(0xFF8B949E),
                modifier = Modifier.size(22.dp)
            )
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(
                    label,
                    color = if (selected) Color.White else Color(0xFF8B949E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Hero banner (Button focusedScale = 1.0f) ──────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    video: MediaItem,
    playButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
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
                color = Color(0xFFE50914),
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
                    containerColor = Color(0xFFE50914),
                    focusedContainerColor = Color(0xFFFF1E27)
                ),
                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                modifier = Modifier
                    .height(44.dp)
                    .focusRequester(playButtonFocusRequester)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageRequest = remember(video) {
        ImageRequest.Builder(context)
            .data(video.getThumbnailUrl(small = true))
            .size(320, 180)
            .build()
    }

    // Explicit scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f) forces ZERO scaling/zoom!
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF161B22),
            focusedContainerColor = Color(0xFF161B22)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
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
                val dur = formatDuration(video.duration)
                if (dur.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            dur,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
    val playButtonFocusRequester = remember { FocusRequester() }
    val isFavorite = video.contentId in viewModel.favoriteContentIds

    LaunchedEffect(Unit) {
        try { playButtonFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .zIndex(50f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = {},
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF161B22),
                focusedContainerColor = Color(0xFF161B22)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            modifier = Modifier
                .width(820.dp)
                .height(440.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.getThumbnailUrl())
                            .size(640, 360)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F27)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF161B22)),
                                startX = 200f, endX = 500f
                            )
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.55f)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { chosenResolution = null },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (chosenResolution == null) Color(0xFF34495E) else Color.Transparent
                                )
                            ) { Text("Auto") }
                            resolutions.forEach { res ->
                                OutlinedButton(
                                    onClick = { chosenResolution = res },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (chosenResolution == res) Color(0xFF34495E) else Color.Transparent
                                    )
                                ) { Text(res) }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onPlay(chosenResolution) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFFE50914),
                                    focusedContainerColor = Color(0xFFFF1E27)
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                                modifier = Modifier
                                    .height(44.dp)
                                    .focusRequester(playButtonFocusRequester)
                            ) {
                                Text("▶ Play Video", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.toggleFavorite(video) },
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    if (isFavorite) "❤️ Favorited" else "🤍 Add Favorite",
                                    color = Color.White
                                )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Language",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().height(380.dp)) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2B2B2B),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                TvLazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        item {
                            Text(
                                "Popular",
                                fontSize = 12.sp,
                                color = Color(0xFF8B949E),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(popular) { lang ->
                            LangRow("${lang.vernacular} (${lang.name})") {
                                viewModel.changeLanguage(lang.code, lang.vernacular)
                                onDismiss()
                            }
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "All Languages",
                                fontSize = 12.sp,
                                color = Color(0xFF8B949E),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    items(filtered) { lang ->
                        LangRow("${lang.vernacular} (${lang.name})") {
                            viewModel.changeLanguage(lang.code, lang.vernacular)
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LangRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF262626),
            focusedContainerColor = Color(0xFF333333)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
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
