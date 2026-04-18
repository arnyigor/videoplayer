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
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.player.getAllCinemaUrls
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.DefaultLoadControl
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
        private const val TAG = "TvPlayerFragment"
        private const val SEEK_STEP_MS = 10_000L
        private const val HIDE_DELAY_MS = 5_000L
        private const val PROGRESS_INTERVAL_MS = 1_000L
    }

    private val viewModel: PlayerViewModel by inject()
    private val moviesInteractor: MoviesInteractor by inject()
    private val historyInteractor: HistoryInteractor by inject()
    private val playerSource: PlayerSource by inject()

    // Получаем аргументы навигации. Timber.log используется для проверки корректности аргументов перехода
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
    private var tvExcludeUrls: Set<String> = emptySet()

    // Fragment lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Timber.d("onCreateView: Inflating layout")
        _binding = FTvPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("onViewCreated: Starting Player Setup. Args -> sharedUrl:${args.sharedUrl.takeIf { it.isNotEmpty() }} , movieId: ${args.movieId}}")

        initPlayer()
        initControls()
        setupDpadListener()
        loadContent()
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause: Saving state and pausing player")
        player?.pause()
        savePosition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.i("onDestroyView: Releasing resources")

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
        Timber.d("initPlayer: Configuring ExoPlayer")

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        player = ExoPlayer.Builder(requireContext())
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                playWhenReady = true
                addListener(playerListener)
                Timber.d("initPlayer: ExoPlayer instance created and attached")
            }

        binding.playerView.apply {
            player = this@TvPlayerFragment.player
            useController = false // Используем кастомные контролы для TV
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Controls
    // ─────────────────────────────────────────────────────────────

    private fun initControls() {
        Timber.d("initControls: Setting up UI clicks")

        binding.btnBack.setOnClickListener {
            Timber.d("Button Clicked: btnBack")
            savePositionAndExit()
        }

        binding.btnRewind.setOnClickListener {
            Timber.d("Button Clicked: btnRewind (Seek Back)")
            seekBackward()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnRewind.requestFocus()
        }

        binding.btnForward.setOnClickListener {
            Timber.d("Button Clicked: btnForward (Seek Forward)")
            seekForward()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnForward.requestFocus()
        }

        binding.btnPrevious.setOnClickListener {
            Timber.d("Button Clicked: btnPrevious")
            previousEpisode()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnPrevious.requestFocus()
        }

        binding.btnNext.setOnClickListener {
            Timber.d("Button Clicked: btnNext")
            nextEpisode()
            // ИСПРАВЛЕНИЕ: возвращаем фокус на ту же кнопку
            binding.btnNext.requestFocus()
        }

        binding.playerView.setOnClickListener {
            Timber.d("UI Interaction: Player View Click -> toggleControls")
            toggleControls()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0) {
                        binding.tvCurrentTime.text = formatTime((progress.toLong() * duration) / 1000)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                Timber.d("UI Interaction: User started seeking")
                stopProgressUpdates()
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = player?.duration ?: return
                val newPos = (sb.progress.toLong() * duration) / 1000
                Timber.d("UI Interaction: Seeking to position $newPos ms")
                player?.seekTo(newPos)
                startProgressUpdates()
                scheduleHide()
            }
        })
    }

    // ИСПРАВЛЕНИЕ: Правильное управление видимостью и доступностью кнопок
    private fun updateEpisodeNavigationVisibility(episodeCount: Int) {
        val showNavigation = episodeCount > 1
        Timber.d("updateEpisodeNavigationVisibility: count=$episodeCount, visible=$showNavigation")

        binding.btnPrevious.isVisible = showNavigation
        binding.btnNext.isVisible = showNavigation

        // НОВОЕ: обновляем доступность кнопок при отображении панели управления
        updateNavigationButtonsState()
    }

    // НОВЫЙ МЕТОД: обновление состояния кнопок навигации
    private fun updateNavigationButtonsState() {
        val p = player ?: return
        Timber.d("updateNavigationButtonsState: hasPrevious=${p.hasPreviousMediaItem()}, hasNext=${p.hasNextMediaItem()}")

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
            Timber.d("Player State Changed: ${stateName(state)}")
            when (state) {
                Player.STATE_BUFFERING -> showLoading(true)
                Player.STATE_READY -> {
                    Timber.d("Player is READY to play")
                    showLoading(false)
                    updateDuration()
                    startProgressUpdates()
                    scheduleHide()
                    // НОВОЕ: обновляем состояние кнопок
                    updateNavigationButtonsState()
                }
                Player.STATE_ENDED -> {
                    Timber.d("Playback ENDED")
                    showLoading(false)
                    stopProgressUpdates()
                    if (player?.hasNextMediaItem() == true) {
                        Timber.d("Auto-playing next media item")
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
            Timber.d("Playback playing changed: $isPlaying")
            if (isPlaying) startProgressUpdates() else stopProgressUpdates()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "ExoPlayer error occurred. Message: ${error.message}")

            val errorUrl = error.localizedMessage ?: ""
            val movie = currentMovie

            if (movie != null) {
                // Логика исключения битых ссылок
                tvExcludeUrls = tvExcludeUrls + errorUrl
                Timber.w("URL excluded due to error: $errorUrl. New exclusion list size: ${tvExcludeUrls.size}")

                val hasMoreUrls = when (movie.type) {
                    MovieType.CINEMA -> movie.getAllCinemaUrls().any { it.isNotBlank() && it !in tvExcludeUrls }
                    MovieType.SERIAL -> {
                        allEpisodes.any { ep ->
                            (ep.hls.isNotBlank() && ep.hls !in tvExcludeUrls) ||
                                    (ep.dash.isNotBlank() && ep.dash !in tvExcludeUrls)
                        }
                    }
                    else -> false
                }

                if (hasMoreUrls) {
                    Timber.i("Attempting to retry playback with alternative URL")
                    toast(getString(R.string.try_open_next_link))
                    when (movie.type) {
                        MovieType.CINEMA -> playCinema(movie)
                        MovieType.SERIAL -> playSerial(movie, args.seasonIndex, args.episodeIndex)
                        else -> showError("Ошибка воспроизведения")
                    }
                } else if (when (movie.type) {
                        MovieType.CINEMA -> movie.getAllCinemaUrls().all { it.isBlank() || it in tvExcludeUrls }
                        MovieType.SERIAL -> allEpisodes.all { ep ->
                            (ep.hls.isBlank() || ep.hls in tvExcludeUrls) &&
                                    (ep.dash.isBlank() || ep.dash in tvExcludeUrls)
                        }
                        else -> true
                    }) {
                    // Все ссылки пусты или недоступны - запускаем обновление
                    Timber.e("All URLs exhausted for movie: ${movie.title}")
                    toast(getString(R.string.update_available_title))
                    findNavController().navigate(R.id.actionToHome)
                } else {
                    showError("Ошибка: ${error.localizedMessage.orEmpty()}")
                }
            } else {
                showError("Ошибка: ${error.localizedMessage.orEmpty()}")
            }
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

        // Вспомогательный метод для читаемого логов состояний плеера
        private fun stateName(state: Int): String = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }

    // ОБНОВИТЕ метод showControls
    private fun showControls() {
        Timber.d("showControls: UI Controls visible")
        binding.controlsGroup.visibility = View.VISIBLE

        if (allEpisodes.isNotEmpty()) {
            binding.tvEpisodeInfo.visibility = View.VISIBLE
        }

        // НОВОЕ: обновляем состояние кнопок при показе панели
        updateNavigationButtonsState()

        binding.btnRewind.requestFocus()
    }

    private fun setupDpadListener() {
        Timber.d("setupDpadListener: Configuring remote control inputs")
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            val keyName = when(keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
                KeyEvent.KEYCODE_ENTER -> "ENTER"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MEDIA_PLAY_PAUSE"
                KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "MEDIA_REWIND"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MEDIA_FAST_FORWARD"
                KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
                KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
                KeyEvent.KEYCODE_MEDIA_NEXT -> "MEDIA_NEXT"
                KeyEvent.KEYCODE_CHANNEL_UP -> "CHANNEL_UP"
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "MEDIA_PREVIOUS"
                KeyEvent.KEYCODE_CHANNEL_DOWN -> "CHANNEL_DOWN"
                KeyEvent.KEYCODE_BACK -> "BACK"
                else -> "OTHER($keyCode)"
            }

            Timber.d("Remote Key Pressed: $keyName")

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

        Timber.d("loadContent: Attempting to load media. SharedUrl present: ${sharedUrl != null}, MovieId: $movieId")

        when {
            movieId != null -> loadMovieAndPlay(movieId)
            sharedUrl != null -> {
                Timber.i("Loading content via Shared URL")
                mediaLoaded = true
                playUrl(sharedUrl, "Video")
                hideEpisodeNavigation()
            }
            else -> {
                Timber.e("loadContent: No data provided in arguments (SharedUrl or MovieId)")
                showError(getString(R.string.error_loading_data))
            }
        }
    }

    private fun loadMovieAndPlay(movieId: Long) {
        Timber.d("loadMovieAndPlay: Fetching movie details for ID=$movieId")

        viewLifecycleOwner.lifecycleScope.launch {
            moviesInteractor.getMovie(movieId).collectLatest { result ->
                when (result) {
                    is DataResult.Success -> {
                        if (!mediaLoaded) {
                            mediaLoaded = true
                            currentMovie = result.result
                            Timber.d("loadMovieAndPlay: Movie loaded successfully. Title: ${result.result.title}")
                            addToHistory(movieId)
                            playMovie(result.result)
                        } else {
                            Timber.w("loadMovieAndPlay: Media already loaded, ignoring new data")
                        }
                    }
                    is DataResult.Error -> {
                        Timber.e(result.throwable, "Failed to load movie ID=$movieId")
                        showError(getString(R.string.error_loading_data))
                    }
                }
            }
        }
    }

    private fun addToHistory(movieId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            historyInteractor.addToViewHistory(movieId).collectLatest { result ->
                when (result) {
                    is DataResult.Success -> Timber.d("Added to history: movieId=$movieId")
                    is DataResult.Error -> Timber.e(result.throwable, "Failed to add to history for movieId=$movieId")
                }
            }
        }
    }

    private fun playMovie(movie: Movie) {
        when (movie.type) {
            MovieType.CINEMA -> {
                Timber.i("Playing Cinema Movie: ${movie.title}")
                playCinema(movie)
                hideEpisodeNavigation()
            }
            MovieType.SERIAL -> {
                Timber.i("Playing Serial Episode: S${args.seasonIndex} E${args.episodeIndex}")
                playSerial(movie, args.seasonIndex, args.episodeIndex)
            }
            else -> showError("Неподдерживаемый тип")
        }
    }

    private fun playCinema(movie: Movie) {
        val selectedUrl = args.sharedUrl.takeIf { it.isNotBlank() && it !in tvExcludeUrls }

        val url = selectedUrl ?: movie.getAllCinemaUrls().firstOrNull {
            it.isNotBlank() && it !in tvExcludeUrls
        }

        if (url.isNullOrBlank()) {
            Timber.e("playCinema: No valid URL found for ${movie.title} after exclusions")
            showError("Видео недоступно")
            return
        }

        binding.tvEpisodeInfo.visibility = View.GONE
        playUrl(url, movie.title)
    }

    private fun playSerial(movie: Movie, seasonIndex: Int, episodeIndex: Int) {
        Timber.i("playSerial: Preparing to load serial data for S${seasonIndex} E${episodeIndex}")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)
                hideError()

                setSerialUrls(
                    movie = movie,
                    seasonIndex = seasonIndex,
                    episodeIndex = episodeIndex,
                    position = 0L,
                    excludeUrls = tvExcludeUrls
                )

            } catch (e: Exception) {
                Timber.e(e, "Error loading serial data structure")
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
        Timber.d("setSerialUrls: Parsed total episodes count: $size")

        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            currentEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes,
                excludeUrls = excludeUrls
            )

            Timber.d("setSerialUrls: Selected target index: $currentEpisodeIndex")

            updateEpisodeNavigationVisibility(size)
            updateEpisodeInfoFromIndex(currentEpisodeIndex)

            player?.apply {
                prepare()
                seekTo(currentEpisodeIndex, position)
                playWhenReady = true
                Timber.i("ExoPlayer prepared and started for index: $currentEpisodeIndex")
            }
        } else {
            // Логика для случая если у некоторых эпизодов нет ссылок, но есть ссылки у других
            Timber.w("setSerialUrls: Some episodes missing URLs but proceeding anyway if valid sources exist.")

            currentEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes,
                excludeUrls = excludeUrls
            )

            if (currentEpisodeIndex == 0 && player?.mediaItemCount == 0) {
                toast(getString(R.string.episodes_not_found))
                findNavController().navigateUp()
            } else {
                updateEpisodeNavigationVisibility(size)
                updateEpisodeInfoFromIndex(currentEpisodeIndex)
                player?.apply {
                    prepare()
                    seekTo(currentEpisodeIndex, position)
                    playWhenReady = true
                }
            }
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

                // Логика выбора URL с приоритетом HLS и учетом исключений
                val url = when {
                    excludeUrls.isEmpty() -> episode.hls.ifBlank { episode.dash }
                    !excludeUrls.contains(episode.hls) -> episode.hls
                    !excludeUrls.contains(episode.dash) -> episode.dash
                    else -> episode.hls.ifBlank { episode.dash } // Фолбэк если оба были исключены (редкий кейс, но защита от NPE)
                }

                if (url.isBlank()) {
                    Timber.w("fillPlayerEpisodes: Skipping Episode ${episode.episode} - No valid URL source found")
                    continue
                }

                // Логирование добавления источника
                Timber.d("fillPlayerEpisodes: Adding source for S${s+1} E${e+1}, URL type: ${if(url.contains("m3u8")) "HLS" else "DASH"}")

                val source = playerSource.getSource(
                    url = url,
                    title = episode.title,
                    season = s,
                    episode = e
                )

                if (source != null) {
                    mediaSources.add(source)
                } else {
                    Timber.w("fillPlayerEpisodes: Failed to create ExoPlayer source for Episode ${episode.episode}")
                }
            }
        }

        Timber.d("fillPlayerEpisodes: Created $mediaSources sources out of available episodes")
        player?.setMediaSources(mediaSources)

        return currentIndexEpisode
    }

    private fun playUrl(url: String, title: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.i("playUrl: Preparing source. URL: ${url.take(50)}..., Title: $title")
                showLoading(true)
                hideError()

                val source = playerSource.getSource(url, title)
                if (source != null) {
                    player?.apply {
                        setMediaSource(source)
                        prepare()
                        playWhenReady = true
                        Timber.i("playUrl: Source prepared successfully")
                    }
                } else {
                    showError("Не удалось создать источник видео")
                }
            } catch (e: Exception) {
                Timber.e(e, "playUrl error during preparation for title: $title")
                showError(e.localizedMessage ?: "Ошибка воспроизведения")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Episode navigation
    // ─────────────────────────────────────────────────────────────

    private fun nextEpisode() {
        val p = player ?: return
        Timber.d("nextEpisode: Attempting to seek to next item")

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
        Timber.d("previousEpisode: Attempting to seek to prev item")

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
        val isPlaying = player?.isPlaying == true
        if (isPlaying) {
            player?.pause()
            Timber.d("User toggled: PAUSE")
        } else {
            player?.play()
            Timber.d("User toggled: PLAY")
        }
        showControls()
        scheduleHide()
    }

    private fun seekForward() {
        val p = player ?: return
        val newPos = minOf(p.duration, p.currentPosition + SEEK_STEP_MS)
        Timber.d("seekForward: Moving to $newPos ms")

        p.seekTo(newPos)
        showControls()
        scheduleHide()
        updateProgress()
    }

    private fun seekBackward() {
        val p = player ?: return
        val newPos = maxOf(0L, p.currentPosition - SEEK_STEP_MS)
        Timber.d("seekBackward: Moving to $newPos ms")

        p.seekTo(newPos)
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
        Timber.d("hideControls: UI controls hidden")
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun showLoading(show: Boolean) {
        // Можно добавить логику отображения прогресс-бара, если он есть в layout
    }

    private fun showError(msg: String) {
        Timber.e("UI Error Displayed: $msg")
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
            Timber.d("startProgressUpdates: Coroutine started")
            while (isActive) {
                updateProgress()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        if (progressJob?.isActive == true) {
            progressJob?.cancel()
            Timber.d("stopProgressUpdates: Progress coroutine cancelled")
        }
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
        Timber.d("savePositionAndExit: User requested exit")
        savePosition()
        findNavController().navigateUp()
    }
}
