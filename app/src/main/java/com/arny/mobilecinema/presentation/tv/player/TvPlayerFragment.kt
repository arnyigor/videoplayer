package com.arny.mobilecinema.presentation.tv.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FTvPlayerBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.core.component.KoinComponent

/**
 * TV-экран плеера для воспроизведения фильмов и сериалов.
 *
 * Особенности TV-версии:
 * - Поддержка управления с пульта (D-pad)
 * - Увеличенные кнопки перемотки (10 сек)
 * - Отображение кнопок next/previous для эпизодов
 * - Автоматическое скрытие UI после 3 секунд бездействия
 *
 * Переиспользует [PlayerViewModel] от Phone-версии для:
 * - Логики сохранения позиции
 * - Определения источника видео
 * - Обработки серий/сезонов
 *
 * @property viewModel ViewModel с логикой плеера
 */
class TvPlayerFragment : Fragment(), KoinComponent {

    private lateinit var viewModel: PlayerViewModel

    /** Аргументы навигации */
    private val args: TvPlayerFragmentArgs by navArgs()

    private var _binding: FTvPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    /** Обработчик для скрытия UI */
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    /** Список всех эпизодов для навигации */
    private var allEpisodes: List<SerialEpisode> = emptyList()
    private var currentEpisodeIndex = 0

    /** Слушатель событий плеера */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> showLoading(true)
                Player.STATE_READY -> {
                    showLoading(false)
                    scheduleHideControls()
                }
                Player.STATE_ENDED -> onPlaybackEnded()
                Player.STATE_IDLE -> showLoading(false)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            showError(error.localizedMessage ?: getString(R.string.error_loading_data))
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
        _binding = FTvPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация ViewModel через Koin
        viewModel = getKoin().get<PlayerViewModel>()

        // Проверка что это TV - если телефон, показываем сообщение
        if (!DeviceUtils.isTV(requireContext())) {
            toast(getString(R.string.internet_connection_error))
            findNavController().navigateUp()
            return
        }

