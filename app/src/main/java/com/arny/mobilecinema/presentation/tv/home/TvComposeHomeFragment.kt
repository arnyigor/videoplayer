package com.arny.mobilecinema.presentation.tv.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.tv.catalog.TvCatalogFragment
import com.arny.mobilecinema.presentation.tv.ui.home.HomeScreen
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.inject

/**
 * Главная на Compose. Навигационный контракт прежний: те же destination id и аргументы.
 * «Продолжить» пока открывает детали — CTA там ведёт на актуальную серию через resolver.
 */
class TvComposeHomeFragment : Fragment(), org.koin.core.component.KoinComponent {

    private val viewModel: TvHomeViewModel by viewModel()
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
                val content by viewModel.homeContent.collectAsState(
                    initial = HomeContent(hero = null, rows = emptyList()),
                )
                val listState = remember {
                    LazyListState(
                        firstVisibleItemIndex = viewModel.homeScrollIndex,
                        firstVisibleItemScrollOffset = viewModel.homeScrollOffset,
                    )
                }
                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.saveHomeScroll(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                    }
                }
                HomeScreen(
                    content = content,
                    listState = listState,
                    onOpenTitle = { movieId ->
                        findNavController().navigate(
                            R.id.actionToDetails,
                            bundleOf("movieId" to movieId),
                        )
                    },
                    onContinue = { item ->
                        findNavController().navigate(
                            R.id.actionToDetails,
                            bundleOf("movieId" to item.movieDbId),
                        )
                    },
                    onPlay = { movieId ->
                        findNavController().navigate(
                            R.id.actionToPlayer,
                            bundleOf("movieId" to movieId),
                        )
                    },
                    onSearch = { findNavController().navigate(R.id.actionToSearch) },
                    onOpenCatalog = { section ->
                        findNavController().navigate(
                            R.id.actionToCatalog,
                            bundleOf(TvCatalogFragment.ARG_SECTION to section.name),
                        )
                    },
                    restoreFocusTitleId = viewModel.focusedTitleId,
                    onTitleFocused = viewModel::saveFocusedTitle,
                    imageBaseUrl = baseUrl,
                )
            }
        }
    }
}
