package com.arny.mobilecinema.presentation.sitesearch

import com.arny.mobilecinema.domain.search.RatingSource
import com.arny.mobilecinema.domain.search.SearchHit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class SearchHitRatingTest {
    private fun hit(rating: Float, source: RatingSource) =
        SearchHit(origin = com.arny.mobilecinema.domain.search.SearchOrigin.CATALOG, rating = rating, ratingSource = source)

    @Test fun `catalogue rating is labelled as IMDb`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        try {
            assertEquals("IMDb 7,7", hit(7.7f, RatingSource.IMDB).ratingLabel())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `site rating keeps a neutral caption`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        try {
            assertEquals("Рейтинг 8,5", hit(8.5f, RatingSource.SITE).ratingLabel())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `absent rating produces no label`() {
        assertNull(hit(0f, RatingSource.NONE).ratingLabel())
        assertNull(hit(0f, RatingSource.IMDB).ratingLabel())
    }
}
