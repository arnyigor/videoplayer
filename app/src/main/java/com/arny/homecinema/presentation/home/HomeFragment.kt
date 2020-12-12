package com.arny.homecinema.presentation.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.databinding.FHomeBinding
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.setDrawableRightListener
import com.arny.homecinema.presentation.utils.setEnterPressListener
import com.arny.homecinema.presentation.utils.viewBinding
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class HomeFragment : Fragment() {

    companion object {
        fun getInstance() = HomeFragment()
    }

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

    private lateinit var groupAdapter: GroupAdapter<GroupieViewHolder>

    private fun initBinding(binding: FHomeBinding) = with(binding) {
        initList(binding)
        vm.loading.observe(this@HomeFragment, { loading ->
            pbLoading.isVisible = loading
            edtSearch.isVisible = !loading
        })
        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = false
            vm.restartLoading()
        }
        edtSearch.setDrawableRightListener { searchVideo() }
        edtSearch.setEnterPressListener { searchVideo() }
        viewResult()
    }

    private fun FHomeBinding.initList(binding: FHomeBinding) {
        groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.setOnItemClickListener { item, _ ->
            val video = (item as VideoItem).video
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
                    val data = content.videos
                    val items = data?.map { VideoItem(it) }
                    groupAdapter.clear()
                    items?.let { groupAdapter.addAll(it) }
                    val emptyData = data?.isEmpty() ?: true
                    binding.rcVideoList.isVisible = !emptyData
                    binding.tvEmptyView.isVisible = emptyData
                    val searchVideoLinks = content.searchVideoLinks ?: emptyList()
                    for (search in searchVideoLinks) {
                        Log.d(HomeFragment::class.java.simpleName, "search: $search");
                    }
                }
                is DataResult.Error -> {
                    val throwable = result.throwable
                    Toast.makeText(
                        requireContext(),
                        throwable.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    throwable.printStackTrace()
                }
            }
        })
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