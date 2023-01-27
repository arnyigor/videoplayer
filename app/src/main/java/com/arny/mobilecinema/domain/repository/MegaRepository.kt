package com.arny.mobilecinema.domain.repository

import java.io.File

interface MegaRepository {
    fun downloadDB(): Boolean
    fun unzipFile(): File
}