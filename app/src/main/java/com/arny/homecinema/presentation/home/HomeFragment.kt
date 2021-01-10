package com.arny.homecinema.presentation.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.utils.FilePathUtils
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.di.models.*
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import moxy.MvpAppCompatFragment
import moxy.ktx.moxyPresenter
import java.util.*
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

class HomeFragment : MvpAppCompatFragment(), HomeView, CoroutineScope {

    private companion object {
        const val REQUEST_OPEN_FILE: Int = 100
        const val REQUEST_OPEN_FOLDER: Int = 101
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()
    private val compositeJob = CompositeJob()

    @Inject
    lateinit var presenterProvider: Provider<HomePresenter>

    private val presenter by moxyPresenter { presenterProvider.get() }

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

    private var videoTypesAdapter: VideoTypesAdapter? = null

    private lateinit var groupAdapter: GroupAdapter<GroupieViewHolder>
    private var emptyData by Delegates.observable(true) { _, _, empty ->
        with(binding) {
            rcVideoList.isVisible = !empty
            tvEmptyView.isVisible = empty
        }
    }

    override fun showMainContent(result: DataResult<MainPageContent>) {
        when (result) {
            is DataResult.Success -> {
                updateList(result.data)
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
                val (sources, current) = hostsResult.data
                showAlertDialog(sources, current)
            }
            is DataResult.Error -> {
            }
        }
    }

    @FlowPreview
    private fun initBinding(binding: FHomeBinding) = with(binding) {
        initList()
        requireActivity().title = getString(R.string.app_name)
        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = false
            presenter.restartLoading()
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
            SimpleAbstractAdapter.OnViewHolderListener<VideoSearchLink> {
            override fun onItemClick(position: Int, item: VideoSearchLink) {
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
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = videoTypesAdapter
        }
    }

    private fun FHomeBinding.initList() {
        groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.setOnItemClickListener { item, _ ->
            val video = (item as VideoItem).movie
            binding.root.findNavController()
                .navigate(HomeFragmentDirections.actionHomeFragmentToDetailsFragment(video))
        }
        rcVideoList.also {
            it.adapter = groupAdapter
            it.layoutManager = LinearLayoutManager(requireContext())
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

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.app_name)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_action_choose_source -> {
                emptyData = false
                presenter.requestHosts()
                true
            }
            R.id.menu_action_settings -> {
                binding.root.findNavController()
                    .navigate(HomeFragmentDirections.actionHomeFragmentToSettingsFragment())
                true
            }
            R.id.menu_action_history -> {
                binding.root.findNavController()
                    .navigate(HomeFragmentDirections.actionHomeFragmentToHistoryFragment())
                true
            }
            R.id.menu_action_get_file -> {
                requestFile()
                true
            }
            else -> false
        }
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.f_home, container, false)
    }

    private fun showAlertDialog(sources: Array<String>, checkedItem: Int) {
        var alert: AlertDialog? = null
        val alertDialog: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        alertDialog.setTitle(getString(R.string.home_choose_source))
        alertDialog.setSingleChoiceItems(sources, checkedItem) { _, which ->
            presenter.selectHost(sources[which])
            videoTypesAdapter?.clear()
            alert?.dismiss()
        }
        alert = alertDialog.create()
        alert.setCanceledOnTouchOutside(true)
        alert.setCancelable(true)
        alert.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeJob.cancel()
    }
}
