package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.domain.repository.GistsRepository
import com.arny.mobilecinema.domain.repository.JsoupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class MainInteractorImpl @Inject constructor(
    private val gistsRepository: GistsRepository,
    private val jsoupRepository: JsoupRepository
) : MainInteractor {
    override fun loadData(): Flow<DataResult<MoviesData>> = gistsRepository.loadData()
    override fun getVideoPath(path: String): Flow<DataResult<String>> =
        if (path.contains("https://mi.anwap.tube/films/\\d+$".toRegex())) {
            flow { emit(jsoupRepository.loadLink(path)) }
        } else {
            flow { emit(DataResult.Success(path)) }
        }
}