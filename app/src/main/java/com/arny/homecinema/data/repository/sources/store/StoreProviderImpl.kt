package com.arny.homecinema.data.repository.sources.store

import com.arny.homecinema.data.repository.sources.prefs.Prefs
import com.arny.homecinema.data.repository.sources.prefs.PrefsConstants
import com.arny.homecinema.data.utils.fromJson
import com.arny.homecinema.data.utils.toJson
import com.arny.homecinema.di.models.Movie
import javax.inject.Inject

class StoreProviderImpl @Inject constructor(
    private val prefs: Prefs
) : StoreProvider {
    override fun canSaveToStore(): Boolean {
        return prefs.get<Boolean>(PrefsConstants.PREF_SAVE_TO_STORE) ?: false
    }

    override fun saveToStore(movie: Movie) {
        prefs.put(movie.title, movie.toJson())
    }

    override fun getFromStore(title: String): Movie? {
        return try {
            prefs.get<String>(title).fromJson(Movie::class.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun searchMovies(searchText: String): List<Movie> {
        return prefs.getAll()?.filter {
            it.key.contains(searchText, true)
        }?.let { entries -> toList(entries) } ?: emptyList()
    }

    override fun allMovies(): List<Movie> {
        return prefs.getAll()?.let { entries -> toList(entries) } ?: emptyList()
    }

    override fun removeFromSaved(movie: Movie) {
        prefs.remove(movie.title)
    }

    private fun toList(
        entries: Map<String, *>
    ): MutableList<Movie> {
        val mutableListOf = mutableListOf<Movie>()
        entries.asSequence()
            .forEach {
                try {
                    it.value.fromJson(Movie::class.java)
                        ?.let { it1 -> mutableListOf.add(it1) }
                } catch (e: Exception) {
                }
            }
        return mutableListOf
    }
}