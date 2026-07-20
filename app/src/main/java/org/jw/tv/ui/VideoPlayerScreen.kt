package org.jw.tv.ui

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.focusable
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    video: MediaItem,
    selectedResolution: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler {
        onBack()
    }

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
    var videoUrl by remember(currentResolution) {
        mutableStateOf(getBestVideoUrl(video, currentResolution) ?: "")
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var isProgressBarFocused by remember { mutableStateOf(false) }
    var showResumeToast by remember { mutableStateOf(savedProgress > 0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaBuilder = Media3Item.Builder().setUri(videoUrl)
            
            val subtitleUrl = video.files?.firstNotNullOfOrNull { it.subtitles?.url }
            if (subtitleUrl != null) {
                val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            
            setMediaItem(mediaBuilder.build())
            prepare()
            if (savedProgress > 0L) {
                seekTo(savedProgress)
            }
            playWhenReady = true
        }
    }

    // Dynamic resolution switching logic
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotEmpty() && exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != videoUrl) {
            val currentPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            
            val mediaBuilder = Media3Item.Builder().setUri(videoUrl)
            val subtitleUrl = video.files?.firstNotNullOfOrNull { it.subtitles?.url }
            if (subtitleUrl != null) {
                val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            
            exoPlayer.setMediaItem(mediaBuilder.build())
            exoPlayer.prepare()
            if (currentPos > 0L) {
                exoPlayer.seekTo(currentPos)
            }
            exoPlayer.playWhenReady = wasPlaying
        }
    }

    // Monitor playback state, buffering, and errors
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
                    org.jw.tv.api.MediatorClient.json.encodeToString(org.jw.tv.api.MediaItem.serializer(), video)
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

    // Polling player position when playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
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

    // Hide resume toast after 3 seconds
    LaunchedEffect(showResumeToast) {
        if (showResumeToast) {
            delay(3000)
            showResumeToast = false
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    val playPauseFocusRequester = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls) {
        if (showControls) {
            playPauseFocusRequester.requestFocus()
        } else {
            focusRequester.requestFocus()
        }
    }

    val availableResolutions = remember(video) {
        video.files?.mapNotNull { it.label }?.distinct() ?: emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
            .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!showControls) {
                                showControls = true
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!showControls) {
                                showControls = true
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            showControls = true
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use custom Compose overlay for controls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom TV Controller Overlay
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar: Back & Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.tv.material3.Button(
                            onClick = onBack,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            androidx.tv.material3.Text("< Back", fontSize = 14.sp)
                        }
                        androidx.tv.material3.Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Center Play/Pause button
                    androidx.tv.material3.Button(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.focusRequester(playPauseFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2C3E50),
                            focusedContainerColor = Color(0xFF34495E)
                        )
                    ) {
                        androidx.tv.material3.Text(
                            text = if (isPlaying) "⏸ Pause" else "▶ Play",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    // Bottom Bar: CC, Quality & Progress
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val posText = formatProgressTime(currentPosition)
                            val durText = formatProgressTime(duration)
                            androidx.tv.material3.Text(
                                text = "$posText / $durText",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Subtitle Toggle
                                androidx.tv.material3.OutlinedButton(
                                    onClick = {
                                        subtitlesEnabled = !subtitlesEnabled
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                            .build()
                                    }
                                ) {
                                    androidx.tv.material3.Text(
                                        text = if (subtitlesEnabled) "Subtitles: ON" else "Subtitles: OFF",
                                        fontSize = 14.sp
                                    )
                                }

                                // Quality Selector
                                androidx.tv.material3.OutlinedButton(
                                    onClick = {
                                        showQualityDialog = true
                                    }
                                ) {
                                    androidx.tv.material3.Text(
                                        text = "Quality: ${currentResolution ?: "Auto"}",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val progressFraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isProgressBarFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionLeft -> {
                                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            val progressHeight = if (isProgressBarFocused) 10.dp else 6.dp
                            val progressColor = if (isProgressBarFocused) Color(0xFF3498DB) else Color.White
                            LinearProgressIndicator(
                                progress = progressFraction,
                                color = progressColor,
                                trackColor = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().height(progressHeight)
                            )
                        }
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
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                androidx.tv.material3.Text(
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
                    androidx.tv.material3.Text(
                        text = "Cannot Play Video",
                        color = Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.tv.material3.Text(
                        text = errorMsg,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.tv.material3.Button(onClick = onBack) {
                        androidx.tv.material3.Text("Go Back")
                    }
                }
            }
        }
    }

    // Resolution Selector Dialog Overlay
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { androidx.tv.material3.Text("Select Video Quality", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    item {
                        Surface(
                            onClick = {
                                currentResolution = null
                                showQualityDialog = false
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (currentResolution == null) Color(0xFF34495E) else Color(0xFF262626),
                                focusedContainerColor = Color(0xFF333333)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.tv.material3.Text(
                                text = "Auto (Best Quality)",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    items(availableResolutions) { res ->
                        Surface(
                            onClick = {
                                currentResolution = res
                                showQualityDialog = false
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (currentResolution == res) Color(0xFF34495E) else Color(0xFF262626),
                                focusedContainerColor = Color(0xFF333333)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.tv.material3.Text(
                                text = res,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showQualityDialog = false }) {
                    androidx.tv.material3.Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

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
