package com.arny.mobilecinema.presentation.playerview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
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
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateLanguagesList
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.playerview.dtpv.YouTubeOverlay
import com.arny.mobilecinema.presentation.utils.getOrientation
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.initAudioManager
import com.arny.mobilecinema.presentation.utils.isPiPAvailable
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.registerContentResolver
import com.arny.mobilecinema.presentation.utils.secToMs
import com.arny.mobilecinema.presentation.utils.setScreenBrightness
import com.arny.mobilecinema.presentation.utils.setTextColorRes
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.unregisterContentResolver
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import kotlin.math.abs
import kotlin.properties.Delegates

@UnstableApi
class PlayerViewFragment : Fragment(R.layout.f_player_view), OnPictureInPictureListener {
    private companion object {
        const val MAX_BOOST_DEFAULT = 1000
    }

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): PlayerViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var prefs: Prefs

    @Inject
    lateinit var playerSource: PlayerSource
    private var mediaSession: MediaSession? = null
    private val viewModel: PlayerViewModel by viewModelFactory { viewModelFactory.create() }
    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var currentEpisodeIndex: Int = -1
    private var title: String = ""
    private var currentCinemaUrl: String? = null
    private var timePosition: Long = 0L
    private var season: Int = 0
    private var episode: Int = 0
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
    private lateinit var binding: FPlayerViewBinding
    private var gestureDetectorCompat: GestureDetectorCompat? = null
    private var audioManager: AudioManager? = null
    private var minSwipeY: Float = 0f
    private var brightness: Int = 0
    private var volume: Int = -1
    private var boost: Int = -1
    private var volumeObs: Int by Delegates.observable(-1) { _, old, newVolume ->
        if (old != newVolume) {
            volume = newVolume
            boost = 0
            updateGain()
            binding.tvVolume.visibility = View.VISIBLE
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
            //minSwipeY for slowly increasing brightness & volume on swipe --> try changing 50 (<50 --> quick swipe & > 50 --> slow swipe
            // & test with your custom values
            if (abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 50) {
                val increase = distanceY > 0
                if (event.x < sWidth / 2) {
                    //brightness
                    binding.tvBrightness.text = brightness.toString()
                    binding.tvBrightness.visibility = View.VISIBLE
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
                    binding.tvVolume.visibility = View.VISIBLE
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
                player?.let {
                    val metadata = it.currentMediaItem?.mediaMetadata
                    this@PlayerViewFragment.title = metadata?.title.toString()
                    setCurrentTitle()
                }
                setupPopupMenus = true
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
        val view = FPlayerViewBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progressBar.isVisible = true
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
            saveMoviePosition(exoPlayer)
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
        audioManager?.abandonAudioFocus(focusChangeListener)
        gestureDetectorCompat = null
    }

    private fun hideVolumeBrightViews() {
        btnsHandler.removeCallbacksAndMessages(null)
        btnsHandler.postDelayed({
            binding.tvBrightness.isVisible = false
            binding.tvVolume.isVisible = false
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
        setScreenRotIconVisible(newConfig.orientation, true)
    }

    private fun setScreenRotIconVisible(orientation: Int, reset: Boolean) {
        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (reset) {
                    resizeModeIndex = 0
                }
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerFullyVisible
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                if (reset) {
                    resizeModeIndex = 4
                }
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.ivScreenRotation.isVisible = binding.playerView.isControllerFullyVisible
            }

            else -> {}
        }
    }

    private fun initListener() = with(binding) {
        ivQuality.setOnClickListener { qualityPopUp?.show() }
        ivLang.setOnClickListener { langPopUp?.show() }
        ivResizes.setOnClickListener { changeResize() }
        ivBack.setOnClickListener {
            findNavController().popBackStack()
        }
        ivScreenRotation.setOnClickListener {
            if (getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
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
        launchWhenCreated {
            viewModel.uiState.collect { state ->
                if (state.path != null || state.movie != null) {
                    movie = state.movie
                    val (season, episode) = getSerialPosition(state.season, state.episode)
                    setCurrentTitle()
                    setMediaSources(
                        path = state.path,
                        time = getTimePosition(state.time),
                        movie = movie,
                        seasonIndex = season,
                        episodeIndex = episode,
                        excludeUrls = state.excludeUrls
                    )
                }
            }
        }
        launchWhenCreated {
            viewModel.pipMode.collect { isPipMode ->
                if (isPipMode) {
                    requestPipMode()
                }
            }
        }
        launchWhenCreated {
            viewModel.toast.collectLatest { toastRes ->
                toast(toastRes.toString(requireContext()))
            }
        }
        launchWhenCreated {
            viewModel.back.collectLatest {
                findNavController().navigateUp()
            }
        }
        launchWhenCreated {
            viewModel.cachedResizeModeIndex.collectLatest { cachedIndex ->
                if (cachedIndex != resizeModeIndex) {
                    resizeModeIndex = cachedIndex
                    binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                }
            }
        }
    }

    private fun getTimePosition(stateTimePosition: Long) =
        if (stateTimePosition >= timePosition) stateTimePosition else timePosition

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

    private fun saveMoviePosition(exoPlayer: ExoPlayer) {
        this.timePosition = exoPlayer.currentPosition
        val metadata = exoPlayer.currentMediaItem?.mediaMetadata
        val bundle = metadata?.extras
        val newSeason = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
        val newEpisode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0
//        Timber.d("saveMoviePosition: dbId:${args.movie?.dbId}, time:$timePosition, season:$season->$newSeason, episode:$episode->$newEpisode")
        if (timePosition != 0L) {
            this.season = newSeason
            this.episode = newEpisode
            viewModel.saveMoviePosition(args.movie?.dbId, timePosition, season, episode)
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
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                // Разрешить подключение всем контроллерам
                return MediaSession.ConnectionResult.accept(
                    SessionCommands.EMPTY,
                    Player.Commands.EMPTY
                )
            }
        }
        player?.let {
            mediaSession = MediaSession.Builder(requireContext(), it)
                .setCallback(sessionCallback)
                .build()
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun preparePlayer() {
        with(binding) {
            trackSelector =
                DefaultTrackSelector(requireContext(), AdaptiveTrackSelection.Factory()).apply {
                    parameters.buildUpon().setPreferredAudioLanguage("rus")
                }
            player = ExoPlayer.Builder(requireContext())
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(secToMs(5))
                .setSeekForwardIncrementMs(secToMs(5))
                .build()
                .apply {
                    playWhenReady = true
                    addAnalyticsListener(analytic)
                }
            initMediaSession()
            enhancer?.release()
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
            playerView.setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    if (isVisible) {
                        changeVisible(visibility == View.VISIBLE)
                    }
                }
            )
            playerView.setOnTouchListener { _, event ->
                gestureDetectorCompat?.onTouchEvent(event)
                false
            }
            viewModel.setPlayData(
                path = args.path,
                movie = args.movie,
                seasonIndex = args.seasonIndex,
                episodeIndex = args.episodeIndex,
            )
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
        if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
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
            setScreenRotIconVisible(getOrientation(), false)
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

    private fun setPlayerSource(time: Long = 0, source: MediaSource?) {
        player?.apply {
            source?.let {
                setMediaSource(source)
                seekTo(time)
                addListener(listener)
                prepare()
            }
        }
    }

    private fun setCurrentTitle() {
        val title = if (movie?.type == MovieType.SERIAL) {
            val bundle = player?.mediaMetadata?.extras
            val newSeason = bundle?.getInt(AppConstants.Player.SEASON) ?: 0
            val newEpisode = bundle?.getInt(AppConstants.Player.EPISODE) ?: 0
            getString(
                R.string.serial_title,
                movie?.title,
                (newSeason + 1).toString(),
                (newEpisode + 1).toString()
            )
        } else movie?.title

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
    /*private fun initMoreLinksPopup() {
        if (movie?.type == MovieType.CINEMA) {
            val cinemaUrlData = movie?.cinemaUrlData
            val hdUrls = cinemaUrlData?.hdUrl?.urls.orEmpty()
            val cinemaUrls = cinemaUrlData?.cinemaUrl?.urls.orEmpty()
            val fullLinkList = hdUrls + cinemaUrls
            val popupItems = fullLinkList.mapIndexed { index, s -> "Ссылка ${index + 1}" to s }
            val notEmpty = fullLinkList.isNotEmpty()
            binding.ivMoreLink.isVisible = notEmpty
            if (notEmpty) {
                moreLinkPopUp = PopupMenu(requireContext(), binding.ivMoreLink)
                for ((i, items) in popupItems.withIndex()) {
                    moreLinkPopUp?.menu?.add(0, i, 0, items.first)
                }
                moreLinkPopUp?.setOnMenuItemClickListener { menuItem ->
                    launchWhenCreated {
                        setMediaSources(
                            path = popupItems[menuItem.itemId].second,
                            time = getTimePosition(player?.currentPosition ?: 0),
                            movie = movie,
                        )
                    }
                    true
                }
            }
        }
    }*/

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

    /*private fun setSubTitles(trackSelectionOverride: TrackSelectionOverride) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .clearOverrides()
                .setSelectUndeterminedTextLanguage(true)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                .addOverride(trackSelectionOverride)
                .setTunnelingEnabled(true)
                .build()
        }
    }*/

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
        player?.let {
            it.removeListener(listener)
            it.stop()
            it.release()
            player = null
        }
    }
}