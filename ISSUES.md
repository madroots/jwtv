# JWTVL Issue Tracker

Generated: 2026-07-20. Updated on each work session.

---

## Legend

| Symbol | Meaning        |
|--------|----------------|
| ✅     | Done           |
| 🔄     | In progress    |
| ⬜     | Pending        |
| ❌     | Won't fix / dropped |

**Effort:** XS < 1h · S < 4h · M < 1d · L < 3d

---

## P0 — Must Fix (Actively Broken) — ✅ ALL DONE (v3.0.9 / code 12)

| ID     | Status | Effort | Impact | Description |
|--------|--------|--------|--------|-------------|
| BUG-1  | ✅ | S | HIGH | Fixed scroll-to-top: `rememberLazyListState()` passed to `LazyColumn`; `LaunchedEffect(categoryKey, uiState)` calls `animateScrollToItem(0)` on every category switch. |
| BUG-2  | ✅ | S | HIGH | Sidebar width fixed at `240.dp` in layout tree. `graphicsLayer { translationX = offset.dp.toPx() }` slides it −170dp off-screen when collapsed. Zero recomposition impact on content. |
| BUG-3  | ✅ | S | MED  | `updateDialogDismissed: Boolean` hoisted to ViewModel. Eliminates dead `remember { mutableStateOf(true) }`. |
| BUG-4  | ✅ | XS | MED  | `isMinifyEnabled = true` + `proguard-rules.pro` covering serialization, OkHttp, ExoPlayer, Compose. |
| BUG-6  | ✅ | S | HIGH | `VideoCard` uses `androidx.tv.material3.Surface(onClick)`. D-pad center/enter fires correctly. Focus highlight via `ClickableSurfaceDefaults.border(focusedBorder = …)`. |
| PERF-3 | ✅ | S | HIGH | `CacheManager.getJson`/`saveJson` are `suspend` dispatched on `Dispatchers.IO`. |

---

## P1 — Should Fix (Performance / UX Quality) — ✅ ALL DONE (v3.1.0 / code 13)

| ID     | Status | Effort | Impact | Description |
|--------|--------|--------|--------|-------------|
| PERF-1 | ✅ | L | HIGH | Migrated to `TvLazyColumn` + `TvLazyRow` from `androidx.tv.foundation` — TV-native scroll physics and focus handling. |
| PERF-2 | ✅ | S | MED  | `JwTvApp` provides Coil `ImageLoader` with 25% heap memory cache (≈64 MB) and 256 MB disk cache. Hero `AsyncImage` uses explicit `size(1280, 720)` decode hint. |
| PERF-4 | ✅ | S | LOW  | Sidebar emoji `Text` replaced with Material Icons `ImageVector` drawables. GPU vector paths, no Unicode shaping pipeline. |
| PERF-5 | ✅ | M | HIGH | `@Immutable` added to all API data classes and `SubcategoryWithMedia`. |
| PERF-6 | ❌ | — | —    | Covered by BUG-2 (GPU translate replaced layout-width animation). |
| BUG-5  | ✅ | M | MED  | Subcategory fetch uses `Mutex`-guarded incremental emit: rows appear one by one as they load. Parallelism capped at 3 concurrent requests. |
| UX-3   | ✅ | M | MED  | `SubcomposeAsyncImage` with `loading = { ShimmerBox() }` in `VideoCard` and `HeroBanner`. |
| UX-5   | ✅ | S | HIGH | ViewModel stores `featuredVideo: MediaItem?` populated from root `VideoOnDemand` media array. |
| UX-6   | ✅ | S | HIGH | Main content `Box` intercepts D-pad Left to open sidebar drawer. |
| UX-8   | ✅ | XS | LOW | Version string in sidebar reads `BuildConfig.VERSION_NAME`. |

---

## P2 — Features & Architecture — ✅ ALL DONE (v3.2.0 / code 14)

| ID     | Status | Effort | Impact | Description |
|--------|--------|--------|--------|-------------|
| BUG-7  | ✅ | XS | HIGH | `MediaItem.contentId` uses deterministic URL path hash instead of `video.title` — prevents watch progress key collisions across language switches and duplicate episode titles. |
| UX-4   | ✅ | M | HIGH | "Continue Watching" row on Home screen displaying partially watched videos with progress bar overlay. Launching directly resumes from saved position. |
| ARCH-6 | ✅ | L | HIGH | Room Database (`AppDatabase`, `MediaDao`, `WatchProgressEntity`, `FavoriteEntity`). "❤️ Favorites" sidebar category and toggle button in details overlay. |
| UX-2   | ✅ | M | MED  | Replaced Material 3 `AlertDialog` with TV-native modal overlay `Surface(820.dp x 440.dp)` with 16:9 poster preview, details, and direct focus on "▶ Play Video". |
| ARCH-1 | ✅ | M | HIGH | ViewModel state encapsulation (`private set` on properties) and Unidirectional Data Flow pattern. |
| ARCH-2 | ✅ | L | HIGH | TV Navigation stack using `androidx.navigation.compose.NavHost` & `rememberNavController` with `"browser"` and `"player"` routes. |
| ARCH-3 | ✅ | S | MED  | Centralized network & cache initialization (`MediatorClient.init`) in `JwTvApp.onCreate()`. |
| ARCH-4 | ✅ | M | MED  | Cache TTL & Stale-While-Revalidate policy in `CacheManager`: 24h categories TTL, 6h subcategories TTL, 7d hard max age. |

---

## Changelog

| Date       | Version     | Changes |
|------------|-------------|---------|
| 2026-07-20 | 3.0.8 / 11  | Initial audit. Plan established. |
| 2026-07-20 | 3.0.9 / 12  | **P0 complete**: BUG-1 scroll-to-top, BUG-2 sidebar GPU slide, BUG-3 dialog state hoisted, BUG-4 R8 enabled, BUG-6 TV Surface on cards, PERF-3 cache IO off main thread. |
| 2026-07-20 | 3.1.0 / 13  | **P1 complete**: PERF-1 TvLazyColumn/Row, PERF-2 Coil 64 MB cache + disk cache, PERF-4 Material Icons vector, PERF-5 @Immutable, BUG-5 incremental subcategory emit, UX-3 shimmer placeholders, UX-5 featured video, UX-6 D-pad Left opens sidebar, UX-8 BuildConfig.VERSION_NAME. |
| 2026-07-20 | 3.2.0 / 14  | **P2 complete**: BUG-7 stable contentId URL hash, UX-4 Continue Watching row, ARCH-6 Room DB persistence for Watch History & Favorites, UX-2 TV-native full-screen details overlay, ARCH-1 ViewModel state encapsulation, ARCH-2 NavHost navigation, ARCH-3 JwTvApp network init, ARCH-4 TTL cache expiry & SWR policy. |
