package com.arny.mobilecinema.presentation.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.prefs.PrefsConstants
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.FDetailsBinding
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieDownloadedData
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.services.MovieDownloadService
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.getDP
import com.arny.mobilecinema.presentation.utils.getDuration
import com.arny.mobilecinema.presentation.utils.getString
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.makeTextViewResizable
import com.arny.mobilecinema.presentation.utils.printTime
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import com.arny.mobilecinema.presentation.utils.updateSpinnerItems
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import javax.inject.Inject

class DetailsFragment : Fragment(R.layout.f_details) {
    private val args: DetailsFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(@Assisted("id") id: Long): DetailsViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: DetailsViewModel by viewModelFactory {
        viewModelFactory.create(args.id)
    }

    @Inject
    lateinit var playerSource: PlayerSource

    @Inject
    lateinit var prefs: Prefs
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentMovie: Movie? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private var hasSavedData: Boolean = false
    private var downloadAll: Boolean = false
    private var canDownload: Boolean = false

    private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            currentEpisodePosition = 0
            fillSpinners(currentMovie)
            viewModel.onSerialPositionChanged(currentSeasonPosition, currentEpisodePosition)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
    private val episodesChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            viewModel.onSerialPositionChanged(currentSeasonPosition, currentEpisodePosition)
            viewModel.initSerialDownloadedData()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
    private lateinit var binding: FDetailsBinding
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkCache()
        }
    }
    private val downloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pageUrl =
                intent?.getStringExtra(AppConstants.SERVICE_PARAM_CACHE_MOVIE_PAGE_URL).orEmpty()
            val percent = intent?.getFloatExtra(AppConstants.SERVICE_PARAM_PERCENT, 0.0f)
            val bytes = intent?.getLongExtra(AppConstants.SERVICE_PARAM_BYTES, 0L)
            val season = intent?.getIntExtra(AppConstants.SERVICE_PARAM_CACHE_SEASON, 0) ?: 0
            val episode = intent?.getIntExtra(AppConstants.SERVICE_PARAM_CACHE_EPISODE, 0) ?: 0
            viewModel.updateDownloadedData(
                pageUrl = pageUrl,
                percent = percent,
                bytes = bytes,
                episode = season,
                season = episode,
                currentSeasonPosition = currentSeasonPosition,
                currentEpisodePosition = currentSeasonPosition
            )
        }
    }

    private fun checkCache() {
        lifecycleScope.launch {
            val movie = currentMovie
            if (movie != null) {
                initButtons(movie)
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
        initTrackAdapters()
        observeData()
        initMenu()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_CACHE_VIDEO_COMPLETE, downloadReceiver)
        registerLocalReceiver(AppConstants.ACTION_CACHE_VIDEO_UPDATE, downloadUpdateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(downloadReceiver)
        unregisterLocalReceiver(downloadUpdateReceiver)
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.menu_action_cache_movie).isVisible = canDownload
                menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.details_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    R.id.menu_action_cache_movie -> {
                        onCache()
                        true
                    }

                    R.id.menu_action_clear_cache -> {
                        viewModel.onClearCacheClick(currentSeasonPosition, currentEpisodePosition)
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun initListeners() = with(binding) {
        btnPlay.setOnClickListener {
            playMovie(false)
        }
        btnTrailer.setOnClickListener {
            playMovie(true)
        }
    }

    private fun playMovie(isTrailer: Boolean) {
        currentMovie?.let { movie ->
            val connectionType = getConnectionType(requireContext())
//            val cinemaUrls = movie.getCinemaUrls()
            when {
//                movie.type == MovieType.CINEMA && cinemaUrls.size > 1 -> {
//                    singleChoiceDialog(
//                        title = "Выберите ссылку для воспроизведения",
//                        items = List(cinemaUrls.size) { index -> "Ссылка ${index + 1}" },
//                        selectedPosition = 0,
//                        cancelable = true,
//                        btnOk = "OK",
//                        btnCancel = "Cancel"
//                    ) { ind, _ ->
//                        toast(cinemaUrls[ind])
//                    }
//                }
                movie.type == MovieType.CINEMA && downloadAll -> {
                    navigateToPLayer(movie, isTrailer)
                }

                connectionType is ConnectionType.WIFI -> {
                    navigateToPLayer(movie, isTrailer)
                }

                connectionType is ConnectionType.MOBILE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.mobile_network_play),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPLayer(movie, isTrailer) }
                    )
                }

                connectionType is ConnectionType.NONE -> {
                    alertDialog(
                        title = getString(R.string.attention),
                        content = getString(R.string.mobile_network_poor_play),
                        btnOkText = getString(android.R.string.ok),
                        btnCancelText = getString(android.R.string.cancel),
                        onConfirm = { navigateToPLayer(movie, isTrailer) }
                    )
                }

                else -> {
                    navigateToPLayer(movie, isTrailer)
                }
            }
        }
    }

    private fun onCache() {
        when (getConnectionType(requireContext())) {
            is ConnectionType.WIFI -> {
                showDialogCache()
            }

            is ConnectionType.MOBILE -> {
                alertDialog(
                    title = getString(R.string.attention),
                    content = getString(R.string.mobile_network_play),
                    btnOkText = getString(android.R.string.ok),
                    btnCancelText = getString(android.R.string.cancel),
                    onConfirm = {
                        showDialogCache()
                    }
                )
            }

            ConnectionType.NONE -> {
                toast(getString(R.string.internet_connection_error))
            }

            else -> {
            }
        }
    }

    private fun showDialogCache() {
        viewModel.showCacheDialog()
    }

    private fun requestCacheMovie(
        url: String,
        resetDownloads: Boolean = false
    ) {
        sendServiceMessage(
            Intent(requireContext(), MovieDownloadService::class.java),
            AppConstants.ACTION_CACHE_MOVIE,
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

    private fun navigateToPLayer(movie: Movie, isTrailer: Boolean) {
        findNavController().navigate(
            DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                path = null,
                movie = movie,
                seasonIndex = currentSeasonPosition,
                episodeIndex = currentEpisodePosition,
                isTrailer = isTrailer
            )
        )
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.currentMovie.collectLatest { movie ->
                if (movie != null) {
                    onMovieLoaded(movie)
                }
            }
        }
        launchWhenCreated {
            viewModel.saveData.collectLatest { saveData ->
                onSaveDataLoaded(saveData)
            }
        }
        launchWhenCreated {
            viewModel.serialTitle.collectLatest { serialTitle ->
                binding.tvTitle.text = getString(serialTitle)
            }
        }
        launchWhenCreated {
            viewModel.toast.collectLatest { text ->
                toast(text.toString(requireContext()))
            }
        }
        launchWhenCreated {
            viewModel.error.collectLatest { text ->
                toast(text.toString(requireContext()))
            }
        }
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.progressBar.isVisible = loading
            }
        }
        launchWhenCreated {
            viewModel.addToHistory.collectLatest { cache ->
                if (cache) {
                    toast(getString(R.string.added_to_history))
                }
            }
        }
        launchWhenCreated {
            viewModel.downloadedData.collectLatest { data ->
                updateDownloadedData(data)
            }
        }
        launchWhenCreated {
            viewModel.alert.collectLatest { alert -> showAlert(alert) }
        }
        launchWhenCreated { viewModel.downloadAll.collectLatest { downloadAll = it } }
        launchWhenCreated {
            viewModel.hasSavedData.collectLatest {
                hasSavedData = it
                requireActivity().invalidateOptionsMenu()
            }
        }
        launchWhenCreated {
            viewModel.downloadInit.collectLatest { init ->
                canDownload = init
                requireActivity().invalidateOptionsMenu()
            }
        }
    }

    private fun Alert.show(onConfirm: () -> Unit = {}) {
        alertDialog(
            title = title.toString(requireContext()).orEmpty(),
            content = content?.toString(requireContext()).orEmpty(),
            btnOkText = btnOk?.toString(requireContext()).orEmpty(),
            btnCancelText = btnCancel?.toString(requireContext()).orEmpty(),
            onConfirm = { onConfirm() }
        )
    }

    private fun showAlert(alert: Alert?) {
        when (val alertType = alert?.type) {
            is AlertType.Download -> {
                when {
                    alertType.complete -> {
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                        )
                    }

                    alertType.empty -> {
                        // Новая
                        alert.show {
                            viewModel.addToHistory()
                            requestCacheMovie(alertType.link)
                        }
                    }

                    alertType.equalsLinks && alertType.equalsTitle -> {
                        // Продолжить загрузку текущего
                        alert.show {
                            viewModel.addToHistory()
                            requestCacheMovie(alertType.link)
                        }
                    }

                    !alertType.equalsLinks && alertType.equalsTitle -> {
                        // Текущий фильм,но ссылки разные(возможно сериал,но нужно будет привязаться к эпизодам)
                        alert.show {
                            viewModel.addToHistory()
                            requestCacheMovie(
                                alertType.link,
                                resetDownloads = true
                            )
                        }
                    }

                    !alertType.equalsLinks && !alertType.equalsTitle -> {
                        // Новая загрузка
                        alert.show {
                            viewModel.addToHistory()
                            requestCacheMovie(
                                alertType.link,
                                resetDownloads = true
                            )
                        }
                    }
                }
            }

            is AlertType.ClearCache -> {
                alert.show {
                    viewModel.clearViewHistory(
                        url = alertType.url,
                        seasonPosition = alertType.seasonPosition,
                        episodePosition = alertType.episodePosition,
                        total = alertType.total
                    )
                    checkCache()
                }
            }
            else -> {}
        }
    }

    private fun updateDownloadedData(data: MovieDownloadedData?) {
        binding.tvSaveData.isVisible = data != null
        when {
            data != null && data.loading -> {
                binding.tvSaveData.text = getString(
                    R.string.cinema_save_data_invalidate,
                )
            }

            data != null && data.downloadedPercent > 0.0f && data.downloadedSize > 0L -> {
                binding.tvSaveData.text = getString(
                    R.string.cinema_save_data,
                    String.format("%.1f", data.downloadedPercent),
                    formatFileSize(data.downloadedSize, 1)
                )
            }
        }
    }

    private fun onMovieLoaded(movie: Movie) {
        currentMovie = movie
        viewModel.loadSaveData(movie.dbId)
        updateSpinData(movie)
        initUI(movie)
        initButtons(movie)
    }

    private fun initButtons(movie: Movie) = with(binding) {
        viewModel.invalidateCache()
        viewModel.onSerialPositionChanged(currentSeasonPosition, currentEpisodePosition)
        btnTrailer.isVisible =
            movie.cinemaUrlData?.trailerUrl?.urls?.filter { it.isNotBlank() }.orEmpty().isNotEmpty()
        if (movie.type == MovieType.CINEMA) {
            val urls = movie.cinemaUrlData?.cinemaUrl?.urls.orEmpty()
            val hdUrls = movie.cinemaUrlData?.hdUrl?.urls.orEmpty()
            btnPlay.isVisible = urls.isNotEmpty() || hdUrls.isNotEmpty()
            viewModel.initCinemaDownloadedData()
        } else {
            val hasAnyLink = movie.seasons.any { season ->
                season.episodes.any { episode -> episode.hls.isNotBlank() || episode.dash.isNotBlank() }
            }
            btnPlay.isVisible = hasAnyLink
            viewModel.initSerialDownloadedData()
        }
    }

    private fun onSaveDataLoaded(saveData: SaveData) {
        when {
            saveData.movieDbId == currentMovie?.dbId && currentMovie?.type == MovieType.SERIAL -> {
                currentSeasonPosition = saveData.season
                currentEpisodePosition = saveData.episode
                fillSpinners(currentMovie)
            }

            saveData.movieDbId == currentMovie?.dbId && currentMovie?.type == MovieType.CINEMA -> {
            }
        }
    }

    private fun initUI(movie: Movie) = with(binding) {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        Glide.with(requireContext())
            .load(movie.img.getWithDomain(baseUrl))
            .fitCenter()
            .into(ivBanner)
        updateTitle(movie.title)
        tvUpdated.text = getString(
            R.string.updated_format,
            DateTime(movie.info.updated).printTime("dd MMM YYYY HH:mm")
        )
        tvDuration.isVisible = movie.type == MovieType.CINEMA
        tvDuration.text = getString(R.string.duration_format, getDuration(movie.info.durationSec))
        tvTitle.text = movie.title
        val info = movie.info
        tvDescription.text = info.description
        tvDescription.makeTextViewResizable()
        tvRating.text = StringBuilder().apply {
            if (info.ratingImdb > 0) {
                append(
                    "%s:%.02f".format(getString(R.string.imdb), info.ratingImdb).replace(",", ".")
                )
                append(" ")
            }
            if (info.ratingKP > 0) {
                append("%s:%.02f".format(getString(R.string.kp), info.ratingKP).replace(",", "."))
                append(" ")
            }
            append(String.format("%d\uD83D\uDC4D %d\uD83D\uDC4E", info.likes, info.dislikes))
        }.toString()
        val seasons = movie.seasons
        val episodesSize = seasons.sumOf { it.episodes.size }
        tvTypeYear.text = StringBuilder().apply {
            if (info.year > 0) {
                append(info.year)
                append(" ")
            }
            if (movie.type == MovieType.SERIAL) {
                append("(")
                append(getString(R.string.serial))
                append(" ")
                append(resources.getQuantityString(R.plurals.sezons, seasons.size, seasons.size))
                append(" ")
                append(resources.getQuantityString(R.plurals.episods, episodesSize, episodesSize))
                append(")")
            } else {
                append(getString(R.string.cinema))
            }
            append(" ${movie.info.countries.joinToString()}")
        }.toString()
        tvQuality.isVisible = movie.type == MovieType.CINEMA
        tvQuality.text = getString(R.string.quality_format, info.quality)
        initGenres(info.genres)
        initDirectors(info.directors)
        initActors(info.actors)
    }

    private fun FDetailsBinding.initDirectors(directors: List<String>) {
        for (director in directors.filter { it.isNotBlank() }) {
            val chip = getCustomChip(chgrDirectors)
            chip.text = director
            chip.isClickable = true
            chip.isCheckable = false
            chip.setOnClickListener {
                findNavController().navigate(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(director = director)
                )
            }
            chgrDirectors.addView(chip)
        }
    }

    private fun FDetailsBinding.initGenres(genres: List<String>) {
        val genresMap = genres.flatMap { it.split(" ") }.filter { it.isNotBlank() }
        binding.tvGenres.isVisible = genresMap.isNotEmpty()
        for (genre in genresMap) {
            val chip = getCustomChip(chgrGenres)
            chip.text = genre
            chip.isClickable = true
            chip.isCheckable = false
            chip.setOnClickListener {
                findNavController().navigate(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(genre = genre)
                )
            }
            chgrGenres.addView(chip)
        }
    }

    private fun getCustomChip(chipGroup: ChipGroup): Chip =
        layoutInflater.inflate(R.layout.i_custom_chip_choise, chipGroup, false) as Chip

    private fun FDetailsBinding.initActors(actors: List<String>) {
        for (actor in actors.filter { it.isNotBlank() }) {
            val chip = getCustomChip(chgrActors)
            val paddingDp = requireContext().getDP(10)
            chip.setPadding(paddingDp.toInt(), 0, paddingDp.toInt(), 0)
            chip.text = actor
            chip.isClickable = true
            chip.isCheckable = false
            chip.setEnsureMinTouchTargetSize(false)
            chip.setOnClickListener {
                findNavController().navigate(
                    DetailsFragmentDirections.actionNavDetailsToNavHome(actor = actor)
                )
            }
            chgrActors.addView(chip)
        }
    }

    private fun initTrackAdapters() {
        with(binding) {
            seasonsTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
            episodesTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
            spinSeasons.adapter = seasonsTracksAdapter
            spinEpisodes.adapter = episodesTracksAdapter
            spinSeasons.setSelection(currentSeasonPosition, false)
            spinEpisodes.setSelection(currentEpisodePosition, false)
            spinSeasons.updateSpinnerItems(seasonsChangeListener)
            spinEpisodes.updateSpinnerItems(episodesChangeListener)
        }
    }

    private fun updateCurrentSerialPosition() {
        currentSeasonPosition = binding.spinSeasons.selectedItemPosition
        currentEpisodePosition = binding.spinEpisodes.selectedItemPosition
    }

    private fun updateSpinData(movie: Movie) = with(binding) {
        if (movie.type == MovieType.SERIAL) {
            fillSpinners(movie)
            spinEpisodes.isVisible = true
            spinSeasons.isVisible = true
            tvSeasons.isVisible = true
        }
    }

    private fun fillSpinners(movie: Movie?) {
        val seasons = movie?.seasons.orEmpty()
        val seasonsList = sortSeasons(seasons)
        if (seasonsList.isNotEmpty()) {
            with(binding) {
                spinSeasons.updateSpinnerItems(seasonsChangeListener) {
                    seasonsTracksAdapter?.clear()
                    seasonsTracksAdapter?.addAll(seasonsList)
                    spinSeasons.setSelection(currentSeasonPosition, false)
                }
                spinEpisodes.updateSpinnerItems(episodesChangeListener) {
                    val episodes = seasons.getOrNull(currentSeasonPosition)?.episodes.orEmpty()
                    episodesTracksAdapter?.clear()
                    episodesTracksAdapter?.addAll(sortEpisodes(episodes))
                    spinEpisodes.setSelection(currentEpisodePosition, false)
                }
            }
            viewModel.initSerialDownloadedData()
        }
    }

    private fun sortSeasons(seasons: List<SerialSeason>) =
        seasons.sortedBy { it.id }.map { getSeasonTitle(it) }

    private fun sortEpisodes(episodes: List<SerialEpisode>): List<String> =
        episodes.asSequence()
            .sortedBy {
                findByGroup(it.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
            .map { getEpisodeTitle(it) }
            .toList()

    private fun getSeasonTitle(it: SerialSeason) =
        "%d %s".format(it.id, getString(R.string.spinner_season))

    private fun getEpisodeTitle(it: SerialEpisode) =
        "%s %s".format(it.episode, getString(R.string.spinner_episode))

}
