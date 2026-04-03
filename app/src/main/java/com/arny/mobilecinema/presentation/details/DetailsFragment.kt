package com.arny.mobilecinema.presentation.details

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.DFeedbackLayoutBinding
import com.arny.mobilecinema.databinding.FDetailsBinding
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieDownloadedData
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.RequestDownloadFile
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.services.MovieDownloadService
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.copyToClipboard
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.getDuration
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.makeTextViewResizable
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.printTime
import com.arny.mobilecinema.presentation.utils.registerReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.unregisterReceiver
import com.arny.mobilecinema.presentation.utils.updateSpinnerItems
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import org.joda.time.DateTime
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

class DetailsFragment : Fragment(R.layout.f_details) {

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(@Assisted("id") id: Long): DetailsViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val args: DetailsFragmentArgs by navArgs()

    private val viewModel: DetailsViewModel by viewModelFactory {
        viewModelFactory.create(args.id)
    }

    @Inject
    lateinit var playerSource: PlayerSource

    @Inject
    lateinit var prefs: Prefs

    // ViewBinding
    private var _binding: FDetailsBinding? = null
    private val binding get() = _binding!!

    // Adapters
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var linksAdapter: TrackSelectorSpinnerAdapter? = null

    // State (локальные копии для UI логики)
    private var currentMovie: Movie? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private var currentLinkPosition: Int = 0
    private var hasSavedData: Boolean = false
    private var downloadAll: Boolean = false
    private var canDownload: Boolean = false
    private var feedbackText = ""

    // Touch flags для Spinners
    private var isUserTouchSeasons = false
    private var isUserTouchEpisodes = false
    private var isUserTouchLinks = false

    // ДОБАВЛЕНО: Флаг для отслеживания пересоздания View
    private var needsUIRefresh = true

    private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (isUserTouchSeasons) {
                updateCurrentSerialPosition()
                currentEpisodePosition = 0
                fillSpinners(currentMovie)
                viewModel.handleEvent(
                    DetailsEvent.SerialPositionChanged(
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                )
                isUserTouchSeasons = false
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            isUserTouchSeasons = false
        }
    }

    private val episodesChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (isUserTouchEpisodes) {
                updateCurrentSerialPosition()
                viewModel.handleEvent(
                    DetailsEvent.SerialPositionChanged(
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                )
                isUserTouchEpisodes = false
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            isUserTouchEpisodes = false
        }
    }

