package com.arny.mobilecinema.presentation.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.MovieComment
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val title: String = "",
    val comments: List<MovieComment> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val addCommentUrl: String = "",
) {
    val canLoadMore: Boolean
        get() = currentPage in 1 until totalPages && !isRefreshing && !isLoadingMore
}

sealed interface CommentsAction {
    data class OpenUrl(val url: String) : CommentsAction
}

class CommentsViewModel(
    private val pageUrl: String,
    title: String,
    private val interactor: MoviesInteractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState(title = title))
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val _actions = Channel<CommentsAction>(Channel.BUFFERED)
    val actions: Flow<CommentsAction> = _actions.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadPage(page = 1, refresh = true)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.canLoadMore) return
        loadPage(page = state.currentPage + 1, refresh = false)
    }

    fun openAddComment() {
        val url = _uiState.value.addCommentUrl
        if (url.isNotBlank()) {
            viewModelScope.launch { _actions.send(CommentsAction.OpenUrl(url)) }
        }
    }

    private fun loadPage(page: Int, refresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = refresh,
                    isLoadingMore = !refresh,
                    error = null,
                )
            }
            interactor.getMovieCommentsPage(pageUrl = pageUrl, page = page)
                .catch { throwable -> updateError(throwable) }
                .collectLatest { result ->
                    when (result) {
                        is DataResult.Error -> updateError(result.throwable)
                        is DataResult.Success -> {
                            val pageData = result.result
                            _uiState.update { state ->
                                val comments = if (refresh) {
                                    pageData.comments
                                } else {
                                    (state.comments + pageData.comments).distinctBy {
                                        it.id.ifBlank { "${it.author}_${it.dateText}_${it.text}" }
                                    }
                                }
                                state.copy(
                                    comments = comments,
                                    currentPage = pageData.currentPage,
                                    totalPages = pageData.totalPages,
                                    addCommentUrl = pageData.addCommentUrl,
                                    isRefreshing = false,
                                    isLoadingMore = false,
                                    error = null,
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun updateError(throwable: Throwable) {
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                error = throwable.message.orEmpty(),
            )
        }
    }
}
