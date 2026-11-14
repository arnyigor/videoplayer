package com.arny.mobilecinema.data.models

import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MovieEntityMapperTest {
    @Test
    fun `writes folded keys but preserves display text`() {
        val movie = Movie(
            title = "Жил-был пёс",
            info = MovieInfo(
                origTitle = "Once Upon a Dog",
                actors = listOf("Пётр Фёдоров", "Семён Трескунов"),
                directors = listOf("Фёдор Бондарчук"),
                genres = listOf("Чёрный юмор", "Фильм-нуар"),
                countries = listOf("Кот-д’Ивуар", "США"),
            ),
        )
        val entity = MovieEntity().setData(movie)
        assertEquals(movie.title, entity.title)
        assertEquals(movie.info.actors.joinToString(","), entity.actors)
        assertEquals(movie.info.genres.joinToString(","), entity.genre)
        assertEquals("жил был пес", entity.titleSearch)
        assertEquals("once upon a dog", entity.origTitleSearch)
        assertEquals("петр федоров семен трескунов", entity.actorsSearch)
        assertEquals("федор бондарчук", entity.directorsSearch)
        assertEquals("черный юмор фильм нуар", entity.genreSearch)
        assertEquals("кот д ивуар сша", entity.countriesSearch)

        // Catalog import reuses one entity; keys must not leak from the previous row.
        entity.setData(Movie(title = "Новый фильм"))
        assertEquals("новый фильм", entity.titleSearch)
        assertEmptyMetadataKeys(entity)
        entity.setData(movie)
        entity.clear()
        assertEquals("", entity.titleSearch)
        assertEmptyMetadataKeys(entity)
    }

    private fun assertEmptyMetadataKeys(entity: MovieEntity) {
        assertEquals(listOf("", "", "", "", ""), listOf(entity.origTitleSearch,
            entity.actorsSearch, entity.directorsSearch, entity.genreSearch, entity.countriesSearch))
    }
}
