package com.arny.mobilecinema.presentation.tv.home

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class TvHomeFragment : BrowseSupportFragment() {

    private val viewModel: TvHomeViewModel by viewModel()

    override fun onAttach(context: Context) {
        // Koin injection
        super.onAttach(context)
    }

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val allMoviesAdapter = ArrayObjectAdapter(MovieCardPresenter())
    private val historyAdapter = ArrayObjectAdapter(MovieCardPresenter())
    private val favoritesAdapter = ArrayObjectAdapter(MovieCardPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        setupRowsAdapter()
    }

    private fun setupUI() {
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = requireContext().getColor(R.color.colorPrimary)
        searchAffordanceColor = requireContext().getColor(R.color.colorAccent)
    }

    private fun setupRowsAdapter() {
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        val allMoviesHeader = HeaderItem(0, getString(R.string.all_movies))
        rowsAdapter.add(ListRow(allMoviesHeader, allMoviesAdapter))

        val historyHeader = HeaderItem(1, getString(R.string.history))
        rowsAdapter.add(ListRow(historyHeader, historyAdapter))

        val favoritesHeader = HeaderItem(2, getString(R.string.favorites))
        rowsAdapter.add(ListRow(favoritesHeader, favoritesAdapter))

        adapter = rowsAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                findNavController().navigate(
                    TvHomeFragmentDirections.actionToDetails(item.dbId)
                )
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                viewModel.onMovieSelected(item)
            }
        }

        setOnSearchClickedListener {
            findNavController().navigate(
                TvHomeFragmentDirections.actionToSearch()
            )
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moviesDataFlow.collectLatest { pagingData ->
                // Movies loaded via paging adapter
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyMovies.collectLatest { movies ->
                updateSimpleAdapter(historyAdapter, movies)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteMovies.collectLatest { movies ->
                updateSimpleAdapter(favoritesAdapter, movies)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateAvailable.collect { available ->
                if (available) {
                    showUpdateDialog()
                }
            }
        }
    }

    private fun updateSimpleAdapter(adapter: ArrayObjectAdapter, movies: List<ViewMovie>) {
        adapter.clear()
        adapter.addAll(0, movies)
    }

    private fun showUpdateDialog() {
        TvUpdateDialogFragment.newInstance()
            .show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    companion object {
        fun newInstance() = TvHomeFragment()
    }
}
