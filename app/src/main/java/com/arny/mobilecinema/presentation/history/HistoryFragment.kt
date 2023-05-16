package com.arny.mobilecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.presentation.home.VideoItemsAdapter
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class HistoryFragment : Fragment() {
    private lateinit var binding: FHistoryBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: HistoryViewModel by viewModels { vmFactory }
    private var itemsAdapter: VideoItemsAdapter? = null
    private var hasSavedData: Boolean = false
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
        initMenu()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHistory()
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.history_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }
                    R.id.menu_action_clear_cache -> {
                        alertDialog(
                            getString(R.string.question_remove),
                            getString(
                                R.string.question_remove_all_history,
                            ),
                            getString(android.R.string.ok),
                            getString(android.R.string.cancel),
                            onConfirm = {
                                viewModel.clearAllViewHistory()
                            }
                        )
                        true
                    }
                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun initAdapters() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        itemsAdapter = VideoItemsAdapter(baseUrl) { item ->
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
            viewModel.empty.collectLatest { empty ->
                hasSavedData = !empty
                requireActivity().invalidateOptionsMenu()
                binding.tvEmptyView.isVisible = empty
            }
        }
        launchWhenCreated {
            viewModel.historyDataFlow.collectLatest { movies ->
                itemsAdapter?.submitData(movies)
            }
        }
    }
}
