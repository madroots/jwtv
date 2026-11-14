package org.jw.tv.ui

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jw.tv.api.MediaItem
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val Accent = Color(0xFFFF0033)

/** Delay after the last seek key press before the player actually seeks. */
private const val SCRUB_COMMIT_DELAY_MS = 900L

/** Controls hide after this long without input while playing. */
private const val CONTROLS_TIMEOUT_MS = 5000L

private enum class ControlFocus { PlayPause, SeekBar }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    video: MediaItem,
    selectedResolution: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val sharedPrefs = remember {
        context.getSharedPreferences("jw_tv_prefs", Context.MODE_PRIVATE)
    }

    val savedProgress = remember(video.contentId) {
        val newKey = "progress_${video.contentId}"
        val oldKey = "progress_${video.title}"
        if (sharedPrefs.contains(newKey)) {
            sharedPrefs.getLong(newKey, 0L)
        } else if (sharedPrefs.contains(oldKey)) {
            val oldPos = sharedPrefs.getLong(oldKey, 0L)
            sharedPrefs.edit().putLong(newKey, oldPos).remove(oldKey).apply()
            oldPos
        } else 0L
    }
    var currentResolution by remember { mutableStateOf(selectedResolution) }

    // Check for locally downloaded file first — if available, play from local
    // storage instead of streaming.
    val videoUrl by remember(currentResolution) {
        val localFile = currentResolution?.let { res ->
            val f = java.io.File(context.filesDir, "downloads/${video.contentId}_${res}.mp4")
            if (f.exists()) f.absolutePath else null
        }
        mutableStateOf(localFile ?: (getBestVideoUrl(video, currentResolution) ?: ""))
    }

    val subtitleUrl = remember(video) { video.files?.firstNotNullOfOrNull { it.subtitles?.url } }

    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showResumeToast by remember { mutableStateOf(savedProgress > 0L) }

    // Seek preview: D-pad Left/Right move this target; the player seeks once
    // input pauses, so holding the key doesn't trigger a re-buffer per step.
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    // Bumped on every key press so the auto-hide timer restarts.
    var interactionTick by remember { mutableIntStateOf(0) }
    var controlFocus by remember { mutableStateOf(ControlFocus.PlayPause) }
    var focusTick by remember { mutableIntStateOf(0) }
    // True while a control in the bottom bar holds focus. Otherwise focus is on
    // the video surface (controls hidden, or just revealed and focus not moved
    // yet). May stay stale after the overlay leaves composition, so it is only
    // consulted together with showControls.
    var overlayFocused by remember { mutableStateOf(false) }

    fun buildMediaItem(url: String): Media3Item {
        val builder = Media3Item.Builder().setUri(url)
        if (subtitleUrl != null) {
            builder.setSubtitleConfigurations(
                listOf(
                    Media3Item.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return builder.build()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(buildMediaItem(videoUrl))
            prepare()
            if (savedProgress > 0L) seekTo(savedProgress)
            playWhenReady = true
        }
    }

    // Dynamic resolution switching
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotEmpty() && exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != videoUrl) {
            val currentPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            exoPlayer.setMediaItem(buildMediaItem(videoUrl))
            exoPlayer.prepare()
            if (currentPos > 0L) exoPlayer.seekTo(currentPos)
            exoPlayer.playWhenReady = wasPlaying
        }
    }

    // Monitor playback state, buffering, and errors; persist progress on exit.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "Playback Error: ${error.localizedMessage ?: "Unknown error occurred"}"
                isBuffering = false
            }

            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val currentPos = exoPlayer.currentPosition
            val totalDuration = exoPlayer.duration
            if (totalDuration > 0L) {
                val progressPercent = currentPos.toFloat() / totalDuration.toFloat()
                val appContext = context.applicationContext
                val dao = org.jw.tv.data.AppDatabase.getDatabase(appContext).mediaDao()
                val jsonStr = try {
                    org.jw.tv.api.MediatorClient.json.encodeToString(MediaItem.serializer(), video)
                } catch (e: Exception) { "" }

                if (currentPos > 5000L && progressPercent < 0.95f) {
                    sharedPrefs.edit().putLong("progress_${video.contentId}", currentPos).apply()
                    val entity = org.jw.tv.data.WatchProgressEntity(
                        contentId = video.contentId,
                        title = video.title,
                        thumbnailUrl = video.getThumbnailUrl(small = true),
                        durationSeconds = video.duration,
                        positionMs = currentPos,
                        durationMs = totalDuration,
                        lastWatchedTimestamp = System.currentTimeMillis(),
                        rawMediaItemJson = jsonStr
                    )
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        dao.saveWatchProgress(entity)
                    }
                } else {
                    sharedPrefs.edit().remove("progress_${video.contentId}").apply()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        dao.deleteWatchProgress(video.contentId)
                    }
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll position while playing
    LaunchedEffect(isPlaying) {
        do {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            if (isPlaying) delay(500)
        } while (isPlaying)
    }

    LaunchedEffect(showResumeToast) {
        if (showResumeToast) {
            delay(3000)
            showResumeToast = false
        }
    }

    // Auto-hide: only while playing, not mid-seek, restarted by any key press.
    LaunchedEffect(showControls, isPlaying, interactionTick, scrubPosition, showQualityDialog) {
        if (showControls && isPlaying && scrubPosition == null && !showQualityDialog) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }

    fun commitScrub() {
        val target = scrubPosition ?: return
        exoPlayer.seekTo(target)
        currentPosition = target
        scrubPosition = null
    }

    // Debounced seek: restarts on every step, fires once input pauses.
    LaunchedEffect(scrubPosition) {
        if (scrubPosition != null) {
            delay(SCRUB_COMMIT_DELAY_MS)
            commitScrub()
        }
    }

    fun revealControls(focus: ControlFocus) {
        showControls = true
        controlFocus = focus
        focusTick++
    }

    /** Moves the seek target; holding the key speeds up 10s → 30s → 60s steps. */
    fun scrub(forward: Boolean, repeatCount: Int) {
        val total = exoPlayer.duration.takeIf { it > 0 } ?: return
        val step = when {
            repeatCount < 6 -> 10_000L
            repeatCount < 20 -> 30_000L
            else -> 60_000L
        }
        val from = scrubPosition ?: exoPlayer.currentPosition
        scrubPosition = (if (forward) from + step else from - step).coerceIn(0L, total)
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    // Back: cancel a pending seek, then hide the controls, then leave.
    BackHandler {
        when {
            scrubPosition != null -> scrubPosition = null
            showControls && playerError == null -> showControls = false
            else -> onBack()
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls, focusTick) {
        try {
            when {
                !showControls -> rootFocusRequester.requestFocus()
                controlFocus == ControlFocus.SeekBar -> seekBarFocusRequester.requestFocus()
                else -> playPauseFocusRequester.requestFocus()
            }
        } catch (_: IllegalStateException) { }
    }

    val availableResolutions = remember(video) {
        video.files?.mapNotNull { it.label }?.distinct() ?: emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) interactionTick++
                false
            }
            // Touch/mouse toggle without adding a second focus target (a
            // clickable is focusable and would swallow D-pad keys).
            .pointerInput(Unit) {
                detectTapGestures {
                    if (showControls) showControls = false else revealControls(ControlFocus.PlayPause)
                }
            }
            // Placed before the focus target so it sees keys from the video
            // surface and, bubbling up, from every overlay control.
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    // Remote media keys work whether or not controls are shown.
                    Key.MediaPlayPause -> { togglePlay(); revealControls(ControlFocus.PlayPause); true }
                    Key.MediaPlay -> { exoPlayer.play(); true }
                    Key.MediaPause -> { exoPlayer.pause(); revealControls(ControlFocus.PlayPause); true }
                    Key.MediaFastForward, Key.MediaRewind -> {
                        revealControls(ControlFocus.SeekBar)
                        scrub(keyEvent.key == Key.MediaFastForward, keyEvent.repeatCount)
                        true
                    }
                    // While focus is on the video surface: Left/Right start
                    // seeking on the seek bar, Center toggles play, Up/Down
                    // just reveal the controls.
                    Key.DirectionLeft, Key.DirectionRight -> if (!showControls || !overlayFocused) {
                        revealControls(ControlFocus.SeekBar)
                        scrub(keyEvent.key == Key.DirectionRight, keyEvent.repeatCount)
                        true
                    } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!showControls || !overlayFocused) {
                        togglePlay()
                        revealControls(ControlFocus.PlayPause)
                        true
                    } else false
                    Key.DirectionUp, Key.DirectionDown -> if (!showControls || !overlayFocused) {
                        revealControls(ControlFocus.PlayPause)
                        true
                    } else false
                    else -> false
                }
            }
            .focusRequester(rootFocusRequester)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom Compose overlay below
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    // The video surface must never take Android view focus:
                    // when it does (e.g. after the controls hide), D-pad keys
                    // go to PlayerView instead of the Compose key handlers.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls fade + slide (graphicsLayer/placement only, no relayout).
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }
        ) {
            // Seek bar + transport row
            Column(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { overlayFocused = it.hasFocus }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 48.dp, end = 48.dp, top = 72.dp, bottom = 32.dp)
            ) {
                TvSeekBar(
                    position = currentPosition,
                    scrubPosition = scrubPosition,
                    bufferedPosition = bufferedPosition,
                    duration = duration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(seekBarFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    scrub(keyEvent.key == Key.DirectionRight, keyEvent.repeatCount)
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (scrubPosition != null) commitScrub() else togglePlay()
                                    true
                                }
                                else -> false
                            }
                        }
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayerIconButton(
                        icon = Icons.Filled.Replay10,
                        description = "Back 10 seconds",
                        onClick = { scrub(forward = false, repeatCount = 0) }
                    )
                    PlayerIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        description = if (isPlaying) "Pause" else "Play",
                        onClick = ::togglePlay,
                        modifier = Modifier.focusRequester(playPauseFocusRequester)
                    )
                    PlayerIconButton(
                        icon = Icons.Filled.Forward10,
                        description = "Forward 10 seconds",
                        onClick = { scrub(forward = true, repeatCount = 0) }
                    )

                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatProgressTime(scrubPosition ?: currentPosition)} / ${formatProgressTime(duration)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp
                    )

                    Spacer(Modifier.weight(1f))

                    if (subtitleUrl != null) {
                        PlayerIconButton(
                            icon = if (subtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionDisabled,
                            description = if (subtitlesEnabled) "Subtitles on" else "Subtitles off",
                            onClick = {
                                subtitlesEnabled = !subtitlesEnabled
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                    .build()
                            }
                        )
                    }
                    androidx.tv.material3.Button(
                        onClick = { showQualityDialog = true },
                        shape = ButtonDefaults.shape(shape = RectangleShape),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(currentResolution ?: "Auto", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showResumeToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Resumed from ${formatProgressTime(savedProgress)}",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        playerError?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Cannot Play Video",
                        color = Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.tv.material3.Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Select Video Quality", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    item {
                        QualityOption(
                            label = "Auto (Best Quality)",
                            selected = currentResolution == null,
                            onClick = {
                                currentResolution = null
                                showQualityDialog = false
                            }
                        )
                    }
                    items(availableResolutions) { res ->
                        QualityOption(
                            label = res,
                            selected = currentResolution == res,
                            onClick = {
                                currentResolution = res
                                showQualityDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showQualityDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

/**
 * Seek bar with an always-visible position marker. When focused the track
 * thickens and a thumb plus time bubble show where Left/Right will seek; while
 * seeking the bubble shows the target time and the jump size.
 */
@Composable
private fun TvSeekBar(
    position: Long,
    scrubPosition: Long?,
    bufferedPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shown = scrubPosition ?: position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val playedFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val active = focused || scrubPosition != null

    BoxWithConstraints(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        val density = LocalDensity.current
        val bubbleWidth = 120.dp
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val bubbleOffset = with(density) {
            (trackWidthPx * fraction).toDp() - bubbleWidth / 2
        }.coerceIn(0.dp, maxWidth - bubbleWidth)

        Column {
            // Time bubble above the thumb
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                if (active) {
                    Column(
                        Modifier
                            .offset(x = bubbleOffset)
                            .width(bubbleWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            formatProgressTime(shown),
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        if (scrubPosition != null) {
                            val delta = scrubPosition - position
                            Text(
                                (if (delta >= 0) "+" else "−") + formatProgressTime(kotlin.math.abs(delta)),
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val trackHeight = (if (active) 8.dp else 4.dp).toPx()
                val y = (size.height - trackHeight) / 2
                val radius = CornerRadius(trackHeight / 2)
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, trackHeight), radius)
                drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, y), Size(size.width * bufferedFraction, trackHeight), radius)
                drawRoundRect(Accent, Offset(0f, y), Size(size.width * playedFraction, trackHeight), radius)
                if (scrubPosition != null) {
                    // Range that will be skipped/rewound, so direction is obvious.
                    val from = size.width * playedFraction
                    val to = size.width * fraction
                    drawRect(
                        Color.White.copy(alpha = 0.7f),
                        Offset(minOf(from, to), y),
                        Size(kotlin.math.abs(to - from), trackHeight)
                    )
                }
                val thumbRadius = (if (active) 10.dp else 6.dp).toPx()
                drawCircle(
                    if (active) Color.White else Accent,
                    radius = thumbRadius,
                    center = Offset(size.width * fraction, size.height / 2)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        shape = IconButtonDefaults.shape(shape = RectangleShape),
        scale = IconButtonDefaults.scale(focusedScale = 1f),
        colors = IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF34495E) else Color(0xFF262626),
            focusedContainerColor = Color(0xFF333333)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private val KeyEvent.repeatCount: Int get() = nativeKeyEvent.repeatCount

private fun formatProgressTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun getBestVideoUrl(video: MediaItem, selectedResolution: String?): String? {
    val files = video.files ?: return null
    if (selectedResolution != null) {
        val file = files.find { it.label?.lowercase() == selectedResolution.lowercase() }
        if (file?.progressiveDownloadURL != null) {
            return file.progressiveDownloadURL
        }
    }
    val preferredLabels = listOf("720p", "480p", "360p", "240p")
    for (label in preferredLabels) {
        val file = files.find { it.label?.lowercase() == label }
        if (file?.progressiveDownloadURL != null) {
            return file.progressiveDownloadURL
        }
    }
    return files.firstOrNull { it.progressiveDownloadURL != null }?.progressiveDownloadURL
}
