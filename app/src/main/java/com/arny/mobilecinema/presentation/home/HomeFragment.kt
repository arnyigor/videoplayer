package com.arny.mobilecinema.presentation.home

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.DAddByLinkBinding
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FHomeBinding

import com.arny.mobilecinema.domain.links.SiteLink
import com.arny.mobilecinema.domain.links.SiteLinkParser
import com.arny.mobilecinema.domain.links.SiteLinkResult
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.extendedsearch.ExtendSearchResult
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.presentation.catalog.CatalogSection
import com.arny.mobilecinema.presentation.catalog.CatalogViewModel
import com.arny.mobilecinema.presentation.mobile.ui.catalog.MobileCatalogScreen
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileDimens
import com.arny.mobilecinema.presentation.mobile.home.MobileHomeViewModel
import com.arny.mobilecinema.presentation.mobile.ui.home.MobileHomeScreen
import com.arny.mobilecinema.presentation.mobile.ui.theme.MobileTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.arny.mobilecinema.presentation.listeners.OnScrollToTopListener
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.PosterGrid
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.getImgCompat
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.inputDialog
import com.arny.mobilecinema.presentation.utils.isNotificationsFullyEnabled
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.openAppSettings
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.requestPermission
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.toastError
import com.arny.mobilecinema.presentation.utils.unlockOrientation
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

/**
 * HomeFragment handles the primary UI for browsing videos.
 * It coordinates with [HomeViewModel] to fetch and display data,
 * manages search state, user permissions, and interactions with other app components.
 */
class HomeFragment : Fragment(), OnSearchListener, OnScrollToTopListener, KoinComponent {
    /** Companion object holding request codes for activity results. */
    private companion object {
        const val REQUEST_LOAD: Int = 99
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    private val prefs: Prefs by inject()

    private val viewModel: HomeViewModel by viewModel()

    /** Главная на Compose (MOB-09C); создаётся только когда включён флаг в настройках. */
    private val mobileHomeViewModel: MobileHomeViewModel by viewModel()

    /**
     * Каталог внутри главной (CAT-01): на раздел свой ViewModel и своя прокрутка,
     * поэтому Фильмы/Сериалы не делят фильтры и позицию с лентой «Все».
     */
    private val filmsCatalogViewModel: CatalogViewModel by viewModel {
        parametersOf(CatalogSection.FILMS.name)
    }
    private val seriesCatalogViewModel: CatalogViewModel by viewModel {
        parametersOf(CatalogSection.SERIES.name)
    }
    private val filmsGridState: LazyGridState by lazy {
        LazyGridState(
            firstVisibleItemIndex = filmsCatalogViewModel.gridFirstIndex,
            firstVisibleItemScrollOffset = filmsCatalogViewModel.gridScrollOffset,
        )
    }
    private val seriesGridState: LazyGridState by lazy {
        LazyGridState(
            firstVisibleItemIndex = seriesCatalogViewModel.gridFirstIndex,
            firstVisibleItemScrollOffset = seriesCatalogViewModel.gridScrollOffset,
        )
    }
    private val moviesInteractor: MoviesInteractor by inject()
    private lateinit var binding: FHomeBinding
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var requestId: Int = -1
    private var requestedNotice: Boolean = false
    private var likesPriority: Boolean = true
    private var currentOrder: String = ""
    private var searchType: String = ""
    private var searchAddTypes: MutableList<String> = mutableListOf(
        AppConstants.SearchType.CINEMA,
        AppConstants.SearchType.SERIAL
    )
    private var force = false
    private var emptySearch = true
    private var extendSearch = false
    private var hasQuery = false
    private var onQueryChangeSubmit = true
    private var itemsAdapter: VideoItemsAdapter? = null
    private var highlightsAdapter: HomeHighlightsAdapter? = null
    private var syncingContentTypeSelection = false
    private var extendSearchResult: ExtendSearchResult? = null
    private var permissionRequestId = 0
    private var lastImportedMovieId: Long? = null

    /** Handles activity result callbacks for file/folder selection. */
    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (requestId) {
                REQUEST_OPEN_FILE -> {
                    requestId = -1
                    onOpenFile(result.data)
                }

                REQUEST_OPEN_FOLDER -> {
                    requestId = -1
                    onOpenFolder(result.data)
                }
            }
        }
    }

    /** Launches permission request for WRITE_EXTERNAL_STORAGE. */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestFiles()
            } else {
                if (SDK_INT >= Build.VERSION_CODES.R) {
                    requestFiles()
                } else {
                    requestPermissions()
                }
            }
        }
    private val handler = Handler(Looper.getMainLooper())
    private val updateReceiver by lazy { makeBroadcastReceiver() }



    /** Inflates the fragment layout and returns the root view. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Initializes UI listeners and starts data loading. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupComposeHome()
        setupCatalogHost()
        initToolbar()
        initListeners()
        initMenu()
        initAdapters()
        observeData()
        observeResult()
        requestPermissions()
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    /** Обновляет видимость пунктов меню */
    private fun updateMenuVisibility() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_search).isVisible = hasQuery
        menu.findItem(R.id.action_search_settings).isVisible = hasQuery && !emptySearch
        menu.findItem(R.id.action_order_settings).isVisible = hasQuery
    }

    /** Sets up button listeners. */
    private fun initListeners() {
        binding.btnUpdateList.setOnClickListener {
            viewModel.loadMovies()
        }
        binding.homeContentTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingContentTypeSelection) return@addOnButtonCheckedListener
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnHomeContentAll -> HomeContentType.ALL
                R.id.btnHomeContentCinema -> HomeContentType.CINEMA
                R.id.btnHomeContentSerial -> HomeContentType.SERIAL
                else -> null
            }?.let(viewModel::setContentType)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        checkPermission()
        updateHomeContentTypeVisibility()
        updateHighlightsVisibility()
        updateComposeHomeVisibility()
        viewModel.reloadList()
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    /** Delegates file or data requests based on [requestId]. */
    private fun requestFiles() {
        when (requestId) {
            REQUEST_OPEN_FILE -> requestFile()
            REQUEST_LOAD -> downloadData()
        }
    }

    /**
     * BroadcastReceiver for update status events.
     *
     * @return a fully configured receiver that logs everything it receives.
     */
    private fun makeBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            // 1️⃣  Печатаем сам объект Intent (если он не null)
