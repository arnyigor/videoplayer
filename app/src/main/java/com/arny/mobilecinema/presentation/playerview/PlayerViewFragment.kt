package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateLanguagesList
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.util.Util
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerViewFragment : Fragment(R.layout.f_player_view) {
    private var qualityVisible: Boolean = false
    private var langVisible: Boolean = false
    private var mediaItemIndex: Int = 0
    private val args: PlayerViewFragmentArgs by navArgs()

    private companion object {
        const val MIN_BUFFER_SEC = 5L
        const val MAX_BUFFER_SEC = 60L
    }

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: PlayerViewModel by viewModels { vmFactory }
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
    private var _binding: FPlayerViewBinding? = null
    private val binding
        get() = _binding!!

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
        _binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setPlayData(args.path, args.movie, args.seasonIndex, args.episodeIndex)
        binding.progressBar.isVisible = true
        observeState()
        initListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initListener() {
        binding.ivQuality.setOnClickListener { qualityPopUp?.show() }
        binding.ivLang.setOnClickListener { langPopUp?.show() }
        binding.ivResizes.setOnClickListener {
            changeResize()
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val movie = state.movie
                    binding.tvTitle.text = movie?.title ?: getString(R.string.no_movie_title)
                    setMediaSources(
                        path = state.path,
                        position = state.position,
                        movie = movie,
                        seasonIndex = state.season,
                        episodeIndex = state.episode
                    )
                }
            }
        }
    }

    private suspend fun setMediaSources(
        path: String?,
        position: Long,
        movie: Movie?,
        seasonIndex: Int? = 0,
        episodeIndex: Int? = 0
    ) {
        when {
            !path.isNullOrBlank() -> {
                try {
                    val mediaSource =
                        playerSource.getSource(path, getString(R.string.no_movie_title))
                    setPlayerSource(position, mediaSource)
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            movie != null && movie.type == MovieType.CINEMA -> setCinemaUrls(movie, position)
            movie != null && movie.type == MovieType.SERIAL -> setSerialUrls(
                movie = movie,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex
            )

            else -> {
                toast(getString(R.string.path_not_found))
                findNavController().navigateUp()
            }
        }
    }

    private suspend fun setSerialUrls(movie: Movie, seasonIndex: Int?, episodeIndex: Int?) {
        val serialSeasons = movie.seasons.sortedBy { it.id }
        val allEpisodes = serialSeasons.flatMap {
            it.episodes.sortedBy { episode ->
                // fixme может возникуть ошибка
                episode.episode.toIntOrNull()
            }
        }
        val size = allEpisodes.size
        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            val startEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes
            )
            binding.playerView.setShowNextButton(size > 0)
            binding.playerView.setShowPreviousButton(size > 0)
            player?.apply {
                if (startEpisodeIndex > 0) {
                    player?.seekTo(startEpisodeIndex, 0)
                }
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
            val episodes = season.episodes.sortedBy { episode -> episode.episode.toIntOrNull() }
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
            toast(getFullError(error))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState(playbackState)
        }

        override fun onTracksChanged(tracks: Tracks) {
            val index = player?.currentMediaItemIndex ?: 0
            if (index != mediaItemIndex) {
                mediaItemIndex = index
                val metadata = player?.currentMediaItem?.mediaMetadata
                val bundle = metadata?.extras
                val title = metadata?.title.toString()
                val season = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
                val episode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0
                viewModel.saveCurrentSerialPosition(season, episode, args.movie?.dbId)
                setCurrentTitle(title)
                setupPopupMenus = true
            }
        }
    }

    private suspend fun setCinemaUrls(
        movie: Movie,
        position: Long
    ) {
        val hdUrl = movie.cinemaUrlData?.hdUrl?.urls?.firstOrNull()
        val cinemaUrl = movie.cinemaUrlData?.cinemaUrl?.urls?.firstOrNull()
        val url = when {
            !hdUrl.isNullOrBlank() -> hdUrl
            !cinemaUrl.isNullOrBlank() -> cinemaUrl
            else -> ""
        }
        url.takeIf { it.isNotBlank() }?.let {
            try {
                setPlayerSource(position, playerSource.getSource(it, movie.title))
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
            window.hideSystemUI()
        }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.show()
            window.showSystemUI()
        }
        player?.let { savePosition(it.currentPosition) }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun preparePlayer() {
        val min = prefs.get<String>(getString(R.string.pref_key_player_min_cache_sec))
            ?.toLongOrNull() ?: MIN_BUFFER_SEC
        val max = prefs.get<String>(getString(R.string.pref_key_player_max_cache_sec))
            ?.toLongOrNull() ?: MAX_BUFFER_SEC
        with(binding) {
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */
                        secToMs(min).toInt(),
                        /* maxBufferMs = */
                        secToMs(max).toInt(),
                        /* bufferForPlaybackMs = */
                        secToMs(1).toInt(),
                        /* bufferForPlaybackAfterRebufferMs = */
                        secToMs(1).toInt()
                    )
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
            player?.playWhenReady = true
            playerView.player = player
            playerView.resizeMode = resizeModes[resizeIndex]
            playerView.setControllerVisibilityListener {
                if (isVisible) {
                    if (it == View.VISIBLE) {
                        tvTitle.isVisible = true
                        ivQuality.isVisible = qualityVisible
                        ivResizes.isVisible = true
                        ivLang.isVisible = langVisible
                        activity?.window?.showSystemUI()
                    } else {
                        ivResizes.isVisible = false
                        ivQuality.isVisible = false
                        tvTitle.isVisible = false
                        ivLang.isVisible = false
                        activity?.window?.hideSystemUI()
                    }
                }
            }
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
        viewModel.saveCurrentPosition(position, args.movie?.dbId)
    }
}