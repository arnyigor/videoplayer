    package com.arny.mobilecinema.presentation.details

    import android.annotation.SuppressLint
    import android.content.BroadcastReceiver
    import android.content.Context
    import android.content.Intent
    import android.os.Bundle
    import com.arny.mobilecinema.domain.playback.legacyPlayableRef
import android.os.SystemClock
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.AdapterView
    import androidx.core.content.ContextCompat
    import androidx.core.view.isVisible
    import androidx.core.os.bundleOf
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
    import com.arny.mobilecinema.data.utils.printTime
    import com.arny.mobilecinema.databinding.DFeedbackLayoutBinding
    import com.arny.mobilecinema.databinding.FDetailsBinding
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
    import com.arny.mobilecinema.presentation.utils.registerReceiver
    import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
    import com.arny.mobilecinema.presentation.utils.sendServiceMessage
    import com.arny.mobilecinema.presentation.utils.toast
    import com.arny.mobilecinema.presentation.utils.unregisterReceiver
    import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
    import com.arny.mobilecinema.presentation.utils.updateSpinnerItems
    import com.arny.mobilecinema.presentation.utils.updateTitle
    import com.bumptech.glide.Glide
    import com.google.android.material.chip.Chip
    import kotlinx.coroutines.flow.collectLatest
    import org.joda.time.DateTime
    import timber.log.Timber
    import java.util.Locale
    import kotlin.math.abs
    import org.koin.androidx.viewmodel.ext.android.viewModel
    import org.koin.core.parameter.parametersOf
    import org.koin.core.component.KoinComponent
    import org.koin.android.ext.android.inject

    class DetailsFragment : Fragment(R.layout.f_details) {
        private companion object {
        /** Тег диагностики цепочки запуска (§4.2). */
        private const val TAG_PLAY = "PlayChain"
            const val AUTO_UPDATE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        }

        private val args: DetailsFragmentArgs by navArgs()

        private val viewModel: DetailsViewModel by viewModel { parametersOf(args.id) }

        private val prefs: Prefs by inject()

        // ViewBinding
        private var _binding: FDetailsBinding? = null
        private val binding get() = _binding!!

        // Adapters
        private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
        private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
        private var linksAdapter: TrackSelectorSpinnerAdapter? = null

        // State (локальные копии для UI логики)
        private val playClickGate = PlayClickGate()

        private var currentMovie: Movie? = null
        private var currentSeasonPosition: Int = 0
        private var currentEpisodePosition: Int = 0
        private var currentLinkPosition: Int = 0
        private var hasSavedData: Boolean = false
        private var downloadAll: Boolean = false
        private var canDownload: Boolean = false
        private var canDownloadFile: Boolean = false
        private var feedbackText = ""
        private var isAutoUpdateRunning = false
        private var autoUpdateRequestedForUrl: String? = null

        // Touch flags для Spinners
        private var isUserTouchSeasons = false
        private var isUserTouchEpisodes = false
        private var isUserTouchLinks = false

        // ДОБАВЛЕНО: Флаг для отслеживания пересоздания View
        private var needsUIRefresh = true

        /** Позиция продолжения из SaveData; 0 — начинать сначала или данных нет. */
        private var resumePositionMs = 0L

        private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserTouchSeasons) return
                isUserTouchSeasons = false

                // Позицию берём из самого события, а не из поля спиннера: пока шёл выбор,
                // могла прийти запоздалая отрисовка (например, обновление статуса кэша),
                // которая сверяет поля с состоянием и успевает откатить выбор назад —
                // именно из-за этого выбор с первого раза «сбрасывался на 1-цу».
                currentSeasonPosition = position
                currentEpisodePosition = 0
                // Состояние обновляем до перерисовки спиннеров, чтобы запоздалый рендер
                // уже видел новый выбор и не откатывал его.
                viewModel.handleEvent(
                    DetailsEvent.SerialPositionChanged(
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                )
                fillSpinners(currentMovie)
                currentMovie?.let { updateLinksSpinner(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                isUserTouchSeasons = false
            }
        }

        private val episodesChangeListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserTouchEpisodes) return
                isUserTouchEpisodes = false

                currentEpisodePosition = position
                viewModel.handleEvent(
                    DetailsEvent.SerialPositionChanged(
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                )
                currentMovie?.let { updateLinksSpinner(it) }
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
                        val items = getVideoUrlsItems(curMovie)
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

            var isCollapsed = false

            binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                val totalScrollRange = appBarLayout.totalScrollRange
                val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

                // Плавно скрываем элементы поверх постера
                val contentAlpha = (1f - percentage * 1.5f).coerceIn(0f, 1f)
                binding.tvTitle.setAlpha(contentAlpha)
                binding.tvTypeYear.setAlpha(contentAlpha)
                binding.llRatings.setAlpha(contentAlpha)

                // Отслеживаем состояние свёрнутости
                val newCollapsed = percentage > 0.8f
                if (newCollapsed != isCollapsed) {
                    isCollapsed = newCollapsed
                }
            }
        }

