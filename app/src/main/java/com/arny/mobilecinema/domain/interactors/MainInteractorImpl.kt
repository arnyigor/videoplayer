package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.domain.repository.GistsRepository
import com.arny.mobilecinema.domain.repository.JsoupRepository
import com.arny.mobilecinema.domain.repository.MegaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MainInteractorImpl @Inject constructor(
    private val gistsRepository: GistsRepository,
    private val jsoupRepository: JsoupRepository,
    private val megaRepository: MegaRepository,
) : MainInteractor {
    override fun loadData(): Flow<DataResult<MoviesData>> = gistsRepository.loadData()
    override fun getVideoPath(path: String): Flow<DataResult<String>> =
        if (path.contains("https://mi.anwap.tube/films/\\d+$".toRegex())) {
            flow { emit(jsoupRepository.loadLink(path)) }
        } else {
            flow { emit(DataResult.Success(path)) }
        }

    override fun loadDb(): Flow<DataResult<Boolean>> = flow {
        megaRepository.downloadDB()
            .takeIf { it }
            ?.let { megaRepository.unzipFile() }
            ?.takeIf { it }?.let {
                emit(DataResult.Success(true))
            } ?: run {
            emit(DataResult.Success(false))
        }
    }.flowOn(Dispatchers.IO)
}