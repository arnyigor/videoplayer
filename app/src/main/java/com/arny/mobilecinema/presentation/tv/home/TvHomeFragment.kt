package com.arny.mobilecinema.presentation.tv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

enum class UpdateAction(val labelResId: Int) {
    CHECK_UPDATE(R.string.check_update),
    CANCEL_UPDATE(R.string.cancel_update)
}

class TvHomeFragment : BrowseSupportFragment() {

    private val viewModel: TvHomeViewModel by viewModel()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var updateRowAdapter: ArrayObjectAdapter

    // Глобальный флаг состояния обновления
    private var isUpdatingDb = false

    private val movieDiffCallback = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(oldItem: ViewMovie, newItem: ViewMovie) = oldItem.dbId == newItem.dbId
        override fun areContentsTheSame(oldItem: ViewMovie, newItem: ViewMovie) = oldItem == newItem
    }

    private val allMoviesAdapter by lazy { PagingDataAdapter(MovieCardPresenter(), movieDiffCallback) }
    private val historyAdapter by lazy { PagingDataAdapter(MovieCardPresenter(), movieDiffCallback) }
    private val favoritesAdapter by lazy { PagingDataAdapter(MovieCardPresenter(), movieDiffCallback) }

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        setupRowsAdapter()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLoadStateListener()
        setupClickListeners()
        setupDialogResultListener()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        viewModel.refreshData()
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

        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.all_movies)), allMoviesAdapter))
        rowsAdapter.add(ListRow(HeaderItem(1, getString(R.string.history)), historyAdapter))
        rowsAdapter.add(ListRow(HeaderItem(2, getString(R.string.favorites)), favoritesAdapter))

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
                // Если база качается, не прячем спиннер!
                if (!isUpdatingDb) {
                    progressBarManager.hide()
                }
            }
        }
    }

    private fun setupClickListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is ViewMovie -> findNavController().navigate(TvHomeFragmentDirections.actionToDetails(item.dbId))
                is UpdateAction -> handleUpdateAction(item)
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item is ViewMovie) viewModel.onMovieSelected(item)
        }

        setOnSearchClickedListener {
            findNavController().navigate(TvHomeFragmentDirections.actionToSearch())
        }
    }

    private fun setupDialogResultListener() {
        childFragmentManager.setFragmentResultListener(
            TvUpdateDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(TvUpdateDialogFragment.KEY_START_UPDATE, false)) {
                viewModel.downloadData()
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moviesDataFlow.collectLatest { allMoviesAdapter.submitData(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyMoviesFlow.collectLatest { historyAdapter.submitData(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteMoviesFlow.collectLatest { favoritesAdapter.submitData(it) }
        }
    }

    private fun handleUpdateAction(action: UpdateAction) {
        when (action) {
            UpdateAction.CHECK_UPDATE -> showUpdateDialog()
            UpdateAction.CANCEL_UPDATE -> viewModel.stopUpdate()
        }
    }

    private fun showUpdateDialog() {
        if (childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null) return
        TvUpdateDialogFragment.newInstance().show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun makeBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)

            // Если пришло уведомление о парсинге конкретного фильма
            val titleProgress = intent?.getStringExtra("update_title")
            if (!titleProgress.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Обновление: $titleProgress", Toast.LENGTH_SHORT).show()
                return
            }

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    isUpdatingDb = true
                    progressBarManager.initialDelay = 0
                    progressBarManager.show()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    isUpdatingDb = false
                    progressBarManager.hide()
                    Toast.makeText(requireContext(), R.string.update_finished_success, Toast.LENGTH_LONG).show()
                    allMoviesAdapter.refresh()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    isUpdatingDb = false
                    progressBarManager.hide()
                    val errorMsg = intent.getStringExtra("error_message") ?: "Неизвестная ошибка"
                    Toast.makeText(requireContext(), "Ошибка: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}