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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.databinding.FHomeBinding
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.Video
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.presentation.utils.KeyboardHelper
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.inputDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.openAppSettings
import com.arny.mobilecinema.presentation.utils.requestPermission
import com.arny.mobilecinema.presentation.utils.setDrawableRightListener
import com.arny.mobilecinema.presentation.utils.setEnterPressListener
import com.arny.mobilecinema.presentation.utils.singleChoiceDialog
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import com.arny.mobilecinema.presentation.utils.textChanges
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.toastError
import com.arny.mobilecinema.presentation.utils.unlockOrientation
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import kotlin.properties.Delegates

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
    private var videoTypesAdapter: VideoTypesAdapter? = null
    private var videosAdapter: VideosAdapter? = null
    private var emptyData by Delegates.observable(true) { _, _, empty ->
        with(binding) {
            rcVideoList.isVisible = !empty
            tvEmptyView.isVisible = empty
        }
    }
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
                        loadDB()
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
        initUI()
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
                loadDB()
            }
        )
    }

    private fun loadDB() {
        viewModel.loadDB()
    }

    @OptIn(FlowPreview::class)
    private fun initUI() {
        with(binding) {
            swiperefresh.setOnRefreshListener {
                swiperefresh.isRefreshing = false
                viewModel.restartLoading()
            }
            btnSearch.setOnClickListener {
                KeyboardHelper.hideKeyboard(requireActivity())
                searchVideo()
            }
            edtSearch.setDrawableRightListener {
                KeyboardHelper.hideKeyboard(requireActivity())
                edtSearch.setText("")
                viewModel.restartLoading()
            }
            edtSearch.setEnterPressListener {
                KeyboardHelper.hideKeyboard(requireActivity())
            }
            binding.edtSearch
                .textChanges()
                .debounce(500)
                .filter { !it.isNullOrBlank() }
                .onEach {
                    viewModel.search(it.toString(), true)
                }
                .launchIn(lifecycleScope)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
    }

    private fun initAdapters() {
        videoTypesAdapter = VideoTypesAdapter(onItemClick = { position, item ->
            val items = videoTypesAdapter?.items
            items?.forEach {
                it.selected = false
            }
            item.selected = true
            videoTypesAdapter?.notifyItemChanged(position)
            viewModel.onTypeChanged(items?.getOrNull(position))
        })
        binding.rvTypesList.adapter = videoTypesAdapter
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
                binding.edtSearch.isVisible = !loading
                binding.rvTypesList.isVisible = !loading
                binding.btnSearch.isVisible = !loading
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
            viewModel.movies.collectLatest { movies ->
                updateList(movies)
            }
        }
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.menu_action_choose_source -> {
                        viewModel.requestHosts()
                        false
                    }

                    R.id.menu_action_get_file -> {
                        request = REQUEST_OPEN_FILE
                        requestPermission(
                            resultLauncher = requestPermissionLauncher,
                            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            checkPermissionOk = ::requestFile
                        )
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
        val movie = Movie(
            uuid = UUID.randomUUID().toString(),
            title = path?.substringBeforeLast(".") ?: "",
            type = MovieType.CINEMA,
            detailUrl = path,
            video = Video(
                videoUrl = uri.toString()
            )
        )
        binding.root.findNavController()
            .navigate(HomeFragmentDirections.actionNavHomeToNavDetails(movie))
    }

    private fun onOpenFile(data: Intent?) {
        val uri = data?.data
        val path = FilePathUtils.getPath(uri, requireContext())
        val movie = Movie(
            uuid = UUID.randomUUID().toString(),
            title = path?.substringBeforeLast(".") ?: "",
            type = MovieType.CINEMA,
            detailUrl = path,
            video = Video(
                videoUrl = uri.toString()
            )
        )
        binding.root.findNavController()
            .navigate(HomeFragmentDirections.actionNavHomeToNavDetails(movie))
    }

    private fun updateList(movies: List<AnwapMovie>) {
        videosAdapter?.submitList(movies.toList())
        emptyData = movies.isEmpty()
    }

    private fun FHomeBinding.searchVideo() {
        tvEmptyView.isVisible = false
        viewModel.search(edtSearch.text.toString())
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

    private fun showSourceDialog(sources: List<String>, checkedItem: Int) {
        singleChoiceDialog(
            title = getString(R.string.home_choose_source),
            items = sources,
            selectedPosition = checkedItem,
            cancelable = true
        ) { index, dlg ->
            viewModel.selectHost(sources[index])
            emptyData = false
            videoTypesAdapter?.submitList(emptyList())
            dlg.dismiss()
        }
    }
}
