package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.domain.models.MoviesData2
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
        if (megaRepository.downloadDB()) {
            val dataFile = megaRepository.unzipFile()
            val data = dataFile.readText()
            val moviesData = data.fromJson(MoviesData2::class.java)
            println("data:${moviesData?.movies?.size}")
        }
        emit(DataResult.Success(true))
    }.flowOn(Dispatchers.IO)
}