package com.arny.mobilecinema.presentation.playerview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.ConnectionType
import com.arny.mobilecinema.data.utils.findByGroup
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.data.utils.getErrorUrl
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
import com.arny.mobilecinema.presentation.utils.getOrientation
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.initAudioManager
import com.arny.mobilecinema.presentation.utils.isPiPAvailable
import com.arny.mobilecinema.presentation.utils.registerContentResolver
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.setScreenBrightness
import com.arny.mobilecinema.presentation.utils.setTextColorRes
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.unregisterContentResolver
import com.github.vkay94.dtpv.youtube.YouTubeOverlay
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.properties.Delegates
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.android.ext.android.inject

class PlayerViewFragment : Fragment(R.layout.f_player_view), OnPictureInPictureListener {
    private companion object {
        const val MAX_BOOST_DEFAULT = 1000
        const val MEDIA_SESSION_TAG = "MEDIA_SESSION_ANWAP_TAG"
        const val CONTROLS_ANIMATION_DURATION = 250L
    }

    private val prefs: Prefs by inject()

    private val playerSource: PlayerSource by inject()

    private val viewModel: PlayerViewModel by viewModel()

    // Убраны дублирующие поля - теперь хранятся в ViewModel
    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var currentEpisodeIndex: Int = -1
    private var title: String = ""
    private var currentCinemaUrl: String? = null

    // Убраны: season, episode, timePosition - теперь в ViewModel
    private var qualityVisible: Boolean = false
    private var volumeObserver: ContentObserver? = null
    private var langVisible: Boolean = false
    private var mediaItemIndex: Int = 0
    private var movie: Movie? = null

    private val btnsHandler = Handler(Looper.getMainLooper())
    private val volumeHandler = Handler(Looper.getMainLooper())
    private val args: PlayerViewFragmentArgs by navArgs()

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
    private var resizeModeIndex = 0

    private var setupPopupMenus = true
    private var enhancer: LoudnessEnhancer? = null

    //private lateinit var binding: FPlayerViewBinding
    private var _binding: FPlayerViewBinding? = null
    private val binding get() = _binding!!

    // Сохраняем listener чтобы потом удалить
    private var windowInsetsListener: View.OnApplyWindowInsetsListener? = null

    // Для отслеживания что UI скрыт
    private var isSystemUIHidden = false

    // Сохраняем значения insets для корректного позиционирования
    private var topInset: Int = 0
    private var bottomInset: Int = 0

    private var gestureDetectorCompat: GestureDetectorCompat? = null
    private var audioManager: AudioManager? = null
    private var minSwipeY: Float = 0f
    private var brightness: Int = 0
    private var volume: Int = -1
    private var boost: Int = -1

    // Флаг для предотвращения повторной инициализации
    private var isPlayerPrepared = false

    // Версия state для предотвращения повторной обработки
    private var lastProcessedVersion: Long = -1

    private var volumeObs: Int by Delegates.observable(-1) { _, old, newVolume ->
        if (old != newVolume) {
            volume = newVolume
            boost = 0
            updateGain()
            showVolumeIndicator()
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            updateUIByVolume()
            hideVolumeBrightViews()
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange <= 0) {
            player?.pause()
        }
    }

    private val maxBoost by lazy {
        prefs.get<String>(getString(R.string.pref_max_boost_size))?.toIntOrNull()
            ?: MAX_BOOST_DEFAULT
    }

