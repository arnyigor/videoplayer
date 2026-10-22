package com.arny.mobilecinema.presentation.tv.search

import android.os.Bundle
import android.view.View
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchSource
import com.arny.mobilecinema.presentation.sitesearch.SearchUiState
import com.arny.mobilecinema.presentation.sitesearch.SiteSearchViewModel
import com.arny.mobilecinema.presentation.tv.presenters.SiteSearchCardPresenter
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Поиск по каталогу сайта на TV (аналог мобильного экрана «Поиск на сайте»).
 * Клик по результату сохраняет карточку в БД и открывает экран деталей.
 */
class TvSiteSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SiteSearchViewModel by viewModel()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val resultsAdapter = ArrayObjectAdapter(SiteSearchCardPresenter())

    private var searchJob: Job? = null
    private var lastEmptyQuery: String? = null
    private var lastItems: List<SearchHit> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.site_search_title)

        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        rowsAdapter = ArrayObjectAdapter(presenterSelector)
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.search_results)), resultsAdapter))

        setOnItemViewClickedListener { _, item, _, _ ->
            (item as? SearchHit)?.let { viewModel.openHit(it) }
        }

        setSearchResultProvider(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state -> render(state) }
                }
                launch {
                    viewModel.toast.collectLatest { message ->
                        toast(message.toString(requireContext()))
                    }
                }
                launch {
                    viewModel.openMovie.collectLatest { dbId ->
                        findNavController().navigateSafely(
                            TvSiteSearchFragmentDirections.actionToDetails(dbId)
                        )
                    }
                }
            }
        }
    }

    // ── SearchResultProvider ──

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        viewModel.setQuery(newQuery)
        searchWithDebounce(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        viewModel.setQuery(query)
        searchWithDebounce(query)
        return true
    }

    private fun searchWithDebounce(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            viewModel.setQuery("")
            submitItems(emptyList())
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            viewModel.search()
        }
    }

    private fun render(state: SearchUiState) {
        submitItems(state.hits)

        if (state.isEmpty) {
            val query = state.query.trim()
            if (query.isNotEmpty() && lastEmptyQuery != query) {
                lastEmptyQuery = query
                toast(getString(R.string.site_search_empty))
            }
        }
    }

    private fun submitItems(items: List<SearchHit>) {
        if (items == lastItems) return
        lastItems = items
        resultsAdapter.clear()
        if (items.isNotEmpty()) {
            resultsAdapter.addAll(0, items)
        }
    }

    private companion object {
        /** Сайт парсится запросами — не дёргаем его на каждую букву. */
        const val SEARCH_DEBOUNCE_MS = 500L
    }
}
