package com.arny.homecinema.data.repository.sources.assets

interface AssetsReader {
    fun readFileText(fileName: String): String
}