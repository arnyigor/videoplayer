package com.arny.mobilecinema.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.feedback.FeedbackInteractor
import com.arny.mobilecinema.domain.interactors.history.HistoryInteractor
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.CacheChangeType
import com.arny.mobilecinema.domain.models.DownloadManagerData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieDownloadedData
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.RequestDownloadFile
import com.arny.mobilecinema.domain.models.SaveData
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.getAllCinemaUrls
import com.arny.mobilecinema.presentation.player.getCinemaUrl
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============ UIState ============
data class DetailsUiState(
    val movie: Movie? = null,
    val isLoading: Boolean = false,
    val downloadedData: MovieDownloadedData? = null,
    val canDownload: Boolean = false,
    val hasSavedData: Boolean = false,
    val isInFavorite: Boolean = false,
    val isDownloadComplete: Boolean = false,
    val selectedCinemaUrl: String? = null,
    val currentSeasonPosition: Int = 0,
    val currentEpisodePosition: Int = 0,
    val movieTime: Long = 0L
)

// ============ Events (User Actions) ============
sealed interface DetailsEvent {
    object LoadMovie : DetailsEvent
    data class SerialPositionChanged(
        val seasonPosition: Int,
        val episodePosition: Int
    ) : DetailsEvent

    data class SelectedUrlChanged(val url: String?, val updateCache: Boolean) : DetailsEvent
    object ShowCacheDialog : DetailsEvent
    object ShowDownloadDialog : DetailsEvent
    object ShowUpdateDialog : DetailsEvent
    data class ClearCache(
        val seasonPosition: Int,
        val episodePosition: Int,
        val clearViewHistory: Boolean = false
    ) : DetailsEvent

    object AddToViewHistory : DetailsEvent
    object DownloadSelectedUrlToFile : DetailsEvent
    object UpdateMovieData : DetailsEvent
    data class SendFeedback(val text: String) : DetailsEvent
    data class UpdateDownloadProgress(
        val pageUrl: String,
        val percent: Float?,
        val bytes: Long?,
        val updateSeason: Int,
        val updateEpisode: Int
    ) : DetailsEvent

    data class ClearViewHistory(
        val url: String,
        val seasonPosition: Int,
        val episodePosition: Int,
        val total: Boolean
    ) : DetailsEvent

    object InvalidateCache : DetailsEvent
    object ToggleFavorite : DetailsEvent
}

// ============ Actions (One-time effects) ============
sealed interface DetailsAction {
    data class ShowToast(val message: IWrappedString) : DetailsAction
    data class ShowError(val message: IWrappedString) : DetailsAction
    data class ShowAlert(val alert: Alert) : DetailsAction
    data class NavigateToUpdate(val url: String) : DetailsAction
    data class RequestDownload(val file: RequestDownloadFile) : DetailsAction
    object HistoryAdded : DetailsAction
    object NavigateBack : DetailsAction
}

