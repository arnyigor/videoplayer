package com.arny.mobilecinema.presentation.tv.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.tv.ui.components.TvErrorState
import com.arny.mobilecinema.presentation.tv.ui.components.TvLoadingState
import com.arny.mobilecinema.presentation.tv.ui.details.DetailsScreen
import com.arny.mobilecinema.presentation.tv.ui.theme.TvColors
import com.arny.mobilecinema.presentation.tv.ui.theme.TvTheme
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsAction
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Детали фильма/сериала на Compose (CMP-09/10).
 *
 * Навигационный контракт прежний: аргумент `movieId`, переходы в `tvPlayerFragment`
 * с `movieId/seasonIndex/episodeIndex/sharedUrl` и в `tvSearchFragment` с `query/searchType`.
 * Обновление данных осталось на `UpdateService`: экран только запускает его и показывает процент.
 */
class TvComposeDetailsFragment : Fragment(), KoinComponent {

    companion object {
        const val ARG_MOVIE_ID = "movieId"
        private const val AUTO_UPDATE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val viewModel: TvDetailsViewModel by viewModel()
    private val prefs: Prefs by inject()

    private val movieId: Long by lazy { arguments?.getLong(ARG_MOVIE_ID) ?: 0L }

    private var autoUpdateRequestedForUrl: String? = null

    /** Состояние обновления читается Compose напрямую, обновляется из BroadcastReceiver. */
    private var isUpdating by mutableStateOf(false)
    private var updatePercent by mutableStateOf(-1)

    private val updateReceiver by lazy { makeUpdateReceiver() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TvTheme {
                val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
                val state by viewModel.uiState.collectAsState()
                val movie = state.movie

                val listState = remember {
                    LazyListState(
                        firstVisibleItemIndex = viewModel.detailsScrollIndex,
                        firstVisibleItemScrollOffset = viewModel.detailsScrollOffset,
                    )
                }
                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.saveDetailsScroll(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                    }
                }

                LaunchedEffect(movie?.dbId, movie?.info?.detailsFetchedAt) {
                    val loaded = movie ?: return@LaunchedEffect
                    val pageUrl = loaded.pageUrl
                    if (pageUrl.isBlank() || autoUpdateRequestedForUrl == pageUrl) return@LaunchedEffect
                    if (!isDataOutdated(loaded) && hasPlayableLinks(loaded)) return@LaunchedEffect
                    autoUpdateRequestedForUrl = pageUrl
                    showUpdateDialog(loaded)
                }

                when {
                    movie == null && state.error -> DetailsPlaceholder {
                        TvErrorState(
                            message = getString(R.string.tv_details_error),
                            onRetry = viewModel::retry,
                        )
                    }

                    movie == null -> DetailsPlaceholder { TvLoadingState() }

                    else -> {
                        val playBlocked = isUpdating && !hasPlayableLinks(movie)
                        DetailsScreen(
                            movie = movie,
                            isFavorite = state.isFavorite,
                            resumeTarget = state.resumeTarget,
                            progress = state.progress,
                            selectedSeasonIndex = viewModel.selectedSeasonIndex,
                            cta = DetailsContentFactory.cta(movie, state.resumeTarget),
                            isUpdating = isUpdating,
                            updatePercent = updatePercent,
                            onPlay = {
                                if (playBlocked) showPleaseWait() else playTarget()
                            },
                            onPlayEpisode = { episode ->
                                if (playBlocked) {
                                    showPleaseWait()
                                } else {
                                    viewModel.selectSeason(episode.seasonIndex)
                                    navigateToPlayer(episode.seasonIndex, episode.episodeIndex)
                                }
                            },
                            onPlaySource = { source ->
                                if (playBlocked) {
                                    showPleaseWait()
                                } else {
                                    navigateToPlayer(
                                        seasonIndex = viewModel.selectedSeasonIndex,
                                        episodeIndex = 0,
                                        selectedUrl = source.url,
                                    )
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(movieId) },
                            onUpdate = { showUpdateDialog(movie) },
                            onCancelUpdate = { cancelUpdate() },
                            onSelectSeason = viewModel::selectSeason,
                            onSearch = { query, searchType -> navigateToSearch(query, searchType) },
                            imageBaseUrl = baseUrl,
                            listState = listState,
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (movieId <= 0L) {
            Toast.makeText(requireContext(), R.string.error_loading_data, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.loadMovie(movieId)
        setupDialogResultListener()
        observeActions()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
        // Прогресс мог измениться после просмотра: перечитываем статусы серий и CTA.
        viewModel.refreshProgress()
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    @Composable
    private fun DetailsPlaceholder(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TvColors.Background),
        ) {
            content()
        }
    }

    private fun showPleaseWait() {
        Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
    }

    private fun isDataOutdated(movie: Movie): Boolean {
        val fetchedAt = movie.info.detailsFetchedAt
        return fetchedAt <= 0L || System.currentTimeMillis() - fetchedAt > AUTO_UPDATE_MAX_AGE_MS
    }

    private fun hasPlayableLinks(movie: Movie): Boolean = when (movie.type) {
        MovieType.CINEMA -> DetailsContentFactory.sources(movie, null).isNotEmpty()
        MovieType.SERIAL -> DetailsContentFactory.playableRefs(movie).any { it.isAvailable }
        else -> false
    }

    private fun playTarget() {
        val ref = DetailsContentFactory.recommendedRef(viewModel.uiState.value.resumeTarget)
        if (ref != null) viewModel.selectSeason(ref.seasonIndex)
        navigateToPlayer(
            seasonIndex = ref?.seasonIndex ?: 0,
            episodeIndex = ref?.episodeIndex ?: 0,
        )
    }

    private fun navigateToPlayer(seasonIndex: Int, episodeIndex: Int, selectedUrl: String = "") {
        findNavController().navigate(
            R.id.tvPlayerFragment,
            bundleOf(
                ARG_MOVIE_ID to movieId,
                "sharedUrl" to selectedUrl,
                "seasonIndex" to seasonIndex,
                "episodeIndex" to episodeIndex,
            ),
        )
    }

    private fun navigateToSearch(query: String, searchType: String) {
        findNavController().navigate(
            R.id.tvSearchFragment,
            bundleOf("query" to query, "searchType" to searchType),
        )
    }

    private fun setupDialogResultListener() {
        childFragmentManager.setFragmentResultListener(
            TvUpdateDialogFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            if (bundle.getBoolean(TvUpdateDialogFragment.KEY_START_UPDATE, false)) {
                viewModel.updateMovieData(movieId)
            }
        }
    }

    private fun showUpdateDialog(movie: Movie?) {
        if (childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null) return
        val message = movie?.title
            ?.takeIf { it.isNotBlank() }
            ?.let { title -> "$title\n\n${getString(R.string.update_description)}" }
            ?: getString(R.string.update_description)
        TvUpdateDialogFragment.newInstance(
            title = getString(R.string.update_attention),
            message = message,
        ).show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun cancelUpdate() {
        isUpdating = false
        updatePercent = -1
        requireContext().startService(
            Intent(requireContext(), UpdateService::class.java).apply {
                action = AppConstants.ACTION_UPDATE_ALL_CANCEL
            }
        )
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actions.collect { action ->
                when (action) {
                    is TvDetailsAction.ShowToast -> Toast.makeText(
                        requireContext(),
                        action.message.toString(requireContext()),
                        Toast.LENGTH_SHORT,
                    ).show()

                    is TvDetailsAction.ShowError -> Toast.makeText(
                        requireContext(),
                        action.message.toString(requireContext()),
                        Toast.LENGTH_LONG,
                    ).show()

                    is TvDetailsAction.NavigateToUpdate -> requestMovieUpdate(action.url)
                }
            }
        }
    }

    private fun requestMovieUpdate(url: String) {
        requireContext().sendServiceMessage(
            Intent(requireContext().applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE_BY_URL,
        ) {
            putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
            // Разбор начат с экрана деталей — главная не должна сама открывать карточку.
            putBoolean(AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND, true)
        }
    }

    private fun makeUpdateReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    isUpdating = true
                    updatePercent = -1
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    isUpdating = true
                    updatePercent = intent.getIntExtra("progress_percent", -1)
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    isUpdating = false
                    updatePercent = -1
                    Toast.makeText(requireContext(), R.string.update_finished_success, Toast.LENGTH_LONG).show()
                    viewModel.retry()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    isUpdating = false
                    updatePercent = -1
                    val errorMsg = intent.getStringExtra("error_message")
                        ?: getString(R.string.error_loading_data)
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }

                AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {
                    isUpdating = false
                    updatePercent = -1
                    Toast.makeText(requireContext(), R.string.update_canceled, Toast.LENGTH_LONG).show()
                }
            }
            Timber.d("details update event: ${intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)}")
        }
    }
}
