package com.arny.videoplayer.presentation.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.videoplayer.R
import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.databinding.FHomeBinding
import com.arny.videoplayer.presentation.utils.viewBinding
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
        groupAdapter = GroupAdapter<GroupieViewHolder>()
        rcVideoList.also {
            it.adapter = groupAdapter
            it.layoutManager = LinearLayoutManager(requireContext())
        }
        vm.loading.observe(this@HomeFragment, { loading ->
            binding.pbLoading.isVisible = loading
        })
        vm.result.observe(this@HomeFragment, { result ->
            when (result) {
                is DataResult.Success -> {
                    groupAdapter.addAll(result.data)
                }
                is DataResult.Error -> {
                    Toast.makeText(
                        requireContext(),
                        result.throwable.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = false
            vm.restartLoading()
        }
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