// ============ ViewModel ============
class DetailsViewModel @AssistedInject constructor(
    @Assisted("id") private val id: Long,
    private val interactor: MoviesInteractor,
    private val historyInteractor: HistoryInteractor,
    private val feedbackInteractor: FeedbackInteractor,
    private val playerSource: PlayerSource
) : ViewModel() {

    private companion object {
        const val MB = 1024 * 1024
    }

    // UIState - единственный источник истины
    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    // Actions - однократные эффекты (не воспроизводятся при пересоздании)
    private val _actions = Channel<DetailsAction>(Channel.BUFFERED)
    val actions: Flow<DetailsAction> = _actions.receiveAsFlow()

    // Внутреннее состояние для кеширования
    private var currentDownloadData: DownloadManagerData? = null
    private var currentCacheAlert: Alert? = null
    private var downloadAlert: Alert? = null
    private var currentRemoveAlert: Alert? = null

    init {
        observeCacheChanges()
    }

    // ============ Event Handler - единая точка входа ============
    fun handleEvent(event: DetailsEvent) {
        when (event) {
            is DetailsEvent.LoadMovie -> loadMovie()
            is DetailsEvent.SerialPositionChanged -> handleSerialPositionChanged(event)
            is DetailsEvent.SelectedUrlChanged -> handleSelectedUrlChanged(event)
            is DetailsEvent.ShowCacheDialog -> showCacheDialog()
            is DetailsEvent.ShowDownloadDialog -> showDownloadDialog()
            is DetailsEvent.ShowUpdateDialog -> showUpdateDialog()
            is DetailsEvent.ClearCache -> handleClearCache(event)
            is DetailsEvent.AddToViewHistory -> addToViewHistory()
            is DetailsEvent.DownloadSelectedUrlToFile -> downloadSelectedUrlToFile()
            is DetailsEvent.UpdateMovieData -> updateMovieData()
            is DetailsEvent.SendFeedback -> sendFeedback(event.text)
            is DetailsEvent.UpdateDownloadProgress -> handleDownloadProgress(event)
            is DetailsEvent.ClearViewHistory -> clearViewHistory(event)
            is DetailsEvent.InvalidateCache -> invalidateCache()
            is DetailsEvent.ToggleFavorite -> onToggleFavorite()
        }
    }

    private fun onToggleFavorite() {
        viewModelScope.launch {
            interactor.onFavoriteToggle(id)
                .catch { throwable ->
                    _actions.send(DetailsAction.ShowError(ThrowableString(throwable)))
                }
                .collect { res ->
                    when (res) {
                        is DataResult.Error -> {
                            _actions.send(DetailsAction.ShowError(ThrowableString(res.throwable)))
                        }

                        is DataResult.Success -> {
                            _uiState.update { it.copy(isInFavorite = res.result) }
                        }
                    }
                }
        }
    }

    // ============ Private Handlers ============

    private fun observeCacheChanges() {
        viewModelScope.launch {
            historyInteractor.cacheChange
                .collectLatest { cacheChange ->
                    when (cacheChange) {
                        CacheChangeType.CACHE, CacheChangeType.SERIAL_POSITION -> {
                            historyInteractor.setCacheChanged(false)
                            loadMovie()
                            onReloadCache()
                        }

                        else -> {}
                    }
                }
        }
    }

    fun loadMovie() {
        viewModelScope.launch {
            val combinedFlow = interactor.getMovie(id)
                .combine(historyInteractor.getSaveData(id)) { movie, save -> Pair(movie, save) }
                .combine(interactor.isFavorite(id)) { pair, isFavorite ->
                    Triple(pair.first, pair.second, isFavorite)
                }

            combinedFlow
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .onCompletion { _uiState.update { it.copy(isLoading = false) } }
                .catch { t -> _actions.send(DetailsAction.ShowError(ThrowableString(t))) }
                .collect { (movie, save, isFavorite) ->
                    handleMovieResult(movie)
                    handleSaveDataResult(save)
                    handleFavorite(isFavorite)
                }
        }
    }

    fun handleFavorite(isFavorite: DataResult<Boolean>) {
        viewModelScope.launch {
            when (isFavorite) {
                is DataResult.Error -> {
                    _actions.send(DetailsAction.ShowError(ThrowableString(isFavorite.throwable)))
                }

                is DataResult.Success -> {
                    _uiState.update {
                        it.copy(isInFavorite = isFavorite.result)
                    }
                }
            }
        }
    }

    private suspend fun handleSaveDataResult(saveResult: DataResult<SaveData>) {
        when (saveResult) {
            is DataResult.Error -> {
                _actions.send(DetailsAction.ShowError(ThrowableString(saveResult.throwable)))
            }

            is DataResult.Success -> {
                val data = saveResult.result
                _uiState.update { state ->
                    state.copy(
                        currentSeasonPosition = data.seasonPosition,
                        currentEpisodePosition = data.episodePosition,
                        movieTime = data.time,
                        hasSavedData = data.movieDbId != null
                    )
                }
                historyInteractor.setSerialPositionChange(false)
            }
        }
    }

    private suspend fun handleMovieResult(movieResult: DataResult<Movie>) {
        when (movieResult) {
            is DataResult.Error -> {
                _actions.send(DetailsAction.ShowError(ThrowableString(movieResult.throwable)))
            }

            is DataResult.Success -> {
                val movie = movieResult.result
                val state = _uiState.value
                _uiState.update {
                    it.copy(
                        movie = movie.copy(
                            seasonPosition = state.currentSeasonPosition,
                            episodePosition = state.currentEpisodePosition,
                            time = state.movieTime
                        )
                    )
                }
                initDownloadData(movie)
            }
        }
    }

    private fun initDownloadData(movie: Movie) {
        when (movie.type) {
            MovieType.CINEMA -> updateCinemaDownloadedData()
            MovieType.SERIAL -> updateSerialDownloadedData()
            else -> {}
        }
    }

    private fun onReloadCache() {
        val movie = _uiState.value.movie
        when {
            movie != null && movie.type == MovieType.CINEMA -> updateCinemaDownloadedData()
            movie != null && movie.type == MovieType.SERIAL -> updateSerialDownloadedData()
        }
    }

    private fun handleSerialPositionChanged(event: DetailsEvent.SerialPositionChanged) {
        _uiState.update {
            it.copy(
                currentSeasonPosition = event.seasonPosition,
                currentEpisodePosition = event.episodePosition,
                canDownload = false
            )
        }
        invalidateCache()
        updateSerialDownloadedData()
    }

    private fun handleSelectedUrlChanged(event: DetailsEvent.SelectedUrlChanged) {
        _uiState.update { it.copy(selectedCinemaUrl = event.url) }
        if (event.updateCache) {
            updateCinemaDownloadedData()
        }
    }

    private fun invalidateCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadedData = MovieDownloadedData(loading = true)) }
            kotlinx.coroutines.delay(1000)
            historyInteractor.setCacheChanged(false)
        }
    }

    // ============ Cache & Download Logic ============

    private fun updateCinemaDownloadedData() {
        viewModelScope.launch {
            val state = _uiState.value
            val movie = state.movie ?: return@launch
            val url = state.selectedCinemaUrl ?: movie.getCinemaUrl()

            val downloadData = try {
                playerSource.getCurrentDownloadData(url)
            } catch (e: Exception) {
                e.printStackTrace()
                DownloadManagerData()
            }

            val data = MovieDownloadedData(
                downloadData.downloadPercent,
                downloadData.downloadBytes,
                false,
                downloadData.totalBytes
            )

            checkDownloadSize(data)
            val movieTitle = downloadData.movieTitle
            val titleEquals = movieTitle == movie.title
            val initValid = downloadData.isInitValid

            if (initValid) {
                checkAllDownload(data)
                prepareCinemaAlerts(downloadData, url, titleEquals, movieTitle)
                _uiState.update { it.copy(canDownload = true) }
            }
        }
    }

    private fun prepareCinemaAlerts(
        downloadData: DownloadManagerData,
        url: String,
        titleEquals: Boolean,
        movieTitle: String
    ) {
        val isDownloadComplete = _uiState.value.isDownloadComplete

        currentCacheAlert = when {
            isDownloadComplete -> Alert(
                title = ResourceString(R.string.cinema_cache_complete),
                btnOk = ResourceString(android.R.string.ok),
                type = AlertType.Download(complete = true, link = url)
            )

            downloadData.downloadsEmpty -> Alert(
                title = ResourceString(R.string.cinema_cache_attention),
                content = ResourceString(R.string.cache_description),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.Download(empty = true, link = url)
            )

            downloadData.isEqualsLinks -> Alert(
                title = ResourceString(R.string.cinema_cache_attention_links_title, movieTitle),
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

            titleEquals -> Alert(
                title = ResourceString(R.string.cache_attention_links_not_equals, movieTitle),
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

            else -> Alert(
                title = ResourceString(R.string.cinema_cache_attention),
                content = ResourceString(R.string.cache_description),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.Download(empty = true, link = url)
            )
        }
    }

    private fun updateSerialDownloadedData() {
        viewModelScope.launch {
            val state = _uiState.value
            val movie = state.movie ?: return@launch

            if (movie.type != MovieType.SERIAL) return@launch

            val episode = movie.seasons
                .getOrNull(state.currentSeasonPosition)
                ?.episodes
                ?.getOrNull(state.currentEpisodePosition)

            val url = episode?.dash?.ifBlank { episode.hls }
            if (url.isNullOrBlank()) return@launch

            val downloadData = playerSource.getCurrentDownloadData(url)
            currentDownloadData = downloadData

            val data = MovieDownloadedData(
                downloadData.downloadPercent,
                downloadData.downloadBytes
            )

            checkDownloadSize(data)
            val initValid = downloadData.isInitValid

            if (initValid) {
                checkAllDownload(data)
                prepareSerialAlerts(downloadData, url, movie, state)
                _uiState.update { it.copy(canDownload = true) }
            }
        }
    }

    private fun prepareSerialAlerts(
        downloadData: DownloadManagerData,
        url: String,
        movie: Movie,
        state: DetailsUiState
    ) {
        val isDownloadComplete = state.isDownloadComplete
        val seasonPos = state.currentSeasonPosition
        val episodePos = state.currentEpisodePosition

        currentCacheAlert = when {
            isDownloadComplete -> Alert(
                title = ResourceString(R.string.serial_episode_cache_complete),
                btnOk = ResourceString(android.R.string.ok),
                type = AlertType.Download(complete = true)
            )

            downloadData.downloadsEmpty -> Alert(
                title = ResourceString(
                    R.string.serial_episode_cache_attention,
                    movie.title,
                    seasonPos + 1,
                    episodePos + 1
                ),
                content = ResourceString(R.string.cache_description),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.Download(empty = true, link = url)
            )

            downloadData.isEqualsLinks -> Alert(
                title = ResourceString(
                    R.string.serial_episode_cache_attention_links_title,
                    downloadData.movieTitle,
                    seasonPos + 1,
                    episodePos + 1
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

            else -> Alert(
                title = ResourceString(
                    R.string.serial_episode_cache_attention,
                    movie.title,
                    seasonPos + 1,
                    episodePos + 1
                ),
                content = ResourceString(R.string.cache_description),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.Download(empty = true, link = url)
            )
        }
    }

    private fun checkAllDownload(data: MovieDownloadedData) {
        val allDownload = data.downloadedPercent >= 100
        _uiState.update {
            it.copy(
                isDownloadComplete = allDownload,
                hasSavedData = if (!it.hasSavedData) data.downloadedPercent > 0 else true
            )
        }
    }

    private fun checkDownloadSize(data: MovieDownloadedData) {
        _uiState.update {
            it.copy(downloadedData = if (data.downloadedSize > MB) data else null)
        }
    }

    private fun handleDownloadProgress(event: DetailsEvent.UpdateDownloadProgress) {
        viewModelScope.launch {
            val state = _uiState.value
            val movie = state.movie ?: return@launch

            if (movie.pageUrl.isBlank() || event.pageUrl.isBlank() || movie.pageUrl != event.pageUrl) {
                return@launch
            }

            val percent = event.percent ?: return@launch
            val bytes = event.bytes ?: return@launch

            if (percent <= 0.0f || bytes <= 0L) return@launch

            val total = ((100 * bytes) / percent).toLong()

            when {
                movie.type == MovieType.CINEMA -> {
                    _uiState.update {
                        it.copy(downloadedData = MovieDownloadedData(percent, bytes, false, total))
                    }
                }

                movie.type == MovieType.SERIAL && isCurrentSerialPositionEqualsDownload(
                    event.updateSeason,
                    state.currentSeasonPosition,
                    event.updateEpisode,
                    state.currentEpisodePosition
                ) -> {
                    _uiState.update {
                        it.copy(downloadedData = MovieDownloadedData(percent, bytes, false, total))
                    }
                }
            }
        }
    }

    private fun isCurrentSerialPositionEqualsDownload(
        seasonPosition: Int,
        currentSeasonPosition: Int,
        episodePosition: Int,
        currentEpisodePosition: Int
    ) = seasonPosition == currentSeasonPosition && episodePosition == currentEpisodePosition

    // ============ Dialog Actions ============

    private fun showCacheDialog() {
        viewModelScope.launch {
            currentCacheAlert?.let { _actions.send(DetailsAction.ShowAlert(it)) }
        }
    }

    private fun showDownloadDialog() {
        viewModelScope.launch {
            val movie = _uiState.value.movie ?: return@launch
            initDownloadFileAlert(movie)
            downloadAlert?.let { _actions.send(DetailsAction.ShowAlert(it)) }
        }
    }

    private fun showUpdateDialog() {
        viewModelScope.launch {
            val movieType = _uiState.value.movie?.type
            val i = if (movieType == MovieType.SERIAL) R.string.serial else R.string.cinema

            val alert = Alert(
                title = ResourceString(R.string.update_attention, i),
                content = ResourceString(R.string.update_description),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.UpdateDirect
            )
            _actions.send(DetailsAction.ShowAlert(alert))
        }
    }

    private fun initDownloadFileAlert(movie: Movie) {
        val selectedUrl = _uiState.value.selectedCinemaUrl
        val availableToDownload = interactor.isAvailableToDownload(selectedUrl, movie.type)
        val mp4Link = movie.getAllCinemaUrls()
            .find { it.endsWith("mp4") }
            ?.substringAfterLast("/")
            .orEmpty()

        downloadAlert = if (availableToDownload) {
            Alert(
                title = ResourceString(R.string.cinema_download_attention),
                content = ResourceString(R.string.download_description, mp4Link),
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.DownloadFile(link = selectedUrl.orEmpty())
            )
        } else {
            Alert(
                title = ResourceString(R.string.cinema_download_not_available),
                content = ResourceString(R.string.download_description_error),
                btnOk = ResourceString(android.R.string.ok),
                type = AlertType.SimpleAlert
            )
        }
    }

    // ============ History & Cache Management ============

    private fun handleClearCache(event: DetailsEvent.ClearCache) {
        viewModelScope.launch {
            val state = _uiState.value
            val movie = state.movie ?: return@launch
            var total = false

            val downloadPercent = currentDownloadData?.downloadPercent
            val content = when {
                movie.type == MovieType.SERIAL && (event.clearViewHistory || (downloadPercent != null && downloadPercent == 0.0f)) -> {
                    total = true
                    ResourceString(R.string.question_remove_serial_from_history, movie.title)
                }

                movie.type == MovieType.SERIAL -> {
                    ResourceString(
                        R.string.question_remove_episodes_from_cache,
                        movie.title,
                        event.seasonPosition + 1,
                        event.episodePosition + 1
                    )
                }

                movie.type == MovieType.CINEMA && (event.clearViewHistory || (downloadPercent != null && downloadPercent == 0.0f)) -> {
                    total = true
                    ResourceString(R.string.question_remove_from_history_title, movie.title)
                }

                movie.type == MovieType.CINEMA -> {
                    ResourceString(R.string.question_remove_cinema_from_cache, movie.title)
                }

                else -> ResourceString(R.string.question_remove_from_history_total, movie.title)
            }

            val url = if (movie.type == MovieType.SERIAL) {
                val episode = movie.seasons
                    .getOrNull(event.seasonPosition)
                    ?.episodes
                    ?.getOrNull(event.episodePosition)
                episode?.dash?.ifBlank { episode.hls }.orEmpty()
            } else {
                movie.getCinemaUrl()
            }

            currentRemoveAlert = Alert(
                title = ResourceString(R.string.question_remove),
                content = content,
                btnOk = ResourceString(android.R.string.ok),
                btnCancel = ResourceString(android.R.string.cancel),
                type = AlertType.ClearCache(url, event.seasonPosition, event.episodePosition, total)
            )

            currentRemoveAlert?.let { _actions.send(DetailsAction.ShowAlert(it)) }
        }
    }

    private fun clearViewHistory(event: DetailsEvent.ClearViewHistory) {
        viewModelScope.launch {
            val movie = _uiState.value.movie
            historyInteractor.clearViewHistory(
                movieDbId = movie?.dbId,
                type = movie?.type,
                total = event.total,
                event.url
            )
                .catch { _actions.send(DetailsAction.ShowError(ThrowableString(it))) }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _actions.send(DetailsAction.ShowError(ThrowableString(result.throwable)))
                        }

                        is DataResult.Success -> {
                            if (result.result) {
                                handleSuccessfulCacheClear(movie?.type, event)
                            }
                        }
                    }
                }
        }
    }

    private suspend fun handleSuccessfulCacheClear(
        movieType: MovieType?,
        event: DetailsEvent.ClearViewHistory
    ) {
        when (movieType) {
            MovieType.CINEMA -> {
                if (event.total) {
                    _actions.send(DetailsAction.ShowToast(ResourceString(R.string.movie_viewhistory_cleared)))
                    _actions.send(DetailsAction.NavigateBack)
                } else {
                    _actions.send(DetailsAction.ShowToast(ResourceString(R.string.movie_cache_cleared)))
                }
            }

            MovieType.SERIAL -> {
                if (event.total) {
                    _actions.send(DetailsAction.ShowToast(ResourceString(R.string.serial_history_cleared)))
                    _actions.send(DetailsAction.NavigateBack)
                } else {
                    _actions.send(DetailsAction.ShowToast(ResourceString(R.string.serial_episode_cache_cleared)))
                }
            }

            else -> {}
        }
    }

    private fun addToViewHistory() {
        viewModelScope.launch {
            val movieDbId = _uiState.value.movie?.dbId ?: return@launch

            historyInteractor.addToViewHistory(movieDbId)
                .catch { _actions.send(DetailsAction.ShowError(ThrowableString(it))) }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _actions.send(DetailsAction.ShowError(ThrowableString(result.throwable)))
                        }

                        is DataResult.Success -> {
                            if (result.result) {
                                _actions.send(DetailsAction.HistoryAdded)
                                _uiState.update { it.copy(hasSavedData = true) }
                            }
                        }
                    }
                }
        }
    }

    // ============ Other Actions ============

    private fun downloadSelectedUrlToFile() {
        viewModelScope.launch {
            val state = _uiState.value
            val movie = state.movie ?: return@launch
            val selectedUrl = state.selectedCinemaUrl

            val file =
                if (selectedUrl?.endsWith("mpd") == true || selectedUrl?.endsWith("m3u8") == true) {
                    val mp4Link = movie.getAllCinemaUrls().find { it.endsWith("mp4") }
                    val fileName = mp4Link?.substringAfterLast("/").orEmpty()
                    RequestDownloadFile(
                        url = selectedUrl,
                        fileName = fileName,
                        title = movie.title,
                        isMp4 = false
                    )
                } else {
                    RequestDownloadFile(
                        url = selectedUrl.orEmpty(),
                        fileName = selectedUrl?.substringAfterLast("/").orEmpty(),
                        title = movie.title,
                        isMp4 = true
                    )
                }

            _actions.send(DetailsAction.RequestDownload(file))
        }
    }

    private fun updateMovieData() {
        viewModelScope.launch {
            val movie = _uiState.value.movie ?: return@launch
            val pageUrl = movie.pageUrl
            val url = interactor.getBaseUrl() + "/" + pageUrl
            _actions.send(DetailsAction.NavigateToUpdate(url))
        }
    }

    private fun sendFeedback(text: String) {
        viewModelScope.launch {
            val state = _uiState.value
            feedbackInteractor.sendMessage(
                content = text,
                movie = state.movie,
                seasonPosition = state.currentSeasonPosition,
                episodePosition = state.currentEpisodePosition
            )
                .catch { _actions.send(DetailsAction.ShowError(ThrowableString(it))) }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _actions.send(DetailsAction.ShowError(ThrowableString(result.throwable)))
                        }

                        is DataResult.Success -> {
                            _actions.send(DetailsAction.ShowToast(ResourceString(R.string.feedback_dialog_result_ok)))
                        }
                    }
                }
        }
    }
}
