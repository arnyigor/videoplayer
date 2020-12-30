package com.arny.homecinema.presentation.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.DataThrowable
import com.arny.homecinema.databinding.FDetailsBinding
import com.arny.homecinema.di.models.*
import com.arny.homecinema.presentation.utils.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.*
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.properties.Delegates


class DetailsFragment : Fragment() {

    private var trackSelector: DefaultTrackSelector? = null
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentMovie: Movie? = null
    private var currentVideo: Video? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private var videoStartRestore = false
    private var videoRestored = false
    private val args: DetailsFragmentArgs by navArgs()
    private var exoPlayer: SimpleExoPlayer? = null
    private var drawerLocker: DrawerLocker? = null
    private var playControlsVisible by Delegates.observable(true) { _, oldValue, visible ->
        if (oldValue != visible && activity != null && isAdded) {
            val land = resources.configuration.orientation == ORIENTATION_LANDSCAPE
            if (land) {
                setFullScreen(activity as AppCompatActivity?, !visible)
            }
            setSpinEpisodesVisible(visible && currentVideo?.type == MovieType.SERIAL)
            setCustomTitleVisible(land && visible)
            binding.mtvQuality.isVisible = visible
        }
    }

    private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            currentEpisodePosition = 0
            currentVideo?.currentPosition = 0
            updatePlayerPosition()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val episodesChangelistener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            currentVideo?.currentPosition = 0
            updatePlayerPosition()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private companion object {
        const val KEY_MOVIE = "KEY_MOVIE"
        const val KEY_VIDEO = "KEY_VIDEO"
        const val KEY_SEASON = "KEY_SEASON"
        const val KEY_EPISODE = "KEY_EPISODE"
        const val BUFFER_64K = 64 * 1024
        const val BUFFER_128K = 128 * 1024
        const val BUFFER_1K = 1024
    }

    @Inject
    lateinit var vm: DetailsViewModel

    private val binding by viewBinding { FDetailsBinding.bind(it).also(::initBinding) }