override fun onResume() {
            super.onResume()
            registerReceiver(AppConstants.ACTION_CACHE_VIDEO_COMPLETE, downloadReceiver)
            registerReceiver(AppConstants.ACTION_CACHE_VIDEO_UPDATE, downloadUpdateReceiver)
            registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)

            // Обновляем статус загрузки (download), но НЕ перезаписываем позицию
            viewModel.refreshDownloadStatus()
        }

        override fun onPause() {
            super.onPause()
            unregisterReceiver(downloadReceiver)
            unregisterReceiver(downloadUpdateReceiver)
            unregisterLocalReceiver(updateReceiver)
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
                btnOpenComments.setOnClickListener {
                    openCommentsScreen()
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
            menu.findItem(R.id.menu_action_download_file)?.isVisible = canDownloadFile
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
                val shouldRefreshUI = currentMovie?.dbId != movie.dbId || needsUIRefresh

                val videoDataChanged = getVideoDataFingerprint(currentMovie) !=
                    getVideoDataFingerprint(movie)

                if (shouldRefreshUI || videoDataChanged) {
                    needsUIRefresh = false
                    onMovieLoaded(movie)
                }

                updateSpinnerPositionsIfNeeded(state)
                updateResumeState(state)
            }

            updateDownloadedData(state.downloadedData)

            canDownload = state.canDownload
            canDownloadFile = state.canDownloadFile
            hasSavedData = state.hasSavedData

            binding.ivFavorite.setImageResource(
                if (state.isInFavorite) R.drawable.baseline_bookmark_24 else R.drawable.outline_bookmark_24
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
                    requestMovieUpdate(action.url)
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
            requestAutoUpdateIfNeeded(movie)
        }

        private fun requestAutoUpdateIfNeeded(movie: Movie) {
            val pageUrl = movie.pageUrl
            if (pageUrl.isBlank()) return
            if (!movie.isDataOutdated()) return
            if (isAutoUpdateRunning || autoUpdateRequestedForUrl == pageUrl) return

            autoUpdateRequestedForUrl = pageUrl
            isAutoUpdateRunning = true
            updatePlayButtonEnabled()
            setAutoUpdateIndicatorVisible(true)
            requestMovieUpdate(pageUrl)
        }

        private fun Movie.isDataOutdated(): Boolean {
            val fetchedAt = info.detailsFetchedAt
            return fetchedAt <= 0L || System.currentTimeMillis() - fetchedAt > AUTO_UPDATE_MAX_AGE_MS
        }

        private fun Movie.hasPlayableLinks(): Boolean =
            PlayStartResolver.sources(this).isNotEmpty()

        private fun setAutoUpdateIndicatorVisible(visible: Boolean) {
            _binding?.llUpdatingIndicator?.isVisible = visible
        }

        private fun requestMovieUpdate(url: String) {
            isAutoUpdateRunning = true
            setAutoUpdateIndicatorVisible(true)
            updatePlayButtonEnabled()
            requireContext().sendServiceMessage(
                Intent(requireContext().applicationContext, UpdateService::class.java),
                AppConstants.ACTION_UPDATE_BY_URL
            ) {
                putString(AppConstants.SERVICE_PARAM_UPDATE_URL, url)
                // Разбор начат с экрана деталей: не просим Home-экран открывать карточку
                // и показывать тосты после завершения.
                putBoolean(AppConstants.SERVICE_PARAM_UPDATE_BACKGROUND, true)
            }
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

                collapsingToolbar.title = movie.title
                updateTitle(movie.title)

                val info = movie.info

                // Рейтинги в виде бейджей
                if (info.ratingImdb > 0) {
                    tvImdbBadge.isVisible = true
                    tvImdbBadge.text = getString(R.string.imdb_formatted,"%.2f".format(info.ratingImdb))
                } else {
                    tvImdbBadge.isVisible = false
                }

                if (info.ratingKP > 0) {
                    tvKpBadge.isVisible = true
                    tvKpBadge.text = getString(R.string.kp_formatted,"%.2f".format(info.ratingKP))
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

        private fun openCommentsScreen() {
            val movie = currentMovie ?: return
            findNavController().navigateSafely(
                DetailsFragmentDirections.actionNavDetailsToNavComments(
                    movie.pageUrl,
                    movie.title
                )
            )
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
                    navigateToHomeSearch(AppConstants.PARAMS.DIRECTOR, director)
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
                    navigateToHomeSearch(AppConstants.PARAMS.GENRE, genre)
                }
                chgrGenres.addView(chip)
            }
        }

        private fun navigateToHomeSearch(param: String, value: String) {
            findNavController().navigate(
                R.id.action_nav_details_to_nav_site_search,
                bundleOf(
                    "query" to value,
                    "searchType" to when (param) {
                        AppConstants.PARAMS.ACTOR -> AppConstants.SearchType.ACTORS
                        AppConstants.PARAMS.DIRECTOR -> AppConstants.SearchType.DIRECTORS
                        else -> AppConstants.SearchType.GENRES
                    },
                    "scope" to if (param == AppConstants.PARAMS.GENRE) "CATALOG" else null,
                )
            )
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
                    navigateToHomeSearch(AppConstants.PARAMS.ACTOR, actor)
                }
                chgrActors.addView(chip)
            }
        }

        private fun initButtons(movie: Movie, invalidate: Boolean = false) {
            with(binding) {
                if (invalidate) {
                    viewModel.handleEvent(DetailsEvent.InvalidateCache)
                }

                // Кнопка остаётся на месте и тогда, когда играть нечем: состояние
                // «Нет источника» понятнее, чем исчезающий CTA (PLAY-01, план v3 §9.2).
                val hasAnyLink = when (movie.type) {
                    MovieType.CINEMA -> PlayStartResolver.cinemaSources(movie).isNotEmpty()
                    MovieType.SERIAL -> PlayStartResolver.sources(
                        movie,
                        currentSeasonPosition,
                        currentEpisodePosition
                    ).isNotEmpty()
                    else -> false
                }
                btnPlay.isVisible = hasAnyLink || movie.type == MovieType.CINEMA ||
                    movie.type == MovieType.SERIAL

                updatePlayButtonEnabled()
            }
        }

        /**
         * Экран до плеера показывает, что запуск продолжит просмотр.
         *
         * Тот же canonical playable ID, что и в плеере. Прогресс другой серии не должен
         * попадать в подпись при ручном выборе сезона/эпизода; completed не продолжаем.
         */
        private fun updateResumeState(state: DetailsUiState) {
            val playable = state.movie?.legacyPlayableRef(currentSeasonPosition, currentEpisodePosition)
            val progress = playable?.let { state.playbackProgress[it.id.value] }
            val resumeMs = progress?.takeUnless { it.completed }?.positionMs?.coerceAtLeast(0L) ?: 0L
            resumePositionMs = resumeMs
            updatePlayButtonEnabled()
        }

        private fun updatePlayButtonEnabled() {
            _binding?.btnPlay?.apply {
                val movie = currentMovie
                // Отсутствие ссылки — явное состояние кнопки, а не молчаливое нажатие (PLAY-01).
                val hasLinks = PlayStartResolver.resolve(
                    movie = movie,
                    seasonPosition = currentSeasonPosition,
                    episodePosition = currentEpisodePosition,
                    selectedIndex = binding.spinLinks.selectedItemPosition.coerceAtLeast(0)
                ) is PlayStart.Ready
                // Обновление данных не должно запирать уже доступные ссылки: играем тем, что есть.
                val enabled = hasLinks
                isEnabled = enabled
                isClickable = enabled
                alpha = if (enabled) 1f else 0.5f
                text = getString(
                    when {
                        hasLinks && resumePositionMs > 0L -> R.string.details_resume
                        hasLinks -> R.string.watch
                        isAutoUpdateRunning -> R.string.please_wait
                        else -> R.string.details_no_source
                    }
                )
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
                    updateLinksSpinner(movie)
                    cardSeasons.isVisible = true
                } else {
                    updateLinksSpinner(movie)
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

        private fun updateLinksSpinner(movie: Movie) {
            val popupItems = getVideoUrlsItems(movie)
            val sizeMoreOne = popupItems.size > 1

            binding.cardLinks.isVisible = popupItems.isNotEmpty()
            binding.spinLinks.isEnabled = sizeMoreOne

            if (popupItems.isNotEmpty()) {
                binding.spinLinks.updateSpinnerItems(linksChangeListener)
                linksAdapter?.clear()
                linksAdapter?.addAll(popupItems.map { it.first })

                val newIndex = currentLinkPosition.coerceIn(0, popupItems.lastIndex)
                currentLinkPosition = newIndex
                binding.spinLinks.setSelection(newIndex, false)
            }

            if (popupItems.isNotEmpty()) {
                viewModel.handleEvent(
                    DetailsEvent.SelectedUrlChanged(
                        popupItems[currentLinkPosition.coerceIn(0, popupItems.lastIndex)].second,
                        false
                    )
                )
            }
        }

        /**
         * Источники текущей серии/фильма: подпись для спиннера + адрес.
         *
         * Сама логика выбора живёт в [PlayStartResolver]: там же её можно проверить
         * без Android, а здесь остаётся только форматирование подписи (план v3 §4.2).
         */
        private fun getVideoUrlsItems(movie: Movie): List<Pair<String, String>> =
            PlayStartResolver.sources(movie, currentSeasonPosition, currentEpisodePosition)
                .mapIndexed { index, source ->
                    getString(
                        R.string.details_source_label,
                        index + 1,
                        source.quality
                    ) to source.url
                }

        /**
         * Отпечаток видео-данных карточки (ссылки фильма / серии сериала).
         *
         * Нужен, чтобы экран деталей перерисовался, когда фоновая докачка данных
         * (UpdateService по ACTION_UPDATE_BY_URL) добавила серии и ссылки к уже открытой карточке:
         * dbId при этом не меняется, а раньше обновлялись только ссылки фильмов —
         * у сериала список серий до повторного открытия экрана оставался пустым.
         */
        private fun getVideoDataFingerprint(movie: Movie?): String = when (movie?.type) {
            MovieType.CINEMA -> getCinemaUrlsRaw(movie).joinToString("\n")

            MovieType.SERIAL -> movie.seasons.joinToString("|") { season ->
                "${season.id}:" + season.episodes.joinToString(",") { episode ->
                    episode.hls + ";" + episode.dash
                }
            }

            else -> ""
        }

        private fun getCinemaUrlsRaw(movie: Movie?): List<String> {
            if (movie?.type != MovieType.CINEMA) return emptyList()

            val cinemaUrlData = movie.cinemaUrlData
            val hdUrls = cinemaUrlData?.hdUrl?.urls.orEmpty()
            val cinemaUrls = cinemaUrlData?.cinemaUrl?.urls.orEmpty()
            return (hdUrls + cinemaUrls).filter { it.isNotBlank() }.distinct()
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
            // Двойной тап не должен открывать два плеера (§4.3).
            if (!playClickGate.tryAccept(SystemClock.elapsedRealtime())) {
                Timber.tag(TAG_PLAY).d("duplicate click ignored")
                return
            }
            // Единственный вход в воспроизведение: сначала типизированный результат,
            // потом уже сетевые предупреждения. Молчаливого «ничего не произошло» нет (PLAY-01).
            // Correlation ID склеивает этапы клика в логах (§4.2).
            val playId = PlayTrace.nextId()
            Timber.tag(TAG_PLAY).d("%s click", playId)
            val start = PlayStartResolver.resolve(
                movie = currentMovie,
                seasonPosition = currentSeasonPosition,
                episodePosition = currentEpisodePosition,
                selectedIndex = binding.spinLinks.selectedItemPosition.coerceAtLeast(0)
            )

            val movie = currentMovie
            when (start) {
                is PlayStart.Failed -> {
                    Timber.tag(TAG_PLAY).w(
                        "%s resolve failed: %s (movieId=%s, season=%d, episode=%d)",
                        playId,
                        start.reason,
                        movie?.dbId,
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                    toast(getString(start.reason.messageRes))
                    updatePlayButtonEnabled()
                    return
                }

                is PlayStart.Ready -> {
                    Timber.tag(TAG_PLAY).d(
                        "%s resolve ready (movieId=%s, quality=%s, season=%d, episode=%d)",
                        playId,
                        movie?.dbId,
                        start.source.quality,
                        currentSeasonPosition,
                        currentEpisodePosition
                    )
                }
            }

            if (movie == null) return
            val connectionType = getConnectionType(requireContext())

            when {
                movie.type == MovieType.CINEMA && downloadAll -> {
                    navigateToPlayer(movie, playId)
                }

                connectionType is ConnectionType.WIFI -> {
                    navigateToPlayer(movie, playId)
                }

                connectionType is ConnectionType.MOBILE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.mobile_network_play),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPlayer(movie, playId) }
                    )
                }

                connectionType is ConnectionType.NONE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.connection_network_none_dialog_content),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPlayer(movie, playId) }
                    )
                }

                else -> {
                    navigateToPlayer(movie, playId)
                }
            }
        }

        /** Сообщение для отказа запуска: одно место, одна причина на каждое состояние. */
        private val PlayStartFailure.messageRes: Int
            get() = when (this) {
                PlayStartFailure.NO_MOVIE -> R.string.play_error_no_movie
                PlayStartFailure.NO_SOURCE -> R.string.play_error_no_source
                PlayStartFailure.INVALID_SOURCE -> R.string.play_error_invalid_source
            }

        private fun navigateToPlayer(movie: Movie, playId: String) {
            val start = PlayStartResolver.resolve(
                movie = movie,
                seasonPosition = currentSeasonPosition,
                episodePosition = currentEpisodePosition,
                selectedIndex = binding.spinLinks.selectedItemPosition.coerceAtLeast(0)
            )
            if (start is PlayStart.Failed) {
                // Раньше при пустом списке ссылок плеер получал path = null, сам уходил
                // назад — со стороны это выглядело как «кнопка не работает».
                Timber.tag(TAG_PLAY).w(
                    "%s navigate skipped: %s (movieId=%s)", playId, start.reason, movie.dbId
                )
                toast(getString(start.reason.messageRes))
                return
            }
            val selectedUrl = (start as PlayStart.Ready).source.url
            // URL логируется только отредактированным: query может содержать токены (§4.2).
            Timber.tag(TAG_PLAY).d(
                "%s navigate playableId=%s quality=%s url=%s",
                playId, movie.dbId, start.source.quality, PlayTrace.redactUrl(selectedUrl)
            )

            try {
                if (movie.type == MovieType.CINEMA) {
                    findNavController().navigate(
                        R.id.nav_player_view,
                        bundleOf("path" to selectedUrl, "movie" to movie, "playId" to playId)
                    )
                } else {
                    findNavController().navigate(
                        R.id.nav_player_view,
                        bundleOf(
                            "path" to selectedUrl,
                            "movie" to movie,
                            "seasonIndex" to currentSeasonPosition,
                            "episodeIndex" to currentEpisodePosition,
                            "playId" to playId
                        )
                    )
                }
            } catch (e: Exception) {
                // Навигация — тоже возможная точка отказа: сообщаем, а не падаем/молчим.
                Timber.tag(TAG_PLAY).e(e, "%s navigation failed (movieId=%s)", playId, movie.dbId)
                toast(getString(R.string.play_error_navigation))
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
                if (intent == null) return
                // Разборов может идти несколько (быстро открыли несколько карточек),
                // поэтому на завершение чужой карточки не реагируем: иначе с этого экрана
                // пропадает индикатор «обновление данных…», пока идёт своё обновление.
                if (!isUpdateForCurrentMovie(intent)) return
                when (intent.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)) {
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                        isAutoUpdateRunning = false
                        setAutoUpdateIndicatorVisible(false)
                        updatePlayButtonEnabled()
                        viewModel.handleEvent(DetailsEvent.LoadMovie)
                    }

                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR,
                    AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {
                        isAutoUpdateRunning = false
                        setAutoUpdateIndicatorVisible(false)
                        updatePlayButtonEnabled()
                    }
                }
            }
        }

        /** Завершение разбора относится к карточке, открытой на этом экране. */
        private fun isUpdateForCurrentMovie(intent: Intent): Boolean {
            val updatedMovieId = intent.getLongExtra("EXTRA_MOVIE_ID", -1L)
            if (updatedMovieId <= 0L) return true
            val currentId = currentMovie?.movieId?.toLong() ?: return true
            return currentId <= 0L || currentId == updatedMovieId
        }
    }
