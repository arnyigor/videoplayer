package com.arny.mobilecinema.domain.repository

interface MegaRepository {
    fun downloadDB(): Boolean
    fun unzipFile(): Boolean
}