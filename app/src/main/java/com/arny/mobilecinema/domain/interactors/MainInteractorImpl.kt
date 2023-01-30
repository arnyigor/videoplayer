package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.models.Data
import com.arny.mobilecinema.domain.repository.DataRepository
import com.arny.mobilecinema.domain.repository.GistsRepository
import com.arny.mobilecinema.domain.repository.JsoupRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileReader
import javax.inject.Inject

class MainInteractorImpl @Inject constructor(
    private val gistsRepository: GistsRepository,
    private val jsoupRepository: JsoupRepository,
    private val dataRepository: DataRepository,
) : MainInteractor {
    override fun loadData(): Flow<DataResult<MoviesData>> = gistsRepository.loadData()
    override fun getVideoPath(path: String): Flow<DataResult<String>> =
        if (path.contains("https://mi.anwap.tube/films/\\d+$".toRegex())) {
            flow { emit(jsoupRepository.loadLink(path)) }
        } else {
            flow { emit(DataResult.Success(path)) }
        }

    override suspend fun downloadData(): DataResult<List<AnwapMovie>> =
        withContext(Dispatchers.IO) {
            var result = emptyList<AnwapMovie>()
            val file = dataRepository.downloadDataFile()
            if (file.isFileExists() && file.length() > 0) {
                val dataFile = dataRepository.unzipFile(file)
                Timber.d("dataFile size:${formatFileSize(dataFile.length())}")
                val movies = readFile(dataFile)
                if (movies.isNotEmpty()) {
                    result = movies
                }
            }
            DataResult.Success(result)
        }

    private fun readFile(dataFile: File): List<AnwapMovie> {
        val fromJson = Gson().fromJson(FileReader(dataFile), Data::class.java)
        return fromJson.movies
    }
}
