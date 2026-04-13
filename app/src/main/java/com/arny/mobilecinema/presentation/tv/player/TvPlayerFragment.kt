// presentation/tv/player/TvPlayerFragment.kt
package com.arny.mobilecinema.presentation.tv.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.databinding.FTvPlayerBinding
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.source.MediaSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import timber.log.Timber

class TvPlayerFragment : Fragment(), KoinComponent {

    companion object {
        private const val SEEK_STEP_MS = 10_000L
        private const val HIDE_DELAY_MS = 5_000L
        private const val PROGRESS_INTERVAL_MS = 1_000L
    }

    private val viewModel: PlayerViewModel by inject()
    private val moviesInteractor: MoviesInteractor by inject()
    private val playerSource: PlayerSource by inject()

    private val args: TvPlayerFragmentArgs by navArgs()

    private var _binding: FTvPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var currentMovie: Movie? = null
    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var serialSeasons: List<SerialSeason> = emptyList()
    private var currentEpisodeIndex = 0

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    private var progressJob: Job? = null
    private var mediaLoaded = false

    // Fragment lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FTvPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initPlayer()
        initControls()
        setupDpadListener()
        loadContent()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        savePosition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideHandler.removeCallbacks(hideRunnable)
        stopProgressUpdates()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────
    // Player init
    // ─────────────────────────────────────────────────────────────

    private fun initPlayer() {
        player = ExoPlayer.Builder(requireContext())
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                playWhenReady = true
                addListener(playerListener)
            }

