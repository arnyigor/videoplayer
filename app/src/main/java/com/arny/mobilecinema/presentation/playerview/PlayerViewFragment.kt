package com.arny.mobilecinema.presentation.playerview

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateLanguagesList
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.player.getTrailerUrl
import com.arny.mobilecinema.presentation.utils.getOrientation
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.isPiPAvailable
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.github.vkay94.dtpv.youtube.YouTubeOverlay
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.util.Util
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class PlayerViewFragment : Fragment(R.layout.f_player_view), OnPictureInPictureListener {
    private var title: String = ""
    private var position: Long = 0L
    private var season: Int = 0
    private var episode: Int = 0
    private var qualityVisible: Boolean = false
    private var langVisible: Boolean = false
    private var mediaItemIndex: Int = 0
    private val args: PlayerViewFragmentArgs by navArgs()

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: PlayerViewModel by viewModels { vmFactory }
    @Inject
    lateinit var prefs: Prefs
    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private var qualityPopUp: PopupMenu? = null
    private var langPopUp: PopupMenu? = null
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var resizeIndex = 0
    private var setupPopupMenus = true
    private lateinit var binding: FPlayerViewBinding

    @Inject
    lateinit var playerSource: PlayerSource

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = FPlayerViewBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progressBar.isVisible = true
        observeState()
        initListener()
        requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                binding.playerView.showController()
            } else {
                binding.playerView.hideController()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setScreenRotIconVisible(newConfig.orientation)
    }

    private fun setScreenRotIconVisible(orientation: Int) {
        binding.ivScreenRotation.isVisible = orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun initListener() = with(binding) {
        ivQuality.setOnClickListener { qualityPopUp?.show() }
        ivLang.setOnClickListener { langPopUp?.show() }
        ivResizes.setOnClickListener { changeResize() }
        ivBack.setOnClickListener {
            findNavController().popBackStack()
        }
        ivScreenRotation.setOnClickListener {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun changeResize() {
        resizeIndex += 1
        if (resizeIndex > resizeModes.size - 1) {
            resizeIndex = 0
        }
        binding.playerView.resizeMode = resizeModes[resizeIndex]
    }

    private fun observeState() {
        launchWhenCreated {
            viewModel.uiState.collect { state ->
                if (state.path != null || state.movie != null) {
                    val movie = state.movie
                    val (season, episode) = getSerialPosition(state.season, state.episode)
                    setCurrentTitle(getTitle(movie?.title))
                    setMediaSources(
                        path = state.path,
                        position = getPosition(state.position),
                        movie = movie,
                        seasonIndex = season,
                        episodeIndex = episode,
                        isTrailer = state.isTrailer
                    )
                }
            }
        }
    }

    private fun getPosition(statePosition: Long) =
        if (statePosition >= position) statePosition else position

    private fun getSerialPosition(stateSeason: Int?, stateEpisode: Int?): Pair<Int, Int> {
        val cSeason: Int = if (stateSeason != null && stateSeason > season)
            stateSeason else season
        val cEpisode: Int = if (stateEpisode != null && stateEpisode > episode)
            stateEpisode else episode
        return cSeason to cEpisode
    }

    private fun getTitle(movieTitle: String?): String {
        val savedTitle = this.title
        return when {
            !movieTitle.isNullOrBlank() && savedTitle.isNotBlank() && savedTitle != "null" ->
                savedTitle
            !movieTitle.isNullOrBlank() && (savedTitle.isBlank() || savedTitle == "null") ->
                movieTitle
            else -> getString(R.string.no_movie_title)
        }
    }

    private suspend fun setMediaSources(
        path: String?,
        position: Long,
        movie: Movie?,
        seasonIndex: Int? = 0,
        episodeIndex: Int? = 0,
        isTrailer: Boolean = false
    ) {
        when {
            movie == null && !path.isNullOrBlank() -> {
                try {
                    setPlayerSource(
                        position = position,
                        source = playerSource.getSource(path, getString(R.string.no_movie_title)),
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            movie != null && isTrailer -> setTrailerUrl(movie)
            movie != null && movie.type == MovieType.CINEMA -> setCinemaUrls(movie, position)
            movie != null && movie.type == MovieType.SERIAL -> setSerialUrls(
                movie = movie,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                position = position
            )

            else -> {
                toast(getString(R.string.path_not_found))
                findNavController().navigateUp()
            }
        }
    }

    private suspend fun setSerialUrls(
        movie: Movie,
        seasonIndex: Int?,
        episodeIndex: Int?,
        position: Long
    ) {
        val seasons = movie.seasons
        val serialSeasons = seasons.sortedBy { it.id }
        val allEpisodes = serialSeasons.flatMap {
            it.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
        }
        val size = allEpisodes.size
        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            val startEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons.toList(),
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes
            )
            binding.playerView.setShowNextButton(size > 0)
            binding.playerView.setShowPreviousButton(size > 0)
            player?.apply {
                player?.seekTo(startEpisodeIndex, position)
                addListener(listener)
                prepare()
            }
        } else {
            toast(getString(R.string.episodes_not_found))
            findNavController().navigateUp()
        }
    }

    private suspend fun fillPlayerEpisodes(
        serialSeasons: List<SerialSeason>,
        seasonIndex: Int?,
        episodeIndex: Int?,
        allEpisodes: List<SerialEpisode>
    ): Int {
        var currentIndexEpisode = 0
        for ((s, season) in serialSeasons.withIndex()) {
            val episodes = season.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
            for ((e, episode) in episodes.withIndex()) {
                if (seasonIndex == s && episodeIndex == e) {
                    currentIndexEpisode = allEpisodes.indexOf(episode)
                    if (currentIndexEpisode == -1) {
                        currentIndexEpisode = 0
                    }
                }
                playerSource.getSource(
                    url = episode.dash.ifBlank { episode.hls },
                    title = episode.title,
                    season = s,
                    episode = e
                )?.let { source ->
                    player?.addMediaSource(source)
                }
            }
        }
        return currentIndexEpisode
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            binding.progressBar.isVisible = false
            when (getConnectionType(requireContext())) {
                ConnectionType.NONE -> {
                    toast(getString(R.string.internet_connection_error))
                }
                else -> toast(getFullError(error))
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState(playbackState)
        }

        override fun onTracksChanged(tracks: Tracks) {
            val index = player?.currentMediaItemIndex ?: 0
            if (index != mediaItemIndex) {
                mediaItemIndex = index
                player?.let {
                    val metadata = it.currentMediaItem?.mediaMetadata
                    this@PlayerViewFragment.title = metadata?.title.toString()
                    setCurrentTitle(title)
                }
                setupPopupMenus = true
            }
        }
    }

    private fun saveSerialPosition(exoPlayer: ExoPlayer) {
        val episodePosition = exoPlayer.currentPosition
        val metadata = exoPlayer.currentMediaItem?.mediaMetadata
        val bundle = metadata?.extras
        val season = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
        val episode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0
        this.season = season
        this.episode = episode
        viewModel.saveCurrentSerialPosition(args.movie?.dbId, season, episode, episodePosition)
    }

    private suspend fun setCinemaUrls(
        movie: Movie,
        position: Long
    ) {
        val url = movie.getCinemaUrl()
        url.takeIf { it.isNotBlank() }?.let { cinemaUrl ->
            try {
                setPlayerSource(
                    position, playerSource.getSource(
                        url = cinemaUrl,
                        title = movie.title
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        } ?: kotlin.run {
            toast(getString(R.string.path_not_found))
            findNavController().navigateUp()
        }
    }

    private suspend fun setTrailerUrl(movie: Movie) {
        movie.getTrailerUrl().takeIf { it.isNotBlank() }?.let { url ->
            try {
                setPlayerSource(
                    position = 0,
                    source = playerSource.getSource(
                        url = url,
                        title = movie.title
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        } ?: kotlin.run {
            toast(getString(R.string.path_not_found))
            findNavController().navigateUp()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.hide()
        }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.show()
        }
        player?.let { exoPlayer ->
            savePosition(exoPlayer.currentPosition)
            saveSerialPosition(exoPlayer)
        }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer()
        }
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun preparePlayer() {
        with(binding) {
            val loadControl =
                DefaultLoadControl.Builder()
                .setBufferDurationsMs(64 * 1024, 128 * 1024, 1024, 1024)
                .build()
            trackSelector = DefaultTrackSelector(requireContext(), AdaptiveTrackSelection.Factory())
            trackSelector?.parameters?.buildUpon()?.setPreferredAudioLanguage("rus")
            player = ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
//                .setBandwidthMeter(DefaultBandwidthMeter.Builder(requireContext()).build())
                .setRenderersFactory(DefaultRenderersFactory(requireContext()))
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(secToMs(5))
                .setSeekForwardIncrementMs(secToMs(5))
                .build()
                .apply {
                    playWhenReady = true
                }
            youtubeOverlay.performListener(object : YouTubeOverlay.PerformListener {
                override fun onAnimationStart() {
                    youtubeOverlay.visibility = View.VISIBLE
                }

                override fun onAnimationEnd() {
                    youtubeOverlay.visibility = View.GONE
                }
            }).playerView(playerView)
            playerView.controller(youtubeOverlay)
            youtubeOverlay.player(player!!)
            playerView.player = player
            playerView.resizeMode = resizeModes[resizeIndex]
            playerView.setControllerVisibilityListener { visibility ->
                if (isVisible) {
                    changeVisible(visibility == View.VISIBLE)
                }
            }
            viewModel.setPlayData(
                path = args.path,
                movie = args.movie,
                seasonIndex = args.seasonIndex,
                episodeIndex = args.episodeIndex,
                trailer = args.isTrailer
            )
        }
    }

    private fun pipMode() {
        if (requireContext().isPiPAvailable()) {
            val builder = PictureInPictureParams.Builder().apply {
                setAutoEnabled(this)
            }
            requireActivity().enterPictureInPictureMode(builder.build())
        }
    }

    override fun onPiPMode(isInPipMode: Boolean) {
        // Change visible elements
    }

    private fun setAutoEnabled(params: PictureInPictureParams.Builder): PictureInPictureParams.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setAutoEnterEnabled(true)
        }
        return params
    }

    override fun enterPiPMode() {
        pipMode()
    }

    override fun isPiPAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            pipMode()
            true
        } else {
            false
        }

    private fun FPlayerViewBinding.changeVisible(visible: Boolean) {
        if (visible) {
            tvTitle.isVisible = true
            ivQuality.isVisible = qualityVisible
            ivResizes.isVisible = true
            ivScreenRotation.isVisible = true
            ivBack.isVisible = true
            ivLang.isVisible = langVisible
            activity?.window?.showSystemUI()
            setScreenRotIconVisible(getOrientation())
        } else {
            ivResizes.isVisible = false
            ivScreenRotation.isVisible = false
            ivQuality.isVisible = false
            ivBack.isVisible = false
            tvTitle.isVisible = false
            ivLang.isVisible = false
            activity?.window?.hideSystemUI()
        }
    }

    private fun setPlayerSource(position: Long = 0, source: MediaSource?) {
        player?.apply {
            source?.let {
                setMediaSource(source)
                seekTo(position)
                addListener(listener)
                prepare()
            }
        }
    }

    private fun setCurrentTitle(title: String?) {
        if (!title.isNullOrBlank() && title != "null") {
            binding.tvTitle.text = title.toString()
        } else {
            binding.tvTitle.text = getString(R.string.no_movie_title)
        }
    }

    private fun setUpPopups() {
        if (setupPopupMenus) {
            setupPopupMenus = false
            trackSelector?.generateLanguagesList(requireContext())?.let { list ->
                langVisible = list.size > 1
                binding.ivLang.isVisible = langVisible
                if (langVisible) {
                    langPopUp = PopupMenu(requireContext(), binding.ivQuality)
                    for ((i, videoQuality) in list.withIndex()) {
                        langPopUp?.menu?.add(0, i, 0, videoQuality.first)
                    }
                    langPopUp?.setOnMenuItemClickListener { menuItem ->
                        setLang(list[menuItem.itemId].second)
                        true
                    }
                }
            }
            trackSelector?.generateQualityList(requireContext())?.let { list ->
                qualityVisible = list.size > 1
                binding.ivQuality.isVisible = qualityVisible
                if (qualityVisible) {
                    qualityPopUp = PopupMenu(requireContext(), binding.ivQuality)
                    for ((i, videoQuality) in list.withIndex()) {
                        qualityPopUp?.menu?.add(0, i, 0, videoQuality.first)
                    }
                    qualityPopUp?.setOnMenuItemClickListener { menuItem ->
                        setQuality(list[menuItem.itemId].second)
                        true
                    }
                    setQuality(list[0].second)
                }
            }
        }
    }
    /*    private fun setQualityByConnection(list: ArrayList<Pair<String, TrackSelectionOverride>>) {
            val connectionType = getConnectionType(requireContext())
            val groupList = list.map { it.second }.map { it.mediaTrackGroup }
            val formats = groupList.mapIndexed { index, trackGroup -> trackGroup.getFormat(index) }
            val bitratesKbps = formats.map { it.bitrate.div(1024) }
            Timber.d("current QualityId:$qualityId")
            val newId =
                bitratesKbps.indexOfLast { it < connectionType.speedKbps }.takeIf { it >= 0 } ?: 0
            Timber.d("connectionType:$connectionType")
            Timber.d("bitrates:$bitratesKbps")
            Timber.d("selected qualityId:$qualityId")
            if (newId > qualityId) {// Check buufering time
                qualityId = newId
                Timber.d("set new qualityId:$qualityId")
                list.getOrNull(qualityId)?.second?.let { setQuality(it) }
            }
        }*/
    private fun setQuality(trackSelectionOverride: TrackSelectionOverride) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .clearOverrides()
                .addOverride(trackSelectionOverride)
                .setTunnelingEnabled(true)
                .build()
        }
    }

    private fun setLang(override: TrackSelectionOverride) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .clearOverrides()
                .addOverride(override)
                .build()
        }
    }

    private fun updateState(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                binding.progressBar.isVisible = true
            }
            Player.STATE_ENDED -> {
                binding.progressBar.isVisible = false
            }

            Player.STATE_READY -> {
                binding.progressBar.isVisible = false
                setUpPopups()
            }
            else -> {}
        }
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.stop()
            it.release()
            player = null
        }
    }

    private fun savePosition(position: Long) {
        this.position = position
        viewModel.saveCurrentCinemaPosition(position, args.movie?.dbId)
    }
}