    private val linksChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (isUserTouchLinks) {
                currentLinkPosition = position
                currentMovie?.let { curMovie ->
                    val items = getCinemaUrlsItems(curMovie)
                    val url = items.getOrNull(position)?.second
                    viewModel.handleEvent(DetailsEvent.SelectedUrlChanged(url, true))
                }
                isUserTouchLinks = false
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            isUserTouchLinks = false
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.handleEvent(DetailsEvent.InvalidateCache)
        }
    }

    private val downloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.handleEvent(
                DetailsEvent.UpdateDownloadProgress(
                    pageUrl = intent?.getStringExtra(AppConstants.SERVICE_PARAM_CACHE_MOVIE_PAGE_URL)
                        .orEmpty(),
                    percent = intent?.getFloatExtra(AppConstants.SERVICE_PARAM_PERCENT, 0.0f),
                    bytes = intent?.getLongExtra(AppConstants.SERVICE_PARAM_BYTES, 0L),
                    updateSeason = intent?.getIntExtra(AppConstants.SERVICE_PARAM_CACHE_SEASON, 0)
                        ?: 0,
                    updateEpisode = intent?.getIntExtra(AppConstants.SERVICE_PARAM_CACHE_EPISODE, 0)
                        ?: 0
                )
            )
        }
    }

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // View пересоздано - нужно обновить UI
        needsUIRefresh = true
        initToolbar()
        initVariables()
        initListeners()
        initSpinnerAdapters()
        observeState()
        observeActions()
        initMenu()
        // Загрузка происходит в init ViewModel
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(AppConstants.ACTION_CACHE_VIDEO_COMPLETE, downloadReceiver)
        registerReceiver(AppConstants.ACTION_CACHE_VIDEO_UPDATE, downloadUpdateReceiver)
        registerReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)

        // Инвалидируем кэш при возврате на экран
        viewModel.handleEvent(DetailsEvent.InvalidateCache)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(downloadReceiver)
        unregisterReceiver(downloadUpdateReceiver)
        unregisterReceiver(updateReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initVariables() {
        // Инициализация системных сервисов
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSpinnerAdapters() {
        val context = requireContext()
        with(binding) {
            seasonsTracksAdapter = TrackSelectorSpinnerAdapter(context)
            episodesTracksAdapter = TrackSelectorSpinnerAdapter(context)
            linksAdapter = TrackSelectorSpinnerAdapter(context)

            spinSeasons.adapter = seasonsTracksAdapter
            spinEpisodes.adapter = episodesTracksAdapter
            spinLinks.adapter = linksAdapter

            spinSeasons.setOnTouchListener { _, _ ->
                isUserTouchSeasons = true
                false
            }
            spinEpisodes.setOnTouchListener { _, _ ->
                isUserTouchEpisodes = true
                false
            }
            spinLinks.setOnTouchListener { _, _ ->
                isUserTouchLinks = true
                false
            }
        }
    }

    private fun initListeners() {
        with(binding) {
            btnPlay.setOnClickListener {
                playMovie()
            }
            ivFavorite.setOnClickListener {
                viewModel.handleEvent(DetailsEvent.ToggleFavorite)
            }
        }
    }

    private fun initMenu() {
        // БЫЛО: requireActivity().addMenuProvider(...)
        // СТАЛО: используем свой toolbar
        binding.toolbar.inflateMenu(R.menu.details_menu)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_action_download_file -> {
                    onDownloadFile()
                    true
                }

                R.id.menu_action_cache_movie -> {
                    onCache()
                    true
                }

                R.id.menu_action_clear_cache -> {
                    viewModel.handleEvent(
                        DetailsEvent.ClearCache(
                            seasonPosition = currentSeasonPosition,
                            episodePosition = currentEpisodePosition,
                            clearViewHistory = true
                        )
                    )
                    true
                }

                R.id.menu_action_send_feedback -> {
                    showFeedbackDialog()
                    true
                }

                R.id.menu_action_copy_mp4_link -> {
                    showCopyMp4LinkDialog()
                    true
                }

                R.id.menu_action_update_data -> {
                    viewModel.handleEvent(DetailsEvent.ShowUpdateDialog)
                    true
                }

                else -> false
            }
        }
    }

    // Обновление видимости пунктов меню
    private fun updateMenuVisibility() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.menu_action_cache_movie)?.isVisible = canDownload
        menu.findItem(R.id.menu_action_clear_cache)?.isVisible = hasSavedData
    }

    private fun observeState() {
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun observeActions() {
        launchWhenCreated {
            viewModel.actions.collect { action ->
                handleAction(action)
            }
        }
    }

    private fun renderState(state: DetailsUiState) {
        binding.progressBar.isVisible = state.isLoading

        state.movie?.let { movie ->
            // ИСПРАВЛЕНО: Обновляем UI если:
            // 1. Другой фильм
            // 2. View было пересоздано (например, при возврате с backstack)
            val shouldRefreshUI = currentMovie?.dbId != movie.dbId || needsUIRefresh

            if (shouldRefreshUI) {
                needsUIRefresh = false
                onMovieLoaded(movie)
            }

            // Всегда обновляем спиннеры если позиция изменилась
            updateSpinnerPositionsIfNeeded(state)
        }

        updateDownloadedData(state.downloadedData)

        canDownload = state.canDownload
        hasSavedData = state.hasSavedData

        binding.ivFavorite.setImageResource(
            if (state.isInFavorite) R.drawable.baseline_favorite_24_filled else R.drawable.outline_favorite_24
        )

        updateMenuVisibility()
    }

    /**
     * Обновляет позиции спиннеров если они изменились (например, после просмотра)
     */
    private fun updateSpinnerPositionsIfNeeded(state: DetailsUiState) {
        val seasonChanged = currentSeasonPosition != state.currentSeasonPosition
        val episodeChanged = currentEpisodePosition != state.currentEpisodePosition

        if (seasonChanged || episodeChanged) {
            currentSeasonPosition = state.currentSeasonPosition
            currentEpisodePosition = state.currentEpisodePosition

            // Обновляем спиннеры без вызова listeners
            updateSpinnerSelectionsWithoutTrigger()
        }
    }

    /**
     * Обновляет выбор в спиннерах без триггера onChange
     */
    private fun updateSpinnerSelectionsWithoutTrigger() {
        val movie = currentMovie ?: return

        if (movie.type != MovieType.SERIAL) return

        with(binding) {
            // Временно убираем listeners
            spinSeasons.onItemSelectedListener = null
            spinEpisodes.onItemSelectedListener = null

            // Обновляем сезон
            if (spinSeasons.selectedItemPosition != currentSeasonPosition) {
                spinSeasons.setSelection(currentSeasonPosition, false)
            }

            // Обновляем список эпизодов для выбранного сезона
            val episodes = movie.seasons
                .getOrNull(currentSeasonPosition)
                ?.episodes.orEmpty()
            episodesTracksAdapter?.clear()
            episodesTracksAdapter?.addAll(sortEpisodes(episodes))

            // Обновляем эпизод
            if (spinEpisodes.selectedItemPosition != currentEpisodePosition) {
                spinEpisodes.setSelection(currentEpisodePosition, false)
            }

            // Возвращаем listeners
            spinSeasons.onItemSelectedListener = seasonsChangeListener
            spinEpisodes.onItemSelectedListener = episodesChangeListener
        }
    }

    private fun handleAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.ShowToast -> {
                toast(action.message.toString(requireContext()))
            }

            is DetailsAction.ShowError -> {
                toast(action.message.toString(requireContext()))
            }

            is DetailsAction.ShowAlert -> {
                showAlert(action.alert)
            }

            is DetailsAction.NavigateToUpdate -> {
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, action.url)
                }
            }

            is DetailsAction.RequestDownload -> {
                downloadFile(action.file)
            }

            is DetailsAction.HistoryAdded -> {
                toast(getString(R.string.added_to_history))
            }

            is DetailsAction.NavigateBack -> {
                findNavController().popBackStack()
            }

            is DetailsAction.CopyMp4Links -> {
                requireContext().copyToClipboard(
                    action.text,
                    getString(R.string.movie_mp4_links_label)
                )
            }
        }
    }

    private fun onMovieLoaded(movie: Movie) {
        currentMovie = movie
        updateSpinData(movie)
        initUI(movie)
        initButtons(movie)
    }

    private fun initUI(movie: Movie) {
        with(binding) {
            val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()

            // Загружаем картинку
            Glide.with(this@DetailsFragment)
                .load(movie.img.getWithDomain(baseUrl))
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .into(binding.ivBanner)

            updateTitle(movie.title)

            val info = movie.info

            // Рейтинги в виде бейджей
            if (info.ratingImdb > 0) {
                tvImdbBadge.isVisible = true
                tvImdbBadge.text = buildString {
                    append("%.1f")
                }.format(Locale.US, info.ratingImdb)
            } else {
                tvImdbBadge.isVisible = false
            }

            if (info.ratingKP > 0) {
                tvKpBadge.isVisible = true
                tvKpBadge.text = buildString {
                    append("%.1f")
                }.format(Locale.US, info.ratingKP)
            } else {
                tvKpBadge.isVisible = false
            }

            tvLikes.text = buildString {
                append("👍 ")
                append(info.likes)
                append("  👎 ")
                append(info.dislikes)
            }

            // Название
            tvTitle.text = movie.title
            if (BuildConfig.DEBUG) {
                tvTitle.setOnLongClickListener {
                    toast(movie.pageUrl)
                    false
                }
            }

            // Год, тип, страна - одной строкой
            tvTypeYear.text = buildTypeYearString(movie)

            // Быстрые чипы
            tvDuration.isVisible = movie.type == MovieType.CINEMA && info.durationSec > 0
            if (tvDuration.isVisible) {
                tvDuration.text = getDuration(info.durationSec)
            }

            tvQuality.isVisible = movie.type == MovieType.CINEMA && info.quality.isNotBlank()
            if (tvQuality.isVisible) {
                tvQuality.text = info.quality
            }

            // Используем более короткий формат даты
            tvUpdated.text = DateTime(info.updated).printTime("dd.MM.yy")

            // Описание
            tvDescription.text = info.description
            tvDescription.makeTextViewResizable()

            initGenres(info.genres)
            initDirectors(info.directors)
            initActors(info.actors)
        }
    }

    private fun buildTypeYearString(movie: Movie): String {
        val info = movie.info
        val parts = mutableListOf<String>()

        if (info.year > 0) {
            parts.add(info.year.toString())
        }

        if (movie.type == MovieType.SERIAL) {
            val seasons = movie.seasons
            val episodesSize = seasons.sumOf { it.episodes.size }
            parts.add(getString(R.string.serial))
            parts.add(resources.getQuantityString(R.plurals.sezons, seasons.size, seasons.size))
            parts.add(resources.getQuantityString(R.plurals.episods, episodesSize, episodesSize))
        } else {
            parts.add(getString(R.string.cinema))
        }

        if (info.countries.isNotEmpty()) {
            parts.add(info.countries.joinToString(", "))
        }

        return parts.joinToString(" • ")
    }

    private fun FDetailsBinding.initDirectors(directors: List<String>) {
        chgrDirectors.removeAllViews()
        tvDirectors.isVisible = directors.any { it.isNotBlank() }

        for (director in directors.filter { it.isNotBlank() }) {
            val chip = createFilterChip(director) {
                findNavController().navigateSafely(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(director = director)
                )
            }
            chgrDirectors.addView(chip)
        }
    }

    private fun FDetailsBinding.initGenres(genres: List<String>) {
        chgrGenres.removeAllViews()
        val genresMap =
            genres.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }
        tvGenres.isVisible = genresMap.isNotEmpty()

        for (genre in genresMap) {
            val chip = createFilterChip(genre) {
                findNavController().navigateSafely(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(genre = genre)
                )
            }
            chgrGenres.addView(chip)
        }
    }

    /**
     * Создаёт кликабельный чип для фильтрации
     */
    private fun createFilterChip(text: String, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isClickable = true
            isCheckable = false

            // Светлый фон и текст для лучшей видимости
            setChipBackgroundColorResource(R.color.chip_bg)
            setTextColor(ContextCompat.getColor(context, R.color.chip_text))

            // Убираем stroke
            chipStrokeWidth = 0f

            // Компактный размер
            setEnsureMinTouchTargetSize(false)
            chipMinHeight = resources.getDimension(R.dimen.chip_min_height)

            setOnClickListener { onClick() }
        }
    }

    private fun FDetailsBinding.initActors(actors: List<String>) {
        chgrActors.removeAllViews()
        tvActors.isVisible = actors.any { it.isNotBlank() }

        for (actor in actors.filter { it.isNotBlank() }) {
            val chip = createFilterChip(actor) {
                findNavController().navigateSafely(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(actor = actor)
                )
            }
            chgrActors.addView(chip)
        }
    }

    private fun initButtons(movie: Movie, invalidate: Boolean = false) {
        with(binding) {
            if (invalidate) {
                viewModel.handleEvent(DetailsEvent.InvalidateCache)
            }

            if (movie.type == MovieType.CINEMA) {
                val urls = movie.cinemaUrlData?.cinemaUrl?.urls.orEmpty()
                val hdUrls = movie.cinemaUrlData?.hdUrl?.urls.orEmpty()
                btnPlay.isVisible = urls.isNotEmpty() || hdUrls.isNotEmpty()
            } else {
                val hasAnyLink = movie.seasons.any { season ->
                    season.episodes.any { episode ->
                        episode.hls.isNotBlank() || episode.dash.isNotBlank()
                    }
                }
                btnPlay.isVisible = hasAnyLink
            }
        }
    }

    private fun updateDownloadedData(data: MovieDownloadedData?) {
        with(binding) {
            val shouldShow = data != null && (data.loading || data.downloadedSize > 0L)
            cardSaveData.isVisible = shouldShow

            when {
                data == null -> {
                    cardSaveData.isVisible = false
                }

                data.loading -> {
                    tvSaveData.text = getString(R.string.cinema_save_data_invalidate)
                }

                data.downloadedPercent > 0.0f && data.downloadedSize > 0L -> {
                    val percentStr = "%.1f%%".format(Locale.getDefault(), data.downloadedPercent)
                    val sizeStr = formatFileSize(data.downloadedSize, 1)
                    val totalStr = if (data.total > 0L) {
                        " / ${formatFileSize(data.total, 1)}"
                    } else ""
                    tvSaveData.text = buildString {
                        append("Сохранён: ")
                        append(percentStr)
                        append(" • ")
                        append(sizeStr)
                        append(totalStr)
                    }
                }

                data.downloadedSize > 0L -> {
                    tvSaveData.text = buildString {
                        append("Сохранён: ")
                        append(formatFileSize(data.downloadedSize, 1))
                    }
                }
            }
        }
    }

    private fun updateCurrentSerialPosition() {
        currentSeasonPosition = binding.spinSeasons.selectedItemPosition
        currentEpisodePosition = binding.spinEpisodes.selectedItemPosition
    }

    private fun updateSpinData(movie: Movie) {
        with(binding) {
            if (movie.type == MovieType.SERIAL) {
                val seasonPosition = movie.seasonPosition
                val episodePosition = movie.episodePosition

                Timber.d("updateSpinData season:$currentSeasonPosition->$seasonPosition,episode:$currentEpisodePosition->$episodePosition")

                if (seasonPosition != null && episodePosition != null) {
                    currentSeasonPosition = seasonPosition
                    currentEpisodePosition = episodePosition
                }

                fillSpinners(movie)
                cardSeasons.isVisible = true
            } else {
                val popupItems = getCinemaUrlsItems(movie)
                val sizeMoreOne = popupItems.size > 1

                cardLinks.isVisible = sizeMoreOne

                if (sizeMoreOne) {
                    spinLinks.updateSpinnerItems(linksChangeListener)
                    linksAdapter?.clear()
                    linksAdapter?.addAll(popupItems.map { it.first })
                }

                if (popupItems.isNotEmpty()) {
                    viewModel.handleEvent(
                        DetailsEvent.SelectedUrlChanged(popupItems[0].second, false)
                    )
                }
            }
        }
    }

    private fun fillSpinners(movie: Movie?) {
        with(binding) {
            val seasons = movie?.seasons.orEmpty()
            val seasonsList = sortSeasons(seasons)

            if (seasonsList.isNotEmpty()) {
                spinSeasons.updateSpinnerItems(seasonsChangeListener)
                seasonsTracksAdapter?.clear()
                seasonsTracksAdapter?.addAll(seasonsList)
                spinSeasons.setSelection(currentSeasonPosition, false)

                spinEpisodes.updateSpinnerItems(episodesChangeListener)
                val episodes = seasons.getOrNull(currentSeasonPosition)?.episodes.orEmpty()
                episodesTracksAdapter?.clear()
                episodesTracksAdapter?.addAll(sortEpisodes(episodes))
                spinEpisodes.setSelection(currentEpisodePosition, false)
            }
        }
    }

    private fun sortSeasons(seasons: List<SerialSeason>): List<String> {
        return seasons.sortedBy { it.id }.map { getSeasonTitle(it) }
    }

    private fun sortEpisodes(episodes: List<SerialEpisode>): List<String> {
        return episodes.asSequence()
            .sortedBy { findByGroup(it.episode, "(\\d+)".toRegex(), 1)?.toIntOrNull() ?: 0 }
            .map { getEpisodeTitle(it) }
            .toList()
    }

    private fun getSeasonTitle(it: SerialSeason): String {
        return "%d %s".format(Locale.getDefault(), it.id, getString(R.string.spinner_season))
    }

    private fun getEpisodeTitle(it: SerialEpisode): String {
        return "%s %s".format(Locale.getDefault(), it.episode, getString(R.string.spinner_episode))
    }

    private fun getCinemaUrlsItems(movie: Movie): List<Pair<String, String>> {
        val cinemaUrlData = movie.cinemaUrlData
        val hdUrls = cinemaUrlData?.hdUrl?.urls.orEmpty()
        val cinemaUrls = cinemaUrlData?.cinemaUrl?.urls.orEmpty()
        val fullLinkList = (hdUrls + cinemaUrls).filter { it.isNotBlank() }

        return fullLinkList.mapIndexed { index, s ->
            getString(R.string.link_format, "${index + 1} (${s.substringAfterLast(".")})") to s
        }.takeIf {
            movie.type == MovieType.CINEMA
        }.orEmpty()
    }

    private fun onCache() {
        when (getConnectionType(requireContext())) {
            is ConnectionType.WIFI -> {
                viewModel.handleEvent(DetailsEvent.ShowCacheDialog)
            }

            is ConnectionType.MOBILE -> {
                alertDialog(
                    title = getString(R.string.attention),
                    content = getString(R.string.mobile_network_play),
                    btnOkText = getString(android.R.string.ok),
                    btnCancelText = getString(android.R.string.cancel),
                    onConfirm = {
                        viewModel.handleEvent(DetailsEvent.ShowCacheDialog)
                    }
                )
            }

            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }

            else -> {}
        }
    }

    private fun onDownloadFile() {
        when (getConnectionType(requireContext())) {
            is ConnectionType.WIFI -> {
                viewModel.handleEvent(DetailsEvent.ShowDownloadDialog)
            }

            is ConnectionType.MOBILE -> {
                alertDialog(
                    title = getString(R.string.attention),
                    content = getString(R.string.mobile_network_play),
                    btnOkText = getString(android.R.string.ok),
                    btnCancelText = getString(android.R.string.cancel),
                    onConfirm = {
                        viewModel.handleEvent(DetailsEvent.ShowDownloadDialog)
                    }
                )
            }

            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }

            else -> {}
        }
    }

    private fun showAlert(alert: Alert?) {
        when (val alertType = alert?.type) {
            is AlertType.Download -> {
                when {
                    alertType.complete -> {
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty()
                        )
                    }

                    alertType.empty -> {
                        alert.show {
                            viewModel.handleEvent(DetailsEvent.AddToViewHistory)
                            requestCacheMovie(alertType.link)
                        }
                    }

                    alertType.equalsLinks && alertType.equalsTitle -> {
                        alert.show {
                            viewModel.handleEvent(DetailsEvent.AddToViewHistory)
                            requestCacheMovie(alertType.link)
                        }
                    }

                    !alertType.equalsLinks && alertType.equalsTitle -> {
                        alert.show {
                            viewModel.handleEvent(DetailsEvent.AddToViewHistory)
                            requestCacheMovie(alertType.link, resetDownloads = true)
                        }
                    }

                    !alertType.equalsLinks && !alertType.equalsTitle -> {
                        alert.show {
                            viewModel.handleEvent(DetailsEvent.AddToViewHistory)
                            requestCacheMovie(alertType.link, resetDownloads = true)
                        }
                    }
                }
            }

            is AlertType.ClearCache -> {
                alert.show {
                    val total = alertType.total
                    viewModel.handleEvent(
                        DetailsEvent.ClearViewHistory(
                            url = alertType.url,
                            seasonPosition = alertType.seasonPosition,
                            episodePosition = alertType.episodePosition,
                            total = total
                        )
                    )
                }
            }

            is AlertType.SimpleAlert -> {
                alert.show()
            }

            is AlertType.DownloadFile -> {
                alert.show {
                    viewModel.handleEvent(DetailsEvent.DownloadSelectedUrlToFile)
                }
            }

            is AlertType.UpdateDirect -> {
                alert.show {
                    viewModel.handleEvent(DetailsEvent.UpdateMovieData)
                }
            }

            else -> {}
        }
    }

    private fun Alert.show(onConfirm: () -> Unit = {}) {
        alertDialog(
            title = title.toString(requireContext()).orEmpty(),
            content = content?.toString(requireContext()).orEmpty(),
            btnOkText = btnOk?.toString(requireContext()).orEmpty(),
            btnCancelText = btnCancel?.toString(requireContext()).orEmpty(),
            onConfirm = onConfirm
        )
    }

    private fun showFeedbackDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.feedback_dialog_title),
            layout = R.layout.d_feedback_layout,
            cancelable = true,
            btnOkText = getString(R.string.feedback_button_text),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                viewModel.handleEvent(DetailsEvent.SendFeedback(feedbackText))
            },
            initView = {
                with(DFeedbackLayoutBinding.bind(this)) {
                    tiedtFeedbackInput.doAfterTextChanged { editable ->
                        feedbackText = editable.toString()
                    }
                }
            }
        )
    }

    private fun showCopyMp4LinkDialog() {
        viewModel.handleEvent(DetailsEvent.CopyMp4Link)
    }

    private fun playMovie() {
        currentMovie?.let { movie ->
            val connectionType = getConnectionType(requireContext())

            when {
                movie.type == MovieType.CINEMA && downloadAll -> {
                    navigateToPlayer(movie)
                }

                connectionType is ConnectionType.WIFI -> {
                    navigateToPlayer(movie)
                }

                connectionType is ConnectionType.MOBILE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.mobile_network_play),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPlayer(movie) }
                    )
                }

                connectionType is ConnectionType.NONE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.connection_network_none_dialog_content),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPlayer(movie) }
                    )
                }

                else -> {
                    navigateToPlayer(movie)
                }
            }
        }
    }

    private fun navigateToPlayer(movie: Movie) {
        val popupItems = getCinemaUrlsItems(movie)
        currentLinkPosition = binding.spinLinks.selectedItemPosition

        if (movie.type == MovieType.CINEMA && popupItems.size > 1) {
            currentLinkPosition = 0
        }

        if (movie.type == MovieType.CINEMA) {
            findNavController().navigateSafely(
                DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                    path = popupItems[currentLinkPosition].second,
                    movie = movie
                )
            )
        } else {
            findNavController().navigateSafely(
                DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                    path = null,
                    movie = movie,
                    seasonIndex = currentSeasonPosition,
                    episodeIndex = currentEpisodePosition
                )
            )
        }
    }

    private fun requestCacheMovie(url: String, resetDownloads: Boolean = false) {
        sendServiceMessage(
            Intent(requireContext(), MovieDownloadService::class.java),
            AppConstants.ACTION_CACHE_MOVIE
        ) {
            putString(AppConstants.SERVICE_PARAM_CACHE_URL, url)
            putString(AppConstants.SERVICE_PARAM_CACHE_MOVIE_PAGE_URL, currentMovie?.pageUrl)
            putString(AppConstants.SERVICE_PARAM_CACHE_TITLE, currentMovie?.title)

            if (currentMovie?.type == MovieType.SERIAL) {
                putInt(AppConstants.SERVICE_PARAM_CACHE_SEASON, currentSeasonPosition + 1)
                putInt(AppConstants.SERVICE_PARAM_CACHE_EPISODE, currentEpisodePosition + 1)
            }

            putBoolean(AppConstants.SERVICE_PARAM_RESET_CURRENT_DOWNLOADS, resetDownloads)
        }
    }

    private fun downloadFile(file: RequestDownloadFile) {
        sendServiceMessage(
            Intent(requireContext(), MovieDownloadService::class.java),
            AppConstants.ACTION_DOWNLOAD_FILE
        ) {
            putString(
                AppConstants.SERVICE_PARAM_DOWNLOAD_TYPE,
                if (file.isMp4) AppConstants.SERVICE_PARAM_DOWNLOAD_TYPE_MP4
                else AppConstants.SERVICE_PARAM_DOWNLOAD_TYPE_M3U8
            )
            putString(AppConstants.SERVICE_PARAM_DOWNLOAD_URL, file.url)
            putString(AppConstants.SERVICE_PARAM_DOWNLOAD_FILENAME, file.fileName)
            putString(AppConstants.SERVICE_PARAM_DOWNLOAD_TITLE, file.title)
        }
    }

    private fun makeBroadcastReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)) {
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                        viewModel.handleEvent(DetailsEvent.LoadMovie)
                    }
                }
            }
        }
    }
}