//            Timber.d("Intent dump: %s", intent?.dump())

            if (intent == null) {
                Timber.w("Received null Intent")
                return
            }

            val action = intent.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)
            Timber.d("Broadcast received – action: %s", action ?: "<null>")

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    toast(getString(R.string.update_started))
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    val movieId = intent.getLongExtra("EXTRA_MOVIE_ID", -1L)
                    val isBackgroundUpdate = intent.getBooleanExtra(
                        AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND,
                        false
                    )
                    if (isBackgroundUpdate) {
                        // Разбор запущен с экрана деталей (автообновление карточки):
                        // карточка обновляет себя сама, отсюда никуда не переходим.
                        Timber.i("Background update finished for movie id: %d", movieId)
                    } else {
                        toast(getString(R.string.update_finished_success))

                        // 3️⃣ Получаем id и логируем, если он отсутствует
                        if (movieId > 0) {
                            Timber.i("Found movie id: %d – loading details.", movieId)
                            viewModel.findMovieByImportedUrl(movieId)
                        } else {
                            Timber.w(
                                "No valid movie id in success broadcast. "
                                        + "Fallback to list reload."
                            )
                            // fallback – reload list
                            viewModel.loadMovies()
                        }
                    }
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    toast(getString(R.string.update_finished_error))
                    Timber.e("Update finished with error.")
                    viewModel.loadMovies()
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    Timber.d(
                        "Update progress: %d%%",
                        intent.getIntExtra("progress_percent", -1)
                    )
                }

                else -> {                                 // 4️⃣ Неизвестный action
                    Timber.w(
                        "Unknown update status: %s. "
                                + "Intent extras: %s",
                        action, intent.extras?.keySet()?.joinToString(", ")
                    )
                }
            }
        }
    }

    /** Handles pending notification permission request. */
    private fun checkPermission() {
        if (requestedNotice) {
            requestedNotice = false
            requestPermissions()
        }
    }

    /** Listener for extended search results from other fragments. */
    private fun observeResult() {
        setFragmentResultListener(AppConstants.FRAGMENTS.RESULTS) { _, bundle ->
            extendSearchResult = bundle.getParcelable(AppConstants.SearchType.SEARCH_RESULT)
            extendedSearch()
        }
    }

    /** Triggers an extended search once the result is ready. */
    private fun extendedSearch() {
        extendSearchResult?.let {
            handler.postDelayed({
                extendSearch = true
                viewModel.extendedSearch(it)
                val search = it.search
                if (search.isNotBlank()) {
                    emptySearch = false
                    onQueryChangeSubmit = false
                    searchMenuItem?.expandActionView()
                    searchView?.setQuery(search, false)
                }
                updateHighlightsVisibility()
                updateFilterChips()
            }, 350)
            extendSearchResult = null
        }
    }

    /** Extracts intent parameters (director/actor/genre) and initiates a search. */
    private fun getIntentParams() {
        if (arguments?.isEmpty == false) {
            var query = ""
            val director = getIntentString(AppConstants.PARAMS.DIRECTOR)
            val actor = getIntentString(AppConstants.PARAMS.ACTOR)
            val genre = getIntentString(AppConstants.PARAMS.GENRE)
            when {
                !director.isNullOrBlank() -> {
                    query = director
                    searchType = AppConstants.SearchType.DIRECTORS
                }

                !actor.isNullOrBlank() -> {
                    query = actor
                    searchType = AppConstants.SearchType.ACTORS
                }

                !genre.isNullOrBlank() -> {
                    query = genre
                    searchType = AppConstants.SearchType.GENRES
                }
            }
            setMenuSearchQuery(query)
        }
    }

    /** Applies a pre‑determined search query to the UI and triggers data load. */
    private fun setMenuSearchQuery(query: String) {
        if (query.isNotBlank() && searchType.isNotBlank()) {
            viewModel.setSearchType(
                type = searchType,
                submit = false,
                addTypes = searchAddTypes
            )
            onQueryChangeSubmit = false
            searchMenuItem?.expandActionView()
            searchView?.setQuery(query, false)
            viewModel.loadMovies(query, delay = true)
            arguments?.clear()
            updateHighlightsVisibility()
            updateFilterChips()
        }
    }

    private fun getIntentString(param: String) = arguments?.getString(param)

    /** Orchestrates permission requests for notifications and storage. */
    private fun requestPermissions() {
        if (DeviceUtils.isTV(requireContext())) return
        when (permissionRequestId) {
            0 -> requestNotice()
            1 -> requestStorage()
        }
    }

    /** Requests notification settings; if denied, prompts user to open app settings. */
    private fun requestNotice() {
        val notificationsFullyEnabled = isNotificationsFullyEnabled()
        if (!notificationsFullyEnabled) {
            alertDialog(
                title = getString(R.string.need_notice_permission_message),
                btnOkText = getString(android.R.string.ok),
                onConfirm = {
                    requestedNotice = true
                    openAppSettings()
                }
            )
        } else {
            permissionRequestId = 1
            requestPermissions()
        }
    }

    /** Requests WRITE_EXTERNAL_STORAGE permission. */
    private fun requestStorage() {
        requestId = REQUEST_LOAD
        requestPermission(
            resultLauncher = requestPermissionLauncher,
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onNeverAskAgain = {
                dialogRequestStoragePermission()
            },
            checkPermissionOk = {
                downloadData()
            }
        )
    }

    private fun dialogRequestStoragePermission() {
        alertDialog(
            title = getString(R.string.need_permission_message),
            btnOkText = getString(android.R.string.ok),
            onConfirm = { openAppSettings() }
        )
    }

    /** Initiates data download, handling connectivity checks. */
    private fun downloadData(force: Boolean = false) {
        when (getConnectionType(requireContext())) {
            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }

            else -> {
                this.force = force
                viewModel.downloadData(force)
                viewModel.checkIntent()
            }
        }
    }

    /** Sets up the RecyclerView adapter and its load state listener. */
    private fun initAdapters() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        highlightsAdapter = HomeHighlightsAdapter(baseUrl) { item ->
            val action = HomeFragmentDirections.actionNavHomeToNavDetails(item.dbId)
            findNavController().navigateSafely(action)
        }
        itemsAdapter = VideoItemsAdapter(
            baseUrl = baseUrl,
            showFreshness = true,
            posterWidthPx = posterWidthPx()
        ) { item ->
            val action = HomeFragmentDirections.actionNavHomeToNavDetails(item.dbId)
            findNavController().navigateSafely(action)
        }

        // Улучшенный LoadStateListener — loading/empty/error/retry
        itemsAdapter?.addLoadStateListener { loadState ->
            val refresh = loadState.refresh
            when {
                refresh is LoadState.Loading -> {
                    binding.pbLoading.isVisible = !hasAnyVisibleContent()
                    binding.llEmptyState.isVisible = false
                    binding.errorView.isVisible = false
                }
                refresh is LoadState.Error -> {
                    binding.pbLoading.isVisible = false
                    binding.llEmptyState.isVisible = false
                    binding.errorView.isVisible = !hasAnyVisibleContent()
                    val errorMsg = refresh.error.localizedMessage
                        ?: getString(R.string.error_loading_data)
                    binding.tvErrorMessage.text = errorMsg
                }
                refresh is LoadState.NotLoading -> {
                    binding.pbLoading.isVisible = false
                    binding.errorView.isVisible = false
                    updateHighlightsVisibility()
                    val isEmpty = !hasAnyVisibleContent()
                    binding.llEmptyState.isVisible = isEmpty
                    binding.btnUpdateList.isVisible = isEmpty
                    binding.tvEmptyView.isVisible = isEmpty
                    hasQuery = !isEmpty
                    updateMenuVisibility()
                }
            }
        }

        binding.rcVideoList.apply {
            val pagingAdapter = requireNotNull(itemsAdapter)
            val headerAdapter = requireNotNull(highlightsAdapter)
            val concatAdapter = ConcatAdapter(headerAdapter, pagingAdapter)
            val gridLayoutManager = GridLayoutManager(context, posterSpans()).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (position < headerAdapter.itemCount) spanCount else 1
                }
            }
            layoutManager = gridLayoutManager
            adapter = concatAdapter
            setHasFixedSize(true)
        }

        // Кнопка Retry
        binding.btnRetry.setOnClickListener {
            itemsAdapter?.retry()
        }
    }

    /** Колонок в сетке постеров: 2 на телефоне, до 6 на планшете (MOB-07). */
    private fun posterSpans(): Int = PosterGrid.spansFor(
        PosterGrid.windowWidthDp(requireContext(), horizontalPaddingDp = 8)
    )

    private fun posterWidthPx(): Int = PosterGrid.posterWidthPx(
        widthDp = PosterGrid.windowWidthDp(requireContext(), horizontalPaddingDp = 8),
        spans = posterSpans(),
        density = resources.displayMetrics.density,
    )

    /** Collects flows from the ViewModel and updates UI accordingly. */
    private fun observeData() {
        // loading/empty теперь управляются через LoadStateListener в initAdapters()
        launchWhenCreated {
            viewModel.error.collectLatest { error ->
                when (error) {
                    is ThrowableString -> {
                        toastError(error.throwable)
                    }

                    else -> toast(error.toString(requireContext()))
                }
            }
        }
        launchWhenCreated {
            viewModel.moviesDataFlow.collectLatest { movies ->
                itemsAdapter?.submitData(movies)
            }
        }
        launchWhenCreated {
            viewModel.homeHighlights.collectLatest { highlights ->
                highlightsAdapter?.submitHighlights(highlights)
                updateHighlightsVisibility()
            }
        }
        launchWhenCreated {
            viewModel.contentType.collectLatest { type ->
                val checkedId = when (type) {
                    HomeContentType.ALL -> R.id.btnHomeContentAll
                    HomeContentType.CINEMA -> R.id.btnHomeContentCinema
                    HomeContentType.SERIAL -> R.id.btnHomeContentSerial
                }
                if (binding.homeContentTypeGroup.checkedButtonId != checkedId) {
                    syncingContentTypeSelection = true
                    binding.homeContentTypeGroup.check(checkedId)
                    syncingContentTypeSelection = false
                }
                updateCatalogVisibility()
            }
        }
        launchWhenCreated {
            viewModel.toast.collectLatest { wrappedString ->
                toast(wrappedString.toString(requireContext()))
            }
        }
        launchWhenCreated {
            viewModel.order.collectLatest { order ->
                currentOrder = order
            }
        }
        launchWhenCreated {
            viewModel.alert.collectLatest { alert ->
                if (alert.type != AlertType.SimpleAlert || force) {
                    alertDialog(
                        title = alert.title.toString(requireContext()).orEmpty(),
                        content = alert.content?.toString(requireContext()),
                        btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                        btnCancelText = alert.btnCancel?.toString(requireContext()),
                        btnNeutralText = alert.btnNeutral?.toString(requireContext()),
                        cancelable = alert.cancelable,
                        icon = alert.icon?.let { requireContext().getImgCompat(it) },
                        onConfirm = {
                            viewModel.onConfirmAlert(alert.type)
                        },
                        onCancel = {
                            viewModel.onCancelAlert(alert.type)
                        },
                        onNeutral = {
                            viewModel.onNeutralAlert(alert.type)
                        }
                    )
                }
            }
        }
        launchWhenCreated {
            viewModel.urlData.collectLatest { url ->
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
                }
            }
        }
        launchWhenCreated {
            viewModel.navigate.collectLatest { event ->
                when (event) {
                    is NavigationEvent.ToDetails -> {
                        findNavController().navigateSafely(
                            HomeFragmentDirections.actionNavHomeToNavDetails(event.movieDbId)
                        )
                        lastImportedMovieId = null
                    }
                }
            }
        }
    }

    /** Обновляет чипы активных фильтров над списком */
    private fun updateFilterChips() {
        updateHomeContentTypeVisibility()
        binding.filterChips.removeAllViews()

        val hasActiveFilters = extendSearch || searchType.isNotBlank() ||
                searchAddTypes.size < 2 || arguments?.isEmpty == false

        if (!hasActiveFilters) {
            binding.filterChipsScroll.isVisible = false
            return
        }

        binding.filterChipsScroll.isVisible = true

        // Чип типа поиска (director/actor/genre из intent)
        val director = getIntentString(AppConstants.PARAMS.DIRECTOR)
        val actor = getIntentString(AppConstants.PARAMS.ACTOR)
        val genre = getIntentString(AppConstants.PARAMS.GENRE)

        when {
            !director.isNullOrBlank() -> addFilterChip("Режиссёр: $director") {
                resetFilters()
            }
            !actor.isNullOrBlank() -> addFilterChip("Актёр: $actor") {
                resetFilters()
            }
            !genre.isNullOrBlank() -> addFilterChip("Жанр: $genre") {
                resetFilters()
            }
        }

        // Чип расширенного поиска
        if (extendSearch && extendSearchResult != null) {
            val result = extendSearchResult!!
            if (result.search.isNotBlank()) {
                addFilterChip("Поиск: ${result.search}") {
                    resetFilters()
                }
            }
            if (result.genres.isNotEmpty()) {
                addFilterChip("Жанры: ${result.genres.take(3).joinToString(", ")}") {
                    resetFilters()
                }
            }
            if (result.countries.isNotEmpty()) {
                addFilterChip("Страны: ${result.countries.take(3).joinToString(", ")}") {
                    resetFilters()
                }
            }
            if (result.yearsRange.from > 1900 || result.yearsRange.to > 0) {
                addFilterChip("Годы: ${result.yearsRange.from}–${result.yearsRange.to}") {
                    resetFilters()
                }
            }
        }

        // Чип сортировки
        if (currentOrder.isNotBlank()) {
            val orderLabel = when (currentOrder) {
                AppConstants.Order.SMART -> "Рекомендуемые"
                AppConstants.Order.NONE -> "По дате обновления"
                AppConstants.Order.TITLE -> "По названию"
                AppConstants.Order.RATINGS -> "По рейтингам"
                AppConstants.Order.YEAR_DESC -> "По году ↓"
                AppConstants.Order.YEAR_ASC -> "По году ↑"
                else -> currentOrder
            }
            addFilterChip("Сортировка: $orderLabel") {
                resetFilters()
            }
        }
    }

    private fun addFilterChip(text: String, onClose: () -> Unit) {
        val chip = Chip(requireContext())
        chip.text = text
        chip.isCloseIconVisible = true
        chip.isClickable = false
        chip.isCheckable = false
        chip.setOnCloseIconClickListener {
            onClose()
        }
        binding.filterChips.addView(chip)
    }

    private fun resetFilters() {
        extendSearch = false
        extendSearchResult = null
        searchType = AppConstants.SearchType.TITLE
        searchAddTypes = mutableListOf(
            AppConstants.SearchType.CINEMA,
            AppConstants.SearchType.SERIAL
        )
        currentOrder = ""
        arguments?.clear()
        viewModel.loadMovies(resetAll = true)
        updateHighlightsVisibility()
        updateFilterChips()
    }

    override fun isSearchComplete(): Boolean = emptySearch && !extendSearch

    /** Повторный тап по вкладке «Главная» возвращает ленту в начало, не перезагружая данные. */
    override fun scrollToTop() {
        if (!isAdded || view == null) return
        if (binding.catalogHost.isVisible) {
            val gridState = when (viewModel.contentType.value) {
                HomeContentType.CINEMA -> filmsGridState
                HomeContentType.SERIAL -> seriesGridState
                HomeContentType.ALL -> null
            } ?: return
            viewLifecycleOwner.lifecycleScope.launch { gridState.scrollToItem(0) }
            return
        }
        binding.rcVideoList.smoothScrollToPosition(0)
    }

    private fun updateHighlightsVisibility() {
        highlightsAdapter?.setShowHighlights(shouldShowHighlights())
        updateComposeHomeVisibility()
        // Поиск и сброс поиска меняют режим главной — каталог должен следовать за ним.
        updateCatalogVisibility()
    }

    private fun isComposeHomeEnabled(): Boolean =
        prefs.get<Boolean>(PrefsConstants.PREF_KEY_COMPOSE_HOME) ?: false

    /**
     * Compose-главная (MOB-09C) показывается только в обычном режиме ленты: во время поиска
     * на экране остаётся прежний список, потому что результаты поиска живут в нём.
     */
    private fun updateComposeHomeVisibility() {
        if (!isComposeHomeEnabled()) {
            binding.composeHome.isVisible = false
            return
        }
        binding.composeHome.isVisible = isSearchComplete()
    }

    /**
     * Каталог «Фильмы»/«Сериалы» внутри главной (аудит v2, CAT-01).
     *
     * Вложенный экран накрывает ленту целиком, поэтому переключение разделов не сбрасывает
     * ни позицию ленты, ни выдачу каталога. Видимостью управляет [updateCatalogVisibility].
     */
    private fun setupCatalogHost() {
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        binding.catalogHost.addView(
            composeView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        composeView.setContent {
            MobileTheme(darkTheme = true) {
                val contentType by viewModel.contentType.collectAsState()
                when (contentType) {
                    HomeContentType.ALL -> Unit
                    HomeContentType.CINEMA -> CatalogSectionContent(
                        catalogViewModel = filmsCatalogViewModel,
                        gridState = filmsGridState,
                    )

                    HomeContentType.SERIAL -> CatalogSectionContent(
                        catalogViewModel = seriesCatalogViewModel,
                        gridState = seriesGridState,
                    )
                }
            }
        }
    }

    @Composable
    private fun CatalogSectionContent(
        catalogViewModel: CatalogViewModel,
        gridState: LazyGridState,
    ) {
        val state by catalogViewModel.uiState.collectAsState()
        val baseUrl = remember { prefs.get<String>(PrefsConstants.BASE_URL).orEmpty() }
        DisposableEffect(catalogViewModel) {
            onDispose {
                catalogViewModel.saveGridScroll(
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                )
            }
        }
        MobileCatalogScreen(
            state = state,
            onOpenTitle = { movieId ->
                findNavController().navigateSafely(
                    HomeFragmentDirections.actionNavHomeToNavDetails(movieId)
                )
            },
            onSelectSort = catalogViewModel::selectSort,
            onSelectGenre = catalogViewModel::selectGenre,
            onLoadMore = catalogViewModel::loadMore,
            onRetry = catalogViewModel::retry,
            imageBaseUrl = baseUrl,
            gridState = gridState,
            columns = PosterGrid.spansFor(
                PosterGrid.windowWidthDp(
                    requireContext(),
                    horizontalPaddingDp = (MobileDimens.ScreenPadding.value * 2).toInt(),
                )
            ),
            // Заголовок раздела уже есть в переключателе над каталогом.
            showTitle = false,
        )
    }

    /** Каталог показывается вместо ленты только в выбранном разделе и вне поиска. */
    private fun updateCatalogVisibility() {
        binding.catalogHost.isVisible =
            isSearchComplete() && viewModel.contentType.value != HomeContentType.ALL
    }

    private fun setupComposeHome() {
        if (!isComposeHomeEnabled()) return
        binding.composeHome.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeHome.setContent {
            MobileTheme(darkTheme = true) {
                val homeState by mobileHomeViewModel.uiState.collectAsState()
                MobileHomeScreen(
                    state = homeState,
                    onPlay = ::playMovie,
                    onOpenDetails = { movieId ->
                        findNavController().navigateSafely(
                            HomeFragmentDirections.actionNavHomeToNavDetails(movieId)
                        )
                    },
                    onRetry = mobileHomeViewModel::retry,
                )
            }
        }
    }

    /**
     * Запуск тайтла прямо с главной: плеер сам решает, что играть дальше (позиция, серия),
     * поэтому отсюда передаётся только карточка тайтла. Без загруженного `Movie` плеер
     * открывать нечего — тогда честно уходим в детали.
     */
    private fun playMovie(movieId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val movie = runCatching { moviesInteractor.getMovie(movieId).first() }
                .getOrNull()
            val data = (movie as? DataResult.Success)?.result
            if (data == null) {
                findNavController().navigateSafely(
                    HomeFragmentDirections.actionNavHomeToNavDetails(movieId)
                )
                return@launch
            }
            findNavController().navigateSafely(
                HomeFragmentDirections.actionNavHomeToNavPlayerView(null, data)
            )
        }
    }

    private fun updateHomeContentTypeVisibility() {
        // Переключатель Все/Фильмы/Сериалы — постоянный режим главной, а не часть витрины.
        val showContentType = isSearchComplete() && !extendSearch
        binding.homeContentTypeGroup.isVisible = showContentType
        // Во время поиска режим сохраняется: раздел не должен теряться из-за ввода в строку.
        if (!showContentType &&
            extendSearch &&
            binding.homeContentTypeGroup.checkedButtonId != R.id.btnHomeContentAll
        ) {
            syncingContentTypeSelection = true
            binding.homeContentTypeGroup.check(R.id.btnHomeContentAll)
            syncingContentTypeSelection = false
            viewModel.setContentType(HomeContentType.ALL)
        }
    }

    private fun isHomeHighlightsEnabled(): Boolean =
        prefs.get<Boolean>(PrefsConstants.PREF_KEY_HOME_HIGHLIGHTS_ENABLED) ?: true

    private fun shouldShowHighlights(): Boolean =
        isHomeHighlightsEnabled() &&
                isSearchComplete() &&
                (highlightsAdapter?.hasHighlights() == true)

    private fun hasAnyVisibleContent(): Boolean =
        (itemsAdapter?.itemCount ?: 0) > 0 || shouldShowHighlights()

 /** Resets search state and UI when the user collapses the search view. */
    override fun collapseSearch() {
        viewModel.loadMovies(resetAll = true)
        emptySearch = true
        extendSearch = false
        extendSearchResult = null
        searchType = AppConstants.SearchType.TITLE
        requireActivity().hideKeyboard()
        updateMenuVisibility()
        updateHighlightsVisibility()
        updateFilterChips()
    }

