package com.arny.mobilecinema.presentation.poster

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.catalog.CatalogSort
import com.arny.mobilecinema.presentation.catalog.CatalogUiState
import com.arny.mobilecinema.presentation.compose.ComposeTestActivity
import com.arny.mobilecinema.presentation.tv.ui.catalog.CatalogScreen
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The grid must start under the chips: a stray filler pushed the first row off screen on TV. */
class TvCatalogLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()

    private val items = (1L..24L).map { ViewMovie(dbId = it, title = "Title $it", type = MovieType.CINEMA.value) }

    @Test fun firstPosterRowIsVisibleRightBelowTheFilters() {
        compose.runOnUiThread {
            compose.activity.setContent {
                TvTheme {
                    val state by remember {
                        mutableStateOf(
                            CatalogUiState(
                                section = CatalogSection.FILMS,
                                genres = listOf("боевик", "драма", "комедия", "ужасы", "фантастика"),
                                sort = CatalogSort.NEW,
                                items = items,
                                isLoading = false,
                            ),
                        )
                    }
                    CatalogScreen(
                        state = state,
                        onOpenTitle = {},
                        onSelectGenre = {},
                        onSelectSort = {},
                        onLoadMore = {},
                        onRetry = {},
                        gridState = rememberLazyGridState(),
                    )
                }
            }
        }
        compose.waitForIdle()
        val poster = compose.onAllNodes(androidx.compose.ui.test.hasText("Title 1"), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        val topDp = (poster.boundsInRoot.top / compose.density.density)
        // Header + sort chips + genre chips need about 110 dp; anything near the middle is a layout bug.
        fun top(text: String): Float = compose.onAllNodes(androidx.compose.ui.test.hasText(text), useUnmergedTree = true)
            .fetchSemanticsNodes().minOf { it.boundsInRoot.top } / compose.density.density
        val header = top("Фильмы")
        val sortChip = top("Рекомендуемые")
        val genreChip = top("Все")
        val posterTop = top("Title 1")
        // Regression guard: the sort chips used to wrap and grow the row by ~460 dp, pushing the
        // first row off screen (genre chip landed at 520 dp, poster title at 728 dp).
        assertTrue("Layout tops: header=$header sort=$sortChip genre=$genreChip poster=$posterTop",
            genreChip - sortChip < 80f && posterTop < 350f)
    }
}