    private val playerListener = object : Player.EventListener {

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
            trackSelector?.let { _ ->
                (exoPlayer?.currentTimeline
                    ?.getWindow(exoPlayer?.currentWindowIndex ?: 0, Timeline.Window())
                    ?.mediaItem?.playbackProperties?.tag as? HashMap<*, *>)?.let { map ->
                    updateSelection(map)
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val window = timeline.getWindow(exoPlayer?.currentWindowIndex ?: 0, Timeline.Window())
            if (reason == TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                (window.mediaItem.playbackProperties?.tag as? HashMap<*, *>)
                    ?.let { map ->
                        updateSelection(map)
                    }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val window = requireActivity().window
            if (isPlaying) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun updateSelection(map: HashMap<*, *>) {
        for ((key, value) in map.entries) {
            val season = (key as? Int) ?: 0
            val episode = (value as? Int) ?: 0
            binding.spinSeasons.setSelection(season, false)
            binding.spinEpisodes.setSelection(episode, false)
            currentSeasonPosition = season
            currentEpisodePosition = episode
        }
    }

    private fun initBinding(binding: FDetailsBinding) {
        return with(binding) {
            val movie = args.movie
            initTrackAdapters()
            vm.loadVideo(movie)
            vm.data.observe(this@DetailsFragment, { dataResult ->
                when (dataResult) {
                    is DataResult.Success -> onMovieLoaded(dataResult.data)
                    is DataResult.Error -> toastError(dataResult.throwable)
                }
            })
            vm.cached.observe(this@DetailsFragment, { dataResult ->
                when (dataResult) {
                    is DataResult.Success -> {
                    }
                    is DataResult.Error -> toastError(dataResult.throwable)
                }
            })
            mtvQuality.setOnClickListener { tv -> initQualityPopup(tv) }
        }
    }

    private fun initQualityPopup(view: View) = with(binding) {
        currentVideo?.hlsList?.keys?.toList()?.let { keys ->
            val qualityPopup = PopupMenu(requireContext(), view)
            qualityPopup.setOnMenuItemClickListener { item ->
                val quality = keys.getOrNull(item.itemId - 1)
                if (quality != currentVideo?.selectedHls) {
                    mtvQuality.text = getString(R.string.quality_format, quality)
                    currentVideo = currentVideo?.copy(
                        selectedHls = quality,
                        playWhenReady = exoPlayer?.playWhenReady == true,
                        currentPosition = exoPlayer?.currentPosition ?: 0
                    )
                    currentMovie = currentMovie?.copy(
                        selectedQuality = quality,
                        video = currentVideo
                    )
                    if (currentMovie != null) {
                        releasePlayer()
                        initPlayer(currentMovie)
                        restorePlayerState()
                    }
                }
                false
            }
            val menu = qualityPopup.menu
            menu.add(Menu.NONE, 0, 0, getString(R.string.video_quality))
            for ((ind, key) in keys.withIndex()) {
                menu.add(1, (ind + 1), (ind + 1), key)
            }
            menu.setGroupCheckable(1, true, true)
            val selectedIndex = keys.indexOf(currentVideo?.selectedHls)
            if (selectedIndex >= 0) {
                menu.findItem(selectedIndex + 1).isChecked = true
            }
            qualityPopup.show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(video: Video) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = video.title
        binding.mtvTitle.text = video.title
        setCustomTitleVisible(resources.configuration.orientation == ORIENTATION_LANDSCAPE)
        setSpinEpisodesVisible(currentVideo?.type == MovieType.SERIAL)
    }

    private fun setSpinEpisodesVisible(visible: Boolean) = with(binding) {
        spinEpisodes.isVisible = visible
        spinSeasons.isVisible = visible
    }

    private fun FDetailsBinding.initTrackAdapters() {
        seasonsTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
        episodesTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
        spinSeasons.adapter = seasonsTracksAdapter
        spinEpisodes.adapter = episodesTracksAdapter
        spinSeasons.setSelection(currentSeasonPosition, false)
        spinEpisodes.setSelection(currentEpisodePosition, false)
        spinSeasons.updateSpinnerItems(seasonsChangeListener)
        spinEpisodes.updateSpinnerItems(episodesChangelistener)
    }

    private fun updatePlayerPosition() {
        val currentTimeline = exoPlayer?.currentTimeline
        val count = currentTimeline?.windowCount ?: 0
        val seasons = currentMovie?.serialData?.seasons
        seasons?.let {
            val allEpisodes = seasons.flatMap { it.episodes ?: emptyList() }
            val playerSeason = seasons.getOrNull(currentSeasonPosition)
            val episode = playerSeason?.let { it.episodes?.getOrNull(currentEpisodePosition) }
            val indexOf = allEpisodes.indexOf(episode)
            val windowIndex = indexOf.takeIf { it >= 0 } ?: 0
            if (count > windowIndex) {
                currentVideo = currentVideo?.copy(
                    id = episode?.id,
                    title = episode?.title,
                    type = MovieType.SERIAL,
                    hlsList = episode?.hlsList
                )
                exoPlayer?.seekTo(windowIndex, currentVideo?.currentPosition ?: 0)
            }
        }
        currentVideo?.let { updateUI(it) }
    }

    private fun updateCurrentSerialPosition() {
        currentSeasonPosition = binding.spinSeasons.selectedItemPosition
        currentEpisodePosition = binding.spinEpisodes.selectedItemPosition
    }

    private fun onMovieLoaded(movie: Movie?) {
        lifecycleScope.launch {
            initPlayer(movie)
        }
    }

    private fun initPlayer(movie: Movie? = null) {
        movie?.let {
            if (!videoRestored) {
                if (currentMovie?.uuid != movie.uuid) {
                    currentMovie = movie
                }
                if (movie.video != currentVideo) {
                    currentVideo = movie.video
                }
            }
        }
        currentVideo?.let { video ->
            videoRestored = movie == null && videoStartRestore
            updateSpinData()
            createPlayer()
            binding.mtvQuality.text = getString(R.string.quality_format, video.selectedHls)
            binding.plVideoPLayer.player = exoPlayer
            binding.plVideoPLayer.setControllerVisibilityListener { viewVisible ->
                if (activity != null && isAdded) {
                    playControlsVisible = when (viewVisible) {
                        View.VISIBLE -> true
                        else -> false
                    }
                }
            }
            setMediaItems(video, currentMovie)
            exoPlayer?.prepare()
            updatePlayerPosition()
        }
    }

    private fun restorePlayerState() {
        currentVideo?.let { video ->
            exoPlayer?.seekTo(video.currentPosition)
            val playWhenReady = video.playWhenReady
            exoPlayer?.playWhenReady = playWhenReady
            if (playWhenReady) {
                binding.plVideoPLayer.hideController()
            }
        }
    }

    private fun setMediaItems(video: Video, movie: Movie?) {
        when (movie?.type) {
            MovieType.CINEMA, MovieType.CINEMA_LOCAL -> playerAddVideoData(video)
            MovieType.SERIAL, MovieType.SERIAL_LOCAL -> playerAddSerialdata(movie)
            else -> playerAddVideoData(video)
        }
    }

    private fun updateSpinData() = with(binding) {
        val cachedSeasonPosition = currentMovie?.currentSeasonPosition ?: 0
        val cachedEpisodPosition = currentMovie?.currentEpisodePosition ?: 0
        if (cachedSeasonPosition != 0 && currentSeasonPosition == 0) {
            currentSeasonPosition = cachedSeasonPosition
        }
        if (cachedEpisodPosition != 0 && currentEpisodePosition == 0) {
            currentEpisodePosition = cachedEpisodPosition
        }
        val seasons = currentMovie?.serialData?.seasons
        val seasonsList = seasons?.mapIndexed { index, _ -> "${index + 1} сезон" }
        if (!seasonsList.isNullOrEmpty()) {
            spinSeasons.updateSpinnerItems(seasonsChangeListener) {
                seasonsTracksAdapter?.clear()
                seasonsTracksAdapter?.addAll(seasonsList)
                spinSeasons.setSelection(currentSeasonPosition, false)
            }

            spinEpisodes.updateSpinnerItems(episodesChangelistener) {
                val seriesList = seasons.getOrNull(currentSeasonPosition)
                    ?.episodes?.mapIndexed { index, _ -> "${index + 1} серия" }
                episodesTracksAdapter?.clear()
                episodesTracksAdapter?.addAll(seriesList)
                spinEpisodes.setSelection(currentEpisodePosition, false)
            }
        }
    }

    private fun createFileSource(fileUri: Uri): MediaSource {
        val playerInfo: String = Util.getUserAgent(requireContext(), "ExoPlayerInfo")
        val dataSourceFactory = DefaultDataSourceFactory(
            requireContext(), playerInfo
        )
        val item = MediaItem.Builder()
            .setUri(fileUri)
            .setMediaId(id.toString())
            .build()
        return ProgressiveMediaSource.Factory(dataSourceFactory, DefaultExtractorsFactory())
            .createMediaSource(item)
    }

    private fun createPlayer() {
        val adaptiveTrackSelection: TrackSelection.Factory = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(requireContext(), adaptiveTrackSelection)
        trackSelector?.let { selector ->
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        BUFFER_64K,
                        BUFFER_128K,
                        BUFFER_1K,
                        BUFFER_1K
                    )
                    .build()
            exoPlayer = SimpleExoPlayer.Builder(
                requireContext(),
                DefaultRenderersFactory(requireContext())
            )
                .setLoadControl(loadControl)
                .setTrackSelector(selector)
                .build()
            exoPlayer?.addAnalyticsListener(EventLogger(trackSelector))
            exoPlayer?.addListener(playerListener)
        }
    }

    private fun setCustomTitleVisible(visible: Boolean) {
        binding.mtvTitle.isVisible = visible
    }

    private fun buildMediaSource(url: String, id: Int?): MediaSource {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(id.toString())
            .build()
        return DashMediaSource.Factory(
            DefaultDataSourceFactory(
                requireContext(),
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
            )
        )
            .createMediaSource(item)
    }

    private fun playerAddVideoData(video: Video) {
        exoPlayer?.clearMediaItems()
        video.videoUrl?.let { url ->
            when {
                "^content://.+".toRegex().matches(url) -> {
                    exoPlayer?.setMediaSource(createFileSource(Uri.parse(url)))
                }
                url.contains(".m3u8") -> {
                    exoPlayer?.setMediaSource(createSource(url, video.id, video.title))
                }
                url.contains(".webm") -> {
                    exoPlayer?.setMediaSource(buildMediaSource(url, video.id))
                }
                else -> {
                    exoPlayer?.setMediaItem(MediaItem.fromUri(url))
                }
            }
        } ?: kotlin.run {
            toastError(DataThrowable(R.string.video_link_not_found))
        }
    }

    private fun playerAddSerialdata(movie: Movie?) {
        exoPlayer?.clearMediaItems()
        movie?.serialData?.seasons?.let { seasons ->
            seasons.asSequence()
                .forEachIndexed { indexSeason, serialSeason ->
                    serialSeason.episodes
                        ?.asSequence()
                        ?.forEachIndexed { indexEpisode, serialEpisode ->
                            getUrl(serialEpisode, movie.selectedQuality)?.let { url ->
                                when {
                                    "^content://.+".toRegex().matches(url) -> {
                                        exoPlayer?.addMediaSource(createFileSource(Uri.parse(url)))
                                    }
                                    url.contains(".m3u8") -> {
                                        exoPlayer?.addMediaSource(
                                            createSource(
                                                url,
                                                serialEpisode.id,
                                                serialEpisode.title,
                                                hashMapOf(indexSeason to indexEpisode)
                                            )
                                        )
                                    }
                                    url.contains(".webm") -> {
                                        exoPlayer?.addMediaSource(
                                            buildMediaSource(
                                                url,
                                                serialEpisode.id
                                            )
                                        )
                                    }
                                    else -> {
                                        exoPlayer?.addMediaItem(MediaItem.fromUri(url))
                                    }
                                }
                            }
                        }
                }
        }
        binding.plVideoPLayer.setShowPreviousButton(true)
        binding.plVideoPLayer.setShowNextButton(true)
    }

    private fun getUrl(episode: SerialEpisode, key: String? = null): String? {
        val keys = episode.hlsList?.keys
        val minQuality = getMinQuality(keys)
        val qualityKey = when {
            !key.isNullOrBlank() -> key
            !minQuality.isNullOrBlank() -> minQuality
            else -> keys?.first()
        }
        return episode.hlsList?.get(qualityKey)
    }

    private fun getMinQuality(keys: MutableSet<String>?) =
        keys?.map { it.toIntOrNull() ?: 0 }
            ?.minByOrNull { it }?.toString()

    private fun createSource(
        url: String?,
        id: Int?,
        title: String?,
        serialPosition: Map<Int, Int>? = null
    ): HlsMediaSource {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setTag(serialPosition)
            .setMediaId(id.toString())
            .setMediaMetadata(metadata)
            .build()
        return HlsMediaSource.Factory(DefaultHttpDataSourceFactory())
            .createMediaSource(item)
    }

    private fun releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.stop()
            cache()
            currentVideo?.currentPosition = exoPlayer?.contentPosition ?: 0
            currentVideo?.playWhenReady = exoPlayer?.playWhenReady ?: false
            binding.plVideoPLayer.player = null
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    private fun cache() {
        currentVideo?.currentPosition = exoPlayer?.contentPosition ?: 0
        currentVideo?.playWhenReady = exoPlayer?.playWhenReady ?: false
        vm.cacheMovie(
            currentMovie?.copy(
                currentEpisodePosition = currentEpisodePosition,
                currentSeasonPosition = currentSeasonPosition,
                video = currentVideo
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(KEY_VIDEO, currentVideo)
        outState.putParcelable(KEY_MOVIE, currentMovie)
        outState.putInt(KEY_SEASON, currentSeasonPosition)
        outState.putInt(KEY_EPISODE, currentEpisodePosition)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        currentMovie = savedInstanceState?.getParcelable(KEY_MOVIE)
        currentVideo = savedInstanceState?.getParcelable(KEY_VIDEO)
        currentSeasonPosition = savedInstanceState?.getInt(KEY_SEASON) ?: 0
        currentEpisodePosition = savedInstanceState?.getInt(KEY_EPISODE) ?: 0
    }

    override fun onStart() {
        super.onStart()
        videoStartRestore = true
        initPlayer()
    }

    override fun onResume() {
        super.onResume()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            appCompatActivity?.supportActionBar?.hide()
            setFullScreen(appCompatActivity, true)
        }
        restorePlayerState()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            val window = appCompatActivity?.window
            setFullScreen(appCompatActivity, false)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setFullScreen(appCompatActivity: AppCompatActivity?, setFullScreen: Boolean) {
        if (setFullScreen) {
            appCompatActivity?.hideSystemBar()
            appCompatActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } else {
            appCompatActivity?.showSystemBar()
            appCompatActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
        if (context is DrawerLocker) {
            drawerLocker = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.details_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_action_clear_cache -> {
                alertDialog(
                    requireContext(),
                    "Удалить?",
                    "Хотите очистить кеш?",
                    "OK",
                    "Отмена",
                    onConfirm = {
                        vm.clearCache(currentMovie)
                    }
                )
                true
            }
            else -> false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.f_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = args.movie.title
    }
}
