package com.arny.mobilecinema.presentation.home

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
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
import com.arny.mobilecinema.presentation.utils.*
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class HomeFragment : Fragment(), OnSearchListener {
    private companion object {
        const val REQUEST_LOAD: Int = 99
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: HomeViewModel by viewModels { vmFactory }
    private lateinit var binding: FHomeBinding
    private var request: Int = -1
    private var currentOrder: String = ""
    private var currentSearch: String = ""
    private var emptySearch = true
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
                when (request) {
                    REQUEST_OPEN_FILE -> {
                        requestFile()
                    }

                    REQUEST_LOAD -> {
                        downloadData()
                    }
                }
            }
        }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.loadMovies()
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
        requestPermission()
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

    private fun downloadData() {
        when (getConnectionType(requireContext())) {
            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }
            else -> {
                viewModel.downloadData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
        registerReceiver(AppConstants.ACTION_UPDATE_COMPLETE, updateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
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
                alertDialog(
                    title = alert.title.toString(requireContext()).orEmpty(),
                    content = alert.content?.toString(requireContext()),
                    btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                    btnCancelText = alert.btnCancel?.toString(requireContext()),
                    cancelable = alert.cancelable,
                    icon = alert.icon?.let { requireContext().getImg(it) },
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
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
                setupSearchView(
                    menuItem = menu.findItem(R.id.action_search),
                    onQueryChange = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadMovies(query.orEmpty())
                    },
                    onMenuCollapse = {
                        requireActivity().hideKeyboard()
                        viewModel.loadMovies()
                        emptySearch = true
                    },
                    onSubmitAvailable = true,
                    onQuerySubmit = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadMovies(query.orEmpty())
                    }
                )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_search -> false
                    R.id.action_order_settings -> {
                        showCustomOrderDialog()
                        false
                    }
                    R.id.action_search_settings -> {
                        showCustomSearchDialog()
                        false
                    }
                    R.id.menu_action_from_path -> {
                        openPath()
                        false
                    }
                    else -> true
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showCustomOrderDialog() {
        val order = StringBuilder()
        createCustomLayoutDialog(
            title = getString(R.string.search_order_settings),
            layout = R.layout.d_custom_order,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                currentOrder = order.toString()
                viewModel.setOrder(currentOrder)
            },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbNone to "",
                        rbUpdatedDesc to AppConstants.Order.UPDATED_DESC,
                        rbUpdatedAsc to AppConstants.Order.UPDATED_ASC,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC,
                        rbImdbDesc to AppConstants.Order.IMDB_DESC,
                        rbKpDesc to AppConstants.Order.KP_DESC,
                    )
                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.UPDATED_DESC -> rbUpdatedDesc.isChecked = true
                            AppConstants.Order.UPDATED_ASC -> rbUpdatedAsc.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            AppConstants.Order.IMDB_DESC -> rbImdbDesc.isChecked = true
                            AppConstants.Order.KP_DESC -> rbKpDesc.isChecked = true
                            else -> rbNone.isChecked = true
                        }
                    }?: kotlin.run {
                        rbNone.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                order.clear()
                                order.append(orderString)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun showCustomSearchDialog() {
        val search = StringBuilder()
        createCustomLayoutDialog(
            title = getString(R.string.search_settings_title),
            layout = R.layout.d_custom_search,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                currentSearch = search.toString()
            },
            initView = {
                with(DCustomSearchBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbTitle to AppConstants.Search.TITLE,
                        rbDirectors to AppConstants.Search.DIRECTORS,
                        rbActors to AppConstants.Search.ACTORS,
                        rbGenres to AppConstants.Search.GENRES,
                    )
                    currentSearch.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Search.TITLE -> rbTitle.isChecked = true
                            AppConstants.Search.DIRECTORS -> rbDirectors.isChecked = true
                            AppConstants.Search.ACTORS -> rbActors.isChecked = true
                            AppConstants.Search.GENRES -> rbGenres.isChecked = true
                            else -> rbTitle.isChecked = true
                        }
                    }?: kotlin.run {
                        rbTitle.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                search.clear()
                                search.append(orderString)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun openPath() {
        inputDialog(
            title = "Введите путь",
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
