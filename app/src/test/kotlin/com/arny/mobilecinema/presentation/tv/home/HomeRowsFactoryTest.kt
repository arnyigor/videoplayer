package com.arny.mobilecinema.presentation.tv.home

import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.ContinueWatchingItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeRowsFactoryTest {

    private fun movie(id: Long, type: MovieType = MovieType.CINEMA) =
        ViewMovie(dbId = id, title = "Movie $id", type = type.value, year = 2026)

    private fun continueItem(id: Long) = ContinueWatchingItem(
        movieDbId = id,
        title = "Series $id",
        posterUrl = "",
        playableId = "ep:$id:1:1",
        positionMs = 600_000,
        durationMs = 2_700_000,
        seasonNumber = 1,
        episodeNumber = 1,
        updatedAt = id,
    )

    @Test
    fun `rows follow the spec order and skip empty blocks`() {
        val content = HomeRowsFactory.build(
            highlights = HomeHighlights(
                recent = listOf(movie(1)),
                bestNow = listOf(movie(2)),
                forYou = listOf(movie(3)),
            ),
            continueWatching = listOf(continueItem(10)),
        )

        assertEquals(
            listOf(
                HomeRowType.CONTINUE,
                HomeRowType.FOR_YOU,
                HomeRowType.NEW,
                HomeRowType.BEST_NOW,
            ),
            content.rows.map { it.type },
        )
    }

    @Test
    fun `new profile has no continue row and no fake for-you row`() {
        val content = HomeRowsFactory.build(
            highlights = HomeHighlights(recent = listOf(movie(1))),
            continueWatching = emptyList(),
        )

        assertEquals(listOf(HomeRowType.NEW), content.rows.map { it.type })
        assertEquals(1, content.hero?.dbId)
    }

    @Test
    fun `series row contains only serials and is deduplicated`() {
        val serial = movie(5, MovieType.SERIAL)
        val content = HomeRowsFactory.build(
            highlights = HomeHighlights(
                recent = listOf(serial, movie(6)),
                bestNow = listOf(serial),
            ),
            continueWatching = emptyList(),
        )

        val series = content.rows.first { it.type == HomeRowType.SERIES }
        assertEquals(listOf(5L), series.movies.map { it.dbId })
    }

    @Test
    fun `series row is absent when catalog has no serials`() {
        val content = HomeRowsFactory.build(
            highlights = HomeHighlights(recent = listOf(movie(1))),
            continueWatching = emptyList(),
        )

        assertNull(content.rows.firstOrNull { it.type == HomeRowType.SERIES })
    }

    @Test
    fun `hero falls back from recent to best-now and then to for-you`() {
        assertEquals(
            2L,
            HomeRowsFactory.build(
                HomeHighlights(bestNow = listOf(movie(2)), forYou = listOf(movie(3))),
                emptyList(),
            ).hero?.dbId,
        )
        assertEquals(
            3L,
            HomeRowsFactory.build(HomeHighlights(forYou = listOf(movie(3))), emptyList()).hero?.dbId,
        )
        assertNull(HomeRowsFactory.build(HomeHighlights(), emptyList()).hero)
    }

    @Test
    fun `duplicate titles inside one row are removed`() {
        val content = HomeRowsFactory.build(
            HomeHighlights(recent = listOf(movie(1), movie(1), movie(2))),
            emptyList(),
        )

        assertEquals(listOf(1L, 2L), content.rows.first().movies.map { it.dbId })
    }
}