    private val gestureDetectListener: GestureDetector.OnGestureListener = object :
        GestureDetector.OnGestureListener {
        override fun onDown(e: MotionEvent): Boolean {
            minSwipeY = 0f
            return false
        }

        override fun onShowPress(e: MotionEvent) {
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean = false

        override fun onScroll(
            e1: MotionEvent?,
            event: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            resetVolumeHandlerState()
            minSwipeY += distanceY
            val sWidth = Resources.getSystem().displayMetrics.widthPixels
            val sHeight = Resources.getSystem().displayMetrics.heightPixels
            val border = 100 * Resources.getSystem().displayMetrics.density.toInt()
            if (event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
                return false

            if (abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 50) {
                val increase = distanceY > 0
                if (event.x < sWidth / 2) {
                    //brightness
                    binding.tvBrightness.text = brightness.toString()
                    showBrightnessIndicator()
                    val newValue = if (increase) {
                        brightness + 1
                    } else {
                        brightness - 1
                    }
                    val brightDiv = 30
                    if (newValue in 0..brightDiv) {
                        brightness = newValue
                    }
                    binding.tvVolume.visibility = View.GONE
                    setScreenBrightness(brightness)
                } else {
                    //volume
                    binding.tvBrightness.visibility = View.GONE
                    showVolumeIndicator()
                    val curVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (volume == -1 && curVolume != null) {
                        volume = curVolume
                    }
                    val maxVolume = getMaxVolume()
                    val targetGain = enhancer?.targetGain
                    if (boost == -1 && targetGain != null) {
                        boost = targetGain.toInt()
                    }
                    val newVolume = when {
                        increase -> volume + 1
                        else -> if (boost == 0) volume - 1 else volume
                    }
                    val isVolumeChanges = newVolume in 1..maxVolume
                    if (isVolumeChanges) {
                        volume = newVolume
                    }
                    val newBoost: Int = when {
                        increase -> if (volume == maxVolume) boost + getBoostDiff(boost) else boost
                        else -> if (volume == maxVolume) boost - getBoostDiff(boost) else boost
                    }
                    val isBoostChanges = newBoost in 1..maxBoost
                    if (isBoostChanges) {
                        boost = newBoost
                    }
                    when {
                        isVolumeChanges && !isBoostChanges -> {
                            boost = 0
                            updateGain()
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            updateUIByVolume()
                        }

                        isBoostChanges -> {
                            updateGain()
                            val volumeWithBoost = boost.toFloat() / 10
                            val boostVolume = volume + volumeWithBoost.toInt()
                            binding.tvVolume.text = boostVolume.toString()
                            binding.tvVolume.setTextColorRes(R.color.colorAccent)
                        }
                    }
                }
                hideVolumeBrightViews()
                minSwipeY = 0f
            }
            hideControlsDelayed()
            return true
        }

        private fun getBoostDiff(boost: Int): Int = when {
            boost < 100 -> 10
            boost < 120 -> 20
            boost < 150 -> 30
            boost < 175 -> 50
            boost < 200 -> 75
            else -> 10
        }

        private fun getMaxVolume() =
            audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 30

        override fun onLongPress(e: MotionEvent) {}
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean = false
    }

    private var mediaSession: MediaSessionCompat? = null

    private fun updateGain() {
        try {
            val targetGain = enhancer?.targetGain
            if (targetGain != null) {
                enhancer?.setTargetGain(boost)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideControlsDelayed() {
        volumeHandler.postDelayed({
            binding.playerView.hideController()
        }, 5000)
    }

    private val analytic = object : AnalyticsListener {
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            binding.progressBar.isVisible = false
            var lastError = ""
            when (getConnectionType(requireContext())) {
                ConnectionType.NONE -> {
                    lastError = getString(R.string.internet_connection_error)
                    toast(lastError)
                }

                else -> {
                    lastError = getFullError(error)
                    toast(lastError)
                }
            }
            viewModel.setLastPlayerError(lastError)
            val errorUrl = getErrorUrl(error)
            val serialEpisode = allEpisodes.getOrNull(currentEpisodeIndex)
            viewModel.retryOpenCinema(errorUrl, serialEpisode)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState(playbackState)
        }

        override fun onTracksChanged(tracks: Tracks) {
            val index = player?.currentMediaItemIndex ?: 0
            if (index != mediaItemIndex) {
                mediaItemIndex = index
                player?.let { exoPlayer ->
                    val metadata = exoPlayer.currentMediaItem?.mediaMetadata
                    val bundle = metadata?.extras
                    val newSeason = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
                    val newEpisode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0

                    this@PlayerViewFragment.title = metadata?.title.toString()

                    // Ключевое исправление: обновляем позицию в ViewModel
                    viewModel.updateCurrentEpisode(newSeason, newEpisode)

                    // Обновляем индекс в allEpisodes
                    currentEpisodeIndex = index

                    setCurrentTitle()
                }
                setupPopupMenus = true
            }
        }
    }

    override fun onAttach(context: Context) {
        // Koin injection
        super.onAttach(context)
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
        binding.progressBar.isVisible = true
        // Сбрасываем version чтобы state обработался заново
        lastProcessedVersion = -1
        observeState()
        initListener()
        initSystemUI()
        initPlayerTouchListener()
        initAudioManager()
        initVolumeObserver()
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
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.hide()
        }
        // Скрываем системный UI
        hideSystemUIImmediately()
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

override fun onPause() {
        super.onPause()
        volumeObserver?.let { unregisterContentResolver(it) }
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.show()
        }
        player?.let { exoPlayer ->
            saveCurrentPosition(exoPlayer)
            saveMoviePosition(exoPlayer)
        }
        // Показываем системный UI обратно
        showSystemUIImmediately()
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            player?.let { exoPlayer ->
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
                            val season = bundle?.getInt(AppConstants.Player.SEASON) ?: state.season ?: 0
                            val episode = bundle?.getInt(AppConstants.Player.EPISODE) ?: state.episode ?: 0
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
        super.onDestroyView()
        btnsHandler.removeCallbacksAndMessages(null)
        volumeHandler.removeCallbacksAndMessages(null)

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
        // AudioFocus с учётом версии API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(focusChangeListener)
        }
        gestureDetectorCompat = null
    }

    // AudioFocusRequest для API 26+
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
        } else null
    }

    private fun showVolumeIndicator() {
        binding.tvVolume.alpha = 0f
        binding.tvVolume.isVisible = true
        binding.tvVolume.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
    }

    private fun showBrightnessIndicator() {
        binding.tvBrightness.alpha = 0f
        binding.tvBrightness.isVisible = true
        binding.tvBrightness.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
    }

    private fun hideVolumeBrightViews() {
        btnsHandler.removeCallbacksAndMessages(null)
        btnsHandler.postDelayed({
            binding.tvBrightness.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.tvBrightness.isVisible = false }
                .start()
            binding.tvVolume.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.tvVolume.isVisible = false }
                .start()
        }, 1000)
    }

    private fun updateUIByVolume() {
        binding.tvVolume.text = volume.toString()
        binding.tvVolume.setTextColorRes(R.color.textColorPrimary)
    }

    private fun initVolumeObserver() {
        volumeObserver =
            SettingsContentObserver(requireContext(), Handler(Looper.getMainLooper())) { v ->
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

    private fun initSystemUI() {
        // Применяем insets к элементам управления
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = insets.top
            bottomInset = insets.bottom

            // Обновляем позиции элементов с учётом insets
            updateControlsPadding()

            // Возвращаем insets без изменений
            windowInsets
        }

        // Скрываем системный UI
        hideSystemUIImmediately()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsListener = View.OnApplyWindowInsetsListener { _, insets ->
                // Проверяем что binding ещё существует
                val currentBinding = _binding ?: return@OnApplyWindowInsetsListener insets

                val isVisible = insets.isVisible(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                )
                if (isVisible && !currentBinding.playerView.isControllerVisible) {
                    // Системные бары появились, но контроллер скрыт - скрываем снова
                    hideSystemUIImmediately()
                } else if (isVisible) {
                    currentBinding.playerView.showController()
                }
                // Важно: возвращаем insets для правильной работы
                insets
            }
            requireActivity().window.decorView.setOnApplyWindowInsetsListener(windowInsetsListener)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                val currentBinding = _binding ?: return@setOnSystemUiVisibilityChangeListener

                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    if (currentBinding.playerView.isControllerVisible) {
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

    /**
     * Обновляет padding для элементов управления с учётом системных insets
     */
    private fun updateControlsPadding() {
        val currentBinding = _binding ?: return

        with(currentBinding) {
            // Title с отступом от верха (56dp = 48dp safe area + 8dp)
            (tvTitle.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 8.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    tvTitle.layoutParams = params
                }
            }

            // Кнопка назад
            (ivBack.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 16.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivBack.layoutParams = params
                }
            }

            // ivQuality - верхняя кнопка справа
            (ivQuality.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 16.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivQuality.layoutParams = params
                }
            }

            // ivResizes
            (ivResizes.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 24.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivResizes.layoutParams = params
                }
            }

            // ivLang
            (ivLang.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 32.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivLang.layoutParams = params
                }
            }

            // ivScreenRotation
            (ivScreenRotation.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 40.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivScreenRotation.layoutParams = params
                }
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    /**
     * Немедленно скрывает системный UI
     */
    private fun hideSystemUIImmediately() {
        val window = activity?.window ?: return

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

    /**
     * Показывает системный UI - ПОЛНОЕ восстановление
     */
    private fun showSystemUIImmediately() {
        val window = activity?.window ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: возвращаем decorFitsSystemWindows
            window.setDecorFitsSystemWindows(true)

            window.insetsController?.apply {
                show(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )
                // Сбрасываем поведение на дефолтное
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        isSystemUIHidden = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setScreenRotIconVisible(newConfig.orientation, true)
        // Перескрываем UI при изменении конфигурации
        if (isSystemUIHidden) {
            hideSystemUIImmediately()
        }
    }

    private fun setScreenRotIconVisible(orientation: Int, reset: Boolean) {
        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (reset) {
                    resizeModeIndex = 0
                }
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerVisible
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                if (reset) {
                    resizeModeIndex = 4
                }
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerVisible
            }

            else -> {}
        }
    }

    private fun initListener() {
        // Проверяем binding перед использованием
        val currentBinding = _binding ?: return

        with(currentBinding) {
            ivQuality.setOnClickListener { qualityPopUp?.show() }
            ivLang.setOnClickListener { langPopUp?.show() }
            ivResizes.setOnClickListener { changeResize() }
            ivBack.setOnClickListener {
                findNavController().popBackStack()
            }
            ivScreenRotation.setOnClickListener {
                toggleScreenOrientation()
            }
        }
    }

    /**
     * Переключение ориентации экрана
     */
    private fun toggleScreenOrientation() {
        val currentOrientation = resources.configuration.orientation
        requireActivity().requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Через небольшую задержку сбрасываем на автоматическую ориентацию
        // чтобы автоповорот продолжал работать
        btnsHandler.postDelayed({
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }, 1500)
    }

    private fun changeResize() {
        resizeModeIndex += 1
        if (resizeModeIndex > resizeModes.size - 1) {
            resizeModeIndex = 0
        }
        binding.playerView.resizeMode = resizeModes[resizeModeIndex]
        viewModel.updateResizeModeIndex(resizeModeIndex)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // Проверяем version чтобы не пересоздавать MediaSources без необходимости
                        if (state.version > lastProcessedVersion
                            && (state.path != null || state.movie != null)
                        ) {
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
                }
                launch {
                    viewModel.pipMode.collect { isPipMode ->
                        if (isPipMode) {
                            requestPipMode()
                        }
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
                    viewModel.cachedResizeModeIndex.collectLatest { cachedIndex ->
                        if (cachedIndex != resizeModeIndex) {
                            resizeModeIndex = cachedIndex
                            binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                        }
                    }
                }
            }
        }
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
                        excludeUrls = excludeUrls
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
        excludeUrls: Set<String>
    ) {
        val seasons = movie.seasons
        val serialSeasons = seasons.sortedBy { it.id }
        allEpisodes = serialSeasons.flatMap {
            it.episodes.sortedBy { episode ->
                findByGroup(episode.episode, "(\\d+).*".toRegex(), 1)?.toIntOrNull() ?: 0
            }
        }
        val size = allEpisodes.size
        if (allEpisodes.all { it.dash.isNotBlank() || it.hls.isNotBlank() }) {
            currentEpisodeIndex = fillPlayerEpisodes(
                serialSeasons = serialSeasons.toList(),
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                allEpisodes = allEpisodes,
                excludeUrls = excludeUrls
            )
            binding.playerView.setShowNextButton(size > 0)
            binding.playerView.setShowPreviousButton(size > 0)
player?.apply {
                player?.seekTo(currentEpisodeIndex, position)
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
                    excludeUrls.isEmpty() -> episode.hls
                    !excludeUrls.contains(episode.dash) -> episode.dash
                    !excludeUrls.contains(episode.hls) -> episode.hls
                    else -> episode.hls
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

    private fun saveCurrentPosition(exoPlayer: ExoPlayer) {
        val position = exoPlayer.currentPosition
        if (position <= 0L) return

        val metadata = exoPlayer.currentMediaItem?.mediaMetadata
        val bundle = metadata?.extras
        val currentSeason = bundle?.getInt(AppConstants.Player.SEASON)
            ?: viewModel.uiState.value.season ?: 0
        val currentEpisode = bundle?.getInt(AppConstants.Player.EPISODE)
            ?: viewModel.uiState.value.episode ?: 0

        viewModel.updatePlaybackPosition(position, currentSeason, currentEpisode)
    }

    private fun saveMoviePosition(exoPlayer: ExoPlayer) {
        val currentPosition = exoPlayer.currentPosition
        if (currentPosition <= 0L) {
            return
        }

        val movie = args.movie
        val dbId = movie?.dbId

        if (dbId == null) {
            return
        }

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
                // Для сериалов берём позицию из metadata или из текущего state
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
            else -> {
                // Для других типов не сохраняем
            }
        }
    }

    private suspend fun setCinemaUrls(
        movie: Movie,
        position: Long,
        path: String? = null
    ) {
        val url = path ?: movie.getCinemaUrl()
        url.takeIf { it.isNotBlank() }?.let { cinemaUrl ->
            this.currentCinemaUrl = cinemaUrl
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

    private fun registerVolumeObserver() {
        volumeObserver?.let { registerContentResolver(it) }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(requireContext(), MEDIA_SESSION_TAG)
        mediaSession?.let {
            it.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            MediaSessionConnector(it).setPlayer(player)
        }
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
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(64 * 1024, 128 * 1024, 1024, 1024)
                    .build()
            trackSelector =
                DefaultTrackSelector(requireContext(), AdaptiveTrackSelection.Factory()).apply {
                    parameters = parameters.buildUpon()
                        .setPreferredAudioLanguage("rus")
                        .build()
                }
            player = ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
                .setRenderersFactory(DefaultRenderersFactory(requireContext()))
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(secToMs(5))
                .setSeekForwardIncrementMs(secToMs(5))
                .build()
                .apply {
                    playWhenReady = true
                    addAnalyticsListener(analytic)
                    addListener(listener)
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
            playerView.resizeMode = resizeModes[resizeModeIndex]
            playerView.setControllerVisibilityListener { visibility ->
                if (isVisible) {
                    changeVisible(visibility == View.VISIBLE)
                }
            }
            playerView.setOnTouchListener { _, event ->
                gestureDetectorCompat?.onTouchEvent(event)
                false
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

    // Безопасное освобождение LoudnessEnhancer
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
            PictureInPictureParams.Builder()
                .apply { setAutoEnabled(this) }
                .also { requireActivity().enterPictureInPictureMode(it.build()) }
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
        // Дополнительная проверка
        if (_binding == null) return

        val targetAlpha = if (visible) 1f else 0f
        val views = listOf(
            tvTitle, ivQuality, ivResizes, ivScreenRotation,
            ivBack, ivLang
        )

        if (visible) {
            // Показать — сначала делаем visible, потом анимируем alpha
            views.forEach { view ->
                val shouldBeVisible = when (view) {
                    ivQuality -> qualityVisible
                    ivLang -> langVisible
                    else -> true
                }
                if (shouldBeVisible) {
                    view.alpha = 0f
                    view.isVisible = true
                    view.animate()
                        .alpha(1f)
                        .setDuration(CONTROLS_ANIMATION_DURATION)
                        .start()
                }
            }
            // Анимация градиентов
            topGradient.animate().alpha(1f)
                .setDuration(CONTROLS_ANIMATION_DURATION).start()
            bottomGradient.animate().alpha(1f)
                .setDuration(CONTROLS_ANIMATION_DURATION).start()
            // НЕ скрываем системный UI при показе контролов - предотвращает прыжки
            setScreenRotIconVisible(getOrientation(), false)
        } else {
            views.forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(CONTROLS_ANIMATION_DURATION)
                    .withEndAction { view.isVisible = false }
                    .start()
            }
            topGradient.animate().alpha(0f)
                .setDuration(CONTROLS_ANIMATION_DURATION).start()
            bottomGradient.animate().alpha(0f)
                .setDuration(CONTROLS_ANIMATION_DURATION).start()
            // НЕ показываем системный UI при скрытии контролов - предотвращает прыжки
        }
    }

private fun setPlayerSource(time: Long = 0, source: MediaSource?) {
        player?.apply {
            source?.let {
                setMediaSource(source)
                seekTo(time)
                prepare()
            }
        }
    }

    // setCurrentTitle теперь берёт данные из ViewModel state
    private fun setCurrentTitle() {
        val state = viewModel.uiState.value
        val title = if (movie?.type == MovieType.SERIAL) {
            val currentSeason = state.season ?: 0
            val currentEpisode = state.episode ?: 0
            getString(
                R.string.serial_title,
                movie?.title,
                (currentSeason + 1).toString(),
                (currentEpisode + 1).toString()
            )
        } else movie?.title

        if (!title.isNullOrBlank() && title != "null") {
            binding.tvTitle.text = title
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
        mediaSession?.release()
        mediaSession = null
        releaseEnhancer()
        player?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analytic)
            it.stop()
            it.release()
            player = null
        }
        isPlayerPrepared = false
        setupPopupMenus = true
        qualityPopUp = null
        langPopUp = null
    }
}
