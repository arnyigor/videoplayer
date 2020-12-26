package com.arny.homecinema.data.repository.sources

import android.content.Context
import javax.inject.Inject

class AssetsReaderImpl @Inject constructor(
    private val context: Context
) : AssetsReader {
    override fun readFileText(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }
}