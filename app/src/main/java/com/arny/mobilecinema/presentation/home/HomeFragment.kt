package com.arny.mobilecinema.presentation.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.utils.FilePathUtils
import com.arny.mobilecinema.databinding.FHomeBinding
import com.arny.mobilecinema.di.models.*
import com.arny.mobilecinema.presentation.utils.*
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

class HomeFragment : Fragment(), HomeView, CoroutineScope {
    private companion object {
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: HomeViewModel by viewModels { vmFactory }
    private lateinit var binding: FHomeBinding
    private var videoTypesAdapter: VideoTypesAdapter? = null
    private var emptyData by Delegates.observable(true) { _, _, empty ->
        with(binding) {
            rcVideoList.isVisible = !empty
            tvEmptyView.isVisible = empty
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        with(binding) {
            initList()
            requireActivity().title = getString(R.string.app_name)
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
                presenter.restartLoading()
            }
            edtSearch.setEnterPressListener {
                KeyboardHelper.hideKeyboard(requireActivity())
            }
            compositeJob.add(
                CoroutineScope(coroutineContext).launch {
                    edtSearch.getQueryTextChangeStateFlow()
                        .debounce(500)
                        .filter { it.isNotBlank() }
                        .collect {
                            presenter.search(it, true)
                        }
                }
            )
            videoTypesAdapter = VideoTypesAdapter()
            videoTypesAdapter?.setViewHolderListener(object :
                SimpleAbstractAdapter.OnViewHolderListener<VideoMenuLink> {
                override fun onItemClick(position: Int, item: VideoMenuLink) {
                    val items = videoTypesAdapter?.getItems()
                    items?.forEach {
                        it.selected = false
                    }
                    val searchLink = items?.getOrNull(position)
                    searchLink?.selected = true
                    videoTypesAdapter?.notifyDataSetChanged()
                    presenter.onTypeChanged(searchLink)
                }
            })
            with(rvTypesList) {
                layoutManager = LinearLayoutManager(
                    requireContext(),
                    androidx.recyclerview.widget.RecyclerView.HORIZONTAL,
                    false
                )
                adapter = videoTypesAdapter
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_action_choose_source -> {
                presenter.requestHosts()
                true
            }
            R.id.menu_action_get_file -> {
                requestFile()
                true
            }
            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_OPEN_FILE -> {
                    val uri = data?.data
                    val path = FilePathUtils.getPath(uri, requireContext())
                    val movie = Movie(
                        uuid = UUID.randomUUID().toString(),
                        title = path?.substringBeforeLast(".") ?: "",
                        type = MovieType.CINEMA_LOCAL,
                        detailUrl = path,
                        video = Video(
                            videoUrl = uri.toString()
                        )
                    )
                    binding.root.findNavController()
                        .navigate(HomeFragmentDirections.actionHomeFragmentToDetailsFragment(movie))
                }
                REQUEST_OPEN_FOLDER -> {
                    val dataString = data?.dataString
                    val uri = data?.data
                    val path = FilePathUtils.getPath(uri, requireContext())
                    val movie = Movie(
                        uuid = UUID.randomUUID().toString(),
                        title = path?.substringBeforeLast(".") ?: "",
                        type = MovieType.SERIAL_LOCAL,
                        detailUrl = path,
                        video = Video(
                            videoUrl = uri.toString()
                        )
                    )
                    binding.root.findNavController()
                        .navigate(HomeFragmentDirections.actionHomeFragmentToDetailsFragment(movie))
                }
            }
        }
    }

    private fun FHomeBinding.initList() {
        groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.setOnItemClickListener { item, _ ->
            val video = (item as VideoItem).movie
            binding.root.findNavController()
                .navigate(HomeFragmentDirections.actionHomeFragmentToDetailsFragment(video))
        }
        rcVideoList.apply {
            adapter = groupAdapter
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    override fun showMainContent(result: DataResult<MainPageContent>) {
        when (result) {
            is DataResult.Success -> {
                updateList(result.result)
            }

            is DataResult.Error -> {
                emptyData = true
                binding.tvEmptyView.text = result.throwable.message
            }
        }
    }

    override fun showMainContentError(error: DataResult<MainPageContent>) {
        emptyData = true
        if (error is DataResult.Error) {
            binding.tvEmptyView.text = error.throwable.message
        }
    }

    override fun showLoading(show: Boolean) = with(binding) {
        pbLoading.isVisible = show
        edtSearch.isVisible = !show
        rvTypesList.isVisible = !show
        btnSearch.isVisible = !show
    }

    override fun chooseHost(hostsResult: DataResult<Pair<Array<String>, Int>>) {
        when (hostsResult) {
            is DataResult.Success -> {
                val (sources, current) = hostsResult.result
                showAlertDialog(sources, current)
            }

            is DataResult.Error -> {
            }
        }
    }

    private fun updateList(pageContent: MainPageContent) = with(binding) {
        val data = pageContent.movies
        fillAdapter(data?.map { VideoItem(it) })
        emptyData = data.isNullOrEmpty()
        val searchVideoLinks = pageContent.searchVideoLinks ?: emptyList()
        videoTypesAdapter?.addAll(searchVideoLinks)
    }

    private fun fillAdapter(items: List<VideoItem>?) {
        groupAdapter.clear()
        items?.let { groupAdapter.addAll(it) }
    }

    private fun FHomeBinding.searchVideo() {
        tvEmptyView.isVisible = false
        presenter.search(edtSearch.text.toString())
    }

    private fun requestFile() {
        launchIntent(REQUEST_OPEN_FILE) {
            action = Intent.ACTION_GET_CONTENT
            addCategory(Intent.CATEGORY_OPENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "*/*"
        }
    }

    private fun showAlertDialog(sources: Array<String>, checkedItem: Int) {
        var alert: AlertDialog? = null
        val alertDialog: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialog.setTitle(getString(R.string.home_choose_source))
        alertDialog.setSingleChoiceItems(sources, checkedItem) { _, which ->
            presenter.selectHost(sources[which])
            emptyData = false
            videoTypesAdapter?.clear()
            alert?.dismiss()
        }
        alert = alertDialog.create()
        alert.setCanceledOnTouchOutside(true)
        alert.setCancelable(true)
        alert.show()
    }
}
