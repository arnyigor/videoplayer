package com.arny.mobilecinema.presentation.mobile.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.catalog.CatalogViewModel
import com.arny.mobilecinema.presentation.listeners.OnScrollToTopListener
import com.arny.mobilecinema.presentation.mobile.ui.catalog.MobileCatalogScreen
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme
import com.arny.mobilecinema.presentation.utils.PosterGrid
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

/**
 * Вкладки «Фильмы» и «Сериалы» (MOB-08A / CAT-01).
 *
 * Один экран на два раздела: тип выдачи приходит навигационным аргументом `section`.
 * Карточка открывает детали тем же `movieId`, что и остальные экраны, — плеер выбирает
 * источник и серию сам.
 */
class MobileCatalogFragment : Fragment(), KoinComponent, OnScrollToTopListener {

    companion object {
        const val ARG_SECTION = "section"
    }

    private val viewModel: CatalogViewModel by viewModel {
        parametersOf(arguments?.getString(ARG_SECTION).orEmpty())
    }
    private val prefs: Prefs by inject()

    /** Прокрутка живёт во фрагменте: `LazyGridState` запоминает позицию, а повторный тап по вкладке её сбрасывает. */
    private val gridState: LazyGridState by lazy {
        LazyGridState(
            firstVisibleItemIndex = viewModel.gridFirstIndex,
            firstVisibleItemScrollOffset = viewModel.gridScrollOffset,
        )
    }

    override fun scrollToTop() {
        if (!isAdded || view == null) return
        viewLifecycleOwner.lifecycleScope.launch { gridState.scrollToItem(0) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MobileTheme {
                val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
                val state by viewModel.uiState.collectAsState()
                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.saveGridScroll(
                            gridState.firstVisibleItemIndex,
                            gridState.firstVisibleItemScrollOffset,
                        )
                    }
                }
                MobileCatalogScreen(
                    state = state,
                    onOpenTitle = { movieId ->
                        findNavController().navigate(
                            R.id.action_nav_catalog_to_nav_details,
                            bundleOf("id" to movieId),
                        )
                    },
                    onSelectSort = viewModel::selectSort,
                    onSelectGenre = viewModel::selectGenre,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    imageBaseUrl = baseUrl,
                    gridState = gridState,
                    columns = PosterGrid.spansFor(
                        PosterGrid.windowWidthDp(
                            requireContext(),
                            horizontalPaddingDp = (MobileDimens.ScreenPadding.value * 2).toInt(),
                        )
                    ),
                )
            }
        }
    }
}
