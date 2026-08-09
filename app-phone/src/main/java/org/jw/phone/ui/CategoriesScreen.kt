package org.jw.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jw.tv.api.CategoryData
import org.jw.tv.api.MediaItem
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.UiState

/** Categories tab: list of all root categories (replaces the TV sidebar). */
@Composable
fun CategoriesScreen(
    viewModel: MainViewModel,
    onCategoryClick: (CategoryData) -> Unit
) {
    if (viewModel.categories.isEmpty()) {
        when (val state = viewModel.uiState) {
            is UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(state.message, onRetry = viewModel::loadRootCategories)
            is UiState.Success -> EmptyView("No categories found.")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = viewModel.categories, key = { it.key }) { category ->
            Card(
                onClick = { onCategoryClick(category) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = phoneCategoryIcon(category.key),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        category.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Category content screen: one adaptive grid, section headers spanning the
 * full width. Content comes from the VM's single content slot, loaded via
 * [MainViewModel.selectCategory] on entry.
 */
@Composable
fun CategoryScreen(
    viewModel: MainViewModel,
    category: CategoryData,
    onVideoClick: (MediaItem) -> Unit
) {
    LaunchedEffect(category.key) {
        if (viewModel.currentCategory?.key != category.key) {
            viewModel.selectCategory(category)
        }
    }

    when (val state = viewModel.uiState) {
        is UiState.Loading -> LoadingView()
        is UiState.Error -> ErrorView(state.message, onRetry = { viewModel.selectCategory(category) })
        is UiState.Success -> {
            if (viewModel.subcategoriesWithMedia.isEmpty()) {
                EmptyView()
                return
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                viewModel.subcategoriesWithMedia.forEach { subcat ->
                    item(key = "header_${subcat.key}", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(subcat.name)
                    }
                    // Composite key: the same video can appear in several
                    // subcategories of one category — bare contentId collides.
                    items(items = subcat.videos, key = { "${subcat.key}:${it.contentId}" }) { video ->
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
}
