package com.arny.mobilecinema.domain.repository

import java.io.File

interface MegaRepository {
  suspend fun downloadDataFile(): Boolean
  fun unzipFile(): File
}