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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FHomeBinding
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.extendedsearch.ExtendSearchResult
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.uimodels.AlertType
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
import com.google.android.material.chip.Chip
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

/**
 * HomeFragment handles the primary UI for browsing videos.
 * It coordinates with [HomeViewModel] to fetch and display data,
 * manages search state, user permissions, and interactions with other app components.
 */
class HomeFragment : Fragment(), OnSearchListener {
    /** Companion object holding request codes for activity results. */
    private companion object {
        const val REQUEST_LOAD: Int = 99
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    @Inject
    lateinit var prefs: Prefs

    /** Factory for creating the associated ViewModel via assisted injection. */
    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): HomeViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: HomeViewModel by viewModelFactory { viewModelFactory.create() }
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

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

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
        menu.findItem(R.id.action_extended_search_settings).isVisible = true
    }

    /** Sets up button listeners. */
    private fun initListeners() {
        binding.btnUpdateList.setOnClickListener {
            viewModel.loadMovies()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        checkPermission()
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
                    toast(getString(R.string.update_finished_success))

                    // 3️⃣ Получаем id и логируем, если он отсутствует
                    val movieId = intent.getLongExtra("EXTRA_MOVIE_ID", -1L)
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

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    toast(getString(R.string.update_finished_error))
                    Timber.e("Update finished with error.")
                    viewModel.loadMovies()
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
            updateFilterChips()
        }
    }

    private fun getIntentString(param: String) = arguments?.getString(param)

    /** Orchestrates permission requests for notifications and storage. */
    private fun requestPermissions() {
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
        itemsAdapter = VideoItemsAdapter(baseUrl) { item ->
            val action = HomeFragmentDirections.actionNavHomeToNavDetails(item.dbId)
            findNavController().navigateSafely(action)
        }

        // Улучшенный LoadStateListener — loading/empty/error/retry
        itemsAdapter?.addLoadStateListener { loadState ->
            val refresh = loadState.refresh
            when {
                refresh is LoadState.Loading -> {
                    binding.pbLoading.isVisible = true
                    binding.llEmptyState.isVisible = false
                    binding.errorView.isVisible = false
                }
                refresh is LoadState.Error -> {
                    binding.pbLoading.isVisible = false
                    binding.llEmptyState.isVisible = false
                    binding.errorView.isVisible = true
                    val errorMsg = refresh.error.localizedMessage
                        ?: getString(R.string.error_loading_data)
                    binding.tvErrorMessage.text = errorMsg
                }
                refresh is LoadState.NotLoading -> {
                    binding.pbLoading.isVisible = false
                    binding.errorView.isVisible = false
                    val isEmpty = (itemsAdapter?.itemCount ?: 0) < 1
                    binding.llEmptyState.isVisible = isEmpty
                    binding.btnUpdateList.isVisible = isEmpty
                    binding.tvEmptyView.isVisible = isEmpty
                    hasQuery = !isEmpty
                    updateMenuVisibility()
                }
            }
        }

        binding.rcVideoList.apply {
            adapter = itemsAdapter
            setHasFixedSize(true)
        }

        // Кнопка Retry
        binding.btnRetry.setOnClickListener {
            itemsAdapter?.retry()
        }
    }

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
        updateFilterChips()
    }

    override fun isSearchComplete(): Boolean = emptySearch && !extendSearch

 /** Resets search state and UI when the user collapses the search view. */
    override fun collapseSearch() {
        viewModel.loadMovies(resetAll = true)
        emptySearch = true
        extendSearch = false
        extendSearchResult = null
        searchType = AppConstants.SearchType.TITLE
        requireActivity().hideKeyboard()
        updateMenuVisibility()
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
            menu.findItem(R.id.action_extended_search_settings).isVisible = true
        }

        val menu = binding.toolbar.menu
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = setupSearchView(
            menuItem = requireNotNull(searchMenuItem),
            onQueryChange = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadMovies(query.orEmpty(), onQueryChangeSubmit)
                onQueryChangeSubmit = true
            },
            onMenuCollapse = {
                viewModel.loadMovies(resetAll = true)
                emptySearch = true
                requireActivity().hideKeyboard()
                updateMenuVisibility()
            },
            onSubmitAvailable = true,
            onQuerySubmit = { query ->
                emptySearch = query?.isBlank() == true
                viewModel.loadMovies(query.orEmpty())
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

                R.id.action_extended_search_settings -> {
                    findNavController().navigateSafely(
                        HomeFragmentDirections.actionNavHomeToNavExtendedSearch()
                    )
                    true
                }

                R.id.menu_action_check_update -> {
                    showVideoUpdateDialog()
                    true
                }

                R.id.menu_action_from_path -> {
                    openPath()
                    true
                }

                R.id.menu_action_import_link -> {
                    showImportLinkDialog()
                    true
                }

                R.id.menu_action_update_list -> {
                    viewModel.loadMovies()
                    true
                }

                R.id.menu_action_update_download_new -> {
                    viewModel.onActionUpdateAll()
                    true
                }

                else -> false
            }
        }
    }

    private fun showVideoUpdateDialog() {
        alertDialog(
            title = getString(R.string.check_new_video_data),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                downloadData(force = true)
            }
        )
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
                    rbLastTime.isVisible = false
                    swPriority.isChecked = likesPriority
                    updatePriorityText(likesPriority)
                    swPriority.setOnCheckedChangeListener { _, isChecked ->
                        likesPriority = isChecked
                        updatePriorityText(likesPriority)
                    }
                    val radioBtn = listOf(
                        rbNone to AppConstants.Order.NONE,
                        rbTitle to AppConstants.Order.TITLE,
                        rbRatings to AppConstants.Order.RATINGS,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC,
                    )
                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.TITLE -> rbTitle.isChecked = true
                            AppConstants.Order.RATINGS -> rbRatings.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            else -> rbNone.isChecked = true
                        }
                    } ?: kotlin.run {
                        rbNone.isChecked = true
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

    private fun openPath() {
        inputDialog(
            title = getString(R.string.enter_url),
            prefill = "",
            btnOkText = getString(android.R.string.ok),
            dialogListener = { result ->
                findNavController().navigateSafely(
                    HomeFragmentDirections.actionNavHomeToNavPlayerView(result, null)
                )
            }
        )
    }

    private var lastImportedUrl: String? = null

    private fun showImportLinkDialog() {
        inputDialog(
            title = getString(R.string.import_link_dialog_title),
            prefill = "",
            hint = getString(R.string.enter_video_url_hint),
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            dialogListener = { url ->
                if (url.isNotBlank()) {
                    importVideoByUrl(url.trim())
                }
            }
        )
    }

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
        val regex = Regex("""(?:films|serials)/(\d+)$""")
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
