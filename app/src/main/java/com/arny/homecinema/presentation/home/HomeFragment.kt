package com.arny.homecinema.presentation.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.utils.FilePathUtils
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.MovieType
import com.arny.homecinema.di.models.Video
import com.arny.homecinema.presentation.CONSTS.REQUESTS.REQUEST_OPEN_FILE
import com.arny.homecinema.presentation.CONSTS.REQUESTS.REQUEST_OPEN_FOLDER
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.tbruyelle.rxpermissions2.RxPermissions
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

class HomeFragment : Fragment() {

    private lateinit var rxPermissions: RxPermissions

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

    private var videoTypesSelectListener: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                vm.onSearchChanged(searchLinksSpinnerAdapter?.items?.getOrNull(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

    private var searchLinksSpinnerAdapter: SearchLinksSpinnerAdapter? = null

    private lateinit var groupAdapter: GroupAdapter<GroupieViewHolder>
    private var emptyData by Delegates.observable(true) { _, _, empty ->
        with(binding) {
            rcVideoList.isVisible = !empty
            tvEmptyView.isVisible = empty
        }
    }

    private fun initBinding(binding: FHomeBinding) = with(binding) {
        initList()
        requireActivity().title = getString(R.string.app_name)
        vm.loading.observe(viewLifecycleOwner, { loading ->
            pbLoading.isVisible = loading
            edtSearch.isVisible = !loading
            acsLinks.isVisible = !loading
        })
        vm.hostsData.observe(viewLifecycleOwner, { hostsResult ->
            when (hostsResult) {
                is DataResult.Success -> {
                    val (sources, current) = hostsResult.data
                    showAlertDialog(sources, current)
                }
                is DataResult.Error -> {
                }
            }
        })
        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = false
            vm.restartLoading()
        }
        btnSearch.setOnClickListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            searchVideo()
        }
        edtSearch.setDrawableRightListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            edtSearch.setText("")
            vm.restartLoading()
        }
        edtSearch.setEnterPressListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            searchVideo()
        }
        edtSearch.doAfterTextChanged {
            if (edtSearch.isFocused) {
                vm.searchCached(it.toString())
            }
        }
        searchLinksSpinnerAdapter = SearchLinksSpinnerAdapter(requireContext())
        acsLinks.setSelection(0, false)
        acsLinks.adapter = searchLinksSpinnerAdapter
        acsLinks.updateSpinnerItems(videoTypesSelectListener)
        viewResult()
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

    private fun viewResult() {
        vm.result.observe(viewLifecycleOwner, { result ->
            when (result) {
                is DataResult.Success -> {
                    updateList(result.data)
                }
                is DataResult.Error -> {
                    emptyData = true
                    binding.tvEmptyView.text = result.throwable.message
                }
            }
        })
    }

    private fun updateList(pageContent: MainPageContent) {
        val data = pageContent.movies
        val items = data?.map { VideoItem(it) }
        fillAdapter(items)
        emptyData = data?.isEmpty() ?: true
        val mutableCollection = pageContent.searchVideoLinks ?: emptyList()
        if (mutableCollection.isNotEmpty()) {
            binding.acsLinks.updateSpinnerItems(videoTypesSelectListener) {
                searchLinksSpinnerAdapter?.clear()
                searchLinksSpinnerAdapter?.addAll(mutableCollection)
            }
        }
    }

    private fun fillAdapter(items: List<VideoItem>?) {
        groupAdapter.clear()
        items?.let { groupAdapter.addAll(it) }
    }

    private fun FHomeBinding.searchVideo() {
        tvEmptyView.isVisible = false
        vm.search(edtSearch.text.toString())
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
        rxPermissions = RxPermissions(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_action_choose_source -> {
                emptyData = false
                vm.requestHosts()
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
            R.id.menu_action_get_folder -> {
                requestFolder()
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

    private fun requestFolder() {
        launchIntent(REQUEST_OPEN_FOLDER) {
            action = Intent.ACTION_GET_CONTENT
            type = "file/*"
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
            vm.selectHost(sources[which])
            alert?.dismiss()
        }
        alert = alertDialog.create()
        alert.setCanceledOnTouchOutside(true)
        alert.setCancelable(true)
        alert.show()
    }
}