package org.jw.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.jw.tv.BuildConfig
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient
import org.jw.tv.data.WatchProgressEntity

private val SIDEBAR_FULL_DP = 240.dp
private val SIDEBAR_ICON_DP = 70.dp
private const val SIDEBAR_HIDDEN_OFFSET_DP = -(240 - 70)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoBrowserScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sidebarExpanded by remember { mutableStateOf(false) }
    val sidebarOffset by animateFloatAsState(
        targetValue = if (sidebarExpanded) 0f else SIDEBAR_HIDDEN_OFFSET_DP.toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "sidebarOffset"
    )

    val sidebarFirstFocus = remember { FocusRequester() }

    val listState = rememberTvLazyListState()
    val currentCategoryKey = viewModel.currentCategory?.key
    LaunchedEffect(currentCategoryKey, viewModel.uiState) {
        if (viewModel.uiState is UiState.Success) listState.animateScrollToItem(0)
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

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090B0E))) {

        // ── Main content ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SIDEBAR_ICON_DP)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        try {
                            sidebarFirstFocus.requestFocus()
                            true
                        } catch (e: Exception) { false }
                    } else false
                }
        ) {
            TvLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0f)
            ) {
                when (val state = viewModel.uiState) {
                    is UiState.Loading -> item {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Color.White) }
                    }

                    is UiState.Error -> item {
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
                        // Featured Hero Banner at top of page
                        val featured = viewModel.featuredVideo
                            ?: viewModel.subcategoriesWithMedia.firstOrNull()?.videos?.firstOrNull()
                        if (featured != null) {
                            item {
                                HeroBanner(
                                    video = featured,
                                    onPlay = { selectedVideoForDetails = featured }
                                )
                            }
                        }

                        // UX-4: "Continue Watching" Row (Home screen only)
                        val isHome = viewModel.currentCategory == null || viewModel.currentCategory?.key == "VideoOnDemand"
                        if (isHome && viewModel.continueWatchingList.isNotEmpty()) {
                            item {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
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
                                        pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0f)
                                    ) {
                                        items(viewModel.continueWatchingList) { progress ->
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
                            item {
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
                            items(viewModel.subcategoriesWithMedia) { subcat ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
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
                                        pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0f)
                                    ) {
                                        items(subcat.videos) { video ->
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

        // ── Sidebar (GPU translate) ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(SIDEBAR_FULL_DP)
                .fillMaxHeight()
                .zIndex(30f)
                .graphicsLayer { translationX = sidebarOffset.dp.toPx() }
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nav items
            TvLazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
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
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
                // ARCH-6: Favorites Sidebar Nav Item
                item {
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
                items(viewModel.categories) { category ->
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
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                    )
                }
            }
        }
    }

    // ── Overlays ─────────────────────────────────────────────────────────────

    // UX-2: TV-Native Video Details Overlay
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

// ── Continue Watching Card (UX-4) ────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale"
    )

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
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { ShimmerBox(Modifier.fillMaxSize()) }
                )
                // Bottom Progress Bar
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

// ── Hero banner ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    video: MediaItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().height(310.dp)) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.getThumbnailUrl())
                .size(1280, 720)
                .build(),
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { ShimmerBox(Modifier.fillMaxSize()) }
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
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
                modifier = Modifier.height(44.dp)
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

// ── Video card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoCard(
    video: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale"
    )

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
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { ShimmerBox(Modifier.fillMaxSize()) }
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

// ── Shimmer placeholder (UX-3) ───────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A1F27),
                            Color(0xFF2A3140),
                            Color(0xFF1A1F27)
                        ),
                        start = Offset(offset * w, 0f),
                        end   = Offset((offset + 1f) * w, h)
                    )
                )
        )
    }
}

// ── TV-Native Video Details Overlay (UX-2 & ARCH-6) ──────────────────────────

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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // absorb click
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left 45% Poster preview
                Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.getThumbnailUrl())
                            .size(640, 360)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = { ShimmerBox(Modifier.fillMaxSize()) }
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

                // Right 55% Details & Controls
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
