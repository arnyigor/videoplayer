package com.arny.videoplayer.presentation.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.arny.videoplayer.R
import com.arny.videoplayer.databinding.FHomeBinding
import com.arny.videoplayer.presentation.utils.viewBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class HomeFragment : Fragment() {

    companion object {
        fun getInstance() = HomeFragment()
    }

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { FHomeBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: FHomeBinding) = with(binding) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.loading.observe(this, { loading ->
            binding.pbLoading.isVisible = loading
        })
        vm.text.observe(this, {
            binding.tvInfo.text = it
        })
        binding.tvInfo.setOnClickListener {
            vm.restartLoading()
        }
    }

}