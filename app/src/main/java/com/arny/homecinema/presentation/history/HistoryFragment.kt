package com.arny.homecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.databinding.FHistoryBinding
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import moxy.MvpAppCompatFragment
import moxy.ktx.moxyPresenter
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext

class HistoryFragment : MvpAppCompatFragment(), HistoryView, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()
    private val compositeJob = CompositeJob()

    @Inject
    lateinit var presenterProvider: Provider<HistoryPresenter>

    private val presenter by moxyPresenter { presenterProvider.get() }

    private lateinit var groupAdapter: GroupAdapter<GroupieViewHolder>

    companion object {
        fun newInstance() = HistoryFragment()
    }

    private val binding by viewBinding { FHistoryBinding.bind(it).also(::initBinding) }

    @FlowPreview
    private fun initBinding(binding: FHistoryBinding) = with(binding) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.f_history_title)
        initList()
        edtSearch.setDrawableRightListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            edtSearch.setText("")
            presenter.loadHistory()
        }
        edtSearch.setEnterPressListener {
            KeyboardHelper.hideKeyboard(requireActivity())
        }
        compositeJob.add(
            CoroutineScope(coroutineContext).launch {
                edtSearch.getQueryTextChangeStateFlow()
                    .debounce(450)
                    .distinctUntilChanged()
                    .collect {
                        presenter.searchCached(edtSearch.text.toString())
                    }
            }
        )
    }

    private fun FHistoryBinding.initList() {
        groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.setOnItemClickListener { item, _ ->
            val video = (item as VideoItem).movie
            binding.root.findNavController()
                .navigate(HistoryFragmentDirections.actionHistoryFragmentToDetailsFragment(video))
        }
        rvHistoryList.also {
            it.adapter = groupAdapter
            it.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun updateList(items: List<VideoItem>) {
        val empty = items.isEmpty()
        binding.tvEmptyView.isVisible = empty
        groupAdapter.clear()
        groupAdapter.addAll(items)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.f_history, container, false)
    }

    override fun showError(result: DataResult<Throwable>) {
        if (result is DataResult.Error) {
            toastError(result.throwable)
        }
    }

    override fun updateList(result: DataResult<List<Movie>>) {
        when (result) {
            is DataResult.Success -> {
                updateList(result.data.map { VideoItem(it) })
            }
            is DataResult.Error -> toastError(result.throwable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeJob.cancel()
    }
}
