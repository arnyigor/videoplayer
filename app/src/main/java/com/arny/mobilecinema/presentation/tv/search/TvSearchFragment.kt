package com.arny.mobilecinema.presentation.tv.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Presenter для отображения фильтров поиска
 */
class SearchFilterPresenter : Presenter() {
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(24, 12, 24, 12)
            textSize = 14f
            setBackgroundResource(R.drawable.bg_sort_category_selector)
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val filter = item as? SearchFilter ?: return
        val textView = viewHolder.view as TextView

        textView.text = textView.context.getString(filter.labelResId)

        val isSelected = filter.ordinal == selectedPosition
        textView.setTextColor(
            ContextCompat.getColor(
                textView.context,
                if (isSelected) R.color.white else R.color.sort_category_text
            )
        )
        textView.isSelected = isSelected
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
    }
}

class TvSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: TvSearchViewModel by viewModel()

    private val movieDiff = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(a: ViewMovie, b: ViewMovie) = a.dbId == b.dbId
        override fun areContentsTheSame(a: ViewMovie, b: ViewMovie) = a == b
    }

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var filterRowAdapter: ArrayObjectAdapter
    private lateinit var filterPresenter: SearchFilterPresenter
    private val moviesAdapter = PagingDataAdapter(MovieCardPresenter(), movieDiff)

    private var searchJob: Job? = null
    private var selectedFilter = SearchFilter.ALL
    private var initialQueryApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем адаптеры
        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        // Row с фильтрами
        filterPresenter = SearchFilterPresenter()
        filterRowAdapter = ArrayObjectAdapter(filterPresenter).apply {
            add(SearchFilter.ALL)
            add(SearchFilter.CINEMA_ONLY)
            add(SearchFilter.SERIAL_ONLY)
        }
        filterPresenter.setSelectedPosition(selectedFilter.ordinal)
        rowsAdapter.add(ListRow(HeaderItem(-1, ""), filterRowAdapter))

        // Row с результатами
        val header = HeaderItem(0, getString(R.string.search_results))
        val row = ListRow(header, moviesAdapter)
        rowsAdapter.add(row)

        // Обработчик клика
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is ViewMovie -> {
                    findNavController().navigate(
                        TvSearchFragmentDirections.actionToDetails(item.dbId)
                    )
                }

                is SearchFilter -> {
                    selectedFilter = item
                    filterPresenter.setSelectedPosition(item.ordinal)
                    filterRowAdapter.notifyArrayItemRangeChanged(0, filterRowAdapter.size())
                    viewModel.setFilter(item)
                }
            }
        }

        // Обязательная привязка провайдера результатов
        setSearchResultProvider(this)
        updateSearchTypeTitle(AppConstants.SearchType.TITLE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (initialQueryApplied) return

        val initialSearchType = arguments?.getString("searchType")
            .orEmpty()
            .ifBlank { AppConstants.SearchType.TITLE }
        viewModel.setSearchType(initialSearchType)
        updateSearchTypeTitle(initialSearchType)

        val initialQuery = arguments?.getString("query").orEmpty().trim()
        if (initialQuery.length >= 2) {
            view.post {
                if (isAdded) {
                    setSearchQuery(initialQuery, true)
                }
            }
        }
        initialQueryApplied = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Подписываемся на результаты поиска
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { pagingData ->
                moviesAdapter.submitData(pagingData)
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    // ── SearchResultProvider ──

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

    private fun updateSearchTypeTitle(searchType: String) {
        val typeLabel = when (searchType) {
            AppConstants.SearchType.ACTORS -> getString(R.string.search_by_actors)
            AppConstants.SearchType.GENRES -> getString(R.string.search_by_genres)
            AppConstants.SearchType.DIRECTORS -> getString(R.string.search_by_directors)
            else -> getString(R.string.search_by_title)
        }
        title = "${getString(R.string.home_search)} - $typeLabel"
    }

    private fun searchWithDebounce(query: String) {
        searchJob?.cancel()
        if (query.isBlank() || query.length < 2) {
            viewModel.clearSearch()
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            viewModel.search(query)
        }
    }
}

