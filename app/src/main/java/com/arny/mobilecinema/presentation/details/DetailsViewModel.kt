package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieDownloadedData
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.BufferedSharedFlow
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class DetailsViewModel @AssistedInject constructor(
    @Assisted("id") private val id: Long,
    private val interactor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
    private val playerSource: PlayerSource
) : ViewModel() {
    private var seasonPosition = 0
    private var episodePosition = 0
    private var currentAlert: Alert? = null
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _downloadInit = MutableStateFlow(false)
    val downloadInit = _downloadInit.asStateFlow()
    private val _downloadAll = MutableStateFlow(false)
    val downloadAll = _downloadAll.asStateFlow()
    private val _hasSavedData = MutableStateFlow(false)
    val hasSavedData = _hasSavedData.asStateFlow()
    private val _currentMovie = MutableStateFlow<Movie?>(null)
    val currentMovie = _currentMovie.asSharedFlow()
    private val _serialTitle = MutableStateFlow<IWrappedString?>(null)
    val serialTitle = _serialTitle.asSharedFlow()
    private val _downloadedData = MutableStateFlow<MovieDownloadedData?>(null)
    val downloadedData = _downloadedData.asStateFlow()
    private val _saveData = MutableSharedFlow<SaveData>()
    val saveData = _saveData.asSharedFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()
    private val _addToHistory = MutableSharedFlow<Boolean>()
    val addToHistory = _addToHistory.asSharedFlow()
    private val _alert = BufferedSharedFlow<Alert>()
    val alert = _alert.asSharedFlow()

    init {
        loadVideo()
    }

    private fun loadVideo() {
        viewModelScope.launch {
            interactor.getMovie(id)
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { _error.emit(ThrowableString(it)) }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(result.throwable))
                        }

                        is DataResult.Success -> {
                            _currentMovie.value = result.result
                        }
                    }
                }
        }
    }

    fun loadSaveData(movieDbId: Long) {
        viewModelScope.launch {
            historyInteractor.getSaveData(movieDbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }

                        is DataResult.Success -> {
                            val data = it.result
                            _saveData.emit(data)
                            seasonPosition = data.seasonPosition
                            if (data.movieDbId != null) {
                                _hasSavedData.value = true
                            }
                        }
                    }
                }
        }
    }

    fun clearViewHistory(url: String, seasonPosition: Int, episodePosition: Int, total: Boolean) {
        viewModelScope.launch {
            val mMovie = _currentMovie.value
            historyInteractor.clearViewHistory(
                movieDbId = mMovie?.dbId, type = mMovie?.type, total = total
            )
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success -> {
                            val removed = it.result
                            if (removed) {
                                playerSource.clearDownloaded(url)
                                when (mMovie?.type) {
                                    MovieType.CINEMA -> {
                                        _toast.emit(ResourceString(R.string.movie_cache_cleared))
                                    }
                                    MovieType.SERIAL -> {
                                        if (total) {
                                            _toast.emit(ResourceString(R.string.serial_history_cleared))
                                        } else {
                                            _toast.emit(ResourceString(R.string.serial_episode_cache_cleared))
                                            onClearCacheClick(seasonPosition, episodePosition, true)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
        }
    }

    fun addToHistory() {
        viewModelScope.launch {
            val mMovie = _currentMovie.value
            val movieDbId = mMovie?.dbId
            if (movieDbId != null) {
                historyInteractor.addToHistory(movieDbId)
                    .catch { _error.emit(ThrowableString(it)) }
                    .collectLatest {
                        when (it) {
                            is DataResult.Error -> {
                                _error.emit(ThrowableString(it.throwable))
                            }

                            is DataResult.Success -> {
                                _addToHistory.emit(it.result)
                            }
                        }
                    }
            }
        }
    }

    fun initCinemaDownloadedData() {
        viewModelScope.launch {
            getCinemaDownloadedData()
        }
    }

    private suspend fun getCinemaDownloadedData() {
        val currentMovie = _currentMovie.value
        val url = currentMovie?.getCinemaUrl().orEmpty()
        val currentDownloadData = playerSource.getCurrentDownloadData(url)
        val data = MovieDownloadedData(
            currentDownloadData.downloadPercent,
            currentDownloadData.downloadBytes
        )
        checkDownloadSize(data)
        val movieTitle = currentDownloadData.movieTitle
        val titleEquals = movieTitle == currentMovie?.title
        val initValid = currentDownloadData.isInitValid
        if (initValid) {
            checkAllDownload(data)
            when {
                _downloadAll.value -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cinema_cache_complete),
                        btnOk = ResourceString(android.R.string.ok),
                        type = AlertType.Download(complete = true, link = url)
                    )
                }

                currentDownloadData.downloadsEmpty -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cinema_cache_attention),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(empty = true, link = url)
                    )
                }
                // Продолжить загрузку текущего
                currentDownloadData.isEqualsLinks -> {
                    currentAlert = Alert(
                        title = ResourceString(
                            R.string.cinema_cache_attention_links_title,
                            movieTitle
                        ),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(
                            equalsLinks = true,
                            equalsTitle = true,
                            empty = false,
                            link = url
                        )
                    )
                }
                // Текущий фильм,но ссылки разные(возможно сериал,но нужно будет привязаться к эпизодам)
                titleEquals -> {
                    currentAlert = Alert(
                        title = ResourceString(
                            R.string.cache_attention_links_not_equals,
                            movieTitle
                        ),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(
                            equalsLinks = false,
                            equalsTitle = true,
                            empty = false,
                            link = url
                        )
                    )
                }
                // Новая загрузка
                else -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cinema_cache_attention_new, movieTitle),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(
                            equalsLinks = false,
                            equalsTitle = false,
                            empty = false,
                            link = url
                        )
                    )
                }
            }
        }
        _downloadInit.value = initValid
    }

    fun initSerialDownloadedData() {
        viewModelScope.launch {
            getSerialDownloadedData(seasonPosition, episodePosition)
        }
    }

    private suspend fun getSerialDownloadedData(
        seasonPosition: Int,
        episodePosition: Int
    ) {
        val currentMovie = _currentMovie.value
        if (currentMovie != null && currentMovie.type == MovieType.SERIAL) {
            val episode =
                currentMovie.seasons.getOrNull(seasonPosition)?.episodes?.getOrNull(
                    episodePosition
                )
            val url = episode?.dash?.ifBlank { episode.hls }
            if (!url.isNullOrBlank()) {
                val currentDownloadData = playerSource.getCurrentDownloadData(url)
                val data = MovieDownloadedData(
                    currentDownloadData.downloadPercent,
                    currentDownloadData.downloadBytes
                )
                checkDownloadSize(data)
                val movieTitle = currentDownloadData.movieTitle
                val initValid = currentDownloadData.isInitValid
                if (initValid) {
                    checkAllDownload(data)
                    when {
                        _downloadAll.value -> {
                            currentAlert = Alert(
                                title = ResourceString(R.string.serial_episode_cache_complete),
                                btnOk = ResourceString(android.R.string.ok),
                                type = AlertType.Download(complete = true)
                            )
                        }

                        currentDownloadData.downloadsEmpty -> {
                            currentAlert = Alert(
                                title = ResourceString(
                                    R.string.serial_episode_cache_attention,
                                    currentMovie.title,
                                    seasonPosition + 1,
                                    episodePosition + 1
                                ),
                                content = ResourceString(R.string.cache_description),
                                btnOk = ResourceString(android.R.string.ok),
                                btnCancel = ResourceString(android.R.string.cancel),
                                type = AlertType.Download(empty = true, link = url)
                            )
                        }
                        // Продолжить загрузку текущего
                        currentDownloadData.isEqualsLinks -> {
                            currentAlert = Alert(
                                title = ResourceString(
                                    R.string.serial_episode_cache_attention_links_title,
                                    movieTitle,
                                    seasonPosition + 1,
                                    episodePosition + 1
                                ),
                                content = ResourceString(R.string.cache_description),
                                btnOk = ResourceString(android.R.string.ok),
                                btnCancel = ResourceString(android.R.string.cancel),
                                type = AlertType.Download(
                                    equalsLinks = true,
                                    equalsTitle = true,
                                    empty = false,
                                    link = url
                                )
                            )
                        }
                        // Новая загрузка
                        else -> {
                            currentAlert = Alert(
                                title = ResourceString(
                                    R.string.serial_episode_cache_attention,
                                    currentMovie.title,
                                    seasonPosition + 1,
                                    episodePosition + 1
                                ),
                                content = ResourceString(R.string.cache_description),
                                btnOk = ResourceString(android.R.string.ok),
                                btnCancel = ResourceString(android.R.string.cancel),
                                type = AlertType.Download(empty = true, link = url)
                            )
                        }
                    }
                }
                _downloadInit.value = initValid
            }
        }
    }

    private fun checkAllDownload(data: MovieDownloadedData) {
        val allDownload = data.downloadedPercent >= 100
        _downloadAll.value = allDownload
        _hasSavedData.value = data.downloadedPercent > 0
    }

    private fun checkDownloadSize(data: MovieDownloadedData) {
        if (data.downloadedPercent > 10.0f && data.downloadedSize > 0L) {
            _downloadedData.value = data
        } else {
            _downloadedData.value = null
        }
    }

    fun updateDownloadedData(
        pageUrl: String,
        percent: Float?,
        bytes: Long?,
        episode: Int,
        season: Int,
    ) {
        viewModelScope.launch {
            val movie = _currentMovie.value
            if (movie != null && movie.pageUrl == pageUrl && percent != null && percent > 0.0f && bytes != null && bytes > 0L) {
                if (movie.type == MovieType.CINEMA || isCurrentSerialPositionEqualsDownload(
                        movie = movie,
                        seasonPosition = season - 1,
                        currentSeasonPosition = seasonPosition,
                        episodePosition = episode - 1,
                        currentEpisodePosition = episodePosition
                    )
                ) {
                    _downloadedData.value = MovieDownloadedData(percent, bytes)
                }
            }
        }
    }

    private fun isCurrentSerialPositionEqualsDownload(
        movie: Movie,
        seasonPosition: Int,
        currentSeasonPosition: Int,
        episodePosition: Int,
        currentEpisodePosition: Int
    ) =
        movie.type == MovieType.SERIAL && seasonPosition == currentSeasonPosition && episodePosition == currentEpisodePosition

    fun showCacheDialog() {
        viewModelScope.launch {
            currentAlert?.let { _alert.emit(it) }
        }
    }

    fun onClearCacheClick(
        currentSeasonPosition: Int,
        currentEpisodePosition: Int,
        clearSerialViewHistory: Boolean = false
    ) {
        val movie = _currentMovie.value
        var total = false
        val content = when {
            movie?.type == MovieType.SERIAL && clearSerialViewHistory -> {
                total = true
                ResourceString(
                    R.string.question_remove_serial_from_history,
                    movie.title,
                )
            }

            movie?.type == MovieType.SERIAL -> {
                ResourceString(
                    R.string.question_remove_episodes_from_cache,
                    movie.title,
                    currentSeasonPosition + 1,
                    currentEpisodePosition + 1
                )
            }

            else -> {
                ResourceString(R.string.question_remove_from_history_title, movie?.title)
            }
        }
        val url = if (movie?.type == MovieType.SERIAL) {
            val episode = movie.seasons.getOrNull(currentSeasonPosition)?.episodes?.getOrNull(
                currentEpisodePosition
            )
            episode?.dash?.ifBlank { episode.hls }.orEmpty()
        } else {
            total = true
            movie?.getCinemaUrl().orEmpty()
        }
        currentAlert = Alert(
            title = ResourceString(R.string.question_remove),
            content = content,
            btnOk = ResourceString(android.R.string.ok),
            btnCancel = ResourceString(android.R.string.cancel),
            type = AlertType.ClearCache(url, currentSeasonPosition, currentEpisodePosition, total)
        )
        showCacheDialog()
    }

    fun onSerialPositionChanged(currentSeasonPosition: Int, currentEpisodePosition: Int) {
        seasonPosition = currentSeasonPosition
        episodePosition = currentEpisodePosition
        _downloadInit.value = false
        invalidateCache()
        updateSerialTitle()
    }

    fun invalidateCache() {
        _downloadedData.value = MovieDownloadedData(loading = true)
    }

    private fun updateSerialTitle() {
        val movie = _currentMovie.value
        if (movie?.type == MovieType.SERIAL) {
            _serialTitle.value = ResourceString(
                R.string.serial_title,
                movie.title,
                "${seasonPosition + 1}",
                "${episodePosition + 1}"
            )
        }
    }
}