/** Builds and handles menu items for the home screen. */
    private fun initMenu() {
        binding.toolbar.inflateMenu(R.menu.home_menu)

        // Обновление видимости пунктов меню
        fun updateMenuVisibility() {
            val menu = binding.toolbar.menu
            menu.findItem(R.id.action_search).isVisible = hasQuery
            menu.findItem(R.id.action_search_settings).isVisible = hasQuery && !emptySearch
            menu.findItem(R.id.action_order_settings).isVisible = hasQuery
            }

        val menu = binding.toolbar.menu
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = setupSearchView(
            menuItem = requireNotNull(searchMenuItem),
            onQueryChange = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadMovies(query.orEmpty(), onQueryChangeSubmit)
                onQueryChangeSubmit = true
                updateHighlightsVisibility()
            },
            onMenuCollapse = {
                viewModel.loadMovies(resetAll = true)
                emptySearch = true
                requireActivity().hideKeyboard()
                updateMenuVisibility()
                updateHighlightsVisibility()
                // Переключатель Все/Фильмы/Сериалы прятался на время поиска и должен вернуться.
                updateFilterChips()
            },
            onSubmitAvailable = true,
            onQuerySubmit = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadMovies(query.orEmpty())
                updateHighlightsVisibility()
            }
        )
        getIntentParams()

        // Устанавливаем visibility при инициализации
        updateMenuVisibility()

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> true
                R.id.action_order_settings -> {
                    showCustomOrderDialog()
                    true
                }

                R.id.action_search_settings -> {
                    showCustomSearchDialog()
                    true
                }

                // Настройки — не root-вкладка, а действие в тулбаре (аудит v2, §3).
                R.id.action_settings -> {
                    findNavController().navigateSafely(
                        HomeFragmentDirections.actionNavHomeToNavPrefs()
                    )
                    true
                }

                R.id.action_site_search -> {
                    val query = searchView?.query?.toString().orEmpty()
                    findNavController().navigateSafely(
                        // Пустая строка, а не null: null означает «экран открыт как вкладка».
                        HomeFragmentDirections.actionNavHomeToNavSiteSearch()
                            .setQuery(query)
                    )
                    true
                }

                // «Открыть ссылку» и «Импорт ссылки» — один диалог с двумя действиями.
                R.id.menu_action_from_path -> {
                    showAddByLinkDialog()
                    true
                }

                R.id.menu_action_import_link -> {
                    showAddByLinkDialog()
                    true
                }

                R.id.menu_action_update_list -> {
                    viewModel.loadMovies()
                    true
                }

                else -> false
            }
        }
    }

    private fun showCustomOrderDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_order_settings),
            layout = R.layout.d_custom_order,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.setOrder(currentOrder, likesPriority)
                updateFilterChips()
            },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
                    fun updatePriorityText(isChecked: Boolean) {
                        swPriority.text = getString(
                            if (isChecked) R.string.likes_priority else R.string.rating_priority
                        )
                    }
                    fun updatePriorityVisibility() {
                        swPriority.isVisible = currentOrder != AppConstants.Order.SMART
                    }
                    rbLastTime.isVisible = false
                    swPriority.isChecked = likesPriority
                    updatePriorityText(likesPriority)
                    swPriority.setOnCheckedChangeListener { _, isChecked ->
                        likesPriority = isChecked
                        updatePriorityText(likesPriority)
                    }
                    val radioBtn = listOf(
                        rbSmart to AppConstants.Order.SMART,
                        rbNone to AppConstants.Order.NONE,
                        rbTitle to AppConstants.Order.TITLE,
                        rbRatings to AppConstants.Order.RATINGS,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC,
                    )
                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.SMART -> rbSmart.isChecked = true
                            AppConstants.Order.TITLE -> rbTitle.isChecked = true
                            AppConstants.Order.RATINGS -> rbRatings.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            else -> rbSmart.isChecked = true
                        }
                    } ?: kotlin.run {
                        rbSmart.isChecked = true
                        currentOrder = AppConstants.Order.SMART
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                currentOrder = orderString
                                updatePriorityVisibility()
                            }
                        }
                    }
                    updatePriorityVisibility()
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
                viewModel.setSearchType(
                    type = searchType,
                    addTypes = searchAddTypes
                )
                updateFilterChips()
            },
            initView = {
                with(DCustomSearchBinding.bind(this)) {
                    checkSearchAddTypes()
                    setAddSearchType()
                    checkSearchType()
                    setSearchBtnsChangeListeners()
                }
            }
        )
    }

    private fun DCustomSearchBinding.checkSearchAddTypes() {
        chbCinemas.isChecked = false
        chbSerials.isChecked = false
        searchAddTypes.forEach {
            when (it) {
                AppConstants.SearchType.CINEMA -> chbCinemas.isChecked = true
                AppConstants.SearchType.SERIAL -> chbSerials.isChecked = true
                else -> {}
            }
        }
    }

    private fun DCustomSearchBinding.setAddSearchType() {
        getAddChBoxTypes().forEach { (chBox, type) ->
            chBox.setOnCheckedChangeListener { _, isChecked ->
                updateSearchAddType(isChecked, type)
            }
        }
    }

    private fun DCustomSearchBinding.getAddChBoxTypes() = listOf(
        chbCinemas to AppConstants.SearchType.CINEMA,
        chbSerials to AppConstants.SearchType.SERIAL,
    )

    private fun DCustomSearchBinding.updateSearchAddType(check: Boolean, type: String) {
        getAddChBoxTypes().map { it.second }.forEach { chbType ->
            if (chbType == type) {
                searchAddTypes = if (check) {
                    searchAddTypes.apply {
                        add(type)
                        distinct()
                    }
                } else {
                    searchAddTypes.apply {
                        remove(type)
                        distinct()
                    }
                }
            }
        }
    }

    private fun DCustomSearchBinding.setSearchBtnsChangeListeners() {
        listOf(
            rbTitle to AppConstants.SearchType.TITLE,
            rbDirectors to AppConstants.SearchType.DIRECTORS,
            rbActors to AppConstants.SearchType.ACTORS,
            rbGenres to AppConstants.SearchType.GENRES,
        ).forEach { (rb, orderString) ->
            rb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    searchType = orderString
                }
            }
        }
    }

    private fun DCustomSearchBinding.checkSearchType() {
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
    }

    /**
     * Единый диалог «Добавить по ссылке» (MENU-01, аудит v2 §5.1).
     *
     * Один UI на два действия: `Открыть` отдаёт ссылку в плеер, `Сохранить в каталог` —
     * в `UpdateService`. Семантика разная, поэтому действия разделены, а не слиты.
     * Ссылка проверяется до любого действия: только http(s), только домены сайта,
     * только путь карточки `/films/<id>` или `/serials/<id>`; внутренние адреса запрещены.
     */
    private fun showAddByLinkDialog() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        var urlInput: TextInputEditText? = null
        lateinit var dialog: MaterialDialog

        fun currentLink(): SiteLink? =
            (SiteLinkParser.parse(urlInput?.text?.toString().orEmpty(), baseUrl)
                    as? SiteLinkResult.Valid)?.link

        fun renderPreview(preview: TextView) {
            val input = urlInput ?: return
            when (val parsed = SiteLinkParser.parse(input.text?.toString().orEmpty(), baseUrl)) {
                is SiteLinkResult.Valid -> {
                    preview.setText(
                        getString(
                            if (parsed.link.kind == MovieType.SERIAL) {
                                R.string.add_by_link_preview_serial
                            } else {
                                R.string.add_by_link_preview_film
                            },
                            parsed.link.movieId
                        )
                    )
                    preview.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorAccent))
                }

                is SiteLinkResult.Invalid -> {
                    preview.setText(
                        when (parsed.reason) {
                            SiteLinkResult.Reason.EMPTY -> R.string.add_by_link_error_empty
                            SiteLinkResult.Reason.NOT_HTTP -> R.string.add_by_link_error_scheme
                            SiteLinkResult.Reason.UNSAFE_HOST -> R.string.add_by_link_error_unsafe
                            SiteLinkResult.Reason.FOREIGN_HOST -> R.string.add_by_link_error_host
                            SiteLinkResult.Reason.UNKNOWN_PATH -> R.string.add_by_link_error_path
                        }
                    )
                    preview.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                    )
                }
            }
        }

        dialog = createCustomLayoutDialog(
            title = getString(R.string.add_by_link_title),
            layout = R.layout.d_add_by_link,
            autoDismiss = false,
            btnOkText = getString(R.string.add_by_link_import),
            btnNeutralText = getString(R.string.add_by_link_open),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                val link = currentLink()
                if (link == null) {
                    toast(getString(R.string.add_by_link_error_path))
                } else {
                    dialog.dismiss()
                    importVideoByUrl(link.url)
                }
            },
            onNeutral = {
                val link = currentLink()
                if (link == null) {
                    toast(getString(R.string.add_by_link_error_path))
                } else {
                    dialog.dismiss()
                    findNavController().navigateSafely(
                        HomeFragmentDirections.actionNavHomeToNavPlayerView(link.url, null)
                    )
                }
            },
            initView = {
                val binding = DAddByLinkBinding.bind(this)
                urlInput = binding.tiedtLink
                binding.tiedtLink.doAfterTextChanged { renderPreview(binding.tvLinkPreview) }
                renderPreview(binding.tvLinkPreview)
                binding.tiedtLink.requestFocus()
            }
        )
    }

    private var lastImportedUrl: String? = null

    private fun importVideoByUrl(url: String) {
        val movieId = extractMovieIdFromUrl(url)
        if (movieId == null) {
            toast("Неверный формат URL: $url")
            return
        }

        lastImportedUrl = url
        lastImportedMovieId = movieId // ← Сохраняем ID для последующего использования!

        requireContext().sendServiceMessage(
            Intent(requireContext().applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE_BY_URL
        ) {
            putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
        }
    }

    private fun extractMovieIdFromUrl(url: String): Long? {
        val regex = Regex("""(?:films|serials)/(\d+)""")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun onOpenFolder(data: Intent?) {
        val uri = data?.data
        val path = FilePathUtils.getPath(uri, requireContext())
        println(path)
    }

    private fun onOpenFile(data: Intent?) {
        val uri = data?.data
        val path = FilePathUtils.getPath(uri, requireContext())
        println(path)
    }

    private fun requestFile() {
        requestId = REQUEST_OPEN_FILE
        startForResult.launch(
            Intent().apply {
                action = Intent.ACTION_GET_CONTENT
                addCategory(Intent.CATEGORY_OPENABLE)
                if (SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "*/*"
            }
        )
    }
}
