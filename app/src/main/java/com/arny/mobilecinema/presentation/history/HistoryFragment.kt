package com.arny.mobilecinema.presentation.history

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FHistoryBinding
import com.arny.mobilecinema.presentation.home.VideoItemsAdapter
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class HistoryFragment : Fragment(), OnSearchListener {
    private lateinit var binding: FHistoryBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: HistoryViewModel by viewModels { vmFactory }
    private var itemsAdapter: VideoItemsAdapter? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var onQueryChangeSubmit = true
    private var emptySearch = true
    private var currentOrder: String = ""
    private var searchType: String = ""
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
        viewModel.reloadHistory()
    }

    override fun isSearchComplete(): Boolean = emptySearch

    override fun collapseSearch() {
        viewModel.loadHistory()
        emptySearch = true
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_search).isVisible = hasSavedData
                menu.findItem(R.id.action_search_settings).isVisible = hasSavedData && !emptySearch
                menu.findItem(R.id.action_order_settings).isVisible = hasSavedData
                menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.history_menu, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                searchView = setupSearchView(
                    menuItem = searchMenuItem!!,
                    onQueryChange = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadHistory(query.orEmpty(), onQueryChangeSubmit)
                        onQueryChangeSubmit = true
                    },
                    onMenuCollapse = {
                        viewModel.loadHistory()
                        emptySearch = true
                        requireActivity().hideKeyboard()
                        requireActivity().invalidateOptionsMenu()
                    },
                    onSubmitAvailable = true,
                    onQuerySubmit = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadHistory(query.orEmpty())
                    }
                )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }
                    R.id.action_order_settings -> {
                        showCustomOrderDialog()
                        true
                    }
                    R.id.action_search_settings -> {
                        showCustomSearchDialog()
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


    private fun showCustomOrderDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_order_settings),
            layout = R.layout.d_custom_order,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.setOrder(currentOrder)
            },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbNone to AppConstants.Order.NONE,
                        rbTitle to AppConstants.Order.TITLE,
                        rbRatings to AppConstants.Order.RATINGS,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC,
                    )
                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.TITLE -> rbTitle.isChecked = true
                            AppConstants.Order.RATINGS -> rbRatings.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            else -> rbNone.isChecked = true
                        }
                    }?: kotlin.run {
                        rbNone.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                currentOrder = orderString
                            }
                        }
                    }
                }
            }
        )
    }

    private fun showCustomSearchDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_settings_title),
            layout = R.layout.d_custom_search,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.setSearchType(searchType)
            },
            initView = {
                with(DCustomSearchBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbTitle to AppConstants.SearchType.TITLE,
                        rbDirectors to AppConstants.SearchType.DIRECTORS,
                        rbActors to AppConstants.SearchType.ACTORS,
                        rbGenres to AppConstants.SearchType.GENRES,
                    )
                    searchType.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.SearchType.TITLE -> rbTitle.isChecked = true
                            AppConstants.SearchType.DIRECTORS -> rbDirectors.isChecked = true
                            AppConstants.SearchType.ACTORS -> rbActors.isChecked = true
                            AppConstants.SearchType.GENRES -> rbGenres.isChecked = true
                            else -> rbTitle.isChecked = true
                        }
                    }?: kotlin.run {
                        rbTitle.isChecked = true
                    }
                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                searchType = orderString
                            }
                        }
                    }
                }
            }
        )
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
