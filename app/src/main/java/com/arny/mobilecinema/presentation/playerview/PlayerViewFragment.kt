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
import com.arny.mobilecinema.presentation.utils.initAudioManager
import com.arny.mobilecinema.presentation.utils.isPiPAvailable
import com.arny.mobilecinema.presentation.utils.registerContentResolver
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.setScreenBrightness
import com.arny.mobilecinema.presentation.utils.setTextColorRes
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
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.properties.Delegates

class PlayerViewFragment : Fragment(R.layout.f_player_view), OnPictureInPictureListener {

    private companion object {
        const val MAX_BOOST_DEFAULT = 1000
        const val MEDIA_SESSION_TAG = "MEDIA_SESSION_ANWAP_TAG"
        const val CONTROLS_ANIMATION_DURATION = 250L
    }

    private val prefs: Prefs by inject()
    private val playerSource: PlayerSource by inject()
    private val viewModel: PlayerViewModel by viewModel()

    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var currentEpisodeIndex: Int = -1
    private var title: String = ""
    private var currentCinemaUrl: String? = null

    private var qualityVisible: Boolean = false
    private var volumeObserver: ContentObserver? = null
    private var langVisible: Boolean = false
    private var mediaItemIndex: Int = 0
    private var movie: Movie? = null

    private var brightnessVolumeController: BrightnessVolumeController? = null

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
            brightnessVolumeController?.updateVolumeExternal(
                volume ?: 0,
                audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            )
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
                // Полноэкранные свайпы яркости/громкости выключены.
                // Управление через иконки и BrightnessVolumeController.
                return false
            }

            override fun onLongPress(e: MotionEvent) {}
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean = false
        }

    private var mediaSession: MediaSessionCompat? = null

    private val analytic = object : AnalyticsListener {}

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            binding.progressBar.isVisible = false
            val lastError = when (getConnectionType(requireContext())) {
                ConnectionType.NONE -> getString(R.string.internet_connection_error)
                else -> getFullError(error)
            }
            toast(lastError)
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

                    title = metadata?.title.toString()
                    viewModel.updateCurrentEpisode(newSeason, newEpisode)
                    currentEpisodeIndex = index
                    setCurrentTitle()
                }
                setupPopupMenus = true
            }
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
        binding.progressBar.isVisible = true
        lastProcessedVersion = -1

        observeState()
        initListener()
        initSystemUI()
        initPlayerTouchListener()
        initAudioManager()
        initVolumeObserver()
        initBrightnessVolumeController()
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

        player?.let { exoPlayer ->
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

    private fun updateGain() {
        try {
            if (enhancer?.targetGain != null) {
                enhancer?.setTargetGain(boost)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        binding.tvVolume.text = volume.toString()
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

    private fun initSystemUI() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = insets.top
            bottomInset = insets.bottom
//            updateControlsPadding()
            windowInsets
        }

        hideSystemUIImmediately()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsListener = View.OnApplyWindowInsetsListener { _, insets ->
                val currentBinding = _binding ?: return@OnApplyWindowInsetsListener insets

                val isVisible = insets.isVisible(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )

                if (isVisible && !currentBinding.playerView.isControllerVisible) {
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

            (ivResizes.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 24.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivResizes.layoutParams = params
                }
            }

            (ivLang.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 32.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivLang.layoutParams = params
                }
            }

            (ivScreenRotation.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 40.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivScreenRotation.layoutParams = params
                }
            }

            (ivBrightness.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 48.dpToPx()
                if (params.topMargin != topMargin) {
                    params.topMargin = topMargin
                    ivBrightness.layoutParams = params
                }
            }

            (ivVolume.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val topMargin = topInset + 56.dpToPx()
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
                systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_DEFAULT
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
                if (reset) resizeModeIndex = 0
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerVisible
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                if (reset) resizeModeIndex = 4
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerVisible
            }
        }
    }

    private fun initListener() {
        val currentBinding = _binding ?: return

        with(currentBinding) {
            ivQuality.setOnClickListener { qualityPopUp?.show() }
            ivLang.setOnClickListener { langPopUp?.show() }
            ivResizes.setOnClickListener { changeResize() }
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
                        if (cachedIndex != resizeModeIndex) {
                            resizeModeIndex = cachedIndex
                            binding.playerView.resizeMode = resizeModes[resizeModeIndex]
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
                excludeUrls = excludeUrls
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
                    currentIndexEpisode = allEpisodes.indexOf(episode).takeIf { it >= 0 } ?: 0
                }

                val url = when {
                    excludeUrls.isEmpty() -> episode.hls.ifBlank { episode.dash }
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
        mediaSession = MediaSessionCompat(requireContext(), MEDIA_SESSION_TAG)
        mediaSession?.let {
            it.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
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
            val loadControl = DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(20000, 20000, 1000, 1000)
                .build()

            trackSelector = DefaultTrackSelector(
                requireContext(),
                AdaptiveTrackSelection.Factory()
            ).apply {
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

    private fun FPlayerViewBinding.changeVisible(visible: Boolean) {
        if (_binding == null) return

        val views = listOf(
            tvTitle,
            ivQuality,
            ivResizes,
            ivScreenRotation,
            ivBack,
            ivLang,
            ivBrightness,
            ivVolume
        )

        if (visible) {
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

            topGradient.animate().alpha(1f)
                .setDuration(CONTROLS_ANIMATION_DURATION)
                .start()
            bottomGradient.animate().alpha(1f)
                .setDuration(CONTROLS_ANIMATION_DURATION)
                .start()

            setScreenRotIconVisible(getOrientation(), false)
        } else {
            views.forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(CONTROLS_ANIMATION_DURATION)
                    .withEndAction {
                        if (_binding != null) view.isVisible = false
                    }
                    .start()
            }

            topGradient.animate().alpha(0f)
                .setDuration(CONTROLS_ANIMATION_DURATION)
                .start()
            bottomGradient.animate().alpha(0f)
                .setDuration(CONTROLS_ANIMATION_DURATION)
                .start()
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

    private fun setUpPopups() {
        if (!setupPopupMenus) return

        setupPopupMenus = false

        trackSelector?.generateLanguagesList(requireContext())?.let { list ->
            langVisible = list.size > 1
            binding.ivLang.isVisible = langVisible
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
            qualityVisible = list.size > 1
            binding.ivQuality.isVisible = qualityVisible
            if (qualityVisible) {
                qualityPopUp = PopupMenu(requireContext(), binding.ivQuality).apply {
                    list.forEachIndexed { i, item ->
                        menu.add(0, i, 0, item.first)
                    }
                    setOnMenuItemClickListener {
                        setQuality(list[it.itemId].second)
                        true
                    }
                }
                setQuality(list[0].second)
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
        mediaSession?.release()
        mediaSession = null
        releaseEnhancer()

        player?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analytic)
            it.stop()
            it.release()
        }
        player = null

        isPlayerPrepared = false
        setupPopupMenus = true
        qualityPopUp = null
        langPopUp = null
    }
}