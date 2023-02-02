package com.arny.mobilecinema.presentation.home

import android.Manifest
import android.app.Activity
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
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.databinding.FHomeBinding
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.getImg
import com.arny.mobilecinema.presentation.utils.inputDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.openAppSettings
import com.arny.mobilecinema.presentation.utils.requestPermission
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.toastError
import com.arny.mobilecinema.presentation.utils.unlockOrientation
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class HomeFragment : Fragment() {
    private companion object {
        const val REQUEST_LOAD: Int = 99
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: HomeViewModel by viewModels { vmFactory }
    private lateinit var binding: FHomeBinding
    private var request: Int = -1
    private var videosAdapter: VideosAdapter? = null
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
        viewModel.downloadData()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
    }

    private fun initAdapters() {
        videosAdapter = VideosAdapter { item ->
//            binding.root.findNavController()
//                .navigate(HomeFragmentDirections.actionNavHomeToNavDetails(item))
        }
        binding.rcVideoList.apply {
            adapter = videosAdapter
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
                videosAdapter?.submitData(movies)
            }
        }
        launchWhenCreated {
            viewModel.toast.collectLatest { wrappedString ->
                toast(wrappedString.toString(requireContext()))
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

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(R.menu.home_menu, menu)
                setupSearchView(
                    menuItem = menu.findItem(R.id.action_search),
                    onQueryChange = { query ->
                        viewModel.search(query.orEmpty())
                    },
                    onMenuCollapse = {
                        viewModel.search("")
                    },
                    onSubmitAvailable = true,
                    onQuerySubmit = { query ->
                        viewModel.search(query.orEmpty())
                    }
                )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_search -> false
                    R.id.action_search_settings -> {
                        // TODO: Add search settings
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

    private fun openPath() {
        inputDialog(
            title = "Введите путь",
            prefill = "",
            btnOkText = getString(android.R.string.ok),
            dialogListener = { result ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavHomeToNavPlayerView(result, "Нет названия")
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
