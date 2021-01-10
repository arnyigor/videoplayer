package com.arny.mobilecinema.data.repository.sources.assets

interface AssetsReader {
    fun readFileText(fileName: String): String
}