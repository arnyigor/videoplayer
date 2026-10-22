package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MobileHomeRowsFactoryTest {

    private fun movie(id: Long) = ViewMovie(dbId = id, title = "Movie $id", type = MovieType.CINEMA.value, year = 2026)

    private fun card(id: Long) = ContinueCardModel(
        movieDbId = id,
        title = "Series $id",
        posterUrl = "",
        episodeLabel = "S01E02",
        progressFraction = 0.4f,
        remaining = RemainingLabel(resId = 1, minutes = 20),
    )

    @Test
    fun `continue goes right after featured`() {
        val content = MobileHomeRowsFactory.build(
            highlights = HomeHighlights(recent = listOf(movie(1)), forYou = listOf(movie(2))),
            continueCards = listOf(card(10)),
        )

        assertEquals(MobileHomeBlockType.CONTINUE, content.blocks.first().type)
        assertEquals(1L, content.featured?.dbId)
    }

    @Test
    fun `empty blocks are not created`() {
        val content = MobileHomeRowsFactory.build(
            highlights = HomeHighlights(recent = listOf(movie(1))),
            continueCards = emptyList(),
        )

        assertEquals(listOf(MobileHomeBlockType.NEW), content.blocks.map { it.type })
    }

    @Test
    fun `my list comes before recommendations`() {
        val content = MobileHomeRowsFactory.build(
            highlights = HomeHighlights(forYou = listOf(movie(2))),
            continueCards = emptyList(),
            myList = listOf(movie(3)),
        )

        assertEquals(
            listOf(MobileHomeBlockType.MY_LIST, MobileHomeBlockType.FOR_YOU),
            content.blocks.map { it.type },
        )
    }

    @Test
    fun `blocks are deduplicated by title id`() {
        val content = MobileHomeRowsFactory.build(
            highlights = HomeHighlights(recent = listOf(movie(1), movie(1), movie(2))),
            continueCards = emptyList(),
        )

        assertEquals(listOf(1L, 2L), content.blocks.first().movies.map { it.dbId })
    }

    @Test
    fun `featured falls back to best now and for you`() {
        assertEquals(5L, MobileHomeRowsFactory.build(HomeHighlights(bestNow = listOf(movie(5))), emptyList()).featured?.dbId)
        assertEquals(6L, MobileHomeRowsFactory.build(HomeHighlights(forYou = listOf(movie(6))), emptyList()).featured?.dbId)
        assertNull(MobileHomeRowsFactory.build(HomeHighlights(), emptyList()).featured)
    }

    @Test
    fun `new profile shows no continue block`() {
        val content = MobileHomeRowsFactory.build(HomeHighlights(recent = listOf(movie(1))), emptyList())

        assertTrue(content.blocks.none { it.type == MobileHomeBlockType.CONTINUE })
    }
}
