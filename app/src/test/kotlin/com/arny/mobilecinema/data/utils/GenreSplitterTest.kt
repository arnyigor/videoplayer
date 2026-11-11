package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Регресс на «жанры одним блоком»: сайт присылает часть карточек строкой
 * без запятых («боевик триллер мелодрама...»), и все жанры показывались одним чипом.
 */
class GenreSplitterTest {

    private val dict = listOf(
        "Боевик", "Триллер", "Мелодрама", "Комедия", "Криминал", "Детектив",
        "Мультфильм", "Мюзикл", "Фэнтези", "Драма", "Приключения", "Семейный",
        "Фантастика", "Ужасы", "Боевые искусства", "Научная фантастика",
        "Черный юмор", "Молодёжная комедия",
    )

    @Test
    fun `comma separated genres split as before`() {
        assertEquals(
            listOf("мультфильм", "мюзикл", "фэнтези", "драма", "комедия", "приключения", "семейный"),
            GenreSplitter.split(listOf("мультфильм,мюзикл,фэнтези,драма,комедия,приключения,семейный"), dict),
        )
    }

    @Test
    fun `space separated block from the site splits into individual genres`() {
        assertEquals(
            listOf("Боевик", "Триллер", "Мелодрама", "Комедия", "Криминал", "Детектив"),
            GenreSplitter.split(listOf("боевик триллер мелодрама комедия криминал детектив"), dict),
        )
    }

    @Test
    fun `multi word genres stay whole`() {
        assertEquals(
            listOf("Боевые искусства", "Приключения"),
            GenreSplitter.split(listOf("боевые искусства приключения"), dict),
        )
        assertEquals(
            listOf("Научная фантастика", "Мультфильм"),
            GenreSplitter.split(listOf("научная фантастика мультфильм"), dict),
        )
    }

    @Test
    fun `greedy match prefers longer genre first`() {
        assertEquals(
            listOf("Молодёжная комедия", "Драма"),
            GenreSplitter.split(listOf("молодёжная комедия драма"), dict),
        )
    }

    @Test
    fun `unknown text is not broken apart`() {
        assertEquals(
            listOf("авторское видение режиссёра"),
            GenreSplitter.split(listOf("авторское видение режиссёра"), dict),
        )
    }

    @Test
    fun `duplicates collapse and casing is normalized`() {
        assertEquals(
            listOf("комедия", "драма"),
            GenreSplitter.split(listOf("комедия", "Комедия", "драма"), dict),
        )
    }
}
