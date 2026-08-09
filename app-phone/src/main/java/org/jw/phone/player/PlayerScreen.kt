package org.jw.phone.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.jw.tv.api.MediaItem

/**
 * Phone video player — touch port of the TV player. Landscape fullscreen on
 * play, portrait inline layout when rotated back, tap/double-tap controls,
 * draggable seek bar, quality + subtitle menus, PiP (API 26+).
 *
 * Playback semantics (URL resolution, resume progress keys, Room entities)
 * are identical to the TV player — see PlaybackHelpers.kt.
 */
@Composable
fun PlayerScreen(
    video: MediaItem,
    selectedResolution: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? android.app.Activity

    // NOTE: no forced orientation here. Playback starts in portrait (like the
    // inline layout); the user goes fullscreen via the rotate button. Forcing
    // landscape on entry + portrait on exit raced the pop-back composition and
    // left Home measuring with stale landscape constraints (blank screen) —
    // verified and fixed by removing the forced config changes entirely.

    BackHandler { onBack() }

    val sharedPrefs = remember {
        context.getSharedPreferences(PLAYER_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val savedProgress = remember(video.contentId) { readSavedProgress(sharedPrefs, video) }

    var currentResolution by remember { mutableStateOf(selectedResolution) }
    var videoUrl by remember(currentResolution) {
        mutableStateOf(resolvePlaybackUrl(context, video, currentResolution))
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showResumeToast by remember { mutableStateOf(savedProgress > 0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // handle audio focus — phone behavior (TV doesn't)
            )
            .build()
            .apply {
                setMediaItem(buildMedia3Item(video, videoUrl))
                prepare()
                if (savedProgress > 0L) seekTo(savedProgress)
                playWhenReady = true
            }
    }

    // Dynamic resolution switching — same as TV player.
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotEmpty() &&
            exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != videoUrl
        ) {
            val currentPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            exoPlayer.setMediaItem(buildMedia3Item(video, videoUrl))
            exoPlayer.prepare()
            if (currentPos > 0L) exoPlayer.seekTo(currentPos)
            exoPlayer.playWhenReady = wasPlaying
        }
    }

    // Player state listener + progress save on dispose (identical to TV).
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
            saveProgressOnExit(
                context, sharedPrefs, video,
                positionMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration
            )
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause when the app goes to background (phone lifecycle behavior).
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && exoPlayer.isPlaying) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Position polling — paused while the user drags the seek bar.
    LaunchedEffect(isPlaying, isSeeking) {
        if (isPlaying && !isSeeking) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                delay(500)
            }
        } else {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
        }
    }

    LaunchedEffect(showResumeToast) {
        if (showResumeToast) {
            delay(3000)
            showResumeToast = false
        }
    }

    // Auto-hide controls after 5s while playing (like TV).
    LaunchedEffect(showControls, isPlaying, isSeeking) {
        if (showControls && isPlaying && !isSeeking) {
            delay(5000)
            showControls = false
        }
    }

    val availableResolutions = remember(video) {
        video.files?.mapNotNull { it.label }?.distinct() ?: emptyList()
    }

    fun seekBy(deltaMs: Long) {
        exoPlayer.seekTo(
            (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        )
    }

    fun togglePlay() {
        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            activity?.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Video surface midpoint in pixels, for the double-tap left/right seek.
    val midPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() } / 2f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLandscape) {
            LandscapePlayerLayout(
                exoPlayer = exoPlayer,
                video = video,
                showControls = showControls,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                dragPosition = dragPosition,
                subtitlesEnabled = subtitlesEnabled,
                midPx = midPx,
                onToggleControls = { showControls = !showControls },
                onDoubleTapSeek = ::seekBy,
                onSeek = { pos ->
                    dragPosition = pos
                    isSeeking = true
                    exoPlayer.seekTo(pos)
                },
                onSeekFinished = {
                    isSeeking = false
                    dragPosition = 0L
                },
                onTogglePlay = ::togglePlay,
                onBack = onBack,
                onOpenQuality = { showQualityDialog = true },
                onToggleSubtitles = {
                    subtitlesEnabled = !subtitlesEnabled
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                        .build()
                },
                onEnterPip = ::enterPip,
                onRotateToPortrait = {
                    activity?.let { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                }
            )
        } else {
            PortraitPlayerLayout(
                exoPlayer = exoPlayer,
                video = video,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                dragPosition = dragPosition,
                subtitlesEnabled = subtitlesEnabled,
                midPx = midPx,
                onDoubleTapSeek = ::seekBy,
                onSeek = { pos ->
                    dragPosition = pos
                    isSeeking = true
                    exoPlayer.seekTo(pos)
                },
                onSeekFinished = {
                    isSeeking = false
                    dragPosition = 0L
                },
                onTogglePlay = ::togglePlay,
                onBack = onBack,
                onOpenQuality = { showQualityDialog = true },
                onToggleSubtitles = {
                    subtitlesEnabled = !subtitlesEnabled
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                        .build()
                },
                onEnterPip = ::enterPip,
                onRotateToLandscape = {
                    activity?.let { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                }
            )
        }

        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showResumeToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 90.dp else 150.dp)
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Text(text = errorMsg, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
        }
    }

    if (showQualityDialog) {
        QualityDialog(
            currentResolution = currentResolution,
            availableResolutions = availableResolutions,
            onSelect = { res ->
                currentResolution = res
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

// ── Landscape: fullscreen video + overlay controls ──────────────────────────

@Composable
private fun LandscapePlayerLayout(
    exoPlayer: ExoPlayer,
    video: MediaItem,
    showControls: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    dragPosition: Long,
    subtitlesEnabled: Boolean,
    midPx: Float,
    onToggleControls: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onEnterPip: () -> Unit,
    onRotateToPortrait: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset -> onDoubleTapSeek(if (offset.x < midPx) -10000L else 10000L) }
                )
            }
    ) {
        VideoSurface(exoPlayer, Modifier.fillMaxSize())

        if (showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top: back + title
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Center: play/pause
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Bottom: time + buttons + seek bar
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${formatProgressTime(currentPosition)} / ${formatProgressTime(duration)}",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = onOpenQuality) {
                                    Text("Quality", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(onClick = onToggleSubtitles) {
                                    Icon(
                                        Icons.Filled.ClosedCaption,
                                        contentDescription = "Subtitles",
                                        tint = if (subtitlesEnabled) Color(0xFFFF0033) else Color.White
                                    )
                                }
                                if (Build.VERSION.SDK_INT >= 26) {
                                    IconButton(onClick = onEnterPip) {
                                        Icon(
                                            Icons.Filled.PictureInPictureAlt,
                                            contentDescription = "Picture in Picture",
                                            tint = Color.White
                                        )
                                    }
                                }
                                IconButton(onClick = onRotateToPortrait) {
                                    Icon(
                                        Icons.Filled.Fullscreen,
                                        contentDescription = "Portrait mode",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SeekSlider(
                            currentPosition = currentPosition,
                            dragPosition = dragPosition,
                            duration = duration,
                            onSeek = onSeek,
                            onSeekFinished = onSeekFinished
                        )
                    }
                }
            }
        }
    }
}

