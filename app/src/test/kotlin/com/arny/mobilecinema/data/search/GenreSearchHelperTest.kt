package com.arny.mobilecinema.data.search

import com.arny.mobilecinema.data.db.sources.getMoviesSQL
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenreSearchHelperTest {

    @Test
    fun `groups similar animation genres into common cartoons genre`() {
        val genres = GenreSearchHelper.toDisplayGenres(
            listOf(
                "Мультсериал, Анимация",
                "мультик",
                "Полнометражный мультфильм",
                "Комедия",
                "Криминальная драма"
            )
        )

        assertTrue("Мультфильмы" in genres)
        assertTrue("Комедии" in genres)
        assertTrue("Драмы" in genres)
        assertTrue("Криминал" in genres)
        assertTrue(genres.count { it == "Мультфильмы" } == 1)
    }

    @Test
    fun `common cartoons genre matches all similar genre occurrences`() {
        assertTrue(GenreSearchHelper.isGenreMatched("мульик, семейный", "Мультфильмы"))
        assertTrue(GenreSearchHelper.isGenreMatched("Полнометражный мультфильм", "Мультфильмы"))
        assertTrue(GenreSearchHelper.isGenreMatched("Анимационный", "Мультфильмы"))
        assertFalse(GenreSearchHelper.isGenreMatched("Криминальная драма", "Мультфильмы"))
    }

    @Test
    fun `extended genre sql uses contains search instead of exact in search`() {
        val query = getMoviesSQL(
            search = "",
            order = AppConstants.Order.YEAR_DESC,
            searchType = AppConstants.SearchType.TITLE,
            movieTypes = listOf(MovieType.CINEMA, MovieType.SERIAL),
            genres = listOf("Мультфильмы", "Комедии"),
            likesPriority = true,
            limit = 20,
            offset = 0
        ).sql

        assertTrue(query.contains("m.genre LIKE"))
        assertTrue(query.contains(" OR "))
        assertFalse(query.contains("LOWER(m.genre) IN"))
    }
}
