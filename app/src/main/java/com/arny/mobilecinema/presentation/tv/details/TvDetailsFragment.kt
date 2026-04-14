package com.arny.mobilecinema.presentation.tv.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.services.UpdateService
import androidx.fragment.app.DialogFragment
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
import com.arny.mobilecinema.presentation.tv.update.TvUpdateProgressDialogFragment
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsAction
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class EpisodeItem(
    val episode: SerialEpisode,
    val seasonIndex: Int,
    val episodeIndex: Int,
)

data class SeasonItem(
    val season: SerialSeason,
    val seasonIndex: Int,
)

data class SourceItem(
    val url: String,
    val label: String,
    val quality: String,
    val index: Int,
)

data class TagItem(
    val label: String,
    val query: String,
    val searchType: String,
)

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent, TvUpdateProgressDialogFragment.Callback {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
        private const val ACTION_UPDATE = 3L

        private const val POSTER_WIDTH = 274
        private const val POSTER_HEIGHT = 400

        private const val ROW_ID_SEASONS = 1001L
        private const val ROW_ID_EPISODES = 1002L
        private const val ROW_ID_SOURCES = 1003L
        private const val ROW_ID_GENRES = 1004L
        private const val ROW_ID_ACTORS = 1005L
    }

    private val viewModel: TvDetailsViewModel by viewModel()
    private val prefs: Prefs by inject()
    private val args: TvDetailsFragmentArgs by navArgs()

    private lateinit var detailsAdapter: ArrayObjectAdapter
    private var detailsRow: DetailsOverviewRow? = null

    private var currentMovie: Movie? = null
    private var sources: List<SourceItem> = emptyList()

    private var selectedSeasonIndex: Int = 0
    private var selectedEpisodeIndex: Int = 0
    private var selectedSourceIndex: Int = 0

    private var isUpdatingDb = false
    private var isCancellingUpdate = false

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAdapter()
        setupClickListeners()
        setupDialogResultListener()
        viewModel.loadMovie(args.movieId)
        observeData()
        observeActions()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    private fun buildAdapter() {
        val selector = ClassPresenterSelector()

        val overviewPresenter = FullWidthDetailsOverviewRowPresenter(
            TvDetailsDescriptionPresenter()
        ).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
            onActionClickedListener = OnActionClickedListener { action ->
                onActionClicked(action)
            }
        }

        selector.addClassPresenter(DetailsOverviewRow::class.java, overviewPresenter)
        selector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        detailsAdapter = ArrayObjectAdapter(selector)
        adapter = detailsAdapter
    }

    private fun setupClickListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is SeasonItem -> handleSeasonChanged(item.seasonIndex)
                is EpisodeItem -> {
                    selectedSeasonIndex = item.seasonIndex
                    selectedEpisodeIndex = item.episodeIndex
                    navigateToPlayer(item.seasonIndex, item.episodeIndex)
                }

                is SourceItem -> {
                    selectedSourceIndex = item.index
                    navigateToPlayerWithUrl(item.url)
                }

                is TagItem -> navigateToSearch(item.query, item.searchType)
            }
        }

        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, _ ->
            when (item) {
                is SeasonItem -> handleSeasonChanged(item.seasonIndex)
                is EpisodeItem -> {
                    selectedSeasonIndex = item.seasonIndex
                    selectedEpisodeIndex = item.episodeIndex
                }

                is SourceItem -> selectedSourceIndex = item.index
                is TagItem -> Unit
            }
        })
    }

    private fun setupDialogResultListener() {
        childFragmentManager.setFragmentResultListener(
            TvUpdateDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(TvUpdateDialogFragment.KEY_START_UPDATE, false)) {
                viewModel.updateMovieData(args.movieId)
            }
        }
    }

    private fun showUpdateDialog() {
        if (childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null) return
        TvUpdateDialogFragment.newInstance().show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }


    private fun showUpdateProgressDialog(progress: Int = -1, stage: String? = null) {
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

    private fun updateUpdateProgressDialog(progress: Int = -1, stage: String? = null) {
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
        val existing = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? DialogFragment

        existing?.dismissAllowingStateLoss()
    }

    override fun onCancelUpdateRequested() {
        if (isCancellingUpdate) return

        isCancellingUpdate = true
        isUpdatingDb = false

        val dialog = childFragmentManager.findFragmentByTag(
            TvUpdateProgressDialogFragment.TAG
        ) as? TvUpdateProgressDialogFragment
        dialog?.markAsCancelled()

        val intent = Intent(requireContext(), UpdateService::class.java).apply {
            action = AppConstants.ACTION_UPDATE_ALL_CANCEL
        }
        requireContext().startService(intent)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.movie.collectLatest { movie ->
                movie?.let {
                    currentMovie = it
                    bindMovie(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFavorite.collectLatest { isFav ->
                updateFavoriteLabel(isFav)
            }
        }
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actions.collect { action ->
                handleAction(action)
            }
        }
    }

    private fun handleAction(action: TvDetailsAction) {
        when (action) {
            is TvDetailsAction.ShowToast -> {
                Toast.makeText(
                    requireContext(),
                    action.message.toString(requireContext()),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is TvDetailsAction.ShowError -> {
                Toast.makeText(
                    requireContext(),
                    action.message.toString(requireContext()),
                    Toast.LENGTH_LONG
                ).show()
            }

            is TvDetailsAction.NavigateToUpdate -> {
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, action.url)
                }
            }
        }
    }

    private fun bindMovie(movie: Movie) {
        syncSelectionState(movie)

        val row = DetailsOverviewRow(movie)
        detailsRow = row

        row.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
        loadPoster(movie, row)

        val actions = SparseArrayObjectAdapter().apply {
            set(ACTION_PLAY.toInt(), Action(ACTION_PLAY, getString(R.string.play)))
            set(
                ACTION_FAVORITE.toInt(),
                Action(
                    ACTION_FAVORITE,
                    if (viewModel.isFavorite.value) getString(R.string.remove_from_favorites)
                    else getString(R.string.add_to_favorites)
                )
            )
            set(ACTION_UPDATE.toInt(), Action(ACTION_UPDATE, getString(R.string.update_data)))
        }
        row.actionsAdapter = actions

        detailsAdapter.clear()
        detailsAdapter.add(row)

        when (movie.type) {
            MovieType.SERIAL -> {
                if (movie.seasons.isNotEmpty()) {
                    addSeasonsRow(movie)
                    addEpisodesRow(movie)
                }
            }

            MovieType.CINEMA -> {
                sources = prepareSourcesList(movie)
                if (sources.isNotEmpty()) {
                    addSourcesRow(sources)
                }
            }

            else -> Unit
        }

        addSearchRows(movie)
    }

    private fun syncSelectionState(movie: Movie) {
        when (movie.type) {
            MovieType.SERIAL -> {
                val seasons = movie.seasons
                if (seasons.isEmpty()) {
                    selectedSeasonIndex = 0
                    selectedEpisodeIndex = 0
                    return
                }

                val maxSeason = seasons.lastIndex
                selectedSeasonIndex = (movie.seasonPosition ?: selectedSeasonIndex).coerceIn(0, maxSeason)

                val episodes = seasons.getOrNull(selectedSeasonIndex)?.episodes.orEmpty()
                selectedEpisodeIndex = if (episodes.isEmpty()) {
                    0
                } else {
                    val maxEpisode = episodes.lastIndex
                    (movie.episodePosition ?: selectedEpisodeIndex).coerceIn(0, maxEpisode)
                }
            }

            MovieType.CINEMA -> {
                val sourceCount = prepareSourcesList(movie).size
                selectedSourceIndex = if (sourceCount == 0) 0 else selectedSourceIndex.coerceIn(0, sourceCount - 1)
            }

            else -> Unit
        }
    }

    private fun loadPoster(movie: Movie, row: DetailsOverviewRow) {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        val fullUrl = movie.img.getWithDomain(baseUrl)

        if (fullUrl.isBlank()) return

        Glide.with(requireContext())
            .asBitmap()
            .load(fullUrl)
            .override(POSTER_WIDTH, POSTER_HEIGHT)
            .centerCrop()
            .error(R.drawable.placeholder_movie)
            .into(object : CustomTarget<Bitmap>(POSTER_WIDTH, POSTER_HEIGHT) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    row.imageDrawable = BitmapDrawable(resources, resource)
                    val idx = detailsAdapter.indexOf(row)
                    if (idx >= 0) {
                        detailsAdapter.notifyArrayItemRangeChanged(idx, 1)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    row.imageDrawable = errorDrawable
                        ?: ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    row.imageDrawable = placeholder
                }
            })
    }

    private fun prepareSourcesList(movie: Movie): List<SourceItem> {
        val cinemaUrlData: CinemaUrlData = movie.cinemaUrlData ?: return emptyList()

        val hdUrls = cinemaUrlData.hdUrl?.urls.orEmpty()
        val cinemaUrls = cinemaUrlData.cinemaUrl?.urls.orEmpty()
        val allUrls = (hdUrls + cinemaUrls).filter { it.isNotBlank() }.distinct()

        return allUrls.mapIndexed { index, url ->
            val isHD = index < hdUrls.size

            val afterProtocol = url.substringAfter("://")
            val host = afterProtocol.substringBefore("/")
            val extension = afterProtocol.substringAfterLast(".", "").take(4)

            val label = buildString {
                append(if (isHD) "HD" else "SD")
                append(" • ")

                val shortHost = if (host.length > 20) "${host.take(17)}..." else host
                append(shortHost)

                if (extension.isNotBlank()) {
                    append(" • ")
                    append(extension.uppercase())
                }
            }

            SourceItem(
                url = url,
                label = label,
                quality = if (isHD) "HD" else "",
                index = index,
            )
        }
    }

    private fun addSeasonsRow(movie: Movie) {
        val header = HeaderItem(ROW_ID_SEASONS, getString(R.string.seasons))
        val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())

        movie.seasons.forEachIndexed { seasonIndex, season ->
            rowAdapter.add(
                SeasonItem(
                    season = season,
                    seasonIndex = seasonIndex,
                )
            )
        }

        detailsAdapter.add(ListRow(header, rowAdapter))
    }

    private fun addEpisodesRow(movie: Movie) {
        val header = HeaderItem(ROW_ID_EPISODES, getString(R.string.episodes))
        val rowAdapter = ArrayObjectAdapter(TvEpisodeCardPresenter())

        val selectedSeason = movie.seasons.getOrNull(selectedSeasonIndex)
        selectedSeason?.episodes
            ?.sortedBy { it.episode }
            ?.forEachIndexed { episodeIndex, episode ->
                rowAdapter.add(
                    EpisodeItem(
                        episode = episode,
                        seasonIndex = selectedSeasonIndex,
                        episodeIndex = episodeIndex,
                    )
                )
            }

        detailsAdapter.add(ListRow(header, rowAdapter))
    }

    private fun addSourcesRow(sources: List<SourceItem>) {
        val header = HeaderItem(ROW_ID_SOURCES, getString(R.string.available_sources))
        val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())

        sources.forEach { source ->
            rowAdapter.add(source)
        }

        detailsAdapter.add(ListRow(header, rowAdapter))
    }

    private fun addSearchRows(movie: Movie) {
        val genres = movie.info.genres
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

        if (genres.isNotEmpty()) {
            val header = HeaderItem(ROW_ID_GENRES, getString(R.string.genres))
            val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())
            genres.forEach { genre ->
                rowAdapter.add(TagItem(label = genre, query = genre, searchType = AppConstants.SearchType.GENRES))
            }
            detailsAdapter.add(ListRow(header, rowAdapter))
        }

        val actors = movie.info.actors
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

        if (actors.isNotEmpty()) {
            val header = HeaderItem(ROW_ID_ACTORS, getString(R.string.actors))
            val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())
            actors.forEach { actor ->
                rowAdapter.add(TagItem(label = actor, query = actor, searchType = AppConstants.SearchType.ACTORS))
            }
            detailsAdapter.add(ListRow(header, rowAdapter))
        }
    }


    private fun onActionClicked(action: Action) {
        when (action.id) {
            ACTION_PLAY -> {
                val movie = currentMovie
                when (movie?.type) {
                    MovieType.CINEMA -> {
                        val selectedUrl = sources.getOrNull(selectedSourceIndex)?.url
                            ?: sources.firstOrNull()?.url

                        if (!selectedUrl.isNullOrBlank()) {
                            navigateToPlayerWithUrl(selectedUrl)
                        } else {
                            navigateToPlayer()
                        }
                    }

                    MovieType.SERIAL -> {
                        navigateToPlayer(selectedSeasonIndex, selectedEpisodeIndex)
                    }

                    else -> navigateToPlayer()
                }
            }

            ACTION_FAVORITE -> viewModel.toggleFavorite(args.movieId)
            ACTION_UPDATE -> showUpdateDialog()
        }
    }

    private fun handleSeasonChanged(newSeasonIndex: Int) {
        if (selectedSeasonIndex == newSeasonIndex) return

        selectedSeasonIndex = newSeasonIndex
        selectedEpisodeIndex = 0
        currentMovie?.let { bindMovie(it) }
    }

    private fun updateFavoriteLabel(isFav: Boolean) {
        val row = detailsRow ?: return
        val actions = row.actionsAdapter as? SparseArrayObjectAdapter ?: return
        actions.set(
            ACTION_FAVORITE.toInt(),
            Action(
                ACTION_FAVORITE,
                if (isFav) getString(R.string.remove_from_favorites)
                else getString(R.string.add_to_favorites)
            )
        )
    }

    private fun navigateToPlayer(seasonIndex: Int = 0, episodeIndex: Int = 0) {
        findNavController().navigate(
            R.id.tvPlayerFragment,
            bundleOf(
                "movieId" to args.movieId,
                "sharedUrl" to "",
                "seasonIndex" to seasonIndex,
                "episodeIndex" to episodeIndex,
            )
        )
    }

    private fun navigateToPlayerWithUrl(url: String) {
        findNavController().navigate(
            R.id.tvPlayerFragment,
            bundleOf(
                "movieId" to 0L,
                "sharedUrl" to url,
                "seasonIndex" to 0,
                "episodeIndex" to 0,
            )
        )
    }

    private fun navigateToSearch(query: String, searchType: String) {
        findNavController().navigate(
            R.id.tvSearchFragment,
            bundleOf(
                "query" to query,
                "searchType" to searchType
            )
        )
    }

    private fun makeBroadcastReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    isUpdatingDb = true
                    isCancellingUpdate = false
                    showUpdateProgressDialog(
                        progress = -1,
                        stage = getString(R.string.updating_all)
                    )
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    if (!isUpdatingDb || isCancellingUpdate) return

                    val percent = intent.getIntExtra("progress_percent", -1)
                    val percentText = if (percent in 0..100) "$percent%" else null

                    updateUpdateProgressDialog(
                        progress = percent,
                        stage = percentText,
                    )
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    isUpdatingDb = false
                    isCancellingUpdate = false
                    hideUpdateProgressDialog()
                    Toast.makeText(
                        requireContext(),
                        R.string.update_finished_success,
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.loadMovie(args.movieId)
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    isUpdatingDb = false
                    isCancellingUpdate = false
                    hideUpdateProgressDialog()
                    val errorMsg = intent.getStringExtra("error_message")
                        ?: getString(R.string.error_loading_data)
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }

                AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {
                    isUpdatingDb = false
                    isCancellingUpdate = false
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


