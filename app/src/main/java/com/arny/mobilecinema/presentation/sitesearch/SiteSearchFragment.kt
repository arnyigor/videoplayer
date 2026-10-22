package com.arny.mobilecinema.presentation.sitesearch

import android.os.Bundle
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

        val initialQuery = args.query.orEmpty()
        if (initialQuery.isNotBlank()) {
            binding.tiedtSiteSearch.setText(initialQuery)
            binding.tiedtSiteSearch.setSelection(initialQuery.length)
            viewModel.setQuery(initialQuery)
            viewModel.search()
        }
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
        }
        binding.btnSearchFilters.text = if (state.filters.isActive) {
            getString(R.string.search_filters_count, state.filters.activeCount)
        } else {
            getString(R.string.search_filters)
        }
        binding.tvFiltersNotice.isVisible = state.filtersNotAppliedToSite

        // Пустой поиск — не чёрная поверхность: подсказка вместо пустоты (аудит v2, §4.6).
        val showState = state.query.isBlank() || state.isEmpty || state.isFailed
        binding.llSiteSearchState.isVisible = showState
        binding.tvSiteSearchEmpty.setText(
            when {
                state.isFailed -> R.string.site_search_error
                state.localError || state.remoteError -> R.string.site_search_error
                state.query.isBlank() -> R.string.search_empty_hint
                else -> R.string.site_search_empty
            }
        )
        binding.btnSiteSearchRetry.isVisible = state.isFailed || state.remoteError || state.localError
        searchAdapter.submitList(state.hits)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvSiteSearch.adapter = null
        _binding = null
    }
}
