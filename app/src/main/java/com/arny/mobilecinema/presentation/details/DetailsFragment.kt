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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
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
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieDownloadedData
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.MovieDownloadService
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.getDP
import com.arny.mobilecinema.presentation.utils.getDuration
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import javax.inject.Inject

class DetailsFragment : Fragment(R.layout.f_details) {
    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: DetailsViewModel by viewModels { vmFactory }

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
    private val args: DetailsFragmentArgs by navArgs()
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
            val percent = intent?.getFloatExtra(AppConstants.SERVICE_PARAM_PERCENT, 0.0f)
            val bytes = intent?.getLongExtra(AppConstants.SERVICE_PARAM_BYTES, 0L)
            viewModel.updateDownloadedData(percent, bytes)
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
        viewModel.loadVideo(args.id)
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
                menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
                menu.findItem(R.id.menu_action_cache_movie).isVisible = canDownload
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
                        alertDialog(
                            getString(R.string.question_remove),
                            getString(
                                R.string.question_remove_from_history_title,
                                currentMovie?.title
                            ),
                            getString(android.R.string.ok),
                            getString(android.R.string.cancel),
                            onConfirm = {
                                viewModel.clearViewHistory()
                                lifecycleScope.launch {
                                    checkCache()
                                }
                            }
                        )
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
            when {
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
        resetDownloads: Boolean = false
    ) {
        sendServiceMessage(
            Intent(requireContext(), MovieDownloadService::class.java),
            AppConstants.ACTION_CACHE_MOVIE,
        ) {
            putString(AppConstants.SERVICE_PARAM_CACHE_URL, currentMovie?.getCinemaUrl())
            putString(AppConstants.SERVICE_PARAM_CACHE_TITLE, currentMovie?.title)
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
                if (init) {
                    canDownload = true
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun showAlert(alert: Alert?) {
        when (val type = alert?.type) {
            is AlertType.Download -> {
                when {
                    type.complete -> {
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                        )
                    }

                    type.empty -> {
                        // Новая
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            content = alert.content?.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancelText = alert.btnCancel?.toString(requireContext()).orEmpty(),
                            onConfirm = {
                                viewModel.addToHistory()
                                requestCacheMovie()
                            }
                        )
                    }

                    type.equalsLinks && type.equalsTitle -> {
                        // Продолжить загрузку текущего
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            content = alert.content?.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancelText = alert.btnCancel?.toString(requireContext()).orEmpty(),
                            onConfirm = {
                                viewModel.addToHistory()
                                requestCacheMovie()
                            }
                        )
                    }

                    !type.equalsLinks && type.equalsTitle -> {
                        // Текущий фильм,но ссылки разные(возможно сериал,но нужно будет привязаться к эпизодам)
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            content = alert.content?.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancelText = alert.btnCancel?.toString(requireContext()).orEmpty(),
                            onConfirm = {
                                viewModel.addToHistory()
                                requestCacheMovie(
                                    resetDownloads = true
                                )
                            }
                        )
                    }

                    !type.equalsLinks && !type.equalsTitle -> {
                        // Новая загрузка
                        alertDialog(
                            title = alert.title.toString(requireContext()).orEmpty(),
                            content = alert.content?.toString(requireContext()).orEmpty(),
                            btnOkText = alert.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancelText = alert.btnCancel?.toString(requireContext()).orEmpty(),
                            onConfirm = {
                                viewModel.addToHistory()
                                requestCacheMovie(
                                    resetDownloads = true
                                )
                            }
                        )
                    }
                }
            }

            else -> {}
        }
    }

    private fun updateDownloadedData(data: MovieDownloadedData?) {
        binding.tvSaveData.isVisible = data != null
        if (data != null) {
            binding.tvSaveData.text = getString(
                R.string.cinema_save_data,
                String.format("%.1f", data.downloadedPercent),
                formatFileSize(data.downloadedSize, 1)
            )
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
        btnTrailer.isVisible =
            movie.cinemaUrlData?.trailerUrl?.urls?.filter { it.isNotBlank() }.orEmpty().isNotEmpty()
        if (movie.type == MovieType.CINEMA) {
            val urls = movie.cinemaUrlData?.cinemaUrl?.urls.orEmpty()
            val hdUrls = movie.cinemaUrlData?.hdUrl?.urls.orEmpty()
            btnPlay.isVisible = urls.isNotEmpty() || hdUrls.isNotEmpty()
            viewModel.updateDownloadedData()
        } else {
            canDownload = false // TODO Продумать как грузить серии от сериалов
            requireActivity().invalidateOptionsMenu()
            btnPlay.isVisible = true
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
                append("%s:%.02f".format(getString(R.string.kp), info.ratingImdb).replace(",", "."))
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
        initGenres(info.genre)
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
