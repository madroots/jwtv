package org.jw.phone.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.UiState
import org.jw.tv.ui.formatDuration

/**
 * Phone home screen — mirrors the TV home semantics: featured hero, Continue
 * Watching row, then one horizontal row per subcategory of the default
 * (first root) category.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit
) {
    // The VM holds a single content slot; re-select the default category
    // whenever Home is shown (Favorites/Category screens replace it).
    val homeCategory = viewModel.categories.firstOrNull()
    LaunchedEffect(homeCategory?.key) {
        if (homeCategory != null && viewModel.currentCategory?.key != homeCategory.key) {
            viewModel.selectCategory(homeCategory)
        }
    }

    when (val state = viewModel.uiState) {
        is UiState.Loading -> LoadingView()
        is UiState.Error -> ErrorView(state.message, onRetry = {
            viewModel.currentCategory?.let { viewModel.selectCategory(it) }
                ?: viewModel.loadRootCategories()
        })
        is UiState.Success -> {
            val featured = viewModel.featuredVideo
                ?: viewModel.subcategoriesWithMedia.firstOrNull()?.videos?.firstOrNull()
            val hasContent = viewModel.subcategoriesWithMedia.isNotEmpty()
                    || viewModel.continueWatchingList.isNotEmpty()

            if (!hasContent) {
                EmptyView()
                return
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (featured != null) {
                    item(key = "hero") {
                        HeroCard(video = featured, onClick = { onVideoClick(featured) })
                    }
                }

                if (viewModel.continueWatchingList.isNotEmpty()) {
                    item(key = "continue_watching") {
                        Column {
                            SectionHeader(
                                "Continue Watching",
                                Modifier.padding(horizontal = 16.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = viewModel.continueWatchingList,
                                    key = { it.contentId }
                                ) { progress ->
                                    ContinueWatchingCard(
                                        progress = progress,
                                        onClick = {
                                            val mediaItem = try {
                                                MediatorClient.json.decodeFromString(
                                                    MediaItem.serializer(),
                                                    progress.rawMediaItemJson
                                                )
                                            } catch (e: Exception) { null }
                                            if (mediaItem != null) onPlayVideo(mediaItem)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = viewModel.subcategoriesWithMedia,
                    key = { it.key }
                ) { subcat ->
                    Column {
                        SectionHeader(subcat.name, Modifier.padding(horizontal = 16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = subcat.videos, key = { it.contentId }) { video ->
                                RowVideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    downloadedIds = viewModel.downloadedIds,
                                    progressFraction = viewModel.watchProgressMap[video.contentId]
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/** Featured video hero — tap opens details. Phone counterpart of HeroBanner. */
@Composable
private fun HeroCard(video: MediaItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.getThumbnailUrl())
                    .size(1280, 720)
                    .build(),
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1F27)),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF090B0E)),
                            startY = 200f
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    "FEATURED VIDEO",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    video.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val dur = formatDuration(video.duration)
                if (dur.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("$dur • HD • Video", color = Color(0xFF8B949E), fontSize = 12.sp)
                }
            }
        }
    }
}
