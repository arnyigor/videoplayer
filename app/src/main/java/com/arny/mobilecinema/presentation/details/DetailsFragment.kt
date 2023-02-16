package com.arny.mobilecinema.presentation.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
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
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.databinding.FDetailsBinding
import com.arny.mobilecinema.domain.models.*
import com.arny.mobilecinema.presentation.player.MovieDownloadService
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.utils.*
import com.bumptech.glide.Glide
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
    private var cinemaDownloaded: Boolean = false

    @Inject
    lateinit var prefs: Prefs
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentMovie: Movie? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private var hasSavedData: Boolean = false
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

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
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
                                lifecycleScope.launch {
                                    currentMovie?.let {
                                        if (it.type == MovieType.CINEMA) {
                                            playerSource.clearDownloaded(it.getCinemaUrl())
                                        }
                                    }
                                }
                                viewModel.clearViewHistory()
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
            currentMovie?.let { movie ->
                val connectionType = getConnectionType(requireContext())
                when {
                    movie.type == MovieType.CINEMA && cinemaDownloaded -> {
                        navigateToPLayer(movie)
                    }
                    connectionType is ConnectionType.WIFI -> {
                        navigateToPLayer(movie)
                    }
                    connectionType is ConnectionType.MOBILE -> {
                        alertDialog(
                            title = getString(R.string.attention),
                            content = getString(R.string.mobile_network_play),
                            btnOkText = getString(android.R.string.ok),
                            btnCancelText = getString(android.R.string.cancel),
                            onConfirm = { navigateToPLayer(movie) }
                        )
                    }
                    connectionType is ConnectionType.NONE -> {
                        toast(getString(R.string.internet_connection_error))
                    }
                    else -> {
                        navigateToPLayer(movie)
                    }
                }
            }
        }
        btnCache.setOnClickListener {
            onBtnCacheClick()
        }
    }

    private fun onBtnCacheClick() {
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

    private fun showDialogCache(){
        alertDialog(
            title = getString(R.string.cache_attention),
            content = getString(R.string.cache_description),
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = {
                sendServiceMessage(
                    Intent(requireContext(), MovieDownloadService::class.java),
                    AppConstants.ACTION_CACHE_MOVIE,
                ) {
                    val url = currentMovie?.getCinemaUrl()
                    putString(AppConstants.SERVICE_PARAM_CACHE_URL, url)
                    putString(AppConstants.SERVICE_PARAM_CACHE_TITLE, currentMovie?.title)
                }
            }
        )
    }

    private fun navigateToPLayer(movie: Movie) {
        findNavController().navigate(
            DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                path = null,
                movie = movie,
                seasonIndex = currentSeasonPosition,
                episodeIndex = currentEpisodePosition
            )
        )
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.movie.collectLatest { movie ->
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
    }

    private suspend fun onMovieLoaded(movie: Movie) {
        currentMovie = movie
        viewModel.loadSaveData(movie.dbId)
        updateSpinData(movie)
        initUI(movie)
        initButtons(movie)
    }

    private suspend fun initButtons(movie: Movie) = with(binding) {
        if (movie.type == MovieType.CINEMA) {
            val urls = movie.cinemaUrlData?.cinemaUrl?.urls.orEmpty()
            val hdUrls = movie.cinemaUrlData?.hdUrl?.urls.orEmpty()
            btnPlay.isVisible = urls.isNotEmpty() || hdUrls.isNotEmpty()
            val cinemaUrl = movie.getCinemaUrl()
            cinemaDownloaded = playerSource.isDownloaded(cinemaUrl)
            btnCache.isVisible = !cinemaDownloaded
        } else {
            btnCache.isVisible = false
            btnPlay.isVisible = true
        }
    }

    private fun onSaveDataLoaded(saveData: SaveData) {
        when {
            saveData.dbId == currentMovie?.dbId && currentMovie?.type == MovieType.SERIAL -> {
                currentSeasonPosition = saveData.season
                currentEpisodePosition = saveData.episode
                fillSpinners(currentMovie)
                hasSavedData = true
                requireActivity().invalidateOptionsMenu()
            }
            saveData.dbId == currentMovie?.dbId && currentMovie?.type == MovieType.CINEMA -> {
                hasSavedData = true
                requireActivity().invalidateOptionsMenu()
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
        }.toString()
        tvQuality.isVisible = movie.type == MovieType.CINEMA
        tvQuality.text = getString(R.string.quality_format, info.quality)
        tvGenres.text = getString(R.string.genre_format, info.genre.joinToString())
        tvDirectors.text = getString(R.string.directors_format, info.directors.joinToString())
        tvActors.text = getString(R.string.actors_format, info.actors.joinToString())
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
