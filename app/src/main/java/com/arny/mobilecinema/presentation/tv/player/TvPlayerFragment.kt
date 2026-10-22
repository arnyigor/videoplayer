// presentation/tv/player/TvPlayerFragment.kt
package com.arny.mobilecinema.presentation.tv.player

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
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
import com.arny.mobilecinema.data.utils.getErrorUrl
import com.arny.mobilecinema.databinding.FTvPlayerBinding
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.playback.PlaybackProgressStore
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.domain.playback.NextEpisodeResolver
import com.arny.mobilecinema.domain.playback.PlayableId
import com.arny.mobilecinema.domain.playback.PlayableRef
import com.arny.mobilecinema.domain.playback.flatPlayableIndex
import com.arny.mobilecinema.domain.playback.playableRefs
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.player.getAllCinemaUrls
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import timber.log.Timber

class TvPlayerFragment : Fragment(), KoinComponent {

    companion object {
        private const val SEEK_STEP_MS = 10_000L
        private const val SEEK_STEP_MAX_MS = 60_000L
        private const val HIDE_DELAY_MS = 5_000L
        private const val PROGRESS_INTERVAL_MS = 1_000L
        private const val PLAYBACK_PROGRESS_INTERVAL_MS = 5_000L
        private const val MAX_PLAYBACK_PROGRESS_DELTA_MS = 15_000L
        private const val NEXT_EPISODE_SECONDS = 10

        /** Запись прогресса не должна зависеть от жизненного цикла фрагмента. */
        private val progressWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val viewModel: PlayerViewModel by inject()
    private val moviesInteractor: MoviesInteractor by inject()
    private val historyInteractor: HistoryInteractor by inject()
    private val playerSource: PlayerSource by inject()
    private val progressStore: PlaybackProgressStore by inject()
    private val resolver: NextEpisodeResolver by inject()
    private val prefs: Prefs by inject()
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val args: TvPlayerFragmentArgs by navArgs()

    private var _binding: FTvPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    /** Доступные качества текущего трека, от высокого разрешения к низкому. */
    private var qualityOptions: List<Pair<String, TrackSelectionOverride>> = emptyList()

    /** Выбранный пункт диалога качества: 0 — «Авто», 1..n — [qualityOptions]. */
    private var selectedQualityIndex = 0

    /** Пользователь сам выбрал качество — не перетираем его выбор максимумом. */
    private var userSelectedQuality = false

    private var currentMovie: Movie? = null
    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var serialSeasons: List<SerialSeason> = emptyList()
    private var currentEpisodeIndex = 0

    /** Рефы, выровненные по индексам media items: [playlistRefs][i] соответствует item i. */
    private val playlistRefs = mutableListOf<PlayableRef>()

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    private var progressJob: Job? = null
    private var playbackProgressJob: Job? = null
    private var lastPlaybackProgressAt: Long = 0L

    /** Объект, которому принадлежит текущий интервал учёта времени (см. startPlaybackProgressTracking). */
    private var trackingRef: PlayableRef? = null
    private var mediaLoaded = false
    private var tvExcludeUrls: Set<String> = emptySet()
    private var nextEpisodeRef: PlayableRef? = null
    private var nextEpisodeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Timber.d("onCreateView: Inflating layout")
        _binding = FTvPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        stopPlaybackProgressTracking()
        savePosition()
        if (binding.nextEpisodePanel.isVisible) dismissNextEpisodePanel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Timber.i("onDestroyView: Releasing resources")

        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.removeCallbacks(hideSeekPreviewRunnable)
        stopProgressUpdates()
        stopPlaybackProgressTracking()
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

        trackSelector = DefaultTrackSelector(
            requireContext(),
            AdaptiveTrackSelection.Factory()
        )

        player = ExoPlayer.Builder(requireContext())
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setTrackSelector(requireNotNull(trackSelector))
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

        binding.btnRetry.setOnClickListener {
            hideError()
            retryPlayback()
        }
        binding.btnErrorBack.setOnClickListener { savePositionAndExit() }
        binding.btnNextPlay.setOnClickListener { playNextEpisode() }
        binding.btnNextCancel.setOnClickListener { dismissNextEpisodePanel() }
        binding.nextEpisodePanel.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dismissNextEpisodePanel()
                true
            } else {
                false
            }
        }

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

        binding.btnPlayPause.setOnClickListener {
            Timber.d("Button Clicked: btnPlayPause")
            togglePlayPause()
            binding.btnPlayPause.requestFocus()
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
                stopPlaybackProgressTracking()
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = player?.duration ?: return
                val newPos = (sb.progress.toLong() * duration) / 1000
                Timber.d("UI Interaction: Seeking to position $newPos ms")
                player?.seekTo(newPos)
                startProgressUpdates()
                if (player?.isPlaying == true) {
                    startPlaybackProgressTracking()
                }
                scheduleHide()
            }
        })

        binding.btnAspectRatio.setOnClickListener {
            toggleAspectRatio()
            binding.btnAspectRatio.requestFocus()
        }

        binding.btnQuality.setOnClickListener {
            Timber.d("Button Clicked: btnQuality")
            showQualityDialog()
        }

        updatePlayPauseButton()
    }

    private fun toggleAspectRatio() {
        currentResizeMode = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                toast(getString(R.string.aspect_zoom))
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                toast(getString(R.string.aspect_fill))
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            else -> {
                toast(getString(R.string.aspect_fit))
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }

        // 1. Применяем режим к PlayerView
        binding.playerView.resizeMode = currentResizeMode

        // 2. Явно говорим ядру плеера использовать масштабирование (помогает аппаратному декодеру)
        player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        // 3. Принудительно заставляем UI перерисовать SurfaceView (Workaround для слабых ТВ и эмуляторов)
        binding.playerView.requestLayout()
        binding.playerView.invalidate()

        showControls()
        scheduleHide()
    }

    private fun updateEpisodeNavigationVisibility(episodeCount: Int) {
        val showNavigation = episodeCount > 1
        Timber.d("updateEpisodeNavigationVisibility: count=$episodeCount, visible=$showNavigation")

        binding.btnPrevious.isVisible = showNavigation
        binding.btnNext.isVisible = showNavigation

        updateNavigationButtonsState()
    }

    private fun updateNavigationButtonsState() {
        val p = player ?: return
        Timber.d("updateNavigationButtonsState: hasPrevious=${p.hasPreviousMediaItem()}, hasNext=${p.hasNextMediaItem()}")

        binding.btnPrevious.apply {
            isEnabled = p.hasPreviousMediaItem()
            alpha = if (isEnabled) 1.0f else 0.5f
        }

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
                    updatePlayPauseButton()
                    refreshQualityOptions()
                }
                Player.STATE_ENDED -> {
                    Timber.d("Playback ENDED")
                    showLoading(false)
                    stopProgressUpdates()
                    stopPlaybackProgressTracking()
                    // Фиксируем завершение до показа панели: позиция уже у конца.
                    savePosition()
                    val movie = currentMovie
                    val current = currentPlayableRef()
                    val next = if (movie?.type == MovieType.SERIAL && current != null) {
                        resolver.next(movie, current.id)
                    } else {
                        null
                    }
                    if (next != null) {
                        showNextEpisodePanel(next)
                    } else {
                        toast(getString(R.string.playback_complete))
                    }
                    updatePlayPauseButton()
                }
                Player.STATE_IDLE -> {
                    showLoading(false)
                    stopProgressUpdates()
                    stopPlaybackProgressTracking()
                    updatePlayPauseButton()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("Playback playing changed: $isPlaying")
            if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            if (isPlaying) startPlaybackProgressTracking() else stopPlaybackProgressTracking()
            updatePlayPauseButton()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "ExoPlayer error occurred. Message: ${error.message}")

            val errorUrl = getErrorUrl(error)
            val currentUrl = getCurrentPlaybackUrl()
            val movie = currentMovie

            if (movie != null) {
                // Логика исключения битых ссылок: для DASH ошибка часто приходит по сегменту,
                // поэтому исключаем и URL текущего MediaItem, чтобы перейти с HD на обычную ссылку.
                tvExcludeUrls = tvExcludeUrls + listOfNotNull(errorUrl, currentUrl).filter { it.isNotBlank() }
                Timber.w(
                    "URL excluded due to error: errorUrl=$errorUrl, currentUrl=$currentUrl. " +
                            "New exclusion list size: ${tvExcludeUrls.size}"
                )

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
                        MovieType.SERIAL -> {
                            val ref = currentPlayableRef()
                            playSerial(
                                movie = movie,
                                seasonIndex = ref?.seasonIndex ?: args.seasonIndex,
                                episodeIndex = ref?.episodeIndex ?: args.episodeIndex,
                                position = player?.currentPosition ?: 0L,
                            )
                        }
                        else -> showError(getString(R.string.playback_error))
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
                    showError(getString(R.string.playback_error_detail, error.localizedMessage.orEmpty()))
                }
            } else {
                showError(getString(R.string.playback_error_detail, error.localizedMessage.orEmpty()))
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            val newIndex = player?.currentMediaItemIndex ?: 0
            Timber.d("Media transition: index $currentEpisodeIndex -> $newIndex, reason=$reason")

            if (newIndex != currentEpisodeIndex) {
                stopPlaybackProgressTracking()
                currentEpisodeIndex = newIndex
                updateEpisodeInfoFromIndex(newIndex)
                updateDuration()
                // НОВОЕ: обновляем состояние кнопок при смене эпизода
                updateNavigationButtonsState()
                if (player?.isPlaying == true) {
                    startPlaybackProgressTracking()
                }
                resetQualitySelection()
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
                stopPlaybackProgressTracking()
                currentEpisodeIndex = newIndex
                updateEpisodeInfoFromIndex(newIndex)
                updateDuration()
                // НОВОЕ: обновляем состояние кнопок
                updateNavigationButtonsState()
                if (player?.isPlaying == true) {
                    startPlaybackProgressTracking()
                }
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
        binding.controlsGroup.visibility = View.VISIBLE
        binding.tvTitle.visibility = View.VISIBLE
        binding.btnAspectRatio.visibility = View.VISIBLE
        binding.btnQuality.isVisible = qualityOptions.size > 1

        if (allEpisodes.isNotEmpty()) {
            binding.tvEpisodeInfo.visibility = View.VISIBLE
        }

        // НОВОЕ: обновляем состояние кнопок при показе панели
        updateNavigationButtonsState()

        binding.btnPlayPause.requestFocus()
    }

    // ─────────────────────────────────────────────────────────────
    // Качество видео
    // ─────────────────────────────────────────────────────────────

    /**
     * Пересобирает список качеств текущего трека.
     *
     * Как и в мобильном плеере, по умолчанию фиксируем максимальное доступное качество:
     * адаптивный выбор на ТВ часто залипает на низком битрейте, и HD-источник
     * выглядит как SD. Пункт «Авто» в диалоге возвращает адаптацию.
     */
    private fun refreshQualityOptions() {
        val selector = trackSelector ?: return
        val currentBinding = _binding ?: return

        qualityOptions = runCatching { selector.generateQualityList(requireContext()) }
            .getOrElse { error ->
                Timber.w(error, "refreshQualityOptions: failed to read tracks")
                emptyList()
            }

        currentBinding.btnQuality.isVisible = qualityOptions.size > 1
        if (qualityOptions.isEmpty()) return

        if (!userSelectedQuality) {
            // Список отсортирован от высокого разрешения к низкому (см. generateQualityList),
            // поэтому пункт 1 — максимальное качество.
            selectedQualityIndex = 1
        }
        selectedQualityIndex = selectedQualityIndex.coerceIn(0, qualityOptions.size)
        applyQuality(selectedQualityIndex)
    }

    /** @param dialogIndex 0 — авто, иначе индекс качества в [qualityOptions] плюс один. */
    private fun applyQuality(dialogIndex: Int) {
        val selector = trackSelector ?: return
        val parameters = selector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (dialogIndex > 0) {
            val override = qualityOptions.getOrNull(dialogIndex - 1)?.second ?: return
            parameters.addOverride(override)
        }
        selector.parameters = parameters.build()
        Timber.d("applyQuality: index=$dialogIndex of ${qualityOptions.size}")
    }

    private fun resetQualitySelection() {
        userSelectedQuality = false
        selectedQualityIndex = 0
    }

    private fun showQualityDialog() {
        if (qualityOptions.size <= 1) return
        val ctx = context ?: return

        val labels = ArrayList<String>(qualityOptions.size + 1)
        labels.add(getString(R.string.quality_auto))
        qualityOptions.forEach { labels.add(it.first) }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.video_quality)
            .setSingleChoiceItems(labels.toTypedArray(), selectedQualityIndex) { dialog, which ->
                userSelectedQuality = true
                selectedQualityIndex = which
                applyQuality(which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                // После диалога возвращаем фокус на контролы плеера.
                if (_binding != null) {
                    showControls()
                    scheduleHide()
                }
            }
            .show()
    }

    private fun setupDpadListener() {
        Timber.d("setupDpadListener: Configuring remote control inputs")
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (binding.errorPanel.isVisible || binding.nextEpisodePanel.isVisible) {
                return@setOnKeyListener false
            }

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
                    seekBackward(seekStepFor(event))
                    binding.root.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekForward(seekStepFor(event))
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

                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_PROG_BLUE,
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    toggleAspectRatio()
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
            when (val result = moviesInteractor.getMovie(movieId).first()) {
                is DataResult.Success -> {
                    if (mediaLoaded) {
                        Timber.w("loadMovieAndPlay: Media already loaded, ignoring new data")
                        return@launch
                    }
                    mediaLoaded = true
                    val movie = result.result
                    currentMovie = movie
                    Timber.d("loadMovieAndPlay: Movie loaded successfully. Title: ${movie.title}")
                    addToHistory(movieId)
                    binding.tvTitle.text = movie.title.ifBlank { getString(R.string.unknown_movie) }

                    // Резолвим сохранённую позицию/серию до старта: раньше TV всегда начинал с 0.
                    viewModel.setPlayData(
                        path = null,
                        movie = movie,
                        seasonIndex = args.seasonIndex,
                        episodeIndex = args.episodeIndex,
                    )
                    val saved = runCatching {
                        viewModel.uiState.filter { it.movie != null }.first()
                    }.getOrNull()
                    playMovie(
                        movie = movie,
                        seasonIndex = saved?.season ?: args.seasonIndex,
                        episodeIndex = saved?.episode ?: args.episodeIndex,
                        position = saved?.time ?: 0L,
                    )
                }

                is DataResult.Error -> {
                    Timber.e(result.throwable, "Failed to load movie ID=$movieId")
                    showError(getString(R.string.error_loading_data))
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

    private fun getCurrentPlaybackUrl(): String? =
        player?.currentMediaItem?.localConfiguration?.uri?.toString()

    private fun playMovie(movie: Movie, seasonIndex: Int, episodeIndex: Int, position: Long) {
        when (movie.type) {
            MovieType.CINEMA -> {
                Timber.i("Playing Cinema Movie: ${movie.title}")
                playCinema(movie)
                hideEpisodeNavigation()
            }
            MovieType.SERIAL -> {
                Timber.i("Playing Serial Episode: S$seasonIndex E$episodeIndex at $position")
                playSerial(movie, seasonIndex, episodeIndex, position)
            }
            else -> showError(getString(R.string.unsupported_type))
        }
    }

    private fun playCinema(movie: Movie) {
        val selectedUrl = args.sharedUrl.takeIf { it.isNotBlank() && it !in tvExcludeUrls }

        val url = selectedUrl ?: movie.getAllCinemaUrls().firstOrNull {
            it.isNotBlank() && it !in tvExcludeUrls
        }

        if (url.isNullOrBlank()) {
            Timber.e("playCinema: No valid URL found for ${movie.title} after exclusions")
            showError(getString(R.string.video_unavailable))
            return
        }

        binding.tvEpisodeInfo.visibility = View.GONE
        val filmRef = movie.playableRefs().firstOrNull()
        playlistRefs.clear()
        if (filmRef != null) playlistRefs.add(filmRef)
        playUrl(url, movie.title, filmRef?.id?.value)
    }

    private fun playSerial(movie: Movie, seasonIndex: Int, episodeIndex: Int, position: Long = 0L) {
        Timber.i("playSerial: Preparing to load serial data for S$seasonIndex E$episodeIndex at $position")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)
                hideError()

                setSerialUrls(
                    movie = movie,
                    seasonIndex = seasonIndex,
                    episodeIndex = episodeIndex,
                    position = position,
                    excludeUrls = tvExcludeUrls,
                    selectedUrl = args.sharedUrl.takeIf { it.isNotBlank() }
                )

            } catch (e: Exception) {
                Timber.e(e, "Error loading serial data structure")
                showError(e.localizedMessage ?: getString(R.string.series_load_error))
            }
        }
    }

    private suspend fun setSerialUrls(
        movie: Movie,
        seasonIndex: Int?,
        episodeIndex: Int?,
        position: Long,
        excludeUrls: Set<String>,
        selectedUrl: String? = null
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
                movie = movie,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                excludeUrls = excludeUrls,
                selectedUrl = selectedUrl
            )

            if (currentEpisodeIndex < 0) {
                showLoading(false)
                showError(getString(R.string.video_unavailable))
                return
            }
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
                movie = movie,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                excludeUrls = excludeUrls,
                selectedUrl = selectedUrl
            )

            if (currentEpisodeIndex < 0 || player?.mediaItemCount == 0) {
                showLoading(false)
                showError(getString(R.string.video_unavailable))
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
        movie: Movie,
        seasonIndex: Int?,
        episodeIndex: Int?,
        excludeUrls: Set<String>,
        selectedUrl: String? = null
    ): Int {
        val refs = movie.playableRefs()
        val selectedFlatIndex = movie.flatPlayableIndex(seasonIndex ?: 0, episodeIndex ?: 0)
        val mediaSources = mutableListOf<MediaSource>()
        val orderedRefs = mutableListOf<PlayableRef>()
        var selectedMediaIndex = -1

        refs.forEachIndexed { flatIndex, ref ->
            val isSelected = flatIndex == selectedFlatIndex
            // На TV обычный HLS безопаснее как дефолт: HD DASH можно выбрать явно в источниках.
            val url = when {
                isSelected && !selectedUrl.isNullOrBlank() && selectedUrl !in excludeUrls -> selectedUrl
                !ref.hlsUrl.isNullOrBlank() && ref.hlsUrl !in excludeUrls -> ref.hlsUrl
                !ref.dashUrl.isNullOrBlank() && ref.dashUrl !in excludeUrls -> ref.dashUrl
                else -> null
            }

            if (url.isNullOrBlank()) {
                Timber.w("fillPlayerEpisodes: skipping ${ref.id.value} - no valid source")
                return@forEachIndexed
            }

            val source = playerSource.getSource(
                url = url,
                title = ref.episodeTitle,
                season = ref.seasonNumber,
                episode = ref.episodeNumber,
                mediaId = ref.id.value,
            )
            if (source == null) {
                Timber.w("fillPlayerEpisodes: failed to create source for ${ref.id.value}")
                return@forEachIndexed
            }

            if (isSelected) selectedMediaIndex = mediaSources.size
            mediaSources.add(source)
            orderedRefs.add(ref)
        }

        playlistRefs.clear()
        playlistRefs.addAll(orderedRefs)
        Timber.d("fillPlayerEpisodes: ${mediaSources.size} sources, selected media index $selectedMediaIndex")
        player?.setMediaSources(mediaSources)

        return selectedMediaIndex
    }

    private fun playUrl(url: String, title: String, mediaId: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.i("playUrl: Preparing source. URL: ${url.take(50)}..., Title: $title")
                showLoading(true)
                hideError()

                val source = playerSource.getSource(url, title, mediaId = mediaId)
                if (source != null) {
                    player?.apply {
                        setMediaSource(source)
                        prepare()
                        playWhenReady = true
                        Timber.i("playUrl: Source prepared successfully")
                    }
                } else {
                    showError(getString(R.string.video_source_error))
                }
            } catch (e: Exception) {
                Timber.e(e, "playUrl error during preparation for title: $title")
                showError(e.localizedMessage ?: getString(R.string.playback_error))
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

    private fun updateEpisodeInfoFromIndex(index: Int = player?.currentMediaItemIndex ?: 0) {
        val ref = currentPlayableRef(index)
        if (ref == null) {
            binding.tvEpisodeInfo.visibility = View.GONE
            return
        }
        showEpisodeInfo(
            ref.seasonNumber ?: 1,
            ref.episodeNumber ?: 1,
            ref.episodeTitle.orEmpty(),
        )
    }

    /**
     * Текущий воспроизводимый объект. Источник истины — mediaId, а не арифметика индексов:
     * серии без URL пропускаются, поэтому индекс media item не равен индексу в каталоге.
     */
    private fun currentPlayableRef(index: Int = player?.currentMediaItemIndex ?: 0): PlayableRef? {
        val mediaId = player?.currentMediaItem?.mediaId
        if (!mediaId.isNullOrBlank()) {
            PlayableId.parse(mediaId)?.let { id ->
                playlistRefs.firstOrNull { it.id == id }?.let { return it }
            }
        }
        return playlistRefs.getOrNull(index)
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
        updatePlayPauseButton()
        showControls()
        scheduleHide()
    }

    private fun updatePlayPauseButton() {
        val currentBinding = _binding ?: return
        val isPlaying = player?.isPlaying == true
        currentBinding.btnPlayPause.apply {
            setImageResource(
                if (isPlaying) R.drawable.ic_pause_circle_outline else R.drawable.play_circle_outline
            )
            contentDescription = getString(if (isPlaying) R.string.pause else R.string.play)
        }
    }

    /** Обычное нажатие — 10 с; удержание ускоряет шаг до 60 с (ТЗ §7.2). */
    private fun seekStepFor(event: KeyEvent): Long {
        if (event.repeatCount <= 0) return SEEK_STEP_MS
        val step = SEEK_STEP_MS * (1 + event.repeatCount / 2)
        return step.coerceAtMost(SEEK_STEP_MAX_MS)
    }

    private fun seekForward(stepMs: Long = SEEK_STEP_MS) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0L } ?: return
        val newPos = (p.currentPosition + stepMs).coerceAtMost(duration)
        Timber.d("seekForward: +$stepMs -> $newPos ms")

        p.seekTo(newPos)
        showSeekPreview(newPos)
        showControls()
        scheduleHide()
        updateProgress()
    }

    private fun seekBackward(stepMs: Long = SEEK_STEP_MS) {
        val p = player ?: return
        if (p.duration <= 0L) return
        val newPos = (p.currentPosition - stepMs).coerceAtLeast(0L)
        Timber.d("seekBackward: -$stepMs -> $newPos ms")

        p.seekTo(newPos)
        showSeekPreview(newPos)
        showControls()
        scheduleHide()
        updateProgress()
    }

    private fun showSeekPreview(positionMs: Long) {
        binding.tvSeekPreview.text = getString(R.string.tv_seek_preview, formatTime(positionMs))
        binding.tvSeekPreview.isVisible = true
        hideHandler.removeCallbacks(hideSeekPreviewRunnable)
        hideHandler.postDelayed(hideSeekPreviewRunnable, 1_500L)
    }

    private val hideSeekPreviewRunnable = Runnable { binding.tvSeekPreview.isVisible = false }

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
        binding.tvTitle.visibility = View.GONE
        binding.btnAspectRatio.visibility = View.GONE
        binding.btnQuality.visibility = View.GONE
        binding.root.requestFocus()
        Timber.d("hideControls: UI controls hidden")
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun showLoading(show: Boolean) {
        binding.loadingIndicator.isVisible = show
    }

    private fun showError(msg: String) {
        Timber.e("UI Error Displayed: $msg")
        showLoading(false)
        dismissNextEpisodePanel()
        binding.tvError.text = msg
        binding.errorPanel.visibility = View.VISIBLE
        binding.controlsGroup.visibility = View.GONE
        binding.btnRetry.requestFocus()
    }

    private fun hideError() {
        binding.errorPanel.visibility = View.GONE
    }

    private fun retryPlayback() {
        val movie = currentMovie
        if (movie == null) {
            val sharedUrl = args.sharedUrl.takeIf { it.isNotBlank() }
            if (sharedUrl != null) playUrl(sharedUrl, getString(R.string.unknown_movie)) else savePositionAndExit()
            return
        }
        when (movie.type) {
            MovieType.CINEMA -> playCinema(movie)
            MovieType.SERIAL -> {
                val ref = currentPlayableRef()
                playSerial(
                    movie = movie,
                    seasonIndex = ref?.seasonIndex ?: args.seasonIndex,
                    episodeIndex = ref?.episodeIndex ?: args.episodeIndex,
                    position = player?.currentPosition ?: 0L,
                )
            }
            else -> Unit
        }
    }

    private fun showNextEpisodePanel(ref: PlayableRef) {
        nextEpisodeRef = ref
        val label = if (ref.seasonNumber != null && ref.episodeNumber != null) {
            getString(R.string.tv_next_episode_label, ref.seasonNumber, ref.episodeNumber)
        } else {
            getString(R.string.next_episode)
        }
        binding.tvNextEpisodeTitle.text = buildString {
            append(label)
            ref.episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        binding.nextEpisodePanel.visibility = View.VISIBLE
        binding.controlsGroup.visibility = View.GONE
        binding.btnNextPlay.requestFocus()
        startNextEpisodeCountdown()
    }

    private fun startNextEpisodeCountdown() {
        nextEpisodeJob?.cancel()
        nextEpisodeJob = viewLifecycleOwner.lifecycleScope.launch {
            var seconds = NEXT_EPISODE_SECONDS
            while (isActive && seconds > 0) {
                binding.tvNextEpisodeCountdown.text = getString(R.string.tv_next_in, seconds)
                delay(1_000L)
                seconds--
            }
            if (!isActive) return@launch
            val autoplay = prefs.get<Boolean>(PrefsConstants.PREF_KEY_AUTOPLAY_NEXT) ?: true
            if (autoplay) {
                playNextEpisode()
            } else {
                binding.tvNextEpisodeCountdown.text = getString(R.string.tv_next_ready)
            }
        }
    }

    private fun playNextEpisode() {
        val ref = nextEpisodeRef ?: return
        nextEpisodeJob?.cancel()
        nextEpisodeJob = null
        binding.nextEpisodePanel.visibility = View.GONE
        nextEpisodeRef = null
        val index = playlistRefs.indexOfFirst { it.id == ref.id }
        if (index >= 0) {
            player?.seekTo(index, 0L)
            player?.play()
        } else if (player?.hasNextMediaItem() == true) {
            player?.seekToNextMediaItem()
            player?.play()
        }
        showControls()
        binding.root.requestFocus()
    }

    private fun dismissNextEpisodePanel() {
        nextEpisodeJob?.cancel()
        nextEpisodeJob = null
        nextEpisodeRef = null
        if (_binding == null) return
        binding.nextEpisodePanel.visibility = View.GONE
        binding.root.requestFocus()
        showControls()
    }

    private fun showEpisodeInfo(seasonNum: Int, episodeNum: Int, title: String) {
        binding.tvEpisodeInfo.text = buildString {
            append(getString(R.string.tv_season_episode_short, seasonNum, episodeNum))
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

    private fun startPlaybackProgressTracking() {
        if (playbackProgressJob?.isActive == true) return

        // Фиксируем объект на старте интервала: при смене серии onMediaItemTransition
        // коммитит время уже после того, как плеер переключил media item, и live-ref
        // отдал бы чужую секунду.
        trackingRef = currentPlayableRef()
        lastPlaybackProgressAt = System.currentTimeMillis()
        playbackProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(PLAYBACK_PROGRESS_INTERVAL_MS)
                commitPlaybackProgressDelta()
            }
        }
    }

    private fun stopPlaybackProgressTracking() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        commitPlaybackProgressDelta()
        lastPlaybackProgressAt = 0L
        trackingRef = null
    }

    private fun commitPlaybackProgressDelta() {
        val now = System.currentTimeMillis()
        val lastTime = lastPlaybackProgressAt
        if (lastTime <= 0L) {
            lastPlaybackProgressAt = now
            return
        }

        val deltaMs = now - lastTime
        lastPlaybackProgressAt = now
        if (deltaMs in 1..MAX_PLAYBACK_PROGRESS_DELTA_MS) {
            savePlaybackProgress(deltaMs)
        }
    }

    private fun savePlaybackProgress(playedMs: Long) {
        val ref = trackingRef ?: return
        if (playedMs <= 0L) return

        // Позиция для продолжения и время просмотра — одна строка по playableId (MOB-10B).
        val position = player?.currentPosition ?: 0L
        val duration = player?.duration?.takeIf { it > 0L }
        progressWrites.launch {
            runCatching {
                progressStore.record(ref, position, duration, playedMsDelta = playedMs)
            }.onFailure { Timber.e(it, "progress write failed for ${ref.id.value}") }
        }
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

        val duration = exo.duration.takeIf { it > 0L }
        val ref = currentPlayableRef()
        if (ref != null) {
            // Запись переживает уничтожение view: store выполняет её в NonCancellable.
            progressWrites.launch {
                runCatching { progressStore.record(ref, pos, duration) }
                    .onFailure { Timber.e(it, "progress write failed for ${ref.id.value}") }
            }
        }

        val seasonIdx = (ref?.seasonNumber ?: 1) - 1
        val episodeIdx = (ref?.episodeNumber ?: 1) - 1
        when (movie.type) {
            MovieType.CINEMA ->
                viewModel.saveMoviePosition(movie.dbId, pos, 0, 0)
            MovieType.SERIAL ->
                viewModel.saveMoviePosition(movie.dbId, pos, seasonIdx.coerceAtLeast(0), episodeIdx.coerceAtLeast(0))
            else -> {}
        }
    }

    private fun savePositionAndExit() {
        Timber.d("savePositionAndExit: User requested exit")
        savePosition()
        findNavController().navigateUp()
    }
}
