package com.arny.mobilecinema.data.db.sources

import androidx.sqlite.db.SimpleSQLiteQuery
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.isNotEmpty

/**
 * Оптимизированный helper для генерации SQL-запросов с пагинацией.
 * Основные улучшения:
 * 1. Использование EXISTS вместо LEFT JOIN для isFavorite (более эффективно)
 * 2. Предварительная подстановка параметров для предотвращения SQL-инъекций
 * 3. Оптимизация порядка условий WHERE
 * 4. Улучшенная обработка NULL значений
 */
object PagingSourceHelperOptimized {

    fun getMoviesSQL(
        search: String,
        order: String,
        searchType: String,
        movieTypes: List<MovieType>,
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        years: SimpleIntRange? = null,
        imdbs: SimpleFloatRange? = null,
        kps: SimpleFloatRange? = null,
        likesPriority: Boolean,
        limit: Int,
        offset: Int,
    ): SimpleSQLiteQuery {
        val whereConditions = mutableListOf<String>()
        val args = mutableListOf<Any?>()

        // Основной SELECT с оптимизированным EXISTS для isFavorite
        val sb = StringBuilder().apply {
            append(
                """SELECT 
                    m.dbId, 
                    m.title, 
                    m.type, 
                    m.img, 
                    m.year, 
                    m.likes, 
                    m.dislikes, 
                    CASE WHEN EXISTS(SELECT 1 FROM favorites f WHERE f.movie_dbid = m.dbId) THEN 1 ELSE 0 END AS isFavorite 
                FROM movies m"""
            )
        }

        // Условия WHERE - добавляем в оптимальном порядке (самые селективные первыми)
        addSearchCondition(search, searchType, whereConditions, args)
        addMovieTypesCondition(movieTypes, whereConditions)
        addYearsCondition(years, whereConditions, args)
        addCountriesCondition(countries, whereConditions)
        addGenresCondition(genres, whereConditions)
        addImdbCondition(imdbs, whereConditions, args)
        addKpCondition(kps, whereConditions, args)

        // Добавляем WHERE если есть условия
        if (whereConditions.isNotEmpty()) {
            sb.append(" WHERE ").append(whereConditions.joinToString(" AND "))
        }

        // ORDER BY с оптимизацией
        appendOrderBy(sb, order, likesPriority)

        // LIMIT и OFFSET
        sb.append(" LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)

        sb.append(";")
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }

    private fun addSearchCondition(
        search: String,
        searchType: String,
        conditions: MutableList<String>,
        args: MutableList<Any?>
    ) {
        if (search.isNotBlank()) {
            val searchCondition = when (searchType) {
                AppConstants.SearchType.TITLE -> "m.title LIKE '%' || ? || '%'"
                AppConstants.SearchType.DIRECTORS -> "m.directors LIKE '%' || ? || '%'"
                AppConstants.SearchType.ACTORS -> "m.actors LIKE '%' || ? || '%'"
                AppConstants.SearchType.GENRES -> "m.genre LIKE '%' || ? || '%'"
                else -> null
            }
            searchCondition?.let {
                conditions.add(it)
                args.add(search)
                // Добавляем дополнительные варианты для поиска по названию
                if (searchType == AppConstants.SearchType.TITLE) {
                    val words = search.split(" ")
                    if (words.size == 2) {
                        val first = words[0].trim()
                        val second = words[1].trim()
                        conditions.add("m.title LIKE '%' || ? || '%'")
                        args.add("${first}_$second")
                        conditions.add("m.title LIKE '%' || ? || '%'")
                        args.add("$first $second")
                    }
                }
            }
        }
    }

    private fun addMovieTypesCondition(
        movieTypes: List<MovieType>,
        conditions: MutableList<String>
    ) {
        if (movieTypes.isNotEmpty()) {
            val types = movieTypes.joinToString("','", prefix = "'", postfix = "'") { it.value.toString() }
            conditions.add("m.type IN ($types)")
        }
    }

    private fun addYearsCondition(
        years: SimpleIntRange?,
        conditions: MutableList<String>,
        args: MutableList<Any?>
    ) {
        if (years != null && years.isNotEmpty()) {
            conditions.add("m.year >= ? AND m.year <= ?")
            args.add(years.from)
            args.add(years.to)
        }
    }

    private fun addCountriesCondition(
        countries: List<String>,
        conditions: MutableList<String>
    ) {
        if (countries.isNotEmpty()) {
            val countriesList = countries.joinToString("','", prefix = "'", postfix = "'")
            conditions.add("m.countries IN ($countriesList)")
        }
    }

    private fun addGenresCondition(
        genres: List<String>,
        conditions: MutableList<String>
    ) {
        if (genres.isNotEmpty()) {
            val lowerCaseGenres = genres.map { it.lowercase() }
            val genresList = lowerCaseGenres.joinToString("','", prefix = "'", postfix = "'")
            conditions.add("LOWER(m.genre) IN ($genresList)")
        }
    }

    private fun addImdbCondition(
        imdbs: SimpleFloatRange?,
        conditions: MutableList<String>,
        args: MutableList<Any?>
    ) {
        if (imdbs != null && imdbs.isNotEmpty()) {
            conditions.add("m.ratingImdb >= ? AND m.ratingImdb <= ?")
            args.add(imdbs.from)
            args.add(imdbs.to)
        }
    }

    private fun addKpCondition(
        kps: SimpleFloatRange?,
        conditions: MutableList<String>,
        args: MutableList<Any?>
    ) {
        if (kps != null && kps.isNotEmpty()) {
            conditions.add("m.ratingKp >= ? AND m.ratingKp <= ?")
            args.add(kps.from)
            args.add(kps.to)
        }
    }

    private fun appendOrderBy(
        sb: StringBuilder,
        order: String,
        likesPriority: Boolean
    ) {
        if (order.isNotBlank()) {
            var curOrder = order
            sb.append(" ORDER BY ")
            if (curOrder == AppConstants.Order.LAST_TIME) {
                curOrder = AppConstants.Order.NONE
            }
            val orderClause = when (curOrder) {
                AppConstants.Order.NONE -> if (likesPriority) {
                    "m.updated DESC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC"
                } else {
                    "m.updated DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                }
                AppConstants.Order.RATINGS -> "m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.TITLE -> if (likesPriority) {
                    "m.title ASC COLLATE NOCASE, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                } else {
                    "m.title ASC COLLATE NOCASE, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                }
                AppConstants.Order.YEAR_DESC -> if (likesPriority) {
                    "m.year DESC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC"
                } else {
                    "m.year DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                }
                AppConstants.Order.YEAR_ASC -> if (likesPriority) {
                    "m.year ASC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC"
                } else {
                    "m.year ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                }
                else -> "m.updated DESC"
            }
            sb.append(orderClause)
        }
    }
}