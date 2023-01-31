package com.arny.mobilecinema.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.update.DataUpdateInteractor
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.presentation.uimodels.Alert
import com.arny.mobilecinema.presentation.uimodels.AlertType
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.strings.ThrowableString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val dataUpdateInteractor: DataUpdateInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()
    private val _toast = MutableSharedFlow<IWrappedString>()
    val toast = _toast.asSharedFlow()
    private val _alert = MutableSharedFlow<Alert>()
    val alert = _alert.asSharedFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _movies = MutableStateFlow<List<AnwapMovie>>(emptyList())
    val movies = _movies.asStateFlow()

    fun downloadData() {
        viewModelScope.launch {
            flow { emit(dataUpdateInteractor.getUpdateDate()) }
                .onStart { _loading.value = true }
                .onCompletion { _loading.value = false }
                .catch { _error.emit(ThrowableString(it)) }
                .collect { result ->
                    when (result) {
                        is DataResult.Error -> {
                            _error.emit(ThrowableString(result.throwable))
                        }

                        is DataResult.Success -> {
                            val updateTime = result.result
                            if (updateTime.isNotBlank()) {
                                _alert.emit(
                                    Alert(
                                        title = ResourceString(R.string.new_films_update),
                                        content = ResourceString(
                                            R.string.question_update_format,
                                            updateTime
                                        ),
                                        btnOk = ResourceString(android.R.string.ok),
                                        btnCancel = ResourceString(android.R.string.cancel),
                                        type = AlertType.Update
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun search(seqrch: String) {
    }

    fun onConfirmAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                AlertType.Update -> {
                    _toast.emit(ResourceString(R.string.update_started))
                    dataUpdateInteractor.requestFile()
                }
            }
        }
    }

    fun onCancelAlert(type: AlertType) {
        viewModelScope.launch {
            when (type) {
                AlertType.Update -> {}
            }
        }
    }
}