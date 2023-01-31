package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.utils.formatFileSize
import com.arny.mobilecinema.data.utils.isFileExists
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.domain.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class MainInteractorImpl @Inject constructor(
    private val dataRepository: DataRepository,
) : MainInteractor {
    override suspend fun getUpdateDate(): DataResult<String> {
        val updateFile = withContext(Dispatchers.IO) { dataRepository.downloadUpdate() }
        val newUpdate = updateFile.readText()
        val update = dataRepository.getLastUpdate()
        println("updateText:$newUpdate")
        println("update:$update")
        return if (update != newUpdate) {
            DataResult.Success(newUpdate)
        } else {
            DataResult.Success("")
        }
    }

    private suspend fun getUpdateData(): List<AnwapMovie> {
        var result = emptyList<AnwapMovie>()
        val file = dataRepository.downloadDataFile()
        if (file.isFileExists() && file.length() > 0) {
            val dataFile = dataRepository.unzipFile(file)
            Timber.d("dataFile size:${formatFileSize(dataFile.length())}")
            val movies = dataRepository.readFile(dataFile)
            if (movies.isNotEmpty()) {
                result = movies
            }
        }
        return result
    }

}
