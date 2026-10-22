package com.arny.mobilecinema.domain.search

/**
 * Релевантность совпадения запроса с названием тайтла.
 *
 * Оценки упорядочены по убыванию: точное совпадение названия → название начинается
 * с запроса → с запроса начинается отдельное слово → запрос встречается внутри названия.
 * Ноль означает, что по названию совпадения нет: такие результаты нельзя поднимать
 * выше совпадений (аудит v2, §4.4).
 *
 * Сравнение регистронезависимое для ASCII (как SQLite `LIKE`). Транслитерация
 * кириллицы в латиницу здесь намеренно не угадывается: для этого служит поле
 * оригинального названия, которое приходит с сайта вместе с русским.
 */
object SearchRelevance {

    const val EXACT: Int = 100
    const val STARTS_WITH: Int = 80
    const val WORD_STARTS_WITH: Int = 60
    const val CONTAINS: Int = 40
    const val NO_MATCH: Int = 0

    /** Наибольшая оценка по всем переданным названиям (русское, оригинальное, алиасы). */
    fun score(query: String, vararg titles: String): Int =
        titles.maxOfOrNull { scoreSingle(query, it) } ?: NO_MATCH

    private fun scoreSingle(query: String, title: String): Int {
        val q = query.trim()
        val t = title.trim()
        if (q.isEmpty() || t.isEmpty()) return NO_MATCH
        if (t.equals(q, ignoreCase = true)) return EXACT
        if (t.startsWith(q, ignoreCase = true)) return STARTS_WITH
        val words = t.split(*SEPARATORS).filter { it.isNotBlank() }
        if (words.any { it.startsWith(q, ignoreCase = true) }) return WORD_STARTS_WITH
        if (t.contains(q, ignoreCase = true)) return CONTAINS
        return NO_MATCH
    }

    /**
     * Устойчивая сортировка по убыванию релевантности. Порядок элементов с одинаковой
     * оценкой сохраняется, поэтому источник может задать свой приоритет (например,
     * фильмы сайта перед сериалами) до вызова.
     */
    fun <T> rank(query: String, items: List<T>, titles: (T) -> List<String>): List<T> {
        if (query.isBlank() || items.size < 2) return items
        return items
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<T>> { score(query, *titles(it.value).toTypedArray()) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private val SEPARATORS = charArrayOf(' ', '\t', '\n', ':', ',', '.', '-', '–', '—', '/', '(', ')', '!', '?', '«', '»', '\'')
}
