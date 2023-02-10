package com.arny.mobilecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.presentation.home.VideoItemsAdapter
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class HistoryFragment : Fragment() {
    private lateinit var binding: FHistoryBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: HistoryViewModel by viewModels { vmFactory }
    private var itemsAdapter: VideoItemsAdapter? = null
    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTitle(getString(R.string.f_history_title))
        initAdapters()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHistory()
    }

    private fun initAdapters() {
        itemsAdapter = VideoItemsAdapter { item ->
            findNavController().navigate(HistoryFragmentDirections.actionNavHistoryToNavDetails(item.dbId))
        }
        binding.rvHistoryList.apply {
            adapter = itemsAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.progressBar.isVisible = loading
            }
        }
        launchWhenCreated {
            viewModel.historyDataFlow.collectLatest { movies ->
                itemsAdapter?.submitData(movies)
                binding.tvEmptyView.isVisible = itemsAdapter?.itemCount == 0
            }
        }
    }
}
