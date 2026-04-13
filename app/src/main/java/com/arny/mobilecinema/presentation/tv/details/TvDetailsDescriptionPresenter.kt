package com.arny.mobilecinema.presentation.tv.details

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType

class TvDetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val movie = item as? Movie ?: return
        val context = viewHolder.view.context

        // Название
        viewHolder.title.text = movie.title

        // Подзаголовок с подробной информацией
        viewHolder.subtitle.text = buildString {
            val info = movie.info

            // Год
            if (info.year > 0) {
                append(info.year)
            }

            // Тип контента
            val typeStr = when (movie.type) {
                MovieType.CINEMA -> {
                    context.getString(R.string.cinema)
                }
                MovieType.SERIAL -> {
                    val seasons = movie.seasons
                    val episodes = seasons.sumOf { it.episodes.size }
                    buildString {
                        append(context.getString(R.string.serial))
                        if (seasons.isNotEmpty()) {
                            append(" • ")
                            append(
                                context.resources.getQuantityString(
                                    R.plurals.sezons,
                                    seasons.size,
                                    seasons.size
                                )
                            )
                            append(" • ")
                            append(
                                context.resources.getQuantityString(
                                    R.plurals.episods,
                                    episodes,
                                    episodes
                                )
                            )
                        }
                    }
                }
                else -> null
            }

            if (typeStr != null) {
                if (isNotEmpty()) append(" • ")
                append(typeStr)
            }

            // Страны
            if (info.countries.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(info.countries.joinToString(", "))
            }

            // Жанры
            if (info.genres.isNotEmpty()) {
                val genresList = info.genres.flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(5)

                if (genresList.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(genresList.joinToString(", "))
                }
            }

            // Рейтинги в отдельной строке
            val ratings = mutableListOf<String>()
            if (info.ratingKP > 0) {
                ratings.add("КП: ★ ${String.format("%.1f", info.ratingKP)}")
            }
            if (info.ratingImdb > 0) {
                ratings.add("IMDb: ★ ${String.format("%.1f", info.ratingImdb)}")
            }
            if (ratings.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(ratings.joinToString("  •  "))
            }

            // Лайки/дизлайки
            if (info.likes > 0 || info.dislikes > 0) {
                if (isNotEmpty()) append("\n")
                append("👍 ${info.likes}  👎 ${info.dislikes}")
            }
        }

        // Описание
        viewHolder.body.text = movie.info.description.ifBlank {
            context.getString(R.string.no_description)
        }
    }
}