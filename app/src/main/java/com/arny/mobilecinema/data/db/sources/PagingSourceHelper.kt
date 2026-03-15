package com.arny.mobilecinema.data.db.sources

import androidx.sqlite.db.SimpleSQLiteQuery
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.isNotEmpty
import timber.log.Timber

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
    val whereWrapper = WhereWrapper()
    val sb = StringBuilder()
    val args = mutableListOf<Any?>()
    // Изменено: добавлен алиас 'm' и JOIN с favorites, добавлено поле isFavorite
    sb.append("SELECT m.dbId, m.title, m.type, m.img, m.year, m.likes, m.dislikes, CASE WHEN f.movie_dbid IS NOT NULL THEN 1 ELSE 0 END AS isFavorite FROM movies m LEFT JOIN favorites f ON m.dbId = f.movie_dbid ")
    search(search, sb, whereWrapper, searchType, args)
    movieTypes(movieTypes, whereWrapper, sb)
    years(years, whereWrapper, sb, args)
    countries(countries, whereWrapper, sb)
    genres(genres, whereWrapper, sb)
    imdbs(imdbs, whereWrapper, sb, args)
    kps(kps, whereWrapper, sb, args)
    order(order, sb, likesPriority)
    limit(sb, args, limit, offset)
    sb.append(";")
    val query = sb.toString()
//    Timber.d("queryString:$query")
//    Timber.d("args:$args")
    return SimpleSQLiteQuery(query, args.toTypedArray())
}

private fun genres(
    genres: List<String>,
    whereWrapper: WhereWrapper,
    sb: StringBuilder
) {
    if (genres.isNotEmpty()) {
        val lowerCaseGenres = genres.map { it.lowercase() }

        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND")
        }
        sb.append(" LOWER(m.genre) IN (${lowerCaseGenres.joinToString(prefix = "'", postfix = "'")}) ")
    }
}

private fun imdbs(
    imdbRange: SimpleFloatRange?,
    whereWrapper: WhereWrapper,
    sb: StringBuilder,
    args: MutableList<Any?>
) {
    if (imdbRange != null && imdbRange.isNotEmpty()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE ")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND ")
        }
        sb.append(" m.ratingImdb >= ? AND m.ratingImdb <= ? ")
        args.add(imdbRange.from)
        args.add(imdbRange.to)
    }
}

private fun kps(
    kpRange: SimpleFloatRange?,
    whereWrapper: WhereWrapper,
    sb: StringBuilder,
    args: MutableList<Any?>
) {
    if (kpRange != null && kpRange.isNotEmpty()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND")
        }
        sb.append(" m.ratingKp >= ? AND m.ratingKp <= ? ")
        args.add(kpRange.from)
        args.add(kpRange.to)
    }
}

private fun countries(
    countries: List<String>,
    whereWrapper: WhereWrapper,
    sb: StringBuilder
) {
    if (countries.isNotEmpty()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND")
        }
        sb.append(" m.countries IN (${countries.joinToString(prefix = "'", postfix = "'")}) ")
    }
}

private fun years(
    years: SimpleIntRange?,
    whereWrapper: WhereWrapper,
    sb: StringBuilder,
    args: MutableList<Any?>
) {
    if (years != null && years.isNotEmpty()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND")
        }
        sb.append(" m.year >= ? AND m.year <= ? ")
        args.add(years.from)
        args.add(years.to)
    }
}

private fun movieTypes(
    movieTypes: List<MovieType>,
    whereWrapper: WhereWrapper,
    sb: StringBuilder
) {
    if (movieTypes.isNotEmpty()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND")
        }
        sb.append(" m.type IN (${movieTypes.joinToString { "'${it.value}'" }}) ")
    }
}

private fun search(
    search: String,
    sb: StringBuilder,
    whereWrapper: WhereWrapper,
    searchType: String,
    args: MutableList<Any?>
) {
    if (search.isNotBlank()) {
        if (!whereWrapper.hasWhere) {
            sb.append(" WHERE ")
            whereWrapper.hasWhere = true
        } else {
            sb.append(" AND ")
        }
        sb.append(
            when (searchType) {
                AppConstants.SearchType.TITLE -> " m.title LIKE '%' || ? || '%' "
                AppConstants.SearchType.DIRECTORS -> " m.directors LIKE '%' || ? || '%' "
                AppConstants.SearchType.ACTORS -> " m.actors LIKE '%' || ? || '%' "
                AppConstants.SearchType.GENRES -> " m.genre LIKE '%' || ? || '%' "
                else -> " "
            }
        )
        extendedSearch(searchType, args, search, sb)
    }
}

