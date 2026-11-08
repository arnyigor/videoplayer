package com.arny.mobilecinema.presentation.sitesearch

import android.os.Bundle
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.databinding.FSiteSearchBinding
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchSource
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Единый поиск (SRCH-02): одно поле, один список, два источника.
 *
 * «Везде» = локальный каталог + сайт. Локальная выдача показывается сразу, внешняя
 * подъезжает следом; ошибка сайта не стирает то, что уже найдено в каталоге.
 */
class SiteSearchFragment : Fragment(R.layout.f_site_search), KoinComponent {

    private val prefs: Prefs by inject()

    private companion object {
        /** Сколько последних видимых элементов остаётся до запроса следующей страницы. */
        const val PRELOAD_DISTANCE = 3

        /** Значение `scope`, при котором экран открывается сразу по локальному каталогу. */
        const val SCOPE_CATALOG = "CATALOG"
    }

    private val viewModel: SiteSearchViewModel by viewModel()
    private val args: SiteSearchFragmentArgs by navArgs()

    private var _binding: FSiteSearchBinding? = null
    private val binding get() = requireNotNull(_binding)

    private lateinit var searchAdapter: SiteSearchAdapter
    private var syncingSourceSelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FSiteSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        // Экран открыт как вкладка «Поиск»: запрос приходит только при переходе с главной,
        // поэтому без него возвращаться некуда — стрелка ломала бы стек вкладки (MOB-08A).
        if (args.query == null) {
            binding.toolbar.navigationIcon = null
        }

