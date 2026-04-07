package com.arny.mobilecinema.presentation.tv.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.databinding.FTvPlayerBinding
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.presentation.playerview.PlayerViewModel
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import androidx.core.view.isVisible
import com.arny.mobilecinema.presentation.player.PlayerSource
import kotlin.getValue

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

    /** Загружаем movie напрямую через интерактор, а не через activityViewModel */
    private val moviesInteractor: MoviesInteractor by inject()

    private val playerSource: PlayerSource by inject()

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
            val errorMsg = error.localizedMessage ?: getString(R.string.error_loading_data)
            showError("Ошибка плеера: $errorMsg")
        }
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
        val sharedUrl = args.sharedUrl.takeIf { it.isNotBlank() }
        val movieId = args.movieId.takeIf { it > 0 }

        when {
            sharedUrl != null -> {
                viewModel.setPlayData(
                    path = sharedUrl,
                    movie = null,
                    seasonIndex = 0,
                    episodeIndex = 0
                )
            }
            movieId != null -> {
                // НЕ загружаем фильм вручную.
                // Вместо этого мы создаем "пустой" фильм только с ID (или извлекаем из аргументов, если у вас есть SafeArgs).
                // PlayerViewModel сам всё загрузит, проверит ссылки и обновит uiState!

                // Если у вас в NavArgs передается только ID, сделайте так:
                viewLifecycleOwner.lifecycleScope.launch {
                    moviesInteractor.getMovie(movieId).collectLatest { result ->
                        if (result is DataResult.Success) {
                            viewModel.setPlayData(
                                path = null,
                                movie = result.result, // Передаем полный фильм во ViewModel
                                seasonIndex = args.seasonIndex,
                                episodeIndex = args.episodeIndex
                            )
                        }
                    }
                }
            }
            else -> {
                toast(getString(R.string.error_loading_data))
                findNavController().navigateUp()
            }
        }
    }

    /**
     * Загружает фильм из базы и передаёт в плеер.
     */
    private fun loadMovieAndPlay(movieId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            moviesInteractor.getMovie(movieId).collectLatest { result ->
                when (result) {
                    is DataResult.Success -> {
                        val movie = result.result
                        viewModel.setPlayData(
                            path = null,
                            movie = movie,
                            seasonIndex = args.seasonIndex,
                            episodeIndex = args.episodeIndex
                        )
                    }
                    is DataResult.Error -> {
                        toast(getString(R.string.error_loading_data))
                        findNavController().navigateUp()
                    }
                }
            }
        }
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
        viewLifecycleOwner.lifecycleScope.launch {
            when {
                !path.isNullOrBlank() -> playUrl(path, "Video")
                movie != null && movie.type == MovieType.CINEMA -> playCinema(movie)
                movie != null && movie.type == MovieType.SERIAL -> playSerial(movie, seasonIndex, episodeIndex)
                else -> {
                    toast(getString(R.string.error_loading_data))
                    findNavController().navigateUp()
                }
            }
        }
    }

    private suspend fun playUrl(url: String, videoTitle: String) {
        try {
            val mediaSource = playerSource.getSource(url, videoTitle)
            if (mediaSource != null) {
                player?.apply {
                    setMediaSource(mediaSource)
                    prepare()
                }
            } else {
                showError("Не удалось создать источник видео")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showError(e.message ?: "Ошибка загрузки видео")
        }
    }

    private fun playCinema(movie: Movie) {
        val url = movie.getFirstPlayableUrl()
        if (url.isNullOrBlank()) {
            showError("Прямая ссылка на видео еще не загружена или недоступна")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            playUrl(url, movie.title)
        }
    }

    private fun playSerial(movie: Movie, seasonIndex: Int, episodeIndex: Int) {
        val seasons = movie.seasons.sortedBy { it.id }
        val season = seasons.getOrNull(seasonIndex) ?: return
        val episodes = season.episodes.sortedBy { it.episode }
        val episode = episodes.getOrNull(episodeIndex) ?: return

        allEpisodes = seasons.flatMap { it.episodes.sortedBy { e -> e.episode } }
        currentEpisodeIndex = allEpisodes.indexOf(episode)

        val url = episode.hls ?: episode.dash
        if (url.isNullOrBlank()) {
            showError("Ссылка на эпизод недоступна")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playUrl(url, episode.title)
        }
        val episodeNum = episode.episode.toIntOrNull() ?: 0
        showEpisodeInfo(season.id ?: 0, episodeNum, episode.title)
    }

    private fun showEpisodeInfo(seasonNum: Int, episodeNum: Int, title: String) {
        binding.tvEpisodeInfo.apply {
            text = "Сезон $seasonNum Серия $episodeNum\n$title"
            visibility = View.VISIBLE
        }
    }

    private fun toggleControls() {
        if (binding.controlsGroup.isVisible) hideControls() else {
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
            url?.let { viewLifecycleOwner.lifecycleScope.launch { playUrl(it, nextEpisode.title ?: "Episode") } }
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