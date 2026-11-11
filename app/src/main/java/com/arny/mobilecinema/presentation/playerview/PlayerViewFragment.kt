@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arny.mobilecinema.presentation.playerview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.github.vkay94.dtpv.DoubleTapPlayerView
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.NetworkStatus
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.data.utils.getErrorUrl
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.data.utils.hasValidatedInternet
import com.arny.mobilecinema.data.utils.networkStatus
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.player.ExternalPlayerLauncher
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateLanguagesList
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import com.arny.mobilecinema.presentation.utils.getOrientation
import com.arny.mobilecinema.presentation.utils.initAudioManager
import com.arny.mobilecinema.presentation.utils.isPiPAvailable
import com.arny.mobilecinema.presentation.utils.registerContentResolver
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.setScreenBrightness
import com.arny.mobilecinema.presentation.utils.setTextColorRes
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.unregisterContentResolver
import com.github.vkay94.dtpv.youtube.YouTubeOverlay
import timber.log.Timber
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.common.Format
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.util.Util
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.properties.Delegates

class PlayerViewFragment : Fragment(R.layout.f_player_view), OnPictureInPictureListener {

    private companion object {
        const val MAX_BOOST_DEFAULT = 1000
        const val MEDIA_SESSION_TAG = "MEDIA_SESSION_ANWAP_TAG"
        const val CONTROLS_ANIMATION_DURATION = 250L
        const val PLAYBACK_PROGRESS_INTERVAL_MS = 5_000L
        const val MAX_PLAYBACK_PROGRESS_DELTA_MS = 15_000L
        const val KEY_START_PAUSED = "player_view_start_paused"

        /** В меню субтитров itemId 0 — «Выключить», реальные дорожки начинаются с 1. */
        const val SUBTITLE_OFF_ITEM_ID = 0
    }

    private val prefs: Prefs by inject()
    private val playerSource: PlayerSource by inject()
    private val viewModel: PlayerViewModel by viewModel()

    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var currentEpisodeIndex: Int = -1
    private var title: String = ""
    private var currentCinemaUrl: String? = null

    private var qualityVisible: Boolean = false
    private var externalPlayerVisible: Boolean = false
    private var volumeObserver: ContentObserver? = null
    private var langVisible: Boolean = false
    private var mediaItemIndex: Int = 0
    private var movie: Movie? = null

    private var brightnessVolumeController: BrightnessVolumeController? = null

    // Вертикальный свайп: справа — громкость, слева — яркость.
    private var swipeActive = false
    private var swipeIsVolume = false
    private var swipeStartY = 0f
    private var swipeStartVolume = 0f
    private var swipeStartBrightness = 0.5f

