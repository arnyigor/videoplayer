package com.arny.mobilecinema.presentation.home

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FHomeBinding
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.checkPermission
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.getImgCompat
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.inputDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.openAppSettings
import com.arny.mobilecinema.presentation.utils.registerReceiver
import com.arny.mobilecinema.presentation.utils.requestPermission
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.toastError
import com.arny.mobilecinema.presentation.utils.unlockOrientation
import com.arny.mobilecinema.presentation.utils.unregisterReceiver
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

class HomeFragment : Fragment(), OnSearchListener {
    private companion object {
        const val REQUEST_LOAD: Int = 99
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: HomeViewModel by viewModels { vmFactory }
    private lateinit var binding: FHomeBinding
    private var request: Int = -1
    private var requestedNotice: Boolean = false
    private var currentOrder: String = ""
    private var searchType: String = ""
    private var searchAddTypes: MutableList<String> = mutableListOf(
        AppConstants.SearchType.CINEMA,
        AppConstants.SearchType.SERIAL
    )
    private var emptySearch = true
    private var hasQuery = false
    private var onQueryChangeSubmit = true
    private var itemsAdapter: VideoItemsAdapter? = null
    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (request) {
                REQUEST_OPEN_FILE -> {
                    request = -1
                    onOpenFile(result.data)
                }

                REQUEST_OPEN_FOLDER -> {
                    request = -1
                    onOpenFolder(result.data)
                }
            }
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                request()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    request()
                }
            }
        }

    private fun request() {
        when (request) {
            REQUEST_OPEN_FILE -> requestFile()
            REQUEST_LOAD -> downloadData()
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)) {
                    AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                        binding.tvEmptyView.setText(R.string.update_started)
                    }

                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                        toast(getString(R.string.update_finished_success))
                        viewModel.loadMovies()
                    }

                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                        toast(getString(R.string.update_finished_error))
                        viewModel.loadMovies()
                    }
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTitle(getString(R.string.app_name))
        initMenu()
        initAdapters()
        observeData()
        observeResult()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
        registerReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        checkPermission()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
    }

    private fun checkPermission() {
        if (requestedNotice) {
            requestedNotice = false
            requestPermissions()
        }
    }

    private fun observeResult() {
        setFragmentResultListener(AppConstants.FRAGMENTS.RESULTS) { _, bundle ->
              val type = bundle.getString(AppConstants.SearchType.TYPE)
              Timber.d("AppConstants.SearchType.TYPE:$type")
        }
    }

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
            }
        }
    }

    private fun getIntentString(param: String) = arguments?.getString(param)

    private fun NotificationManagerCompat.areNotificationsFullyEnabled(): Boolean {
        if (!areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (notificationChannel in notificationChannels) {
                if (!notificationChannel.isFullyEnabled(this)) return false
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun NotificationChannel.isFullyEnabled(notificationManager: NotificationManagerCompat): Boolean {
        if (importance == NotificationManager.IMPORTANCE_NONE) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (notificationManager.getNotificationChannelGroup(group)?.isBlocked == true) return false
        }
        return true
    }

    private fun requestPermissions() {
        if (requestNotice()) {
            requestPermission()
        }
    }

    private fun requestNotice(): Boolean {
        val enabled =
            NotificationManagerCompat.from(requireContext()).areNotificationsFullyEnabled()
        if (!enabled) {
            alertDialog(
                title = getString(R.string.need_notice_permission_message),
                btnOkText = getString(android.R.string.ok),
                onConfirm = {
                    requestedNotice = true
                    openAppSettings()
                }
            )
        }
        return enabled
    }

    private fun requestPermission() {
        request = REQUEST_LOAD
        requestPermission(
            resultLauncher = requestPermissionLauncher,
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onNeverAskAgain = {
                alertDialog(
                    title = getString(R.string.need_permission_message),
                    btnOkText = getString(android.R.string.ok),
                    onConfirm = { openAppSettings() }
                )
            },
            checkPermissionOk = {
                downloadData()
            }
        )
    }

    private fun downloadData(force: Boolean = false) {
        when (getConnectionType(requireContext())) {
            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }

            else -> {
                viewModel.downloadData(force)
            }
        }
    }

    private fun initAdapters() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        itemsAdapter = VideoItemsAdapter(baseUrl) { item ->
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToNavDetails(item.dbId))
        }
        binding.rcVideoList.apply {
            adapter = itemsAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.pbLoading.isVisible = loading
            }
        }
        launchWhenCreated {
            viewModel.empty.collectLatest { empty ->
                hasQuery = !empty
                requireActivity().invalidateOptionsMenu()
                binding.tvEmptyView.isVisible = empty
            }
        }
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
            viewModel.updateText.collectLatest { updateText ->
                if (updateText != null) {
                    binding.tvEmptyView.text = updateText.toString(requireContext())
                }
            }
        }
        launchWhenCreated {
            viewModel.alert.collectLatest { alert ->
                alertDialog(
                    title = alert.title.toString(requireContext()).orEmpty(),
                    content = alert.content?.toString(requireContext()),
                    btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                    btnCancelText = alert.btnCancel?.toString(requireContext()),
                    cancelable = alert.cancelable,
                    icon = alert.icon?.let { requireContext().getImgCompat(it) },
                    onConfirm = {
                        viewModel.onConfirmAlert(alert.type)
                    },
                    onCancel = {
                        viewModel.onCancelAlert(alert.type)
                    }
                )
            }
        }
    }

    override fun isSearchComplete(): Boolean = emptySearch

    override fun collapseSearch() {
        viewModel.loadMovies()
        emptySearch = true
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_search).isVisible = hasQuery
                menu.findItem(R.id.action_search_settings).isVisible = hasQuery && !emptySearch
                menu.findItem(R.id.action_order_settings).isVisible = hasQuery
                menu.findItem(R.id.action_extended_search_settings).isVisible = false // TODO Сделать открытым
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                searchView = setupSearchView(
                    menuItem = requireNotNull(searchMenuItem),
                    onQueryChange = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadMovies(query.orEmpty(), onQueryChangeSubmit)
                        onQueryChangeSubmit = true
                    },
                    onMenuCollapse = {
                        viewModel.loadMovies()
                        emptySearch = true
                        requireActivity().hideKeyboard()
                        requireActivity().invalidateOptionsMenu()
                    },
                    onSubmitAvailable = true,
                    onQuerySubmit = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadMovies(query.orEmpty())
                    }
                )
                getIntentParams()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
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
                        findNavController().navigate(
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

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showVideoUpdateDialog() {
        alertDialog(
            title = getString(R.string.check_new_video_data),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                Timber.d("showVideoUpdateDialog")
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
                viewModel.setOrder(currentOrder)
            },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
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
                findNavController().navigate(
                    HomeFragmentDirections.actionNavHomeToNavPlayerView(result, null)
                )
            }
        )
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
        request = REQUEST_OPEN_FILE
        startForResult.launch(
            Intent().apply {
                action = Intent.ACTION_GET_CONTENT
                addCategory(Intent.CATEGORY_OPENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "*/*"
            }
        )
    }
}
