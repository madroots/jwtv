package org.jw.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jw.tv.api.MediaItem
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.UiState

/**
 * Favorites tab. Uses the VM's single content slot via
 * [MainViewModel.selectFavoritesCategory] (same path as the TV sidebar).
 */
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onVideoClick: (MediaItem) -> Unit
) {
    // Runs on entry and whenever the favorites set changes (e.g. unfavorite
    // from the details sheet).
    LaunchedEffect(viewModel.favoriteContentIds) { viewModel.selectFavoritesCategory() }

    when (val state = viewModel.uiState) {
        is UiState.Loading -> LoadingView()
        is UiState.Error -> ErrorView(state.message, onRetry = viewModel::selectFavoritesCategory)
        is UiState.Success -> {
            val favorites = viewModel.subcategoriesWithMedia
                .firstOrNull { it.key == "FAVORITES_ROW" }?.videos ?: emptyList()
            if (favorites.isEmpty()) {
                EmptyView("No favorites yet.\nTap ♡ on a video to add it here.")
                return
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = favorites, key = { it.contentId }) { video ->
                    PhoneVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        downloadedIds = viewModel.downloadedIds,
                        progressFraction = viewModel.watchProgressMap[video.contentId]
                    )
                }
            }
        }
    }
}