    private val btnsHandler = Handler(Looper.getMainLooper())
    private val volumeHandler = Handler(Looper.getMainLooper())
    private val playbackProgressHandler = Handler(Looper.getMainLooper())
    private val args: PlayerViewFragmentArgs by navArgs()

    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,  // 0: Оригинал (черные полосы)
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM, // 1: Заполнить экран (обрезка краев без искажений)
        AspectRatioFrameLayout.RESIZE_MODE_FILL  // 2: Растянуть (искажение пропорций, но без черных полос)
    )

    private var qualityPopUp: PopupMenu? = null
    private var langPopUp: PopupMenu? = null
    private val playbackSession = PlaybackSession<ExoPlayer> { engine ->
        engine.removeListener(listener)
        engine.removeAnalyticsListener(analytic)
        engine.stop()
        engine.release()
    }
    private val player: ExoPlayer? get() = playbackSession.player
    private var trackSelector: DefaultTrackSelector? = null
    private var resizeModeIndex = 0
    private var setupPopupMenus = true
    private var enhancer: LoudnessEnhancer? = null

    private var _binding: FPlayerViewBinding? = null
    private val binding get() = _binding!!

    private var windowInsetsListener: View.OnApplyWindowInsetsListener? = null
    private var isSystemUIHidden = false
    private var topInset: Int = 0
    private var bottomInset: Int = 0

    private var gestureDetectorCompat: GestureDetectorCompat? = null
    private var audioManager: AudioManager? = null
    private var brightness: Int = 0
    private var volume: Int = -1
    private var boost: Int = -1

    private var isPlayerPrepared = false
    private var shouldStartPaused = false
    private var lastProcessedVersion: Long = -1
    private var lastPlaybackProgressAt: Long = 0L

    private val playbackProgressRunnable = object : Runnable {
        override fun run() {
            commitPlaybackProgressDelta()
            if (player?.isPlaying == true) {
                playbackProgressHandler.postDelayed(this, PLAYBACK_PROGRESS_INTERVAL_MS)
            }
        }
    }

    private var volumeObs: Int by Delegates.observable(-1) { _, old, newVolume ->
        if (old != newVolume) {
            volume = newVolume
            if (newVolume < (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15)) {
                boost = 0
                updateGain()
            }
            showVolumeIndicator()
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            updateUIByVolume()
            // Во время свайпа индикатор гасится один раз в конце жеста, иначе он мигает.
            if (!swipeActive) hideVolumeBrightViews()
            brightnessVolumeController?.updateVolumeExternal(
                volume ?: 0,
                audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            )
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange <= 0) {
            pauseForAudioFocusLoss()
        }
    }

    private val maxBoost by lazy {
        prefs.get<String>(getString(R.string.pref_max_boost_size))?.toIntOrNull()
            ?: MAX_BOOST_DEFAULT
    }

    private val gestureDetectListener: GestureDetector.OnGestureListener =
        object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onShowPress(e: MotionEvent) {}
            override fun onSingleTapUp(e: MotionEvent): Boolean = false

            override fun onScroll(
                e1: MotionEvent?,
                event: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val down = e1 ?: return false
                val view = _binding?.root ?: return false
                val height = view.height.takeIf { it > 0 } ?: return false
                val totalDy = event.y - down.y
                val totalDx = event.x - down.x
                if (!swipeActive) {
                    // Свайп считаем вертикальным только если он заметно длиннее горизонтального:
                    // иначе перехватывали бы обычные тапы и двойной тап.
                    val slop = (ViewConfiguration.get(view.context)?.scaledTouchSlop ?: 8) * 2
                    if (abs(totalDy) < slop || abs(totalDy) <= abs(totalDx) * 1.5f) return false
                    swipeActive = true
                    swipeIsVolume = down.x >= view.width / 2f
                    swipeStartY = down.y
                    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    swipeStartVolume = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat()
                    if (maxBoost > 0 && swipeStartVolume >= maxVolume) {
                        swipeStartVolume += boost.coerceAtLeast(0).toFloat() / maxBoost * maxVolume
                    }
                    swipeStartBrightness = currentBrightnessFraction()
                    btnsHandler.removeCallbacksAndMessages(null)
                    // Двойной тап не должен «доживать» до конца свайпа.
                    (binding.playerView as? DoubleTapPlayerView)?.cancelInDoubleTapMode()
                }
                // Вверх — прибавляем, вниз — убавляем; 70% высоты экрана = полный диапазон.
                val fraction = (swipeStartY - event.y) / (height * 0.7f)
                if (swipeIsVolume) applyVolumeSwipe(fraction) else applyBrightnessSwipe(fraction)
                return true
            }

            override fun onLongPress(e: MotionEvent) {}
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean = false
        }

    /** Media3-сессия заменяет MediaSessionConnector: медиа-кнопки идут прямо в engine. */
    private var mediaSession: MediaSession? = null
    private var playbackErrorDialog: androidx.appcompat.app.AlertDialog? = null

    /** Correlation ID клика, пришедший из деталей: склеивает этапы плеера с экраном деталей (§4.2). */
    private val playId: String? by lazy {
        arguments?.getString("playId")
    }

    // Диагностика цепочки запуска (S-008): фиксируем этапы плеера, которых раньше не было в логах.
    // Отсутствие этих строк означало неясность — дошли ли до MediaItem/prepare или нет.
    private val analytic = object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: EventTime,
            surface: Any,
            renderTimeMs: Long
        ) {
            // PlaybackStarted: экран действительно показал кадр (§4.2).
            Timber.tag("PlayChain").d("%s playbackStarted (render %d ms)", playId, renderTimeMs)
        }

        override fun onVideoInputFormatChanged(
            eventTime: EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.tag("PlayChain").d(
                "%s videoFormat %s mime=%s size=%dx%d",
                playId,
                format.id,
                format.sampleMimeType,
                format.width,
                format.height
            )
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // PlayerError: типизированная ветка контракта — пользователь всегда видит причину.
            Timber.tag("PlayChain").e(error, "%s playerError %s", playId, error.errorCodeName)
            binding.progressBar.isVisible = false
            val lastError = when (
                networkStatus(getConnectionType(requireContext()), hasValidatedInternet(requireContext()))
            ) {
                NetworkStatus.NONE -> getString(R.string.internet_connection_error)
                NetworkStatus.UNVALIDATED -> getString(R.string.internet_not_validated_error)
                NetworkStatus.ONLINE -> getFullError(error)
            }
            viewModel.setLastPlayerError(lastError)
            val errorUrl = getErrorUrl(error)
            val currentUrl = getCurrentPlaybackUrl()
            val serialEpisode = allEpisodes.getOrNull(currentEpisodeIndex)
            playbackErrorDialog?.dismiss()
            playbackErrorDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.playback_error)
                .setMessage(lastError)
                .setPositiveButton(R.string.retry) { _, _ ->
                    player?.apply {
                        prepare()
                        playWhenReady = true
                    }
                }
                .setNeutralButton(R.string.select_source) { _, _ ->
                    viewModel.retryOpenCinema(errorUrl, currentUrl, serialEpisode)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .also { it.show() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // S-008: состояние плеера в логе — иначе не видно, дошло ли до BUFFERING/READY.
            Timber.tag("PlayChain").d("%s state=%d", playId, playbackState)
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                stopPlaybackProgressTracking()
            }
            updateState(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPlaybackProgressTracking()
            } else {
                stopPlaybackProgressTracking()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, playbackState: Int) {
            shouldStartPaused = !playWhenReady
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            setQuality(null)
            updateCurrentEpisodeFromPlayer()
            updateExternalPlayerVisibility()
        }

        override fun onTracksChanged(tracks: Tracks) {
            setupPopupMenus = true
            // Меню дорожек строится по уже известным трекам. Раньше оно собиралось только при
            // STATE_READY: если треки приходили позже (или в момент READY списка ещё не было),
            // кнопки языка/субтитров оставались скрытыми навсегда.
            setUpPopups()
        }
    }

    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .build()
        } else {
            null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FPlayerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            shouldStartPaused = savedInstanceState.getBoolean(KEY_START_PAUSED, false)
        }

        binding.progressBar.isVisible = true
        updateExternalPlayerVisibility()
        lastProcessedVersion = -1

        observeState()
        initListener()
        initSystemUI()
        initPlayerTouchListener()
        fixControllerPadding()
        initAudioManager()
        initVolumeObserver()
        initBrightnessVolumeController()
    }

    private fun fixControllerPadding() {
        val controlView =
            binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
                ?: return

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestureInsets =
                insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val bottomInset = maxOf(systemBars.bottom, gestureInsets.bottom)

            // Применяем padding к контролам
            controlView.setPadding(
                controlView.paddingLeft,
                controlView.paddingTop,
                controlView.paddingRight,
                bottomInset + 16.dpToPx() // + запас
            )
            insets
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
        registerVolumeObserver()
        if (brightness != 0) {
            setScreenBrightness(brightness)
        }
        viewModel.updatePipModeEnable()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
        hideSystemUIImmediately()
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        volumeObserver?.let { unregisterContentResolver(it) }
        (requireActivity() as AppCompatActivity).supportActionBar?.show()

        forcePausedOnRestore()

        player?.let { exoPlayer ->
            stopPlaybackProgressTracking()
            saveCurrentPosition(exoPlayer)
            saveMoviePosition(exoPlayer)
        }

        showSystemUIImmediately()

        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            forcePausedOnRestore()

            player?.let { exoPlayer ->
                stopPlaybackProgressTracking()
                val currentPosition = exoPlayer.currentPosition
                val movie = args.movie
                val dbId = movie?.dbId

                if (dbId != null) {
                    val state = viewModel.uiState.value
                    when (movie.type) {
                        MovieType.CINEMA -> {
                            viewModel.updatePlaybackPosition(currentPosition, 0, 0)
                            viewModel.saveMoviePosition(dbId, currentPosition, 0, 0)
                        }

                        MovieType.SERIAL -> {
                            val metadata = exoPlayer.currentMediaItem?.mediaMetadata
                            val bundle = metadata?.extras
                            val season = bundle?.getInt(AppConstants.Player.SEASON)
                                ?: state.season
                                ?: 0
                            val episode = bundle?.getInt(AppConstants.Player.EPISODE)
                                ?: state.episode
                                ?: 0

                            viewModel.updatePlaybackPosition(currentPosition, season, episode)
                            viewModel.saveMoviePosition(dbId, currentPosition, season, episode)
                        }

                        else -> {}
                    }
                }
            }
            releasePlayer()
        }
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onDestroyView() {
        controlsShown = null
        playbackErrorDialog?.dismiss()
        playbackErrorDialog = null
        super.onDestroyView()
        btnsHandler.removeCallbacksAndMessages(null)
        volumeHandler.removeCallbacksAndMessages(null)
        playbackProgressHandler.removeCallbacksAndMessages(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.decorView?.setOnApplyWindowInsetsListener(null)
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.setOnSystemUiVisibilityChangeListener(null)
        }

        windowInsetsListener = null
        showSystemUIImmediately()
        gestureDetectorCompat = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(focusChangeListener)
        }

        gestureDetectorCompat = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setScreenRotIconVisible(newConfig.orientation, true)
        if (isSystemUIHidden) {
            hideSystemUIImmediately()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_START_PAUSED, shouldStartPaused)
    }

    /**
     * PiP — это «свёрнуто, но смотрю»: активность уходит в onPause, однако воспроизведение
     * должно продолжаться, иначе PiP показывает застывший кадр.
     */
    private fun isInPictureInPictureMode(): Boolean =
        activity?.isInPictureInPictureMode == true

    private fun forcePausedOnRestore() {
        if (isInPictureInPictureMode()) return
        pauseForAudioFocusLoss()
    }

    private fun pauseForAudioFocusLoss() {
        shouldStartPaused = true
        player?.pause()
    }

    private fun updateGain() {
        try {
            if (enhancer?.targetGain != null) {
                enhancer?.setTargetGain(boost)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Текущая яркость окна как доля 0..1 (BRIGHTNESS_OVERRIDE_NONE → 0.5). */
    private fun currentBrightnessFraction(): Float {
        val value = activity?.window?.attributes?.screenBrightness ?: -1f
        return if (value in 0.01f..1f) value else 0.5f
    }

    private fun applyVolumeSwipe(fraction: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // 0..max � ��������� ���������; max..2*max � �������� 0..maxBoost (���������).
        val level = (swipeStartVolume + fraction * max).coerceIn(0f, max * 2f)
        val target = level.roundToInt().coerceIn(0, max)
        val targetBoost = ((level - max).coerceAtLeast(0f) / max * maxBoost.coerceAtLeast(0)).roundToInt()
        if (target != manager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        // Обновляем UI прямо в MOVE, не ждём асинхронный ContentObserver.
        volumeObs = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        boost = if (volumeObs == max) targetBoost else 0
        updateGain()
        brightnessVolumeController?.initializeBoost(boost)
        updateUIByVolume()
        showVolumeIndicator()
    }

    private fun applyBrightnessSwipe(fraction: Float) {
        val window = activity?.window ?: return
        val target = (swipeStartBrightness + fraction).coerceIn(0.01f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = target }
        brightness = (target * 30).roundToInt().coerceIn(1, 30)
        binding.tvBrightness.text = "${(target * 100).roundToInt()}%"
        showBrightnessIndicator()
    }

    private fun showVolumeIndicator() {
        // Отменяем и отложенный endAction скрытия: он не должен погасить новый жест.
        binding.tvVolume.animate().withEndAction(null).cancel()
        binding.tvVolume.alpha = 1f
        binding.tvVolume.isVisible = true
    }

    private fun showBrightnessIndicator() {
        // Отменяем и отложенный endAction скрытия: он не должен погасить новый жест.
        binding.tvBrightness.animate().withEndAction(null).cancel()
        binding.tvBrightness.alpha = 1f
        binding.tvBrightness.isVisible = true
    }

    private fun hideVolumeBrightViews() {
        btnsHandler.removeCallbacksAndMessages(null)
        btnsHandler.postDelayed({
            if (_binding == null) return@postDelayed

            binding.tvBrightness.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (_binding != null) binding.tvBrightness.isVisible = false
                }
                .start()

            binding.tvVolume.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (_binding != null) binding.tvVolume.isVisible = false
                }
                .start()
        }, 1000)
    }

    private fun updateUIByVolume() {
        binding.tvVolume.text = if (boost > 0) "$volume (+${boost / 100f} dB)" else volume.toString()
        binding.tvVolume.setTextColorRes(R.color.textColorPrimary)
    }

    private fun initBrightnessVolumeController() {
        val window = requireActivity().window
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

        brightnessVolumeController = BrightnessVolumeController(
            rootView = binding.root,
            window = window,
            maxBoost = maxBoost,
            onVolumeChanged = { newVolumeAbsolute ->
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeAbsolute, 0)
                volume = newVolumeAbsolute
                showVolumeIndicator()
                updateUIByVolume()
                hideVolumeBrightViews()
            },
            onBoostChanged = { boostMb ->
                boost = boostMb
                updateGain()
            }
        )

        val windowBrightness = window.attributes.screenBrightness
        val brightnessFloat = if (windowBrightness > 0f) windowBrightness else 0.5f
        brightnessVolumeController?.initializeBrightness(brightnessFloat)
        brightness = (brightnessFloat * 255).toInt()

        val curVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        volume = curVolume
        brightnessVolumeController?.initializeVolume(curVolume, maxVol)

        val targetGain = try {
            enhancer?.targetGain?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
        boost = targetGain
        brightnessVolumeController?.initializeBoost(targetGain)
    }

    private fun initVolumeObserver() {
        volumeObserver = SettingsContentObserver(
            requireContext(),
            Handler(Looper.getMainLooper())
        ) { v ->
            updateVolumeByContentResolver(v)
        }
    }

    private fun updateVolumeByContentResolver(v: Int) {
        volumeObs = v
    }

    private fun initAudioManager() {
        audioManager = initAudioManager(audioManager, focusChangeListener)
    }

    private fun initPlayerTouchListener() {
        gestureDetectorCompat = GestureDetectorCompat(requireContext(), gestureDetectListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSystemUI() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topInset = insets.top
            bottomInset = insets.bottom
            updateControlsPadding()
            windowInsets
        }

        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && event.y > binding.root.height - 150.dpToPx()) {
                // Зона системных жестов — скрываем контролы плеера
                binding.playerView.hideController()
            }
            false
        }

        hideSystemUIImmediately()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsListener = View.OnApplyWindowInsetsListener { _, insets ->
                val currentBinding = _binding ?: return@OnApplyWindowInsetsListener insets

                val isVisible = insets.isVisible(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )

                if (isVisible && !currentBinding.playerView.isControllerFullyVisible) {
                    hideSystemUIImmediately()
                } else if (isVisible) {
                    currentBinding.playerView.showController()
                }

                insets
            }
            requireActivity().window.decorView.setOnApplyWindowInsetsListener(windowInsetsListener)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                val currentBinding = _binding ?: return@setOnSystemUiVisibilityChangeListener

                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    if (currentBinding.playerView.isControllerFullyVisible) {
                        currentBinding.playerView.showController()
                    } else {
                        hideSystemUIImmediately()
                    }
                } else {
                    currentBinding.playerView.hideController()
                }
            }
        }
    }

    private fun updateControlsPadding() {
        val currentBinding = _binding ?: return

        with(currentBinding) {
            (tvTitle.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 8.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    tvTitle.layoutParams = params
                }
            }

            (ivBack.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 16.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivBack.layoutParams = params
                }
            }

            (ivQuality.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 16.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivQuality.layoutParams = params
                }
            }

            (ivExternalPlayer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 24.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivExternalPlayer.layoutParams = params
                }
            }

            (ivResizes.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 32.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivResizes.layoutParams = params
                }
            }

            (ivLang.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 40.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivLang.layoutParams = params
                }
            }

            (ivScreenRotation.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 48.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivScreenRotation.layoutParams = params
                }
            }

            (ivBrightness.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 56.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivBrightness.layoutParams = params
                }
            }

            (ivVolume.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 64.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivVolume.layoutParams = params
                }
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    private fun hideSystemUIImmediately() {
        val window = activity?.window ?: return
        // При пересоздании активити (поворот, смена размера) окно ещё без DecorView:
        // window.insetsController в этом состоянии падает NPE внутри фреймворка.
        if (!window.decorView.isAttachedToWindow) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
        isSystemUIHidden = true
    }

    private fun showSystemUIImmediately() {
        val window = activity?.window ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.apply {
                show(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        isSystemUIHidden = false
    }

    private fun setScreenRotIconVisible(orientation: Int, reset: Boolean) {
        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (reset) setPlayerResizeMode(0) else setPlayerResizeMode(resizeModeIndex)
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerFullyVisible
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                if (reset) setPlayerResizeMode(minOf(1, resizeModes.lastIndex)) else setPlayerResizeMode(resizeModeIndex)
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerFullyVisible
            }
        }
    }

    private fun setPlayerResizeMode(index: Int) {
        resizeModeIndex = index.coerceIn(0, resizeModes.lastIndex)
        binding.playerView.resizeMode = resizeModes[resizeModeIndex]
    }

    private fun initListener() {
        val currentBinding = _binding ?: return

        with(currentBinding) {
            ivQuality.setOnClickListener {
                // Selecting the already playing track need not emit onTracksChanged.
                // Re-read overrides so the radio selection never stays stale.
                setupPopupMenus = true
                setUpPopups()
                qualityPopUp?.show()
            }
            ivLang.setOnClickListener { langPopUp?.show() }
            ivResizes.setOnClickListener { changeResize() }
            ivExternalPlayer.setOnClickListener { openExternalPlayer() }
            ivBack.setOnClickListener { findNavController().popBackStack() }
            ivScreenRotation.setOnClickListener { toggleScreenOrientation() }
            ivBrightness.setOnClickListener { showBrightnessSlider() }
            ivVolume.setOnClickListener { showVolumeSlider() }
        }
    }

    private fun showBrightnessSlider() {
        brightnessVolumeController?.showBrightness()
        showBrightnessIndicator()
        hideVolumeBrightViews()
    }

    private fun showVolumeSlider() {
        brightnessVolumeController?.showVolume()
        showVolumeIndicator()
        hideVolumeBrightViews()
    }

    private fun toggleScreenOrientation() {
        val currentOrientation = resources.configuration.orientation
        requireActivity().requestedOrientation =
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

        btnsHandler.postDelayed({
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }, 1500)
    }

    private fun changeResize() {
        val nextIndex = if (resizeModeIndex >= resizeModes.lastIndex) 0 else resizeModeIndex + 1
        setPlayerResizeMode(nextIndex)
        viewModel.updateResizeModeIndex(resizeModeIndex)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        if (state.version <= lastProcessedVersion) return@collectLatest
                        if (state.path == null && state.movie == null) return@collectLatest

                        lastProcessedVersion = state.version
                        movie = state.movie
                        setCurrentTitle()
                        setMediaSources(
                            path = state.path,
                            time = state.time,
                            movie = state.movie,
                            seasonIndex = state.season ?: 0,
                            episodeIndex = state.episode ?: 0,
                            excludeUrls = state.excludeUrls
                        )
                    }
                }

                launch {
                    viewModel.pipMode.collect { isPipMode ->
                        if (isPipMode) requestPipMode()
                    }
                }

                launch {
                    viewModel.toast.collectLatest { toastRes ->
                        toast(toastRes.toString(requireContext()))
                    }
                }

                launch {
                    viewModel.back.collectLatest {
                        findNavController().navigateUp()
                    }
                }

                launch {
                    viewModel.requestUpdate.collectLatest {
                        findNavController().navigateUp()
                    }
                }

                launch {
                    viewModel.cachedResizeModeIndex.collectLatest { cachedIndex ->
                        val safeIndex = cachedIndex.coerceIn(0, resizeModes.lastIndex)
                        if (safeIndex != resizeModeIndex) {
                            setPlayerResizeMode(safeIndex)
                        }
                    }
                }
            }
        }
    }

    private suspend fun setMediaSources(
        path: String?,
        time: Long,
        movie: Movie?,
        seasonIndex: Int? = 0,
        episodeIndex: Int? = 0,
        excludeUrls: Set<String>
    ) {
        when {
            movie == null && !path.isNullOrBlank() -> {
                try {
                    setPlayerSource(
                        time = time,
                        source = playerSource.getSource(path, getString(R.string.no_movie_title)),
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            movie != null && movie.type == MovieType.CINEMA && !path.isNullOrBlank() -> {
                try {
                    setCinemaUrls(movie, time, path)
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            movie != null && movie.type == MovieType.CINEMA -> {
                try {
                    setCinemaUrls(movie, time)
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            movie != null && movie.type == MovieType.SERIAL -> {
                try {
                    setSerialUrls(
                        movie = movie,
                        seasonIndex = seasonIndex,
                        episodeIndex = episodeIndex,
                        position = time,
                        excludeUrls = excludeUrls,
                        selectedUrl = path
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

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
        position: Long,
        excludeUrls: Set<String>,
        selectedUrl: String? = null
    ) {
        val serialSeasons = movie.seasons.sortedBy { it.id }
        allEpisodes = serialSeasons.flatMap { season ->
            season.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
        }

        val size = allEpisodes.size
        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            currentEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes,
                excludeUrls = excludeUrls,
                selectedUrl = selectedUrl
            )
            binding.playerView.setShowNextButton(size > 0)
            binding.playerView.setShowPreviousButton(size > 0)

            player?.apply {
                seekTo(currentEpisodeIndex, position)
                prepare()
            }
        } else {
            binding.progressBar.isVisible = false
            toast(getString(R.string.episodes_not_found))
            findNavController().navigateUp()
        }
    }

    private suspend fun fillPlayerEpisodes(
        serialSeasons: List<SerialSeason>,
        seasonIndex: Int?,
        episodeIndex: Int?,
        allEpisodes: List<SerialEpisode>,
        excludeUrls: Set<String>,
        selectedUrl: String? = null
    ): Int {
        var currentIndexEpisode = 0
        val mediaSources = mutableListOf<MediaSource>()

        for ((s, season) in serialSeasons.withIndex()) {
            val episodes = season.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }

            for ((e, episode) in episodes.withIndex()) {
                if (seasonIndex == s && episodeIndex == e) {
                    currentIndexEpisode = allEpisodes.indexOf(episode).takeIf { it >= 0 } ?: 0
                }

                val preferDash = episode.dash.contains(".mpd", ignoreCase = true)
                val isSelectedEpisode = seasonIndex == s && episodeIndex == e
                val url = when {
                    isSelectedEpisode && !selectedUrl.isNullOrBlank() && selectedUrl !in excludeUrls -> selectedUrl
                    excludeUrls.isEmpty() && preferDash -> episode.dash
                    excludeUrls.isEmpty() -> episode.hls.ifBlank { episode.dash }
                    preferDash && episode.dash.isNotBlank() && episode.dash !in excludeUrls -> episode.dash
                    episode.hls.isNotBlank() && episode.hls !in excludeUrls -> episode.hls
                    episode.dash.isNotBlank() && episode.dash !in excludeUrls -> episode.dash
                    else -> episode.hls.ifBlank { episode.dash }
                }

                val source = playerSource.getSource(
                    url = url,
                    title = episode.title,
                    season = s,
                    episode = e
                )
                if (source != null) {
                    mediaSources.add(source)
                }
            }
        }

        player?.clearMediaItems()
        player?.setMediaSources(mediaSources)

        return currentIndexEpisode
    }

    private fun getCurrentPlaybackUrl(): String? =
        player?.currentMediaItem?.localConfiguration?.uri?.toString()

    private fun updateCurrentEpisodeFromPlayer() {
        val exoPlayer = player ?: return
        val index = exoPlayer.currentMediaItemIndex
        if (index == mediaItemIndex) return

        stopPlaybackProgressTracking()
        mediaItemIndex = index

        val metadata = exoPlayer.currentMediaItem?.mediaMetadata
        val bundle = metadata?.extras
        val newSeason = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
        val newEpisode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0

        title = metadata?.title.toString()
        viewModel.updateCurrentEpisode(newSeason, newEpisode)
        currentEpisodeIndex = index
        setCurrentTitle()

        if (exoPlayer.isPlaying) {
            startPlaybackProgressTracking()
        }
    }

    private fun startPlaybackProgressTracking() {
        if (lastPlaybackProgressAt == 0L) {
            lastPlaybackProgressAt = System.currentTimeMillis()
        }
        playbackProgressHandler.removeCallbacks(playbackProgressRunnable)
        playbackProgressHandler.postDelayed(playbackProgressRunnable, PLAYBACK_PROGRESS_INTERVAL_MS)
    }

    private fun stopPlaybackProgressTracking() {
        playbackProgressHandler.removeCallbacks(playbackProgressRunnable)
        commitPlaybackProgressDelta()
        lastPlaybackProgressAt = 0L
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
        val movie = args.movie ?: return
        if (movie.dbId <= 0L) return

        // positionMs — позиция для продолжения, playedMs — дельта с прошлого тика (MOB-10B).
        val position = player?.currentPosition ?: 0L
        val duration = player?.duration?.takeIf { it > 0L }
        when (movie.type) {
            MovieType.CINEMA -> viewModel.recordProgress(
                movie = movie,
                seasonIndex = 0,
                episodeIndex = 0,
                positionMs = position,
                durationMs = duration,
                playedMsDelta = playedMs,
            )

            MovieType.SERIAL -> viewModel.recordProgress(
                movie = movie,
                seasonIndex = viewModel.uiState.value.season ?: 0,
                episodeIndex = viewModel.uiState.value.episode ?: 0,
                positionMs = position,
                durationMs = duration,
                playedMsDelta = playedMs,
            )

            else -> {}
        }
    }

    private fun saveCurrentPosition(exoPlayer: ExoPlayer) {
        val position = exoPlayer.currentPosition
        if (position <= 0L) return

        val metadata = exoPlayer.currentMediaItem?.mediaMetadata
        val bundle = metadata?.extras
        val currentSeason = bundle?.getInt(AppConstants.Player.SEASON)
            ?: viewModel.uiState.value.season
            ?: 0
        val currentEpisode = bundle?.getInt(AppConstants.Player.EPISODE)
            ?: viewModel.uiState.value.episode
            ?: 0

        viewModel.updatePlaybackPosition(position, currentSeason, currentEpisode)
    }

    private fun saveMoviePosition(exoPlayer: ExoPlayer) {
        val currentPosition = exoPlayer.currentPosition
        if (currentPosition <= 0L) return

        val movie = args.movie
        val dbId = movie?.dbId ?: return

        when (movie.type) {
            MovieType.CINEMA -> {
                viewModel.saveMoviePosition(
                    dbId = dbId,
                    time = currentPosition,
                    season = 0,
                    episode = 0
                )
            }

            MovieType.SERIAL -> {
                val metadata = exoPlayer.currentMediaItem?.mediaMetadata
                val bundle = metadata?.extras
                val currentSeason = bundle?.getInt(AppConstants.Player.SEASON)
                    ?: viewModel.uiState.value.season
                    ?: 0
                val currentEpisode = bundle?.getInt(AppConstants.Player.EPISODE)
                    ?: viewModel.uiState.value.episode
                    ?: 0

                viewModel.saveMoviePosition(
                    dbId = dbId,
                    time = currentPosition,
                    season = currentSeason,
                    episode = currentEpisode
                )
            }

            else -> {}
        }
    }

    private suspend fun setCinemaUrls(
        movie: Movie,
        position: Long,
        path: String? = null
    ) {
        val url = path ?: movie.getCinemaUrl()
        url.takeIf { it.isNotBlank() }?.let { cinemaUrl ->
            currentCinemaUrl = cinemaUrl
            try {
                setPlayerSource(
                    position,
                    playerSource.getSource(
                        url = cinemaUrl,
                        title = movie.title
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        } ?: run {
            toast(getString(R.string.path_not_found))
            findNavController().navigateUp()
        }
    }

    private fun registerVolumeObserver() {
        volumeObserver?.let { registerContentResolver(it) }
    }

    private fun initMediaSession() {
        val engine = player ?: return
        mediaSession = MediaSession.Builder(requireContext(), engine)
            .setId(MEDIA_SESSION_TAG)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun preparePlayer() {
        val currentBinding = _binding ?: return
        if (isPlayerPrepared && player != null) return

        val isRecreatedPlayer = isPlayerPrepared && player == null
        if (isRecreatedPlayer) {
            lastProcessedVersion = -1
        }

        isPlayerPrepared = true

        with(currentBinding) {
            val loadControl = DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(20000, 20000, 1000, 1000)
                .build()

            trackSelector = DefaultTrackSelector(
                requireContext(),
                AdaptiveTrackSelection.Factory()
            ).apply {
                parameters = parameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .setPreferredAudioLanguage("rus")
                    .build()
            }

            playbackSession.acquire {
                ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
                .setRenderersFactory(DefaultRenderersFactory(requireContext()))
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(secToMs(5))
                .setSeekForwardIncrementMs(secToMs(5))
                .build()
                .apply {
                    playWhenReady = !shouldStartPaused
                    addAnalyticsListener(analytic)
                    addListener(listener)
                }
            }

            mediaItemIndex = viewModel.uiState.value.episode ?: 0

            initMediaSession()
            releaseEnhancer()
            initEnhancer()

            youtubeOverlay.performListener(object : YouTubeOverlay.PerformListener {
                override fun onAnimationStart() {
                    youtubeOverlay.visibility = View.VISIBLE
                    resetVolumeHandlerState()
                }

                override fun onAnimationEnd() {
                    youtubeOverlay.visibility = View.GONE
                    hideControlsDelayed()
                }
            }).playerView(playerView)

            playerView.controller(youtubeOverlay)
            youtubeOverlay.player(player!!)
            playerView.player = player
            setPlayerResizeMode(resizeModeIndex)

            // Controller animation is disabled in XML. One visibility callback drives
            // both the Media3 controls and our overlay; no second timer/alpha animation.
            playerView.setControllerVisibilityListener(
                androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                    applyControllerVisibility(visibility == View.VISIBLE)
                }
            )

            playerView.setOnTouchListener { _, event ->
                gestureDetectorCompat?.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasSwipe = swipeActive
                        swipeActive = false
                        if (wasSwipe) {
                            (binding.playerView as? DoubleTapPlayerView)?.cancelInDoubleTapMode()
                            hideVolumeBrightViews()
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> swipeActive
                    else -> false
                }
            }

            setupPopupMenus = true
            qualityPopUp = null
            langPopUp = null

            val currentState = viewModel.uiState.value
            if (currentState.version > 0) {
                viewModel.forceReEmit()
            } else {
                viewModel.setPlayData(
                    path = args.path,
                    movie = args.movie,
                    seasonIndex = args.seasonIndex,
                    episodeIndex = args.episodeIndex,
                )
            }
        }
    }

    private fun resetVolumeHandlerState() {
        volumeHandler.removeCallbacksAndMessages(null)
    }

    private fun initEnhancer() {
        val audioSessionId = player?.audioSessionId
        if (audioSessionId != null && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            try {
                enhancer = LoudnessEnhancer(audioSessionId)
                enhancer?.enabled = true
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
        }
    }

    private fun releaseEnhancer() {
        try {
            enhancer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            enhancer = null
        }
    }

    private fun pipMode() {
        viewModel.requestPipMode()
    }

    private fun requestPipMode() {
        if (requireContext().isPiPAvailable()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PictureInPictureParams.Builder()
                    .apply { setAutoEnabled(this) }
                    .also { requireActivity().enterPictureInPictureMode(it.build()) }
            }
        }
    }

    override fun onPiPMode(isInPipMode: Boolean) {}

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            pipMode()
            true
        } else {
            false
        }

    /** Текущее состояние, которое мы показали для оверлеев поверх видео. */
    private var controlsShown: Boolean? = null

    private fun applyControllerVisibility(visible: Boolean) {
        if (controlsShown == visible) return
        controlsShown = visible
        _binding?.changeVisible(visible)
    }

    private fun FPlayerViewBinding.changeVisible(visible: Boolean) {
        if (_binding == null) return

        val views = listOf(
            tvTitle,
            ivQuality,
            ivExternalPlayer,
            ivResizes,
            ivScreenRotation,
            ivBack,
            ivLang,
            ivBrightness,
            ivVolume
        )

        if (visible) {
            // Один переход видимости без собственных alpha-анимаций: DoubleTapPlayerView уже
            // анимирует контроллер, и наложение двух анимаций давало двойное мигание.
            views.forEach { view ->
                val shouldBeVisible = when (view) {
                    ivQuality -> qualityVisible
                    ivLang -> langVisible
                    ivExternalPlayer -> externalPlayerVisible
                    else -> true
                }
                view.animate().cancel()
                view.alpha = 1f
                view.isVisible = shouldBeVisible
            }

            topGradient.animate().cancel()
            bottomGradient.animate().cancel()
            topGradient.alpha = 1f
            bottomGradient.alpha = 1f

            setScreenRotIconVisible(getOrientation(), false)
        } else {
            views.forEach { view ->
                view.animate().cancel()
                view.alpha = 0f
                view.isVisible = false
            }

            topGradient.animate().cancel()
            bottomGradient.animate().cancel()
            topGradient.alpha = 0f
            bottomGradient.alpha = 0f
        }
    }

    private fun setPlayerSource(time: Long = 0, source: MediaSource?) {
        player?.apply {
            source?.let {
                setMediaSource(it)
                seekTo(time)
                prepare()
            } ?: run {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun setCurrentTitle() {
        val state = viewModel.uiState.value
        val titleText = if (movie?.type == MovieType.SERIAL) {
            val currentSeason = state.season ?: 0
            val currentEpisode = state.episode ?: 0
            getString(
                R.string.serial_title,
                movie?.title,
                (currentSeason + 1).toString(),
                (currentEpisode + 1).toString()
            )
        } else {
            movie?.title
        }

        binding.tvTitle.text = if (!titleText.isNullOrBlank() && titleText != "null") {
            titleText
        } else {
            getString(R.string.no_movie_title)
        }
    }

    private fun openExternalPlayer() {
        if (DeviceUtils.isTV(requireContext())) return
        // Use the current MediaItem, including the selected episode or fallback URL.
        val url = getCurrentPlaybackUrl()
        if (url.isNullOrBlank()) {
            toast(getString(R.string.video_link_not_found))
            return
        }
        playbackErrorDialog?.dismiss()
        playbackErrorDialog = androidx.appcompat.app.AlertDialog.Builder(
            requireContext(), R.style.PlayerAlertDialogTheme
        )
            .setTitle(R.string.open_external_player)
            .setMessage(R.string.external_player_unsupported_source)
            .setPositiveButton(R.string.open_external_player) { _, _ ->
                // Возврат из внешнего плеера не должен сам запускать воспроизведение:
                // позиция во внешнем приложении неизвестна, продолжаем только по кнопке Play.
                shouldStartPaused = true
                player?.let { engine ->
                    pauseForAudioFocusLoss()
                    saveCurrentPosition(engine)
                    saveMoviePosition(engine)
                }
                val opened = ExternalPlayerLauncher(requireContext()).open(url, binding.tvTitle.text.toString())
                if (!opened) toast(getString(R.string.external_player_not_found))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateExternalPlayerVisibility() {
        if (_binding == null) return
        // Keep the action discoverable even while the MediaItem is still preparing.
        // Source availability is checked when the user presses it.
        externalPlayerVisible = !DeviceUtils.isTV(requireContext())
        binding.ivExternalPlayer.isVisible = externalPlayerVisible && controlsShown == true
    }

    private fun setUpPopups() {
        if (!setupPopupMenus) return

        setupPopupMenus = false
        updateExternalPlayerVisibility()

        trackSelector?.generateLanguagesList(requireContext())?.let { list ->
            Timber.tag("PlayChain").d(
                "tracks: audio=%d",
                list.size
            )
            langVisible = list.size > 1
            binding.ivLang.isVisible = langVisible && controlsShown == true
            if (langVisible) {
                langPopUp = PopupMenu(requireContext(), binding.ivLang).apply {
                    list.forEachIndexed { i, item ->
                        menu.add(0, i, 0, item.first)
                    }
                    setOnMenuItemClickListener {
                        setLang(list[it.itemId].second)
                        true
                    }
                }
            }
        }

        trackSelector?.generateQualityList(requireContext())?.let { list ->
            qualityPopUp?.dismiss()
            qualityPopUp = null
            qualityVisible = list.isNotEmpty()
            binding.ivQuality.isVisible = qualityVisible && controlsShown == true
            if (qualityVisible) {
                val overrides = trackSelector?.parameters?.overrides.orEmpty().values
                qualityPopUp = PopupMenu(requireContext(), binding.ivQuality).apply {
                    menu.add(0, 0, 0, R.string.quality_auto).apply {
                        isCheckable = true
                        isChecked = list.none { it.second in overrides }
                    }
                    list.forEachIndexed { i, item ->
                        menu.add(0, i + 1, i + 1, item.first).apply {
                            isCheckable = true
                            isChecked = item.second in overrides
                        }
                    }
                    menu.setGroupCheckable(0, true, true)
                    setOnMenuItemClickListener {
                        setQuality(if (it.itemId == 0) null else list[it.itemId - 1].second)
                        it.isChecked = true
                        true
                    }
                }
            }
        }
    }

    /** Null restores adaptation; changing video quality must not reset audio or subtitles. */
    private fun setQuality(trackSelectionOverride: TrackSelectionOverride?) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .apply { if (trackSelectionOverride != null) addOverride(trackSelectionOverride) }
                .build()
        }
    }

    private fun setLang(override: TrackSelectionOverride) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
        }
    }

    private fun hideControlsDelayed() {
        volumeHandler.removeCallbacksAndMessages(null)
        volumeHandler.postDelayed({
            if (_binding != null) {
                binding.playerView.hideController()
            }
        }, 5000)
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
        stopPlaybackProgressTracking()
        mediaSession?.release()
        mediaSession = null
        releaseEnhancer()

        playbackSession.release()

        isPlayerPrepared = false
        setupPopupMenus = true
        qualityPopUp = null
        langPopUp = null
    }
}
