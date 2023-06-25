package com.arny.mobilecinema.data.db.sources

import androidx.sqlite.db.SimpleSQLiteQuery
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType

fun getMoviesSQL(
    search: String,
    order: String,
    searchType: String,
    movieTypes: List<MovieType>,
    limit: Int,
    offset: Int,
): SimpleSQLiteQuery {
    val sb = StringBuilder()
    val args = mutableListOf<Any?>()
    sb.append("SELECT dbId, title, type, img, year, likes, dislikes FROM movies")
    if (search.isNotBlank()) {
        sb.append(" WHERE")
        sb.append(
            when (searchType) {
                AppConstants.SearchType.TITLE -> " title LIKE '%' || ? || '%'"
                AppConstants.SearchType.DIRECTORS -> " directors LIKE '%' || ? || '%'"
                AppConstants.SearchType.ACTORS -> " actors LIKE '%' || ? || '%'"
                AppConstants.SearchType.GENRES -> " genre LIKE '%' || ? || '%'"
                else -> ""
            }
        )
        args.add(search)
        if (movieTypes.isNotEmpty()) {
            sb.append(" AND")
            sb.append(" type IN (${movieTypes.joinToString { "'${it.value}'" }})")
        }
    }
    if (order.isNotBlank()) {
        sb.append(" ORDER BY")
        sb.append(
            when (order) {
                AppConstants.Order.NONE -> " updated DESC, likes DESC, ratingImdb DESC, ratingKp DESC"
                AppConstants.Order.RATINGS -> " ratingImdb DESC, ratingKp DESC, likes DESC"
                AppConstants.Order.TITLE -> " title ASC, likes DESC, ratingImdb DESC, ratingKp DESC"
                AppConstants.Order.YEAR_DESC -> " year DESC, likes DESC, ratingImdb DESC, ratingKp DESC"
                AppConstants.Order.YEAR_ASC -> " year ASC, likes DESC, ratingImdb DESC, ratingKp DESC"
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

fun getHistorySQL(
    search: String,
    order: String,
    searchType: String,
    limit: Int,
    offset: Int
): SimpleSQLiteQuery {
    val sb = StringBuilder()
    val args = mutableListOf<Any?>()
    sb.append("SELECT m.dbId, m.title, m.type, m.img, m.year, m.likes, m.dislikes FROM movies m INNER JOIN history h ON m.dbId=h.movie_dbid")
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
        args.add(search)
    }
    if (order.isNotBlank()) {
        sb.append(" ORDER BY")
        sb.append(
            when (order) {
                AppConstants.Order.NONE -> " m.updated DESC, m.ratingImdb DESC, m.ratingKp DESC, m.likes DESC"
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