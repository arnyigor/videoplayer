package com.arny.mobilecinema.presentation.tv.search

import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: TvSearchViewModel by viewModel()

    private val movieDiff = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(a: ViewMovie, b: ViewMovie) = a.dbId == b.dbId
        override fun areContentsTheSame(a: ViewMovie, b: ViewMovie) = a == b
    }

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val moviesAdapter = PagingDataAdapter(MovieCardPresenter(), movieDiff)

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Обязательная привязка провайдера результатов
        setSearchResultProvider(this)

        // 2. Создаем ряд с результатами один раз
        val header = HeaderItem(0, getString(R.string.search_results))
        val row = ListRow(header, moviesAdapter)
        rowsAdapter.add(row)

        // 3. Обработчик клика
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                findNavController().navigate(
                    TvSearchFragmentDirections.actionToDetails(item.dbId)
                )
            }
        }

        // 4. Подписываемся на результаты поиска
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { pagingData ->
                moviesAdapter.submitData(pagingData)
            }
        }
    }

    // ── SearchResultProvider ──

    // Leanback сам вызывает этот метод, чтобы узнать, что рисовать
    override fun getResultsAdapter(): ObjectAdapter {
        return rowsAdapter
    }

    override fun onQueryTextChange(newQuery: String): Boolean {
        searchWithDebounce(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        searchWithDebounce(query)
        return true
    }

    private fun searchWithDebounce(query: String) {
        searchJob?.cancel()
        if (query.isBlank() || query.length < 2) {
            viewModel.clearSearch()
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(500) // Ждем полсекунды, пока пользователь допечатает
            Timber.d("Searching: %s", query)
            viewModel.search(query)
        }
    }
}