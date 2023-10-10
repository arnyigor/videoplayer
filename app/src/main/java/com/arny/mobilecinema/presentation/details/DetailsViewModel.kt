package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.MoviesInteractor
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailsViewModel @Inject constructor(
    private val interactor: MoviesInteractor,
    private val playerSource: PlayerSource
) : ViewModel() {
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

    fun loadVideo(id: Long) {
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

    fun loadSaveData(dbId: Long) {
        viewModelScope.launch {
            interactor.getSaveData(dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success ->
                            _saveData.emit(it.result)
                    }
                }
        }
    }

    fun clearViewHistory() {
        viewModelScope.launch {
            val mMovie = _currentMovie.value
            interactor.clearViewHistory(mMovie?.dbId)
                .catch { _error.emit(ThrowableString(it)) }
                .collectLatest {
                    when (it) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(it.throwable))
                        }
                        is DataResult.Success -> {
                            val removed = it.result
                            if (removed) {
                                when (mMovie?.type) {
                                    MovieType.CINEMA -> {
                                        playerSource.clearDownloaded(mMovie.getCinemaUrl())
                                        _toast.emit(ResourceString(R.string.movie_cache_cleared))
                                    }
                                    MovieType.SERIAL -> {
                                        _toast.emit(ResourceString(R.string.serial_cache_cleared))
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
            interactor.addToHistory(mMovie?.dbId)
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

    private suspend fun getDownloadedData() {
        val currentMovie = _currentMovie.value
        val cinemaUrl = currentMovie?.getCinemaUrl().orEmpty()
        val currentDownloadData = playerSource.getCurrentDownloadData(cinemaUrl)
        val data = MovieDownloadedData(
            currentDownloadData.downloadPercent,
            currentDownloadData.downloadBytes
        )
        if (data.downloadedPercent > 0.0f && data.downloadedSize > 0L) {
            _downloadedData.value = data
        } else {
            _downloadedData.value = null
        }
        val movieTitle = currentDownloadData.movieTitle
        val titleEquals = movieTitle == currentMovie?.title
        val initValid = currentDownloadData.isInitValid
        if (initValid) {
            val allDownload = data.downloadedPercent >= 100
            _downloadAll.value = allDownload
            _hasSavedData.value = data.downloadedPercent > 0
            when {
                allDownload -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cache_complete),
                        btnOk = ResourceString(android.R.string.ok),
                        type = AlertType.Download(complete = true)
                    )
                }

                currentDownloadData.downloadsEmpty -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cache_attention),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(empty = true)
                    )
                }
                // Продолжить загрузку текущего
                currentDownloadData.isEqualsLinks -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cache_attention_links_title, movieTitle),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(
                            equalsLinks = true,
                            equalsTitle = true,
                            empty = false
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
                            empty = false
                        )
                    )
                }
                // Новая загрузка
                else -> {
                    currentAlert = Alert(
                        title = ResourceString(R.string.cache_attention_new, movieTitle),
                        content = ResourceString(R.string.cache_description),
                        btnOk = ResourceString(android.R.string.ok),
                        btnCancel = ResourceString(android.R.string.cancel),
                        type = AlertType.Download(
                            equalsLinks = false,
                            equalsTitle = false,
                            empty = false
                        )
                    )
                }
            }
        }
        _downloadInit.value = initValid
    }

    fun updateDownloadedData() {
        viewModelScope.launch {
            getDownloadedData()
        }
    }

    fun updateDownloadedData(percent: Float?, bytes: Long?) {
        viewModelScope.launch {
            if (percent != null && percent > 0.0f && bytes != null && bytes > 0L) {
                _downloadedData.value = MovieDownloadedData(percent, bytes)
            }
        }
    }

    fun showCacheDialog() {
        viewModelScope.launch {
            val alert = currentAlert
            if (alert != null) {
                _alert.emit(alert)
            }
        }
    }
}