        binding.playerView.apply {
            player = this@TvPlayerFragment.player
            useController = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Controls
    // ─────────────────────────────────────────────────────────────

    private fun initControls() {
        binding.btnBack.setOnClickListener { savePositionAndExit() }

        binding.btnRewind.setOnClickListener {
            seekBackward()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnRewind.requestFocus()
        }

        binding.btnForward.setOnClickListener {
            seekForward()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnForward.requestFocus()
        }

        binding.btnPrevious.setOnClickListener {
            previousEpisode()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnPrevious.requestFocus()
        }

        binding.btnNext.setOnClickListener {
            nextEpisode()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnNext.requestFocus()
        }

        binding.playerView.setOnClickListener { toggleControls() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0) {
                        binding.tvCurrentTime.text =
                            formatTime((progress.toLong() * duration) / 1000)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                stopProgressUpdates()
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = player?.duration ?: return
                val newPos = (sb.progress.toLong() * duration) / 1000
                player?.seekTo(newPos)
                startProgressUpdates()
                scheduleHide()
            }
        })
    }

    // ИСПРАВЛЕНИЕ: Правильное управление видимостью и доступностью кнопок
    private fun updateEpisodeNavigationVisibility(episodeCount: Int) {
        val showNavigation = episodeCount > 1

        binding.btnPrevious.isVisible = showNavigation
        binding.btnNext.isVisible = showNavigation

        // НОВОЕ: обновляем доступность кнопок при отображении панели управления
        updateNavigationButtonsState()
    }

    // НОВЫЙ МЕТОД: обновление состояния кнопок навигации
    private fun updateNavigationButtonsState() {
        val p = player ?: return

        // Предыдущая серия доступна?
        binding.btnPrevious.apply {
            isEnabled = p.hasPreviousMediaItem()
            alpha = if (isEnabled) 1.0f else 0.5f
        }

        // Следующая серия доступна?
        binding.btnNext.apply {
            isEnabled = p.hasNextMediaItem()
            alpha = if (isEnabled) 1.0f else 0.5f
        }
    }

    // ОБНОВИТЕ метод onMediaItemTransition
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> showLoading(true)
                Player.STATE_READY -> {
                    showLoading(false)
                    updateDuration()
                    startProgressUpdates()
                    scheduleHide()
                    // НОВОЕ: обновляем состояние кнопок
                    updateNavigationButtonsState()
                }
                Player.STATE_ENDED -> {
                    showLoading(false)
                    stopProgressUpdates()
                    if (player?.hasNextMediaItem() == true) {
                        player?.seekToNextMediaItem()
                        player?.play()
                    } else {
                        toast(getString(R.string.playback_complete))
                    }
                }
                Player.STATE_IDLE -> {
                    showLoading(false)
                    stopProgressUpdates()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressUpdates() else stopProgressUpdates()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "ExoPlayer error")
            showError("Ошибка: ${error.localizedMessage.orEmpty()}")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            val newIndex = player?.currentMediaItemIndex ?: 0
            Timber.d("Media transition: index $currentEpisodeIndex -> $newIndex, reason=$reason")

            if (newIndex != currentEpisodeIndex) {
                currentEpisodeIndex = newIndex
                updateEpisodeInfoFromIndex(newIndex)
                updateDuration()
                // НОВОЕ: обновляем состояние кнопок при смене эпизода
                updateNavigationButtonsState()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            val newIndex = player?.currentMediaItemIndex ?: 0
            if (newIndex != currentEpisodeIndex) {
                currentEpisodeIndex = newIndex
                updateEpisodeInfoFromIndex(newIndex)
                updateDuration()
                // НОВОЕ: обновляем состояние кнопок
                updateNavigationButtonsState()
            }
        }
    }

    // ОБНОВИТЕ метод showControls
    private fun showControls() {
        binding.controlsGroup.visibility = View.VISIBLE
        if (allEpisodes.isNotEmpty()) {
            binding.tvEpisodeInfo.visibility = View.VISIBLE
        }

        // НОВОЕ: обновляем состояние кнопок при показе панели
        updateNavigationButtonsState()

        binding.btnRewind.requestFocus()
    }

    private fun setupDpadListener() {
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    togglePlayPause()
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekBackward()
                    binding.root.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekForward()
                    binding.root.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    showControls()
                    scheduleHide()
                    binding.btnRewind.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    showControls()
                    scheduleHide()
                    binding.btnRewind.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    nextEpisode()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    previousEpisode()
                    true
                }

                KeyEvent.KEYCODE_BACK -> {
                    savePositionAndExit()
                    true
                }

                else -> false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Content loading
    // ─────────────────────────────────────────────────────────────

    private fun loadContent() {
        if (mediaLoaded) return

        val sharedUrl = args.sharedUrl.takeIf { it.isNotBlank() }
        val movieId = args.movieId.takeIf { it > 0L }

        when {
            sharedUrl != null -> {
                mediaLoaded = true
                playUrl(sharedUrl, "Video")
                hideEpisodeNavigation()
            }
            movieId != null -> loadMovieAndPlay(movieId)
            else -> showError(getString(R.string.error_loading_data))
        }
    }

    private fun loadMovieAndPlay(movieId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            moviesInteractor.getMovie(movieId).collectLatest { result ->
                when (result) {
                    is DataResult.Success -> {
                        if (!mediaLoaded) {
                            mediaLoaded = true
                            currentMovie = result.result
                            playMovie(result.result)
                        }
                    }
                    is DataResult.Error -> showError(getString(R.string.error_loading_data))
                }
            }
        }
    }

    private fun playMovie(movie: Movie) {
        when (movie.type) {
            MovieType.CINEMA -> {
                playCinema(movie)
                hideEpisodeNavigation()
            }
            MovieType.SERIAL -> {
                playSerial(movie, args.seasonIndex, args.episodeIndex)
            }
            else -> showError("Неподдерживаемый тип")
        }
    }

    private fun playCinema(movie: Movie) {
        val url = movie.getCinemaUrl()
        if (url.isBlank()) {
            showError("Видео недоступно")
            return
        }
        binding.tvEpisodeInfo.visibility = View.GONE
        playUrl(url, movie.title)
    }

    private fun playSerial(movie: Movie, seasonIndex: Int, episodeIndex: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)
                hideError()

                setSerialUrls(
                    movie = movie,
                    seasonIndex = seasonIndex,
                    episodeIndex = episodeIndex,
                    position = 0L,
                    excludeUrls = emptySet()
                )

            } catch (e: Exception) {
                Timber.e(e, "Error loading serial")
                showError(e.localizedMessage ?: "Ошибка загрузки сериала")
            }
        }
    }

    private suspend fun setSerialUrls(
        movie: Movie,
        seasonIndex: Int?,
        episodeIndex: Int?,
        position: Long,
        excludeUrls: Set<String>
    ) {
        val seasons = movie.seasons
        serialSeasons = seasons.sortedBy { it.id }

        allEpisodes = serialSeasons.flatMap { season ->
            season.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
        }

        val size = allEpisodes.size
        Timber.d("setSerialUrls: total episodes = $size")

        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            currentEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes,
                excludeUrls = excludeUrls
            )

            Timber.d("setSerialUrls: currentEpisodeIndex = $currentEpisodeIndex")

            updateEpisodeNavigationVisibility(size)
            updateEpisodeInfoFromIndex(currentEpisodeIndex)

            player?.apply {
                prepare()
                seekTo(currentEpisodeIndex, position)
                playWhenReady = true
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
        allEpisodes: List<SerialEpisode>,
        excludeUrls: Set<String>
    ): Int {
        var currentIndexEpisode = 0
        val mediaSources = mutableListOf<MediaSource>()

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

                val url = when {
                    excludeUrls.isEmpty() -> episode.hls.ifBlank { episode.dash }
                    !excludeUrls.contains(episode.hls) -> episode.hls
                    !excludeUrls.contains(episode.dash) -> episode.dash
                    else -> episode.hls.ifBlank { episode.dash }
                }

                if (url.isBlank()) {
                    Timber.w("Episode ${episode.episode} has no valid URL")
                    continue
                }

                val source = playerSource.getSource(
                    url = url,
                    title = episode.title,
                    season = s,
                    episode = e
                )

                if (source != null) {
                    mediaSources.add(source)
                } else {
                    Timber.w("Failed to create source for episode ${episode.episode}")
                }
            }
        }

        Timber.d("fillPlayerEpisodes: created ${mediaSources.size} sources")

        player?.setMediaSources(mediaSources)

        return currentIndexEpisode
    }

    private fun playUrl(url: String, title: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)
                hideError()

                val source = playerSource.getSource(url, title)
                if (source != null) {
                    player?.apply {
                        setMediaSource(source)
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    showError("Не удалось создать источник видео")
                }
            } catch (e: Exception) {
                Timber.e(e, "playUrl error")
                showError(e.localizedMessage ?: "Ошибка воспроизведения")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Episode navigation
    // ─────────────────────────────────────────────────────────────

    private fun nextEpisode() {
        val p = player ?: return

        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
            showControls()
            scheduleHide()
        } else {
            toast(getString(R.string.playback_complete))
        }
    }

    private fun previousEpisode() {
        val p = player ?: return

        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            p.play()
            showControls()
            scheduleHide()
        } else {
            toast(getString(R.string.episodes_not_found))
        }
    }

    private fun hideEpisodeNavigation() {
        binding.btnPrevious.isVisible = false
        binding.btnNext.isVisible = false
    }

    private fun updateEpisodeInfoFromIndex(index: Int) {
        if (allEpisodes.isEmpty() || serialSeasons.isEmpty()) {
            binding.tvEpisodeInfo.visibility = View.GONE
            return
        }

        val episode = allEpisodes.getOrNull(index)
        if (episode == null) {
            binding.tvEpisodeInfo.visibility = View.GONE
            return
        }

        var seasonNum = 1
        var episodeNumInSeason = 1
        var episodeCounter = 0

        for ((sIdx, season) in serialSeasons.withIndex()) {
            val sortedEpisodes = season.episodes.sortedBy { ep ->
                findByGroup(ep.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }

            for ((eIdx, ep) in sortedEpisodes.withIndex()) {
                if (episodeCounter == index) {
                    seasonNum = season.id ?: (sIdx + 1)
                    episodeNumInSeason = findByGroup(ep.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull()
                        ?: (eIdx + 1)
                    break
                }
                episodeCounter++
            }
        }

        showEpisodeInfo(seasonNum, episodeNumInSeason, episode.title ?: "")
    }

    // ─────────────────────────────────────────────────────────────
    // Playback controls
    // ─────────────────────────────────────────────────────────────

    private fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
        showControls()
        scheduleHide()
    }

    private fun seekForward() {
        player?.let { p ->
            p.seekTo(minOf(p.duration, p.currentPosition + SEEK_STEP_MS))
        }
        showControls()
        scheduleHide()
        updateProgress()
    }

    private fun seekBackward() {
        player?.let { p ->
            p.seekTo(maxOf(0L, p.currentPosition - SEEK_STEP_MS))
        }
        showControls()
        scheduleHide()
        updateProgress()
    }

    // ─────────────────────────────────────────────────────────────
    // UI: controls visibility
    // ─────────────────────────────────────────────────────────────

    private fun toggleControls() {
        if (binding.controlsGroup.isVisible) hideControls()
        else {
            showControls()
            scheduleHide()
        }
    }

    private fun hideControls() {
        binding.controlsGroup.visibility = View.GONE
        binding.tvEpisodeInfo.visibility = View.GONE
        binding.root.requestFocus()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun showLoading(show: Boolean) {
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun showEpisodeInfo(seasonNum: Int, episodeNum: Int, title: String) {
        binding.tvEpisodeInfo.text = buildString {
            append("Сезон $seasonNum • Серия $episodeNum")
            if (title.isNotBlank()) append("\n$title")
        }
        binding.tvEpisodeInfo.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // Progress bar updates
    // ─────────────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                updateProgress()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateProgress() {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        val pos = p.currentPosition

        binding.seekBar.max = 1000
        binding.seekBar.progress = ((pos * 1000) / duration).toInt()
        binding.tvCurrentTime.text = formatTime(pos)
    }

    private fun updateDuration() {
        val dur = player?.duration?.takeIf { it > 0 } ?: return
        binding.tvDuration.text = formatTime(dur)
        binding.seekBar.max = 1000
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    // ─────────────────────────────────────────────────────────────
    // Save position
    // ─────────────────────────────────────────────────────────────

    private fun savePosition() {
        val exo = player ?: return
        val pos = exo.currentPosition
        val movie = currentMovie ?: return
        if (pos <= 0) return

        val (seasonIdx, episodeIdx) = getSeasonEpisodeFromIndex(currentEpisodeIndex)

        when (movie.type) {
            MovieType.CINEMA ->
                viewModel.saveMoviePosition(movie.dbId, pos, 0, 0)
            MovieType.SERIAL ->
                viewModel.saveMoviePosition(movie.dbId, pos, seasonIdx, episodeIdx)
            else -> {}
        }
    }

    private fun getSeasonEpisodeFromIndex(playlistIndex: Int): Pair<Int, Int> {
        var counter = 0

        for ((sIdx, season) in serialSeasons.withIndex()) {
            val sortedEpisodes = season.episodes.sortedBy { ep ->
                findByGroup(ep.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }

            for ((eIdx, _) in sortedEpisodes.withIndex()) {
                if (counter == playlistIndex) {
                    return sIdx to eIdx
                }
                counter++
            }
        }

        return 0 to 0
    }

    private fun savePositionAndExit() {
        savePosition()
        findNavController().navigateUp()
    }
}