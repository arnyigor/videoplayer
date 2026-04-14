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
import androidx.fragment.app.DialogFragment
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import com.arny.mobilecinema.presentation.tv.update.TvUpdateProgressDialogFragment
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

enum class UpdateAction(val labelResId: Int) {
    CHECK_UPDATE(R.string.check_update),
    CANCEL_UPDATE(R.string.cancel_update)
}

class TvHomeFragment : BrowseSupportFragment(), TvUpdateProgressDialogFragment.Callback {

    private val viewModel: TvHomeViewModel by viewModel()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var updateRowAdapter: ArrayObjectAdapter
    private lateinit var sortRowAdapter: ArrayObjectAdapter
    private lateinit var sortPresenter: SortCategoryPresenter

    // Глобальный флаг состояния обновления
    private var isUpdatingDb = false
    private var isCancellingUpdate = false

    private var selectedSortCategory = MovieSortCategory.NEW

    private val movieDiffCallback = object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(oldItem: ViewMovie, newItem: ViewMovie) = oldItem.dbId == newItem.dbId
        override fun areContentsTheSame(oldItem: ViewMovie, newItem: ViewMovie) = oldItem == newItem
    }

    private val allMoviesAdapter by lazy { PagingDataAdapter(MovieCardPresenter(), movieDiffCallback) }
    private val continueWatchingAdapter by lazy { PagingDataAdapter(MovieCardPresenter(), movieDiffCallback) }
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

        // Row сортировки (добавляем первым!)
        sortPresenter = SortCategoryPresenter()
        sortRowAdapter = ArrayObjectAdapter(sortPresenter).apply {
            add(MovieSortCategory.NEW)
            add(MovieSortCategory.POPULAR)
            add(MovieSortCategory.ALPHABET)
            add(MovieSortCategory.RATING)
        }
        sortPresenter.setSelectedPosition(selectedSortCategory.ordinal)
        rowsAdapter.add(ListRow(HeaderItem(-1, ""), sortRowAdapter))

        // Основные категории
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.all_movies)), allMoviesAdapter))
        rowsAdapter.add(ListRow(HeaderItem(1, getString(R.string.continue_watching)), continueWatchingAdapter))
        rowsAdapter.add(ListRow(HeaderItem(2, getString(R.string.history)), historyAdapter))
        rowsAdapter.add(ListRow(HeaderItem(3, getString(R.string.favorites)), favoritesAdapter))

        updateRowAdapter = ArrayObjectAdapter(UpdateActionPresenter())
        updateRowAdapter.add(UpdateAction.CHECK_UPDATE)
        rowsAdapter.add(ListRow(HeaderItem(4, getString(R.string.update_list)), updateRowAdapter))

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
                is MovieSortCategory -> applySortCategory(item)
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            when (item) {
                is ViewMovie -> viewModel.onMovieSelected(item)
                is MovieSortCategory -> applySortCategory(item)
            }
        }

        setOnSearchClickedListener {
            findNavController().navigate(TvHomeFragmentDirections.actionToSearch())
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
            if (bundle.getBoolean(TvUpdateDialogFragment.KEY_START_UPDATE, false)) {
                // Для TV сразу запускаем update-flow после подтверждения
                // Пропускаем второй alert диалог
                viewModel.startUpdateAfterUserConfirmation(force = false)
            }
        }
    }

    private fun showUpdateProgressDialog(
        progress: Int = -1,
        stage: String? = null,
        title: String? = null
    ) {
        val existing = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment

        Timber.d("showUpdateProgressDialog: progress=$progress, stage=$stage, title=$title")

        if (existing != null) {
            Timber.d("showUpdateProgressDialog: updating existing dialog with progress=$progress, stage=$stage")
            existing.updateProgress(progress, stage)
            return
        }

        Timber.d("showUpdateProgressDialog: creating new dialog with progress=$progress, stage=$stage, title=$title")
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

        Timber.d("updateUpdateProgressDialog: progress=$progress, stage=$stage")

        if (dialog != null) {
            Timber.d("updateUpdateProgressDialog: updating existing dialog with progress=$progress, stage=$stage")
            dialog.updateProgress(progress, stage)
        } else {
            Timber.d("updateUpdateProgressDialog: creating new dialog via showUpdateProgressDialog")
            showUpdateProgressDialog(progress, stage)
        }
    }

    private fun hideUpdateProgressDialog() {
        val existing = (childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? DialogFragment)

        Timber.d("hideUpdateProgressDialog: dismissing dialog")

        existing?.dismissAllowingStateLoss()
    }


    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moviesDataFlow.collectLatest { allMoviesAdapter.submitData(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.continueWatchingMoviesFlow.collectLatest { continueWatchingAdapter.submitData(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyMoviesFlow.collectLatest { historyAdapter.submitData(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteMoviesFlow.collectLatest { favoritesAdapter.submitData(it) }
        }
        // Наблюдаем URL flow для запуска UpdateService
        // Progress dialog показывается по broadcast из сервиса
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.urlData.collectLatest { url ->
                Timber.d("URL received for update: $url")
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
                }
            }
        }
    }

    private fun handleUpdateAction(action: UpdateAction) {
        when (action) {
            UpdateAction.CHECK_UPDATE -> showUpdateDialog()
            UpdateAction.CANCEL_UPDATE -> viewModel.stopUpdate()
        }
    }

    override fun onCancelUpdateRequested() {
        if (isCancellingUpdate) return

        isCancellingUpdate = true
        isUpdatingDb = false
        progressBarManager.hide()

        // Останавливаем spinner и показываем "Отменено"
        val dialog = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment
        dialog?.markAsCancelled()

        viewModel.stopUpdate()
    }

    private fun showUpdateDialog() {
        if (childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null) return
        Timber.d("showUpdateDialog: new dialog shown")
        TvUpdateDialogFragment.newInstance().show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun makeBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)

            Timber.d("BroadcastReceiver received ACTION=$action")

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    isUpdatingDb = true
                    progressBarManager.initialDelay = 0
                    progressBarManager.show()
                    showUpdateProgressDialog(
                        progress = -1,
                        stage = getString(R.string.updating_all),
                        title = null
                    )
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    // Игнорируем поздние PROGRESS после отмены
                    if (!isUpdatingDb || isCancellingUpdate) {
                        Timber.d("PROGRESS ignored: isUpdatingDb=$isUpdatingDb, isCancellingUpdate=$isCancellingUpdate")
                        return
                    }

                    progressBarManager.initialDelay = 0
                    progressBarManager.show()

                    val percent = intent.getIntExtra("progress_percent", -1)
                    val current = intent.getIntExtra("progress_current", -1)
                    val total = intent.getIntExtra("progress_total", -1)

                    val percentText = if (percent in 0..100) "$percent%" else null

                    Timber.d(
                        "PROGRESS_UPDATE: percent=$percent, current=$current, total=$total, " +
                                "percentText=$percentText"
                    )

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
                    Timber.d("UPDATE_COMPLETE_SUCCESS: showing success toast")
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

                    Timber.d("UPDATE_COMPLETE_ERROR: error=$errorMsg")
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
            }
        }
    }
}
