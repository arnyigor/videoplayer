package com.arny.mobilecinema.presentation.tv.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.catalog.CatalogViewModel
import com.arny.mobilecinema.presentation.tv.ui.catalog.CatalogScreen
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

/**
 * Каталог «Фильмы»/«Сериалы» на Compose. Навигационный контракт не менялся:
 * переход в детали — тем же `actionToDetails` с аргументом `movieId`.
 */
class TvCatalogFragment : Fragment(), KoinComponent {

    companion object {
        const val ARG_SECTION = "section"
    }

    private val viewModel: CatalogViewModel by viewModel {
        parametersOf(arguments?.getString(ARG_SECTION).orEmpty())
    }
    private val prefs: Prefs by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TvTheme {
                val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
                val state by viewModel.uiState.collectAsState()
                val gridState = remember {
                    LazyGridState(
                        firstVisibleItemIndex = viewModel.gridFirstIndex,
                        firstVisibleItemScrollOffset = viewModel.gridScrollOffset,
                    )
                }
                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.saveGridScroll(
                            gridState.firstVisibleItemIndex,
                            gridState.firstVisibleItemScrollOffset,
                        )
                    }
                }
                CatalogScreen(
                    state = state,
                    onOpenTitle = { movieId ->
                        findNavController().navigate(
                            R.id.actionToDetails,
                            bundleOf("movieId" to movieId),
                        )
                    },
                    onSelectGenre = viewModel::selectGenre,
                    onSelectSort = viewModel::selectSort,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    imageBaseUrl = baseUrl,
                    gridState = gridState,
                    restoreFocusTitleId = viewModel.focusedTitleId,
                    onTitleFocused = viewModel::saveFocusedTitle,
                )
            }
        }
    }
}
