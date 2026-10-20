package com.arny.mobilecinema.presentation.tv.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.tv.update.TvUpdateDialogFragment
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
import timber.log.Timber

data class EpisodeItem(
    val episode: SerialEpisode,
    val seasonIndex: Int,
    val seasonNumber: Int,
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

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
        private const val ACTION_UPDATE = 3L

        private const val POSTER_WIDTH = 274
        private const val POSTER_HEIGHT = 400

        private const val ROW_ID_TOP = 1000L
        private const val ROW_ID_SEASONS = 1001L
        private const val ROW_ID_EPISODES = 1002L
        private const val ROW_ID_SOURCES = 1003L
        private const val ROW_ID_GENRES = 1004L
        private const val ROW_ID_ACTORS = 1005L
        private const val ROW_ID_UPDATE_PROGRESS = 1006L

        /** Строка прогресса обновления — сразу под шапкой, иначе она не видна. */
        private const val UPDATE_PROGRESS_ROW_INDEX = 1

        private const val AUTO_UPDATE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
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

    /** Последний выбранный (сфокусированный) элемент списка — нужен для кликов по карточкам. */
    private var lastSelectedItem: Any? = null

    private var isUpdatingDb = false
    private var isCancellingUpdate = false
    private var autoUpdateRequestedForUrl: String? = null
    private var currentUpdatePercent: Int = -1
    private var currentUpdateStage: String? = null

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // ВАЖНО: Leanback не даёт менять click/select-слушатели после создания вида,
        // поэтому сначала слушатели, затем адаптер.
        setupClickListeners()
        buildAdapter()
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
            // Цвет фона для описания
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)

            // Закрашиваем стандартную серую полосу под кнопками в цвет фона (или Color.TRANSPARENT)
            actionsBackgroundColor = ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)

            // Слушатель кликов остается нативным
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
                is EpisodeItem -> onEpisodeClicked(item)

                is SourceItem -> {
                    selectedSourceIndex = item.index
                    navigateToPlayerWithUrl(item.url)
                }

                is TagItem -> navigateToSearch(item.query, item.searchType)
                is UpdateProgressItem -> onCancelUpdateRequested()
            }
            Timber.d("Details clicked: ${item::class.simpleName}")
        }

        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, _ ->
            lastSelectedItem = item
            when (item) {
                is SeasonItem -> handleSeasonChanged(item.seasonIndex)
                is EpisodeItem -> {
                    selectedSeasonIndex = item.seasonIndex
                    selectedEpisodeIndex = item.episodeIndex
                    selectedSourceIndex = 0
                    updateSourcesRow(currentMovie)
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

    private fun showUpdateDialog(movie: Movie? = currentMovie) {
        if (childFragmentManager.findFragmentByTag(TvUpdateDialogFragment.TAG) != null) return

        val title = getString(R.string.update_attention)
        val message = movie?.title
            ?.takeIf { it.isNotBlank() }
            ?.let { movieTitle -> "$movieTitle\n\n${getString(R.string.update_description)}" }
            ?: getString(R.string.update_description)

        TvUpdateDialogFragment.newInstance(
            title = title,
            message = message
        ).show(childFragmentManager, TvUpdateDialogFragment.TAG)
    }

    private fun createUpdateProgressRow(): ListRow {
        val header = HeaderItem(ROW_ID_UPDATE_PROGRESS, getString(R.string.update_data))
        val rowAdapter = ArrayObjectAdapter(TvUpdateProgressCardPresenter())
        rowAdapter.add(UpdateProgressItem(currentUpdatePercent, currentUpdateStage))
        return ListRow(header, rowAdapter)
    }

    private fun findUpdateProgressRowIndex(): Int =
        (0 until detailsAdapter.size()).firstOrNull { index ->
            (detailsAdapter[index] as? ListRow)?.headerItem?.id == ROW_ID_UPDATE_PROGRESS
        } ?: -1

    private fun addUpdateProgressRow() {
        if (findUpdateProgressRowIndex() >= 0) return
        // Строка прогресса должна быть видна сразу, под шапкой, а не в конце списка.
        val index = UPDATE_PROGRESS_ROW_INDEX.coerceAtMost(detailsAdapter.size())
        detailsAdapter.add(index, createUpdateProgressRow())
    }

    private fun refreshUpdateProgressRow() {
        val index = findUpdateProgressRowIndex()
        if (index >= 0) {
            detailsAdapter.replace(index, createUpdateProgressRow())
        } else {
            addUpdateProgressRow()
        }
        updatePlayActionState()
    }

    private fun hideUpdateProgressRow() {
        val index = findUpdateProgressRowIndex()
        if (index >= 0) {
            detailsAdapter.removeItems(index, 1)
        }
    }

    private fun onCancelUpdateRequested() {
        if (isCancellingUpdate) return

        isCancellingUpdate = true
        isUpdatingDb = false
        updatePlayActionState()
        hideUpdateProgressRow()

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
                requestMovieUpdate(action.url, showProgressDialog = true)
            }
        }
    }

    private fun bindMovie(movie: Movie) {
        syncSelectionState(movie)

        val row = DetailsOverviewRow(movie)
        detailsRow = row

        row.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
        loadPoster(movie, row)

        val actionAdapter = ArrayObjectAdapter(TvActionCardPresenter())

        // Добавляем стандартные Leanback Action вместо кастомных
        actionAdapter.add(Action(ACTION_PLAY, getString(R.string.play)))
        actionAdapter.add(Action(ACTION_FAVORITE, getFavoriteText(viewModel.isFavorite.value)))
        actionAdapter.add(Action(ACTION_UPDATE, getString(R.string.update_data)))

        // Привязываем адаптер действий к нативной строке
        row.actionsAdapter = actionAdapter

        detailsAdapter.clear()
        detailsAdapter.add(row)

        if (isUpdatingDb) {
            addUpdateProgressRow()
        }

        when (movie.type) {
            MovieType.SERIAL -> {
                if (movie.seasons.isNotEmpty()) {
                    addSeasonsRow(movie)
                    addEpisodesRow(movie)
                    sources = prepareSourcesList(movie)
                    // HD-источник (dash) идёт первым — играем его по умолчанию,
                    // как и у фильмов. Если HD нет, останется обычная ссылка.
                    selectedSourceIndex = 0
                    if (sources.isNotEmpty()) {
                        addSourcesRow(sources)
                    }
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
        updatePlayActionState()
        view?.post { showUpdateDialogIfNeeded(movie) }
    }

    private fun showUpdateDialogIfNeeded(movie: Movie) {
        val pageUrl = movie.pageUrl
        if (pageUrl.isBlank()) return
        if (isUpdatingDb || autoUpdateRequestedForUrl == pageUrl) return
        if (!movie.isDataOutdated() && movie.hasPlayableLinks()) return

        autoUpdateRequestedForUrl = pageUrl
        showUpdateDialog(movie)
    }

    private fun Movie.isDataOutdated(): Boolean {
        val fetchedAt = info.detailsFetchedAt
        return fetchedAt <= 0L || System.currentTimeMillis() - fetchedAt > AUTO_UPDATE_MAX_AGE_MS
    }

    private fun Movie.hasPlayableLinks(): Boolean = when (type) {
        MovieType.CINEMA -> prepareSourcesList(this).isNotEmpty()
        MovieType.SERIAL -> seasons.any { season ->
            season.episodes.any { episode ->
                episode.hls.isNotBlank() || episode.dash.isNotBlank()
            }
        }
        else -> false
    }

    private fun requestMovieUpdate(url: String, showProgressDialog: Boolean) {
        isUpdatingDb = true
        isCancellingUpdate = false
        currentUpdatePercent = -1
        currentUpdateStage = null
        if (showProgressDialog) {
            addUpdateProgressRow()
        }
        updatePlayActionState()
        requireContext().sendServiceMessage(
            Intent(requireContext().applicationContext, UpdateService::class.java),
            AppConstants.ACTION_UPDATE_BY_URL
        ) {
            putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
            // Разбор начат с экрана деталей — TV Home не должен сам открывать карточку.
            putBoolean(AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND, true)
        }
    }

    private fun syncSelectionState(movie: Movie) {
        when (movie.type) {
            MovieType.SERIAL -> {
                val seasons = sortedSeasons(movie)
                if (seasons.isEmpty()) {
                    selectedSeasonIndex = 0
                    selectedEpisodeIndex = 0
                    return
                }

                val maxSeason = seasons.lastIndex
                selectedSeasonIndex =
                    (movie.seasonPosition ?: selectedSeasonIndex).coerceIn(0, maxSeason)

                val episodes = sortedEpisodes(seasons.getOrNull(selectedSeasonIndex)?.episodes.orEmpty())
                selectedEpisodeIndex = if (episodes.isEmpty()) {
                    0
                } else {
                    val maxEpisode = episodes.lastIndex
                    (movie.episodePosition ?: selectedEpisodeIndex).coerceIn(0, maxEpisode)
                }
            }

            MovieType.CINEMA -> {
                val sourceCount = prepareSourcesList(movie).size
                selectedSourceIndex =
                    if (sourceCount == 0) 0 else selectedSourceIndex.coerceIn(0, sourceCount - 1)
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
                    row.imageDrawable = resource.toDrawable(resources)
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

    private fun prepareSourcesList(movie: Movie): List<SourceItem> = when (movie.type) {
        MovieType.CINEMA -> prepareCinemaSourcesList(movie)
        MovieType.SERIAL -> prepareSerialEpisodeSourcesList(movie)
        else -> emptyList()
    }

    private fun prepareCinemaSourcesList(movie: Movie): List<SourceItem> {
        val cinemaUrlData: CinemaUrlData = movie.cinemaUrlData ?: return emptyList()

        val hdUrls = cinemaUrlData.hdUrl?.urls.orEmpty().filter { it.isNotBlank() }
        val cinemaUrls = cinemaUrlData.cinemaUrl?.urls.orEmpty().filter { it.isNotBlank() }
        val allUrls = (hdUrls + cinemaUrls).distinct()

        return allUrls.mapIndexed { index, url ->
            val isHD = url in hdUrls
            createSourceItem(index, url, isHD)
        }
    }

    private fun prepareSerialEpisodeSourcesList(movie: Movie): List<SourceItem> {
        val episode = sortedSeasons(movie)
            .getOrNull(selectedSeasonIndex)
            ?.episodes
            ?.let { sortedEpisodes(it) }
            ?.getOrNull(selectedEpisodeIndex)
            ?: return emptyList()

        val urls = listOf(
            true to episode.dash,
            false to episode.hls
        ).filter { (_, url) -> url.isNotBlank() }
            .distinctBy { (_, url) -> url }

        return urls.mapIndexed { index, (isHD, url) -> createSourceItem(index, url, isHD) }
    }

    private fun createSourceItem(index: Int, url: String, isHD: Boolean): SourceItem {
        val label = "${if (isHD) "HD" else "SD"} • ${url.toShortSourceLabel()}"
        return SourceItem(
            url = url,
            label = label,
            quality = if (isHD) "HD" else "",
            index = index,
        )
    }

    private fun String.toShortSourceLabel(): String {
        val urlWithoutQuery = substringBefore("?")
        val afterProtocol = urlWithoutQuery.substringAfter("://")
        val host = afterProtocol.substringBefore("/")
        val extension = urlWithoutQuery.substringAfterLast(".", "link").uppercase()
        return "$host • $extension"
    }

    private fun addSeasonsRow(movie: Movie) {
        val header = HeaderItem(ROW_ID_SEASONS, getString(R.string.seasons))
        val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())

        sortedSeasons(movie).forEachIndexed { seasonIndex, season ->
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
        detailsAdapter.add(createEpisodesRow(movie))
    }

    private fun createEpisodesRow(movie: Movie): ListRow {
        val header = HeaderItem(ROW_ID_EPISODES, getString(R.string.episodes))
        val rowAdapter = ArrayObjectAdapter(TvEpisodeCardPresenter { item -> onEpisodeClicked(item) })

        val selectedSeason = sortedSeasons(movie).getOrNull(selectedSeasonIndex)
        val seasonNumber = selectedSeason?.id ?: (selectedSeasonIndex + 1)
        sortedEpisodes(selectedSeason?.episodes.orEmpty())
            .forEachIndexed { episodeIndex, episode ->
                rowAdapter.add(
                    EpisodeItem(
                        episode = episode,
                        seasonIndex = selectedSeasonIndex,
                        seasonNumber = seasonNumber,
                        episodeIndex = episodeIndex,
                    )
                )
            }

        return ListRow(header, rowAdapter)
    }

    /** Ряд серий выбранного сезона без перестройки всего адаптера. */
    private fun updateEpisodesRow(movie: Movie) {
        val rowIndex = findRowIndex(ROW_ID_EPISODES)
        if (rowIndex != null) {
            detailsAdapter.replace(rowIndex, createEpisodesRow(movie))
        } else {
            addEpisodesRow(movie)
        }
    }

    private fun findRowIndex(headerId: Long): Int? =
        (0 until detailsAdapter.size()).firstOrNull { index ->
            (detailsAdapter[index] as? ListRow)?.headerItem?.id == headerId
        }

    /** Сезоны всегда по возрастанию номера — так же, как в мобильном приложении и в плеере. */
    private fun sortedSeasons(movie: Movie): List<SerialSeason> = movie.seasons.sortedBy { it.id }

    /** Номер серии сравнивается численно: иначе «10» попадает между «1» и «2». */
    private fun sortedEpisodes(episodes: List<SerialEpisode>): List<SerialEpisode> =
        episodes.sortedBy { findByGroup(it.episode, "(\\d+)".toRegex(), 1)?.toIntOrNull() ?: 0 }

    private fun addSourcesRow(sources: List<SourceItem>) {
        // Ряд источников держим сразу после серий, а не в конце списка.
        val episodesRowIndex = findRowIndex(ROW_ID_EPISODES)
        if (episodesRowIndex != null) {
            detailsAdapter.add(episodesRowIndex + 1, createSourcesRow(sources))
        } else {
            detailsAdapter.add(createSourcesRow(sources))
        }
    }

    private fun createSourcesRow(sources: List<SourceItem>): ListRow {
        val header = HeaderItem(ROW_ID_SOURCES, getString(R.string.available_sources))
        val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())

        sources.forEach { source ->
            rowAdapter.add(source)
        }

        return ListRow(header, rowAdapter)
    }

    private fun updateSourcesRow(movie: Movie?) {
        if (movie?.type != MovieType.SERIAL) return
        sources = prepareSourcesList(movie)
        selectedSourceIndex = selectedSourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
        val rowIndex = findRowIndex(ROW_ID_SOURCES)
        when {
            rowIndex != null && sources.isNotEmpty() -> detailsAdapter.replace(rowIndex, createSourcesRow(sources))
            rowIndex != null -> detailsAdapter.removeItems(rowIndex, 1)
            sources.isNotEmpty() -> addSourcesRow(sources)
        }
    }

    private fun addSearchRows(movie: Movie) {
        val genres = movie.info.genres
            .asSequence()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .toList()

        if (genres.isNotEmpty()) {
            val header = HeaderItem(ROW_ID_GENRES, getString(R.string.genres))
            val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())
            genres.forEach { genre ->
                rowAdapter.add(
                    TagItem(
                        label = genre,
                        query = genre,
                        searchType = AppConstants.SearchType.GENRES
                    )
                )
            }
            detailsAdapter.add(ListRow(header, rowAdapter))
        }

        val actors = movie.info.actors
            .asSequence()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .toList()

        if (actors.isNotEmpty()) {
            val header = HeaderItem(ROW_ID_ACTORS, getString(R.string.actors))
            val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())
            actors.forEach { actor ->
                rowAdapter.add(
                    TagItem(
                        label = actor,
                        query = actor,
                        searchType = AppConstants.SearchType.ACTORS
                    )
                )
            }
            detailsAdapter.add(ListRow(header, rowAdapter))
        }
    }

    private fun onActionClicked(action: Action) {
        when (action.id) {
            ACTION_PLAY -> {
                // Блокируем только если обновление идёт и нет ни одной играбельной ссылки
                if (isUpdatingDb && currentMovie?.hasPlayableLinks() == false) {
                    Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
                    return
                }

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
                        // Источник, выбранный в строке «Доступные источники» (в т.ч. HD),
                        // важнее дефолтного SD: иначе выбор HD-ссылки игнорировался.
                        val selectedUrl = sources.getOrNull(selectedSourceIndex)?.url
                            ?: sources.firstOrNull { it.quality != "HD" }?.url
                            ?: sources.firstOrNull()?.url
                        navigateToPlayer(selectedSeasonIndex, selectedEpisodeIndex, selectedUrl.orEmpty())
                    }

                    else -> navigateToPlayer()
                }
            }

            ACTION_FAVORITE -> viewModel.toggleFavorite(args.movieId)
            ACTION_UPDATE -> {
                if (isUpdatingDb) {
                    // Строка прогресса может быть ниже свёрнутой панели — показываем статус сразу.
                    val percent = currentUpdatePercent.takeIf { it in 0..100 }?.let { " $it%" }.orEmpty()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.updating_data) + percent,
                        Toast.LENGTH_SHORT
                    ).show()
                    addUpdateProgressRow()
                } else {
                    showUpdateDialog(currentMovie)
                }
            }
        }
    }

    private fun onEpisodeClicked(item: EpisodeItem?) {
        // Презентер может не знать свой item (Leanback чистит ViewHolder при rebind),
        // поэтому берём текущий сфокусированный эпизод.
        val episodeItem = item ?: (lastSelectedItem as? EpisodeItem)
        episodeItem ?: return
        selectedSeasonIndex = episodeItem.seasonIndex
        selectedEpisodeIndex = episodeItem.episodeIndex
        selectedSourceIndex = 0
        Timber.d("Episode clicked: season=${episodeItem.seasonIndex}, episode=${episodeItem.episodeIndex}")
        navigateToPlayer(episodeItem.seasonIndex, episodeItem.episodeIndex)
    }

    private fun handleSeasonChanged(newSeasonIndex: Int) {
        if (selectedSeasonIndex == newSeasonIndex) return

        selectedSeasonIndex = newSeasonIndex
        selectedEpisodeIndex = 0
        val movie = currentMovie ?: return

        // Вызывается и из onItemViewSelectedListener (во время скролла), поэтому обновляем
        // только ряды серий и источников, а не перестраиваем весь адаптер: полный
        // bindMovie здесь падал с IllegalStateException и сбрасывал скролл наверх.
        updateEpisodesRow(movie)
        updateSourcesRow(movie)
    }

    private fun updateFavoriteLabel(isFav: Boolean) {
        val adapter = detailsRow?.actionsAdapter as? ArrayObjectAdapter ?: return

        val favoriteIndex = (0 until adapter.size()).firstOrNull { index ->
            (adapter[index] as? Action)?.id == ACTION_FAVORITE
        } ?: return

        val action = adapter[favoriteIndex] as Action
        action.label1 = getFavoriteText(isFav)

        adapter.notifyArrayItemRangeChanged(favoriteIndex, 1)
    }

    private fun updatePlayActionState() {
        val adapter = detailsRow?.actionsAdapter as? ArrayObjectAdapter ?: return

        // Блокируем (меняем label) только если обновление идёт и нет ни одной играбельной ссылки
        val block = isUpdatingDb && currentMovie?.hasPlayableLinks() == false
        val playIndex = (0 until adapter.size()).firstOrNull { index ->
            (adapter[index] as? Action)?.id == ACTION_PLAY
        }
        if (playIndex != null) {
            val action = adapter[playIndex] as Action
            action.label1 = getString(if (block) R.string.please_wait else R.string.play)
            adapter.notifyArrayItemRangeChanged(playIndex, 1)
        }

        // Кнопка «Обновить данные» — единственный всегда видимый индикатор обновления.
        val updateIndex = (0 until adapter.size()).firstOrNull { index ->
            (adapter[index] as? Action)?.id == ACTION_UPDATE
        }
        if (updateIndex != null) {
            val updateAction = adapter[updateIndex] as Action
            updateAction.label1 = if (isUpdatingDb) {
                val percent = currentUpdatePercent.takeIf { it in 0..100 }?.let { " $it%" }.orEmpty()
                getString(R.string.updating_data) + percent
            } else {
                getString(R.string.update_data)
            }
            adapter.notifyArrayItemRangeChanged(updateIndex, 1)
        }
    }

    private fun getFavoriteText(value: Boolean): String = if (value) {
        getString(R.string.remove_from_favorites)
    } else {
        getString(R.string.add_to_favorites)
    }

    private fun navigateToPlayer(
        seasonIndex: Int = 0,
        episodeIndex: Int = 0,
        selectedUrl: String = ""
    ) {
        // Блокируем только если обновление идёт и нет ни одной играбельной ссылки
        if (isUpdatingDb && currentMovie?.hasPlayableLinks() == false) {
            Timber.w("navigateToPlayer blocked: updating=${isUpdatingDb}")
            Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
            return
        }
        Timber.d("navigateToPlayer: season=$seasonIndex, episode=$episodeIndex, url=${selectedUrl.take(40)}")

        findNavController().navigate(
            R.id.tvPlayerFragment,
            bundleOf(
                "movieId" to args.movieId,
                "sharedUrl" to selectedUrl,
                "seasonIndex" to seasonIndex,
                "episodeIndex" to episodeIndex,
            )
        )
    }

    private fun navigateToPlayerWithUrl(url: String) {
        // Блокируем только если обновление идёт и нет ни одной играбельной ссылки
        if (isUpdatingDb && currentMovie?.hasPlayableLinks() == false) {
            Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
            return
        }

        findNavController().navigate(
            R.id.tvPlayerFragment,
            bundleOf(
                "movieId" to args.movieId,
                "sharedUrl" to url,
                "seasonIndex" to selectedSeasonIndex,
                "episodeIndex" to selectedEpisodeIndex,
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
                    currentUpdatePercent = -1
                    currentUpdateStage = null
                    updatePlayActionState()
                    refreshUpdateProgressRow()
                }

                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    if (!isUpdatingDb || isCancellingUpdate) return

                    val percent = intent.getIntExtra("progress_percent", -1)
                    currentUpdatePercent = percent
                    currentUpdateStage = intent.getStringExtra("update_title")
                    refreshUpdateProgressRow()
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    isUpdatingDb = false
                    isCancellingUpdate = false
                    currentUpdatePercent = -1
                    currentUpdateStage = null
                    updatePlayActionState()
                    hideUpdateProgressRow()
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
                    currentUpdatePercent = -1
                    currentUpdateStage = null
                    updatePlayActionState()
                    hideUpdateProgressRow()
                    val errorMsg = intent.getStringExtra("error_message")
                        ?: getString(R.string.error_loading_data)
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }

                AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {
                    isUpdatingDb = false
                    isCancellingUpdate = false
                    currentUpdatePercent = -1
                    currentUpdateStage = null
                    updatePlayActionState()
                    hideUpdateProgressRow()
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


