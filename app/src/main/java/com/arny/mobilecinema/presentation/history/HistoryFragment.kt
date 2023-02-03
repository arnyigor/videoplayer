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
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.presentation.utils.KeyboardHelper
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.setDrawableRightListener
import com.arny.mobilecinema.presentation.utils.setEnterPressListener
import com.arny.mobilecinema.presentation.utils.textChanges
import com.arny.mobilecinema.presentation.utils.toastError
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class HistoryFragment : Fragment() {
    private lateinit var binding: FHistoryBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: HistoryViewModel by viewModels { vmFactory }
    private var videosAdapter: HistoryVideosAdapter? = null
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
        initUI()
        iniList()
        observeData()
    }

    private fun iniList() {
        videosAdapter = HistoryVideosAdapter(
            onItemClick = { item ->
//               findNavController().navigate(HistoryFragmentDirections.actionNavHistoryToNavDetails())
            },
            onItemClearClick = { item ->
                alertDialog(
                    title = getString(R.string.question_remove),
                    content = getString(R.string.question_remove_cache_title, item.title),
                    btnOkText = getString(android.R.string.ok),
                    btnCancelText = getString(android.R.string.cancel),
                    onConfirm = {
//                        viewModel.clearCache(item)
                    }
                )
            }
        )
        binding.rvHistoryList.adapter = videosAdapter
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.progressBar.isVisible = loading
                binding.edtSearch.isVisible = !loading
                binding.rvHistoryList.isVisible = !loading
            }
        }
        launchWhenCreated {
            viewModel.mainContent.collectLatest { result ->
                when (result) {
                    is DataResult.Error -> toastError(result.throwable)
                    is DataResult.Success -> updateList(result.result)
                }
            }
        }
        launchWhenCreated {
            viewModel.mainContent.collectLatest { result ->
                when (result) {
                    is DataResult.Error -> toastError(result.throwable)
                    is DataResult.Success ->  updateList(result.result)
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun initUI() {
        updateTitle(getString(R.string.f_history_title))
        with(binding) {
            binding.edtSearch.setDrawableRightListener {
                KeyboardHelper.hideKeyboard(requireActivity())
                edtSearch.setText("")
//                viewModel.loadHistory()
            }
            edtSearch.setEnterPressListener {
                KeyboardHelper.hideKeyboard(requireActivity())
            }
            binding.edtSearch
                .textChanges()
                .debounce(500)
                .filter { !it.isNullOrBlank() }
                .onEach {
//                    viewModel.searchCached(edtSearch.text.toString())
                }
                .launchIn(lifecycleScope)
        }
    }

    private fun updateList(items: List<Movie>?) {
        val empty = items.isNullOrEmpty()
        binding.tvEmptyView.isVisible = empty
        videosAdapter?.submitList(items)
    }
}
