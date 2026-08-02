package com.arny.mobilecinema.presentation.comments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FCommentsBinding
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import androidx.core.net.toUri

class CommentsFragment : Fragment(R.layout.f_comments) {

    private val args: CommentsFragmentArgs by navArgs()
    private val viewModel: CommentsViewModel by viewModel {
        parametersOf(args.pageUrl, args.title)
    }

    private var _binding: FCommentsBinding? = null
    private val binding get() = _binding!!

    private val adapter = CommentsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initList()
        initListeners()
        observeState()
        observeActions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun initList() {
        binding.rvComments.adapter = adapter
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun initListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.btnLoadMore.setOnClickListener {
            viewModel.loadNextPage()
        }
        binding.btnOpenSite.setOnClickListener {
            viewModel.openAddComment()
        }
    }

    private fun observeState() {
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun observeActions() {
        launchWhenCreated {
            viewModel.actions.collectLatest { action ->
                when (action) {
                    is CommentsAction.OpenUrl -> openUrl(action.url)
                }
            }
        }
    }

    private fun renderState(state: CommentsUiState) {
        binding.toolbar.title = getString(R.string.movie_comments)
        binding.tvMovieTitle.text = state.title
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        adapter.submitList(state.comments)

        binding.tvCommentsStatus.text = when {
            state.error != null -> getString(R.string.movie_comments_error, state.error)
            state.isRefreshing && state.comments.isEmpty() -> getString(R.string.movie_comments_loading)
            state.comments.isEmpty() -> getString(R.string.movie_comments_empty)
            else -> getString(
                R.string.movie_comments_pages_status,
                state.comments.size,
                state.currentPage.coerceAtLeast(1),
                state.totalPages.coerceAtLeast(1)
            )
        }
        binding.tvCommentsStatus.isVisible = true

        binding.btnLoadMore.isVisible = state.canLoadMore && !state.isLoadingMore
        binding.btnLoadMore.text = getString(
            R.string.movie_comments_load_more_page,
            state.currentPage + 1,
            state.totalPages
        )
        binding.progressLoadMore.isVisible = state.isLoadingMore
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            toast(getString(R.string.movie_comments_open_site_error))
        }
    }
}
