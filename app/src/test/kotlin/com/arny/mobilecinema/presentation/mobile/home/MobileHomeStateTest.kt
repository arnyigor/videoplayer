package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.ViewMovie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MobileHomeStateTest {

    private val movie = ViewMovie(dbId = 1, title = "Колония", year = 2026)

    private val continueCard = ContinueCardModel(
        movieDbId = 2,
        title = "Кэрри",
        posterUrl = "",
        episodeLabel = "S01E01",
        progressFraction = 0.2f,
        remaining = null,
    )

    @Test
    fun `loading wins over empty data`() {
        val state = MobileHomeUiState(isLoading = true)
        assertEquals(MobileHomeMode.LOADING, state.mode)
    }

    @Test
    fun `error without data is an error screen`() {
        val state = MobileHomeUiState(isLoading = false, error = true)
        assertEquals(MobileHomeMode.ERROR, state.mode)
    }

    @Test
    fun `empty feed without error is an empty screen`() {
        val state = MobileHomeUiState(isLoading = false)
        assertEquals(MobileHomeMode.EMPTY, state.mode)
    }

    @Test
    fun `error with stale data keeps content on screen`() {
        val state = MobileHomeUiState(
            isLoading = false,
            error = true,
            highlights = HomeHighlights(recent = listOf(movie)),
        )
        assertEquals(MobileHomeMode.CONTENT, state.mode)
        assertEquals(movie.dbId, state.content.featured?.dbId)
    }

    @Test
    fun `continue watching alone is enough for content`() {
        val state = MobileHomeUiState(
            isLoading = false,
            continueCards = listOf(continueCard),
        )
        assertEquals(MobileHomeMode.CONTENT, state.mode)
        assertEquals(MobileHomeBlockType.CONTINUE, state.content.blocks.first().type)
    }

    @Test
    fun `progress update does not touch other blocks`() {
        val state = MobileHomeUiState(
            isLoading = false,
            highlights = HomeHighlights(recent = listOf(movie)),
        )
        val before = state.content.blocks.map { it.id }
        val after = state.copy(continueCards = listOf(continueCard)).content.blocks.map { it.id }

        // Появился только блок «Продолжить», порядок остальных рядов не изменился.
        assertEquals(listOf("continue") + before, after)
    }
}
