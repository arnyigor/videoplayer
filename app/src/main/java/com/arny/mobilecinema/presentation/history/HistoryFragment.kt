package com.arny.mobilecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.presentation.utils.*
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
            val video = (item as HistoryVideoItem).movie
            binding.root.findNavController()
                .navigate(HistoryFragmentDirections.actionHistoryFragmentToDetailsFragment(video))
        }
        rvHistoryList.also {
            it.adapter = groupAdapter
            it.layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun updateList(items: List<HistoryVideoItem>) {
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

    override fun toastMessage(@StringRes strRes: Int?) {
        toast(strRes?.let { getString(it) })
    }

    override fun updateList(result: DataResult<List<Movie>>) {
        when (result) {
            is DataResult.Success -> {
                updateList(result.data.map { map ->
                    HistoryVideoItem(map) { m ->
                        alertDialog(
                            requireContext(),
                            getString(R.string.question_remove),
                            getString(R.string.question_remove_cache_title, m.title),
                            getString(android.R.string.ok),
                            getString(android.R.string.cancel),
                            onConfirm = {
                                presenter.clearCache(m)
                            }
                        )
                    }
                })
            }
            is DataResult.Error -> toastError(result.throwable)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().unlockOrientation()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeJob.cancel()
    }
}