        initPlayer()
        initControls()
        observeState()
    }

    /**
     * Инициализирует ExoPlayer с настройками для TV.
     */
    private fun initPlayer() {
        player = ExoPlayer.Builder(requireContext())
            .build()
            .apply {
                playWhenReady = true
                addListener(playerListener)
            }

        binding.playerView.apply {
            player = this@TvPlayerFragment.player
            useController = true
            setShowNextButton(true)
            setShowPreviousButton(true)

            // Настройки для TV пульта
            setControllerOnFullScreenModeChangedListener { isFullScreen ->
                // Обработка полноэкранного режима
            }
        }

        // Загружаем данные
        viewModel.setPlayData(
            path = args.sharedUrl.takeIf { it.isNotBlank() },
            movie = null,
            seasonIndex = 0,
            episodeIndex = 0
        )
    }

    /**
     * Инициализирует элементы управления.
     */
    private fun initControls() {
        binding.btnBack.setOnClickListener {
            savePositionAndExit()
        }

        binding.btnRewind.setOnClickListener {
            player?.seekTo(maxOf(0, (player?.currentPosition ?: 0) - 10000))
        }

        binding.btnForward.setOnClickListener {
            player?.seekTo(minOf(player?.duration ?: 0, (player?.currentPosition ?: 0) + 10000))
        }

        binding.playerView.setOnClickListener {
            toggleControls()
        }
    }

    /**
     * Наблюдает за состоянием ViewModel.
     */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.path != null || state.movie != null) {
                            playMedia(state.path, state.movie, state.season ?: 0, state.episode ?: 0)
                        }
                    }
                }

                launch {
                    viewModel.back.collectLatest {
                        findNavController().navigateUp()
                    }
                }

                launch {
                    viewModel.toast.collectLatest { message ->
                        message?.let { toast(it.toString(requireContext())) }
                    }
                }
            }
        }
    }

    /**
     * Воспроизводит медиа-контент.
     */
    private fun playMedia(path: String?, movie: Movie?, seasonIndex: Int, episodeIndex: Int) {
        when {
            !path.isNullOrBlank() -> playUrl(path)
            movie != null && movie.type == MovieType.CINEMA -> playCinema(movie)
            movie != null && movie.type == MovieType.SERIAL -> playSerial(movie, seasonIndex, episodeIndex)
            else -> {
                toast(getString(R.string.error_loading_data))
                findNavController().navigateUp()
            }
        }
    }

    private fun playUrl(url: String) {
        val mediaSource = createMediaSource(url, "Video")
        player?.apply {
            setMediaSource(mediaSource)
            prepare()
        }
    }

    private fun playCinema(movie: Movie) {
        val url = movie.getFirstPlayableUrl() ?: return
        playUrl(url)
    }

    private fun playSerial(movie: Movie, seasonIndex: Int, episodeIndex: Int) {
        val seasons = movie.seasons.sortedBy { it.id }
        val season = seasons.getOrNull(seasonIndex) ?: return
        val episodes = season.episodes.sortedBy { it.episode }
        val episode = episodes.getOrNull(episodeIndex) ?: return

        allEpisodes = seasons.flatMap { it.episodes.sortedBy { e -> e.episode } }
        currentEpisodeIndex = allEpisodes.indexOf(episode)

        val url = episode.hls ?: episode.dash ?: return
        playUrl(url)
        showEpisodeInfo(season.id ?: 0, 0, episode.title)
    }

    private fun createMediaSource(url: String, title: String): MediaSource {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(requireContext())
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
    }

    private fun showEpisodeInfo(seasonNum: Int, episodeNum: Int, title: String) {
        binding.tvEpisodeInfo.apply {
            text = "Сезон $seasonNum Серия $episodeNum\n$title"
            visibility = View.VISIBLE
        }
    }

    private fun toggleControls() {
        val isVisible = binding.controlsGroup.visibility == View.VISIBLE
        if (isVisible) hideControls() else {
            showControls()
            scheduleHideControls()
        }
    }

    private fun showControls() { binding.controlsGroup.visibility = View.VISIBLE }
    private fun hideControls() {
        binding.controlsGroup.visibility = View.GONE
        binding.tvEpisodeInfo.visibility = View.GONE
    }

    private fun scheduleHideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun onPlaybackEnded() {
        if (currentEpisodeIndex < allEpisodes.size - 1) {
            currentEpisodeIndex++
            val nextEpisode = allEpisodes[currentEpisodeIndex]
            val url = nextEpisode.hls ?: nextEpisode.dash
            url?.let { playUrl(it) }
        } else {
            toast(getString(R.string.update_finished_success))
        }
    }

    private fun savePositionAndExit() {
        player?.let { exoPlayer ->
            val position = exoPlayer.currentPosition
            val movie = viewModel.uiState.value.movie
            val dbId = movie?.dbId
            if (dbId != null && position > 0) {
                val state = viewModel.uiState.value
                when (movie.type) {
                    MovieType.CINEMA -> viewModel.saveMoviePosition(dbId, position, 0, 0)
                    MovieType.SERIAL -> viewModel.saveMoviePosition(dbId, position, state.season ?: 0, state.episode ?: 0)
                    else -> {}
                }
            }
        }
        findNavController().navigateUp()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        savePosition()
    }

    private fun savePosition() {
        player?.let { exo ->
            val pos = exo.currentPosition
            val state = viewModel.uiState.value
            if (pos > 0) {
                viewModel.updatePlaybackPosition(pos, state.season ?: 0, state.episode ?: 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideHandler.removeCallbacks(hideRunnable)
        player?.removeListener(playerListener)
        player?.release()
        player = null
        _binding = null
    }

        companion object {
        private fun Movie.getFirstPlayableUrl(): String? {
            return cinemaUrlData?.cinemaUrl?.url
                ?: cinemaUrlData?.hdUrl?.url
        }
    }
}