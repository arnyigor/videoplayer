package com.arny.homecinema.presentation.home

import android.content.Context
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
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.di.models.MainPageContent
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlin.properties.Delegates


class HomeFragment : Fragment() {

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
        initList(binding)
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
        acsLinks.adapter = searchLinksSpinnerAdapter
        acsLinks.updateSpinnerItems(videoTypesSelectListener)
        viewResult()
    }

    private fun FHomeBinding.initList(binding: FHomeBinding) {
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
            else -> false
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