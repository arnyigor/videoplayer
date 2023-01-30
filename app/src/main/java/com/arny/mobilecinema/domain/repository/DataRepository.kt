package com.arny.mobilecinema.domain.repository

import java.io.File

interface DataRepository {
  suspend fun downloadDataFile(): File
  fun unzipFile(zipFile: File): File
}