        searchAdapter = SiteSearchAdapter(
            imageBaseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty(),
        ) { hit -> viewModel.openHit(hit) }
        searchAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvSiteSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSiteSearch.adapter = searchAdapter
        // Пагинация inline: следующая страница подгружается при подходе к концу списка,
        // поэтому кнопка «Показать ещё» больше не занимает место над результатами (аудит §8.6).
        binding.rvSiteSearch.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= (recyclerView.adapter?.itemCount ?: 0) - PRELOAD_DISTANCE) {
                    viewModel.loadMore()
                }
            }
        })

        binding.btnSiteSearch.setOnClickListener {
            requireActivity().hideKeyboard()
            viewModel.search()
        }
        binding.btnSiteSearchRetry.setOnClickListener {
            viewModel.retry()
        }
        binding.btnSourceRetry.setOnClickListener { viewModel.retry() }
        binding.tiedtSiteSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                requireActivity().hideKeyboard()
                viewModel.search()
                true
            } else {
                false
            }
        }
        binding.tiedtSiteSearch.doAfterTextChanged {
            viewModel.setQuery(it?.toString().orEmpty())
        }

        binding.searchSourceGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingSourceSelection || !isChecked) return@addOnButtonCheckedListener
            viewModel.setSource(
                when (checkedId) {
                    R.id.btnSourceLocal -> SearchSource.LOCAL
                    R.id.btnSourceRemote -> SearchSource.REMOTE
                    else -> SearchSource.ALL
                }
            )
        }
        binding.btnSearchFilters.setOnClickListener { showFiltersSheet() }

        parentFragmentManager.setFragmentResultListener(
            SearchFiltersSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            val encoded = result.getString(SearchFiltersSheet.RESULT_FILTERS)
            viewModel.setFilters(SearchFilters.decode(encoded))
        }

        observeState()

        viewModel.initialize(
            query = args.query.orEmpty(),
            searchType = args.searchType,
            source = SearchSource.LOCAL.takeIf { args.scope.equals(SCOPE_CATALOG, ignoreCase = true) },
        )
        val restoredQuery = viewModel.uiState.value.query
        binding.tiedtSiteSearch.setText(restoredQuery)
        binding.tiedtSiteSearch.setSelection(restoredQuery.length)
    }

    private fun showFiltersSheet() {
        val state = viewModel.uiState.value
        val years = state.yearsRange
        SearchFiltersSheet.newInstance(
            filters = state.filters,
            genres = state.genres,
            yearMin = years?.from ?: 1900,
            yearMax = years?.to ?: SearchFilters.MAX_YEAR,
        ).show(parentFragmentManager, "search_filters")
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        render(state)
                    }
                }

                launch {
                    viewModel.toast.collectLatest { message ->
                        toast(message.toString(requireContext()))
                    }
                }

                launch {
                    viewModel.openMovie.collectLatest { dbId ->
                        findNavController().navigateSafely(
                            SiteSearchFragmentDirections.actionNavSiteSearchToNavDetails(dbId)
                        )
                    }
                }
            }
        }
    }

    private fun render(state: SearchUiState) {
        binding.pbSiteSearch.isVisible = state.isLoading || state.isOpening
        binding.btnSiteSearch.isEnabled = !state.isLoading && !state.isOpening

        // Переключатель источника синхронизируем с состоянием: его меняет и ViewModel.
        val checkedId = when (state.source) {
            SearchSource.ALL -> R.id.btnSourceAll
            SearchSource.LOCAL -> R.id.btnSourceLocal
            SearchSource.REMOTE -> R.id.btnSourceRemote
        }
        if (binding.searchSourceGroup.checkedButtonId != checkedId) {
            syncingSourceSelection = true
            binding.searchSourceGroup.check(checkedId)
            syncingSourceSelection = false
            val selected = binding.searchSourceGroup.findViewById<View>(checkedId)
            selected.post {
                selected.requestRectangleOnScreen(Rect(0, 0, selected.width, selected.height), false)
            }
        }
        val filtersAvailable = SearchListSections.areFiltersAvailable(state.source)
        binding.btnSearchFilters.isEnabled = filtersAvailable
        binding.btnSearchFilters.text = if (state.filters.isActive) {
            getString(R.string.search_filters_count, state.filters.activeCount)
        } else {
            getString(R.string.search_filters)
        }
        binding.btnSearchFilters.contentDescription = when {
            !filtersAvailable -> getString(R.string.search_filters_disabled_for_site)
            state.filters.isActive -> getString(R.string.search_filters_count, state.filters.activeCount)
            else -> getString(R.string.search_filters)
        }
        // Объяснение не раздувает кнопку фильтров до нескольких строк.
        binding.tvFiltersNotice.isVisible = !filtersAvailable || state.filtersNotAppliedToSite
        binding.tvFiltersNotice.setText(
            if (!filtersAvailable) R.string.search_filters_disabled_for_site
            else R.string.search_filters_site_notice
        )

        renderSourceStatus(state)

        // Пустой поиск — не чёрная поверхность: подсказка вместо пустоты (аудит v2, §4.6).
        val showState = state.query.isBlank() || state.isEmpty || state.isFailed
        binding.llSiteSearchState.isVisible = showState
        // Очистка поля не должна оставлять прежнюю выдачу под idle/empty/error-состоянием.
        binding.rvSiteSearch.isVisible = !showState
        binding.tvSiteSearchEmpty.setText(
            when {
                // Ошибки источников различаются: каталог — это база, сайт — сеть.
                state.isFailed -> R.string.site_search_error
                state.localError && !state.remoteError -> R.string.search_local_error
                state.remoteError -> R.string.site_search_error
                state.query.isBlank() -> R.string.search_empty_hint
                // Пустая выдача называется тем источником, по которому искали.
                state.source == SearchSource.LOCAL -> R.string.search_catalog_empty
                state.source == SearchSource.REMOTE -> R.string.site_search_empty
                else -> R.string.search_all_empty
            }
        )
        binding.btnSiteSearchRetry.isVisible = state.isFailed || state.remoteError || state.localError
        searchAdapter.submitList(SearchListSections.build(state.hits, state.source, state.filters))
    }

    /**
     * Загрузка и ошибки по каждому источнику: общий прогресс-бар не говорил, что именно
     * ещё грузится, а ошибка сайта при готовом каталоге выглядела как пустой экран.
     */
    private fun renderSourceStatus(state: SearchUiState) {
        // Статус берётся из состояния конкретного источника (S-015), а не из комбинации флагов.
        val status = when {
            state.localSection == ResultSection.LOADING -> R.string.search_status_catalog_loading
            state.remoteSection == ResultSection.LOADING -> R.string.search_status_site_loading
            state.remoteSection == ResultSection.ERROR && state.hits.isNotEmpty() ->
                R.string.search_status_site_error
            state.localSection == ResultSection.ERROR && state.hits.isNotEmpty() ->
                R.string.search_status_catalog_error
            else -> null
        }
        binding.tvSourceStatus.isVisible = status != null
        binding.btnSourceRetry.isVisible = state.hits.isNotEmpty() && (state.localError || state.remoteError)
        binding.btnSourceRetry.isEnabled = !state.isLoading && !state.isOpening
        status?.let { binding.tvSourceStatus.setText(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvSiteSearch.adapter = null
        _binding = null
    }
}