// ── Portrait: 16:9 video + inline controls panel ────────────────────────────

@Composable
private fun PortraitPlayerLayout(
    exoPlayer: ExoPlayer,
    video: MediaItem,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    dragPosition: Long,
    subtitlesEnabled: Boolean,
    midPx: Float,
    onDoubleTapSeek: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onEnterPip: () -> Unit,
    onRotateToLandscape: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 16:9 video, double-tap seek gestures
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset -> onDoubleTapSeek(if (offset.x < midPx) -10000L else 10000L) }
                    )
                }
        ) {
            VideoSurface(exoPlayer, Modifier.fillMaxSize())
        }

        // Controls panel
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatProgressTime(currentPosition)} / ${formatProgressTime(duration)}",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onOpenQuality) {
                        Text("Quality", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onToggleSubtitles) {
                        Icon(
                            Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = if (subtitlesEnabled) Color(0xFFFF0033) else Color.White
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 26) {
                        IconButton(onClick = onEnterPip) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = onRotateToLandscape) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
            SeekSlider(
                currentPosition = currentPosition,
                dragPosition = dragPosition,
                duration = duration,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek((currentPosition - 10000L).coerceAtLeast(0L)); onSeekFinished() }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
                }
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isPlaying) "⏸ Pause" else "▶ Play",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                IconButton(onClick = { onSeek((currentPosition + 10000L).coerceAtMost(duration.coerceAtLeast(0L))); onSeekFinished() }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VideoSurface(exoPlayer: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // custom Compose controls
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepScreenOn = true
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SeekSlider(
    currentPosition: Long,
    dragPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit
) {
    val max = duration.coerceAtLeast(0L).toFloat()
    val shown = if (dragPosition > 0) dragPosition else currentPosition
    Slider(
        value = shown.coerceIn(0L, max.toLong()).toFloat(),
        onValueChange = { onSeek(it.toLong()) },
        onValueChangeFinished = onSeekFinished,
        valueRange = 0f..max.coerceAtLeast(1f),
        enabled = max > 0f,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color(0xFFFF0033),
            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun QualityDialog(
    currentResolution: String?,
    availableResolutions: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Video Quality", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(260.dp)
            ) {
                item {
                    QualityRow("Auto (Best Quality)", currentResolution == null) { onSelect(null) }
                }
                items(availableResolutions) { res ->
                    QualityRow(res, currentResolution == res) { onSelect(res) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFF34495E) else Color(0xFF262626),
        shape = RoundedCornerShape(4.dp),
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
