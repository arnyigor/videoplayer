package com.arny.homecinema.data.repository.sources

interface AssetsReader {
    fun readFileText(fileName: String): String
}