private fun order(order: String, sb: StringBuilder, likesPriority: Boolean) {
    if (order.isNotBlank()) {
        var curOrder = order
        sb.append(" ORDER BY ")
        if (curOrder == AppConstants.Order.LAST_TIME) {
            curOrder = AppConstants.Order.NONE
        }
        sb.append(
            when (curOrder) {
                AppConstants.Order.NONE -> if (likesPriority) " m.updated DESC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC " else " m.updated DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC "
                AppConstants.Order.RATINGS -> " m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC "
                AppConstants.Order.TITLE -> if (likesPriority) " m.title ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC " else " m.title ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC "
                AppConstants.Order.YEAR_DESC -> if (likesPriority) " m.year DESC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC " else " m.year DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC "
                AppConstants.Order.YEAR_ASC -> if (likesPriority) " m.year ASC, m.likes DESC, m.ratingImdb DESC, m.ratingKp DESC " else " m.year ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC "
                else -> " "
            }
        )
    }
}

private fun extendedSearch(
    searchType: String,
    args: MutableList<Any?>,
    search: String,
    sb: StringBuilder
) {
    if (searchType == AppConstants.SearchType.TITLE) {
        val words = search.split(" ")
        if (words.size == 2) {
            val first = words[0].trim()
            val second = words[1].trim()
            args.add("${first}_${second}")
            sb.append(" OR")
            sb.append(" title LIKE '%' || ? || '%'")
            args.add("${first}_ $second")
            sb.append(" OR")
            sb.append(" title LIKE '%' || ? || '%'")
            args.add("$first _ $second")
        } else {
            args.add(search)
        }
    } else {
        args.add(search)
    }
}

fun getHistorySQL(
    search: String,
    order: String,
    searchType: String,
    limit: Int,
    offset: Int
): SimpleSQLiteQuery {
    val sb = StringBuilder()
    val args = mutableListOf<Any?>()
    sb.append("SELECT m.dbId, m.title, m.type, m.img, m.year, m.likes, m.dislikes, CASE WHEN f.movie_dbid IS NOT NULL THEN 1 ELSE 0 END AS isFavorite FROM movies m INNER JOIN history h ON m.dbId=h.movie_dbid LEFT JOIN favorites f ON m.dbId = f.movie_dbid ")
    if (search.isNotBlank()) {
        sb.append(" WHERE")
        sb.append(
            when (searchType) {
                AppConstants.SearchType.TITLE -> " m.title LIKE '%' || ? || '%'"
                AppConstants.SearchType.DIRECTORS -> " m.directors LIKE '%' || ? || '%'"
                AppConstants.SearchType.ACTORS -> " m.actors LIKE '%' || ? || '%'"
                AppConstants.SearchType.GENRES -> " m.genre LIKE '%' || ? || '%'"
                else -> ""
            }
        )
        extendedSearch(searchType, args, search, sb)
    }
    if (order.isNotBlank()) {
        sb.append(" ORDER BY")
        sb.append(
            when (order) {
                AppConstants.Order.NONE -> " m.updated DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.LAST_TIME -> " h.latest_time DESC, m.updated DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.RATINGS -> " m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.TITLE -> " m.title ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.YEAR_DESC -> " m.year DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                AppConstants.Order.YEAR_ASC -> " m.year ASC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
                else -> ""
            }
        )
    }
    sb.append(" LIMIT ? OFFSET ?")
    args.add(limit)
    args.add(offset)
    sb.append(";")
    val query = sb.toString()
//    println("queryString:$query")
//    println("args:$args")
    return SimpleSQLiteQuery(query, args.toTypedArray())
}

private fun limit(
    sb: StringBuilder,
    args: MutableList<Any?>,
    limit: Int,
    offset: Int
) {
    sb.append(" LIMIT ? OFFSET ?")
    args.add(limit)
    args.add(offset)
}