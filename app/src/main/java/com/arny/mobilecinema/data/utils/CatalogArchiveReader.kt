package com.arny.mobilecinema.data.utils

import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MoviesData
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException

/** Parse every selected shard before the caller is allowed to mutate the catalog. */
internal fun readCatalogFiles(files: List<File>, hasUpdateByPeriod: Boolean): List<Movie> {
    if (files.isEmpty()) throw IOException("Catalog archive contains no JSON files")
    val selected = if (hasUpdateByPeriod) {
        files.find { it.name == "data_0.json" }?.let { listOf(it) } ?: files
    } else {
        files
    }
    val gson = GsonBuilder().setLenient().create()
    return selected.flatMap { file ->
        try {
            val data = file.reader(Charsets.UTF_8).buffered().use { reader ->
                gson.fromJson(reader, MoviesData::class.java)
            }
            // Gson can populate nulls despite Kotlin's non-null declarations.
            val movies: List<Movie>? = data?.movies
            if (movies.isNullOrEmpty()) throw IOException("Missing or empty movie list")
            movies.forEach { movie ->
                if (movie.title.isBlank() || movie.pageUrl.isBlank()) {
                    throw IOException("Movie is missing its title or page URL")
                }
            }
            movies
        } catch (error: Exception) {
            throw IOException("Invalid catalog file: ${file.name}", error)
        }
    }
}
