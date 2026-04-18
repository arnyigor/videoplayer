package com.arny.mobilecinema.presentation.tv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import com.arny.mobilecinema.presentation.tv.update.TvUpdateProgressDialogFragment
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class TvHomeFragment : BrowseSupportFragment(), TvUpdateProgressDialogFragment.Callback {

    private val viewModel: TvHomeViewModel by viewModel()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var updateRowAdapter: ArrayObjectAdapter
    private lateinit var sortRowAdapter: ArrayObjectAdapter
    private lateinit var sortPresenter: SortCategoryPresenter

    private var isUpdatingDb = false
    private var isCancellingUpdate = false

    private var selectedSortCategory = MovieSortCategory.NEW
    private var force = false

    private val movieDiffCallback = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean =
            oldItem.dbId == newItem.dbId

        override fun areContentsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean =
            oldItem == newItem
    }

    private val allMoviesAdapter by lazy {
        PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)
    }

    private val historyAdapter by lazy {
        PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)
    }

    private val favoritesAdapter by lazy {
        PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)
    }

    private val updateReceiver by lazy {
        makeBroadcastReceiver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TV_HOME_FRAG", "onCreate | Initializing UI components")
        setupUI()
        setupRowsAdapter()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TV_HOME_FRAG", "onViewCreated | Setting up listeners and observing data")
        adapter = rowsAdapter
        setupLoadStateListener()
        setupClickListeners()
        setupDialogResultListener()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        Log.d("TV_HOME_FRAG", "onResume | Calling refreshData")
        viewModel.refreshData()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(200)
            viewModel.reloadHistory()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    private fun setupUI() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = requireContext().getColor(R.color.colorPrimary)
        searchAffordanceColor = requireContext().getColor(R.color.colorAccent)
        title = getString(R.string.app_name)
    }

    private fun setupRowsAdapter() {

        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }

        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        sortPresenter = SortCategoryPresenter()
        sortRowAdapter = ArrayObjectAdapter(sortPresenter).apply {
            add(MovieSortCategory.NEW)
            add(MovieSortCategory.POPULAR)
            add(MovieSortCategory.ALPHABET)
            add(MovieSortCategory.RATING)
        }

        sortPresenter.setSelectedPosition(selectedSortCategory.ordinal)
        rowsAdapter.add(ListRow(HeaderItem(-1, ""), sortRowAdapter))
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.all_movies)), allMoviesAdapter))
        rowsAdapter.add(ListRow(HeaderItem(2, getString(R.string.history)), historyAdapter))
        rowsAdapter.add(ListRow(HeaderItem(3, getString(R.string.favorites)), favoritesAdapter))

        updateRowAdapter = ArrayObjectAdapter(UpdateActionPresenter())
        updateRowAdapter.add(UpdateAction.CHECK_UPDATE)
        rowsAdapter.add(ListRow(HeaderItem(3, getString(R.string.update_list)), updateRowAdapter))

        adapter = rowsAdapter
    }

    private fun setupLoadStateListener() {
        allMoviesAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading
            if (isLoading) {
                progressBarManager.show()
            } else {
                if (!isUpdatingDb) {
                    progressBarManager.hide()
                }
            }
        }
    }

    private fun setupClickListeners() {

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is ViewMovie -> {
                    val bundle = Bundle().apply {
                        putLong("movieId", item.dbId)
                    }
                    findNavController().navigate(R.id.actionToDetails, bundle)
                }

                is UpdateAction -> {
                    handleUpdateAction(item)
                }

                is MovieSortCategory -> {
                    applySortCategory(item)
                }

                else -> {
                }
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            when (item) {
                is ViewMovie -> {
                    viewModel.onMovieSelected(item)
                }

                is MovieSortCategory -> {
                    applySortCategory(item)
                }

                else -> {
                }
            }
        }

        setOnSearchClickedListener {
            findNavController().navigate(R.id.actionToSearch)
        }
    }

    private fun applySortCategory(category: MovieSortCategory) {
        if (selectedSortCategory == category) {
            return
        }

        selectedSortCategory = category
        sortPresenter.setSelectedPosition(category.ordinal)
        sortRowAdapter.notifyArrayItemRangeChanged(0, sortRowAdapter.size())
        viewModel.setSortCategory(category)
    }

    private fun setupDialogResultListener() {
        childFragmentManager.setFragmentResultListener(
            TvUpdateDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val startUpdate = bundle.getBoolean(TvUpdateDialogFragment.KEY_START_UPDATE, false)
            if (startUpdate) {
                viewModel.startUpdateAfterUserConfirmation(force = false)
            }
        }
    }

    private fun showUpdateProgressDialog(
        progress: Int = -1,
        stage: String? = null
    ) {
        val existing = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment
        if (existing != null) {
            existing.updateProgress(progress, stage)
            return
        }

        TvUpdateProgressDialogFragment
            .newInstance(progress, stage)
            .show(childFragmentManager, TvUpdateProgressDialogFragment.TAG)
    }

    private fun updateUpdateProgressDialog(
        progress: Int = -1,
        stage: String? = null,
    ) {
        val dialog = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment
        if (dialog != null) {
            dialog.updateProgress(progress, stage)
        } else {
            showUpdateProgressDialog(progress, stage)
        }
    }

    private fun hideUpdateProgressDialog() {
        val existing =
            childFragmentManager.findFragmentByTag(TvUpdateProgressDialogFragment.TAG) as? DialogFragment

        existing?.dismissAllowingStateLoss()
    }

    private fun observeData() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moviesDataFlow.collectLatest { pagingData ->
                allMoviesAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("TV_HISTORY_COLLECTOR", "Starting collection for historyMoviesFlow")
            viewModel.historyMoviesFlow.collect { pagingData ->
                Log.d("TV_HISTORY_COLLECTOR", "historyMoviesFlow emitted | Submitting to adapter")
                historyAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteMoviesFlow.collectLatest { pagingData ->
                favoritesAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.urlData.collectLatest { url ->
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateAvailable.collectLatest { available ->
                if (available) {
                    viewModel.downloadData(force = false)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alert.collectLatest { alert ->
                showUpdateAlertDialog(alert)
            }
        }

    }

    private fun handleUpdateAction(action: UpdateAction) {
        when (action) {
            UpdateAction.CHECK_UPDATE -> viewModel.downloadData(force = true)
            UpdateAction.CANCEL_UPDATE -> viewModel.stopUpdate()
        }
    }

    override fun onCancelUpdateRequested() {
        if (isCancellingUpdate) {
            return
        }

        isCancellingUpdate = true
        isUpdatingDb = false
        progressBarManager.hide()
        val dialog = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment

        dialog?.markAsCancelled()

        viewModel.stopUpdate()
    }

    private fun showUpdateAlertDialog(alert: Alert) {
        if (!isAdded) return
        val exists = childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null
        if (exists) return

        when (alert.type) {
            is com.arny.mobilecinema.presentation.uimodels.AlertType.Update -> {
                showUpdateDialogWithData(
                    alert.content?.toString(requireContext()) ?: "",
                    alert.type.hasPartUpdate
                )
            }

            is com.arny.mobilecinema.presentation.uimodels.AlertType.SimpleAlert -> {
                showUpdateDialog()
            }

            else -> {}
        }
    }

    private fun showUpdateDialogWithData(updateTime: String, hasPartUpdate: Boolean) {
        if (!isAdded) return
        val exists = childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null
        if (exists) return

        TvUpdateDialogFragment.newInstance(updateTime, hasPartUpdate)
            .show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun showUpdateDialog() {
        if (!isAdded) return
        val exists = childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null
        if (exists) return

        TvUpdateDialogFragment.newInstance().show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun makeBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    isUpdatingDb = true
                    progressBarManager.initialDelay = 0
                    progressBarManager.show()

                    showUpdateProgressDialog(
                        progress = -1,
                        stage = getString(R.string.updating_all)
                    )
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    if (!isUpdatingDb || isCancellingUpdate) {
                        return
                    }

                    progressBarManager.initialDelay = 0
                    progressBarManager.show()

                    val percent = intent.getIntExtra("progress_percent", -1)
                    val current = intent.getIntExtra("progress_current", -1)
                    val total = intent.getIntExtra("progress_total", -1)
                    val percentText = if (percent in 0..100) "$percent%" else null

                    updateUpdateProgressDialog(
                        progress = percent,
                        stage = percentText,
                    )
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {

                    isUpdatingDb = false
                    isCancellingUpdate = false
                    progressBarManager.hide()
                    hideUpdateProgressDialog()

                    Toast.makeText(
                        requireContext(),
                        R.string.update_finished_success,
                        Toast.LENGTH_LONG
                    ).show()

                    allMoviesAdapter.refresh()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {

                    isUpdatingDb = false
                    isCancellingUpdate = false
                    progressBarManager.hide()
                    hideUpdateProgressDialog()

                    val errorMsg = intent.getStringExtra("error_message")
                        ?: "Неизвестная ошибка"


                    Toast.makeText(
                        requireContext(),
                        "Ошибка: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }

                AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {

                    isUpdatingDb = false
                    isCancellingUpdate = false
                    progressBarManager.hide()
                    hideUpdateProgressDialog()


                    Toast.makeText(
                        requireContext(),
                        R.string.update_canceled,
                        Toast.LENGTH_LONG
                    ).show()
                }

                else -> {
                }
            }
        }
    }
}
