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

    @Test
    fun `movies sql can filter by source update date`() {
        val query = getMoviesSQL(
            search = "",
            order = AppConstants.Order.SMART,
            searchType = AppConstants.SearchType.TITLE,
            movieTypes = listOf(MovieType.CINEMA, MovieType.SERIAL),
            updatedFrom = 123456789L,
            likesPriority = true,
            limit = 20,
            offset = 0
        )

        assertTrue(query.sql.contains("m.updated >= ?"))
    }

    @Test
    fun `smart order discounts accumulated popularity by age`() {
        val query = getMoviesSQL(
            search = "",
            order = AppConstants.Order.SMART,
            searchType = AppConstants.SearchType.TITLE,
            movieTypes = listOf(MovieType.CINEMA, MovieType.SERIAL),
            likesPriority = true,
            limit = 20,
            offset = 0
        ).sql

        assertTrue(query.contains("CAST(m.likes AS REAL)"))
        assertTrue(query.contains("m.likes + 100"))
        assertTrue(query.contains("CAST(m.likes + m.dislikes AS REAL)"))
        assertTrue(query.contains("m.likes + m.dislikes + 150"))
        assertTrue(query.contains("CAST(m.likes + 10 AS REAL)"))
        assertTrue(query.contains("m.likes + m.dislikes + 20"))
        assertTrue(query.contains("<= 0.5 THEN 0"))
        assertTrue(query.contains("* 0.75"))
        assertTrue(query.contains("* 0.25"))
        assertTrue(query.contains("THEN 0.55"))
        assertTrue(query.contains("THEN 0.40"))
        assertTrue(query.contains("THEN 0.32"))
        assertTrue(query.contains("ELSE 0.12"))
        assertTrue(query.contains("* 0.65"))
        assertTrue(query.contains("* 0.35"))
        assertTrue(query.contains("* 0.70"))
        assertTrue(query.contains("* 0.30"))
        assertTrue(query.contains("THEN 0.68"))
        assertTrue(query.contains("THEN 0.50"))
        assertTrue(query.contains("m.ratingImdb > 0 AND m.ratingKp > 0"))
        val orderBy = query.substringAfter("ORDER BY")
        val qualityOrderIndex = orderBy.indexOf("m.ratingImdb > 0 OR m.ratingKp > 0")
        val adjustedPopularityOrderIndex = orderBy.indexOf("ELSE 0.12")
        val yearOrderIndex = orderBy.indexOf("m.year DESC")
        val likesOrderIndex = orderBy.indexOf("m.likes DESC")
        val reactionsOrderIndex = orderBy.indexOf("(m.likes + m.dislikes) DESC")
        val dbIdOrderIndex = orderBy.indexOf("m.dbId DESC")
        assertTrue(qualityOrderIndex >= 0)
        assertTrue(adjustedPopularityOrderIndex > qualityOrderIndex)
        assertTrue(yearOrderIndex > adjustedPopularityOrderIndex)
        assertTrue(likesOrderIndex > yearOrderIndex)
        assertTrue(reactionsOrderIndex > likesOrderIndex)
        assertTrue(dbIdOrderIndex > yearOrderIndex)
        assertFalse(query.contains("m.likes + m.dislikes + 200"))
        assertFalse(orderBy.contains("m.updated DESC"))
        assertFalse(query.contains("m.likes DESC, m.ratingImdb DESC"))
    }
}
