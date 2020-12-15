package com.arny.homecinema.presentation.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.setEnterPressListener
import com.arny.homecinema.presentation.utils.viewBinding
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlin.properties.Delegates

class HomeFragment : Fragment() {

    companion object {
        fun getInstance() = HomeFragment()
    }

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

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
        vm.loading.observe(this@HomeFragment, { loading ->
            pbLoading.isVisible = loading
            edtSearch.isVisible = !loading
            acsLinks.isVisible = !loading
        })
        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = false
            vm.restartLoading()
        }
        btnSearch.setOnClickListener { searchVideo() }
        edtSearch.setEnterPressListener { searchVideo() }
        searchLinksSpinnerAdapter = SearchLinksSpinnerAdapter(requireContext())
        acsLinks.adapter = searchLinksSpinnerAdapter
        acsLinks.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
        vm.result.observe(this@HomeFragment, { result ->
            when (result) {
                is DataResult.Success -> {
                    val content = result.data
                    val data = content.movies
                    val items = data?.map { VideoItem(it) }
                    fillAdapter(items)
                    emptyData = data?.isEmpty() ?: true
                    val mutableCollection = content.searchVideoLinks ?: emptyList()
                    binding.acsLinks.isVisible = mutableCollection.isNotEmpty()
                    searchLinksSpinnerAdapter?.clear()
                    searchLinksSpinnerAdapter?.addAll(mutableCollection)
                }
                is DataResult.Error -> {
                    emptyData = true
                    binding.tvEmptyView.text = result.throwable.message
                }
            }
        })
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.f_home, container, false)
    }
}