package com.arny.homecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.databinding.FHistoryBinding
import com.arny.homecinema.presentation.models.VideoItem
import com.arny.homecinema.presentation.utils.*
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject


class HistoryFragment : Fragment() {

    @Inject
    lateinit var vm: HistoryViewModel

    private lateinit var groupAdapter: GroupAdapter<GroupieViewHolder>

    companion object {
        fun newInstance() = HistoryFragment()
    }

    private val binding by viewBinding { FHistoryBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: FHistoryBinding) = with(binding) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.f_history_title)
        initList()
        vm.result.observe(this@HistoryFragment) { result ->
            when (result) {
                is DataResult.Success -> {
                    val items = result.data.map { VideoItem(it) }
                    updateList(items)
                }
                is DataResult.Error -> toastError(result.throwable)
            }
        }
        edtSearch.setDrawableRightListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            edtSearch.setText("")
            vm.loadHistory()
        }
        edtSearch.setEnterPressListener {
            KeyboardHelper.hideKeyboard(requireActivity())
            vm.searchCached(edtSearch.text.toString())
        }
        edtSearch.doAfterTextChanged {
            if (edtSearch.isFocused) {
                vm.searchCached(it.toString())
            }
        }
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
}
