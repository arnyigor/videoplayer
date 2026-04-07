package com.arny.mobilecinema.presentation.tv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.setFragmentResultListener
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

class TvHomeFragment : BrowseSupportFragment() {

    private val viewModel: TvHomeViewModel by viewModel()

    private lateinit var rowsAdapter: ArrayObjectAdapter

    private val movieDiffCallback = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean {
            return oldItem.dbId == newItem.dbId
        }

        override fun areContentsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean {
            return oldItem == newItem
        }
    }

    private val allMoviesAdapter = PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)
    private val historyAdapter = PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)
    private val favoritesAdapter = PagingDataAdapter(MovieCardPresenter(), movieDiffCallback)

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        setupRowsAdapter()
        setupDialogListener()
    }

    private fun setupUI() {
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = requireContext().getColor(R.color.colorPrimary)
        searchAffordanceColor = requireContext().getColor(R.color.colorAccent)
    }

    private fun setupDialogListener() {
        Timber.i( "setupDialogListener: INIT")
        childFragmentManager.setFragmentResultListener("UPDATE_REQUEST", this) { _, bundle ->
            val shouldUpdate = bundle.getBoolean("START_UPDATE", false)
            val shouldStop = bundle.getBoolean("STOP_UPDATE", false)
            Timber.d( "setupDialogListener: shouldUpdate:$shouldUpdate, shouldStop:$shouldStop")
            if (shouldUpdate) {
                viewModel.downloadData()
            }
            if (shouldStop) {
                viewModel.stopUpdate()
            }
        }
    }

    private fun setupRowsAdapter() {
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        val allMoviesHeader = HeaderItem(0, getString(R.string.all_movies))
        rowsAdapter.add(ListRow(allMoviesHeader, allMoviesAdapter))

        val historyHeader = HeaderItem(1, getString(R.string.history))
        rowsAdapter.add(ListRow(historyHeader, historyAdapter))

        val favoritesHeader = HeaderItem(2, getString(R.string.favorites))
        rowsAdapter.add(ListRow(favoritesHeader, favoritesAdapter))

        adapter = rowsAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                findNavController().navigate(
                    TvHomeFragmentDirections.actionToDetails(item.dbId)
                )
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                viewModel.onMovieSelected(item)
            }
        }

        setOnSearchClickedListener {
            findNavController().navigate(
                TvHomeFragmentDirections.actionToSearch()
            )
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moviesDataFlow.collectLatest { pagingData ->
                allMoviesAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyMoviesFlow.collectLatest { pagingData ->
                historyAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteMoviesFlow.collectLatest { pagingData ->
                favoritesAdapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateAvailable.collect { available ->
                if (available) {
                    showUpdateDialog()
                }
            }
        }
    }

    private fun showUpdateDialog() {
        TvUpdateDialogFragment.newInstance()
            .show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    private fun makeBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)
            Timber.d("TV Broadcast received – action: %s", action ?: "<null>")

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    Toast.makeText(requireContext(), R.string.update_started, Toast.LENGTH_SHORT).show()
                    progressBarManager.show()
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    val percent = intent?.getIntExtra("progress_percent", 0) ?: 0
                    progressBarManager.show()
                    title = getString(R.string.app_name) + " — ${percent}%"
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    Toast.makeText(requireContext(), R.string.update_finished_success, Toast.LENGTH_SHORT).show()
                    progressBarManager.hide()
                    title = getString(R.string.app_name)
                    viewModel.refreshData()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    val errorMsg = intent.getStringExtra("error_message") ?: ""
                    Toast.makeText(requireContext(), getString(R.string.update_finished_error, errorMsg), Toast.LENGTH_LONG).show()
                    progressBarManager.hide()
                    title = getString(R.string.app_name)
                }

                else -> {
                    Timber.w("Unknown update status action: %s", action)
                }
            }
        }
    }
}