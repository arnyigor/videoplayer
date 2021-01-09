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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.utils.FilePathUtils
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.di.models.*
import com.arny.homecinema.presentation.CONSTS.REQUESTS.REQUEST_OPEN_FILE
import com.arny.homecinema.presentation.CONSTS.REQUESTS.REQUEST_OPEN_FOLDER
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

class HomeFragment : Fragment() {

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

    private var videoTypesAdapter: VideoTypesAdapter? = null

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
            rvTypesList.isVisible = !loading
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
                vm.onSearchChanged(searchLink)
            }
        })
        with(rvTypesList) {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = videoTypesAdapter
        }
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

    private fun updateList(pageContent: MainPageContent) = with(binding) {
        val data = pageContent.movies
        fillAdapter(data?.map { VideoItem(it) })
        emptyData = data.isNullOrEmpty()
        pageContent.searchVideoLinks?.let {
            videoTypesAdapter?.clear()
            videoTypesAdapter?.addAll(it)
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
            vm.selectHost(sources[which])
            alert?.dismiss()
        }
        alert = alertDialog.create()
        alert.setCanceledOnTouchOutside(true)
        alert.setCancelable(true)
        alert.show()
    }
}