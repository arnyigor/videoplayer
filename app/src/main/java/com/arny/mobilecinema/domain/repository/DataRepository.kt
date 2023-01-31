package com.arny.mobilecinema.domain.repository

import com.arny.mobilecinema.domain.models.AnwapMovie
import java.io.File

interface DataRepository {
  suspend fun downloadDataFile(): File
  fun unzipFile(zipFile: File): File
  suspend fun downloadUpdate(): File
  fun getLastUpdate():String
  fun readFile(file: File): List<AnwapMovie>
}