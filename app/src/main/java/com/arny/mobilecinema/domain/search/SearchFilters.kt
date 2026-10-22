package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleIntRange

/** Сортировка выдачи локального каталога в едином поиске (SRCH-03). */
enum class SearchSort(val order: String, val titleRes: Int) {
    RELEVANCE(AppConstants.Order.SMART, R.string.search_sort_relevance),
    NEW(AppConstants.Order.YEAR_DESC, R.string.search_sort_new),
    RATING(AppConstants.Order.RATINGS, R.string.search_sort_rating),
    ALPHABET(AppConstants.Order.TITLE, R.string.search_sort_alphabet),
}

/**
 * Фильтры локального каталога (SRCH-03).
 *
 * Набор ограничен тем, что реально поддерживает `getMoviesSQL`: тип, жанр, страна, годы,
 * сортировка. Сайт эти фильтры не поддерживает, поэтому применять их к внешней выдаче
 * нельзя — экран обязан показать это явно, а не делать вид, что фильтр применился.
 */
data class SearchFilters(
    val kinds: List<MovieType> = emptyList(),
    val genre: String? = null,
    val country: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {

    /** Есть ли активные критерии (сортировка по релевантности — не критерий). */
    val activeCount: Int
        get() = (if (kinds.isNotEmpty()) 1 else 0) +
                (if (!genre.isNullOrBlank()) 1 else 0) +
                (if (!country.isNullOrBlank()) 1 else 0) +
                (if (yearFrom != null || yearTo != null) 1 else 0)

    val isActive: Boolean get() = activeCount > 0

    val movieTypes: List<MovieType>
        get() = kinds.ifEmpty { listOf(MovieType.CINEMA, MovieType.SERIAL) }

    fun reset(): SearchFilters = SearchFilters(sort = sort)

    /** Годы в корректном порядке: пользователь мог ввести «с» больше, чем «по». */
    val yearRange: SimpleIntRange?
        get() {
            val start = yearFrom
            val end = yearTo
            if (start == null && end == null) return null
            return when {
                start == null -> SimpleIntRange(0, requireNotNull(end))
                end == null -> SimpleIntRange(start, MAX_YEAR)
                else -> SimpleIntRange(minOf(start, end), maxOf(start, end))
            }
        }

    /** Компактное представление для бандла/`FragmentResult` — без Parcelable-обвязки. */
    fun encode(): String = listOf(
        kinds.joinToString(",") { it.name },
        genre.orEmpty(),
        country.orEmpty(),
        yearFrom?.toString().orEmpty(),
        yearTo?.toString().orEmpty(),
        sort.name,
    ).joinToString(SEPARATOR)

    companion object {
        /** Верхняя граница выдачи, когда ограничен только нижний год. */
        const val MAX_YEAR = 2100

        private const val SEPARATOR = "\u001f"

        fun decode(raw: String?): SearchFilters {
            val parts = raw?.split(SEPARATOR).orEmpty()
            if (parts.size < 6) return SearchFilters()
            return SearchFilters(
                kinds = parts[0].split(",").filter { it.isNotBlank() }
                    .mapNotNull { name -> MovieType.entries.firstOrNull { it.name == name } },
                genre = parts[1].ifBlank { null },
                country = parts[2].ifBlank { null },
                yearFrom = parts[3].toIntOrNull(),
                yearTo = parts[4].toIntOrNull(),
                sort = SearchSort.entries.firstOrNull { it.name == parts[5] } ?: SearchSort.RELEVANCE,
            )
        }
    }
}
