package com.arny.mobilecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.uimodels.ListScreenState
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.isRootDestination
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.android.ext.android.inject

class HistoryFragment : Fragment(), OnSearchListener {
    private val prefs: Prefs by inject()
    private val viewModel: HistoryViewModel by viewModel()
    private lateinit var binding: FHistoryBinding
    private var itemsAdapter: HistoryRowAdapter? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var onQueryChangeSubmit = true
    private var emptySearch = true
    private var currentOrder: String = ""
    private var searchType: String = ""
    private var hasSavedData: Boolean = false

    override fun onAttach(context: Context) {
        // Koin injection
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initAdapters()
        observeData()
        initMenu()
    }

    private fun initToolbar() {
        // История — корневая вкладка (аудит v2, §3): стрелка назад нужна только когда
        // экран открыт поверх другого (например, из деталей).
        if (!isRootDestination()) {
            binding.toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        } else {
            binding.toolbar.navigationIcon = null
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadHistory()
    }

    override fun isSearchComplete(): Boolean = emptySearch

    override fun isSearchOpen(): Boolean = searchMenuItem?.isActionViewExpanded == true

    override fun resetSearchContent() {
        // Первое «Назад»: текст и результаты сбрасываются, поиск остаётся открытым.
        searchView?.setQuery("", false)
        viewModel.loadHistory()
        emptySearch = true
    }

    override fun collapseSearch() {
        // Второе «Назад»: закрыть поиск и вернуть экран в обычное состояние.
        searchView?.setQuery("", false)
        // Фокус + программное сворачивание — известный баг Toolbar: иконки
        // не возвращаются. Снимаем фокус, сворачиваем виджет и пункт меню.
        searchView?.clearFocus()
        searchView?.isIconified = true
        searchMenuItem?.collapseActionView()
        searchMenuItem?.isVisible = false
        searchMenuItem?.isVisible = true
        viewModel.loadHistory()
        emptySearch = true
    }

    private fun initMenu() {
        binding.toolbar.inflateMenu(R.menu.history_menu)
        if (!isRootDestination()) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        }
        val menu = binding.toolbar.menu
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = setupSearchView(
            menuItem = searchMenuItem!!,
            onQueryChange = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadHistory(query.orEmpty(), onQueryChangeSubmit)
                onQueryChangeSubmit = true
                updateMenuVisibility()
            },
            onMenuCollapse = {
                viewModel.loadHistory()
                emptySearch = true
                requireActivity().hideKeyboard()
                updateMenuVisibility()
            },
            onSubmitAvailable = true,
            onQuerySubmit = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadHistory(query.orEmpty())
            }
        )

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                android.R.id.home -> {
                    findNavController().popBackStack()
                    true
                }

                R.id.action_order_settings -> {
                    showCustomOrderDialog()
                    true
                }

                R.id.action_search_settings -> {
                    showCustomSearchDialog()
                    true
                }

                R.id.menu_action_clear_cache -> {
                    alertDialog(
                        getString(R.string.question_remove),
                        getString(
                            R.string.question_remove_all_history,
                        ),
                        getString(android.R.string.ok),
                        getString(android.R.string.cancel),
                        onConfirm = {
                            viewModel.clearAllViewHistory()
                        }
                    )
                    true
                }

                else -> false
            }
        }

        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_search).isVisible = hasSavedData
        menu.findItem(R.id.action_search_settings).isVisible = hasSavedData && !emptySearch
        menu.findItem(R.id.action_order_settings).isVisible = hasSavedData
        menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
    }

    private fun showCustomOrderDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_order_settings),
            layout = R.layout.d_custom_order,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.setOrder(currentOrder)
            },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
                    rbSmart.isVisible = false
                    rbLastTime.isVisible = true
                    swPriority.isVisible = false
                    val radioBtn = listOf(
                        rbLastTime to AppConstants.Order.LAST_TIME,
                        rbNone to AppConstants.Order.NONE,
                        rbTitle to AppConstants.Order.TITLE,
                        rbRatings to AppConstants.Order.RATINGS,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC,
                    )
                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.LAST_TIME -> rbLastTime.isChecked = true
                            AppConstants.Order.TITLE -> rbTitle.isChecked = true
                            AppConstants.Order.RATINGS -> rbRatings.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            else -> rbLastTime.isChecked = true
                        }
                    } ?: kotlin.run {
                        rbLastTime.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                currentOrder = orderString
                            }
                        }
                    }
                }
            }
        )
    }

    private fun showCustomSearchDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_settings_title),
            layout = R.layout.d_custom_search,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.setSearchType(searchType)
            },
            initView = {
                with(DCustomSearchBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbTitle to AppConstants.SearchType.TITLE,
                        rbDirectors to AppConstants.SearchType.DIRECTORS,
                        rbActors to AppConstants.SearchType.ACTORS,
                        rbGenres to AppConstants.SearchType.GENRES,
                    )
                    searchType.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.SearchType.TITLE -> rbTitle.isChecked = true
                            AppConstants.SearchType.DIRECTORS -> rbDirectors.isChecked = true
                            AppConstants.SearchType.ACTORS -> rbActors.isChecked = true
                            AppConstants.SearchType.GENRES -> rbGenres.isChecked = true
                            else -> rbTitle.isChecked = true
                        }
                    } ?: kotlin.run {
                        rbTitle.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                searchType = orderString
                            }
                        }
                    }
                }
            }
        )
    }

    /** Превью в строке истории 128dp — просим Glide декодировать именно этот размер. */
    private fun thumbWidthPx(): Int = (128 * resources.displayMetrics.density).toInt()

    private fun initAdapters() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        itemsAdapter = HistoryRowAdapter(
            baseUrl = baseUrl,
            thumbWidthPx = thumbWidthPx(),
            progressByMovie = { viewModel.progressByMovie.value },
            onOpenDetails = { item ->
                val action = HistoryFragmentDirections.actionNavHistoryToNavDetails(item.dbId)
                findNavController().navigateSafely(action)
            },
            onResume = { item -> viewModel.playFromHistory(item.dbId) },
        )

        // LoadStateListener — единый источник правды для loading/empty/error
        itemsAdapter?.addLoadStateListener { loadState ->
            val refresh = loadState.refresh
            when {
                refresh is LoadState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.tvEmptyView.isVisible = false
                    binding.errorView.isVisible = false
                }
                refresh is LoadState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.tvEmptyView.isVisible = false
                    binding.errorView.isVisible = true
                    val errorMsg = (refresh as? LoadState.Error)?.error?.localizedMessage
                        ?: getString(R.string.error_loading_data)
                    binding.tvErrorMessage.text = errorMsg
                }
                refresh is LoadState.NotLoading -> {
                    binding.progressBar.isVisible = false
                    binding.errorView.isVisible = false
                    val isEmpty = (itemsAdapter?.itemCount ?: 0) == 0
                    binding.tvEmptyView.isVisible = isEmpty
                    hasSavedData = !isEmpty
                    updateMenuVisibility()
                }
            }
        }

        binding.rvHistoryList.apply {
            adapter = itemsAdapter
            setHasFixedSize(true)
            // История — список строк «продолжить просмотр», а не сетка постеров (HIST-01).
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(),
                androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
                false
            )
        }

        // Кнопка Retry
        binding.btnRetry.setOnClickListener {
            itemsAdapter?.retry()
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.screenState.collectLatest { state ->
                when (state) {
                    is ListScreenState.Loading -> {
                        binding.progressBar.isVisible = true
                        binding.tvEmptyView.isVisible = false
                        binding.errorView.isVisible = false
                    }
                    is ListScreenState.Empty -> {
                        binding.progressBar.isVisible = false
                        binding.tvEmptyView.isVisible = true
                        binding.errorView.isVisible = false
                        hasSavedData = false
                        updateMenuVisibility()
                    }
                    is ListScreenState.Error -> {
                        binding.progressBar.isVisible = false
                        binding.tvEmptyView.isVisible = false
                        binding.errorView.isVisible = true
                        binding.tvErrorMessage.text = state.message
                    }
                    is ListScreenState.Content -> {
                        // Visibility управляется через LoadStateListener
                    }
                }
            }
        }
        launchWhenCreated {
            viewModel.historyDataFlow.collectLatest { movies ->
                itemsAdapter?.submitData(movies)
            }
        }
        launchWhenCreated {
            viewModel.order.collectLatest { order ->
                currentOrder = order
            }
        }
        launchWhenCreated {
            viewModel.progressByMovie.collectLatest {
                itemsAdapter?.refreshProgress()
            }
        }
        launchWhenCreated {
            viewModel.commands.collectLatest { command ->
                when (command) {
                    is HistoryCommand.Play -> navigateToPlayer(command)
                    is HistoryCommand.PlayError -> toast(getString(R.string.history_resume_error))
                }
            }
        }
    }

    /**
     * Плеер открывается с полным тайтлом; серию и позицию он берёт из общего стора
     * прогресса — поэтому из истории не нужно передавать индексы сезона/серии.
     */
    private fun navigateToPlayer(command: HistoryCommand.Play) {
        try {
            // `path` объявлен в графе как nullable без defaultValue: ключ должен присутствовать,
            // иначе Safe Args падает с «Required argument path is missing». Серию и позицию
            // подставляет сам плеер из общего стора прогресса.
            findNavController().navigate(
                R.id.nav_player_view,
                androidx.core.os.bundleOf("path" to null, "movie" to command.movie)
            )
        } catch (e: Exception) {
            Timber.e(e, "history: failed to open player for movieId=%d", command.movie.dbId)
            toast(getString(R.string.history_resume_error))
        }
    }
}
