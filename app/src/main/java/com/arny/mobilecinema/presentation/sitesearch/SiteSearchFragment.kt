package com.arny.mobilecinema.presentation.sitesearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FSiteSearchBinding
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Поиск по каталогу сайта. Результаты — предпросмотр: карточка попадает в базу
 * при клике, после чего открывается экран деталей.
 */
class SiteSearchFragment : Fragment(R.layout.f_site_search) {

    private val viewModel: SiteSearchViewModel by viewModel()
    private val args: SiteSearchFragmentArgs by navArgs()

    private var _binding: FSiteSearchBinding? = null
    private val binding get() = requireNotNull(_binding)

    private lateinit var searchAdapter: SiteSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FSiteSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        searchAdapter = SiteSearchAdapter { item -> viewModel.openItem(item) }
        binding.rvSiteSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSiteSearch.adapter = searchAdapter

        binding.btnSiteSearch.setOnClickListener {
            requireActivity().hideKeyboard()
            viewModel.search()
        }
        binding.btnSiteSearchMore.setOnClickListener {
            viewModel.loadMore()
        }
        binding.tiedtSiteSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                requireActivity().hideKeyboard()
                viewModel.search()
                true
            } else {
                false
            }
        }
        binding.tiedtSiteSearch.doAfterTextChanged {
            viewModel.setQuery(it?.toString().orEmpty())
        }

        observeState()

        val initialQuery = args.query.orEmpty()
        if (initialQuery.isNotBlank()) {
            binding.tiedtSiteSearch.setText(initialQuery)
            binding.tiedtSiteSearch.setSelection(initialQuery.length)
            viewModel.setQuery(initialQuery)
            viewModel.search()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        render(state)
                    }
                }

                launch {
                    viewModel.toast.collectLatest { message ->
                        toast(message.toString(requireContext()))
                    }
                }

                launch {
                    viewModel.openMovie.collectLatest { dbId ->
                        findNavController().navigateSafely(
                            SiteSearchFragmentDirections.actionNavSiteSearchToNavDetails(dbId)
                        )
                    }
                }
            }
        }
    }

    private fun render(state: SiteSearchUiState) {
        binding.pbSiteSearch.isVisible = state.isLoading || state.isOpening
        binding.btnSiteSearch.isEnabled = !state.isLoading && !state.isOpening
        binding.btnSiteSearchMore.isVisible = state.hasMoreFilms
        binding.btnSiteSearchMore.isEnabled = !state.isLoadingMore
        binding.tvSiteSearchEmpty.isVisible =
            state.searched && !state.isLoading && state.items.isEmpty()
        searchAdapter.submitList(state.items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvSiteSearch.adapter = null
        _binding = null
    }
}
