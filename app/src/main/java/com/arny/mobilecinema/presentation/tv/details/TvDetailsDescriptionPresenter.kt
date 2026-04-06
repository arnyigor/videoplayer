package com.arny.mobilecinema.presentation.tv.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.arny.mobilecinema.R

class TvDetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val movie = item as? com.arny.mobilecinema.domain.models.Movie ?: return

        viewHolder.title.text = movie.title

        viewHolder.subtitle.text = buildString {
            val year = movie.info.year
            if (year > 0) append(year)
            val typeStr = when (movie.type) {
                com.arny.mobilecinema.domain.models.MovieType.CINEMA -> "Фильм"
                com.arny.mobilecinema.domain.models.MovieType.SERIAL -> "Сериал"
                else -> null
            }
            if (typeStr != null) {
                if (isNotEmpty()) append(" • ")
                append(typeStr)
            }
            val rating = movie.info.ratingKP.takeIf { it > 0 }
                ?: movie.info.ratingImdb.takeIf { it > 0 }
            if (rating != null && rating > 0) {
                if (isNotEmpty()) append(" • ")
                append("★ $rating")
            }
        }

        viewHolder.body.text = movie.info.description
    }
}
