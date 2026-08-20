package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHomeHighlightMovieBinding
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import java.util.Locale

class HomeHighlightMoviesAdapter(
    private val baseUrl: String,
    private val onItemClick: (item: ViewMovie) -> Unit
) : ListAdapter<ViewMovie, HomeHighlightMoviesAdapter.HighlightMovieViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.dbId == m2.dbId },
        contentsTheSame = { m1, m2 -> m1 == m2 }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HighlightMovieViewHolder =
        HighlightMovieViewHolder(
            IHomeHighlightMovieBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: HighlightMovieViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            root.setOnClickListener {
                onItemClick(item)
            }
            tvTitle.text = item.title
            tvMeta.text = buildList {
                if (item.year > 0) {
                    add(item.year.toString())
                }
                bestRating(item)?.let(::add)
            }.joinToString(" \u2022 ")
            Glide.with(ivPoster)
                .load(item.img.getWithDomain(baseUrl))
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .centerCrop()
                .into(ivPoster)
        }
    }

    private fun bestRating(item: ViewMovie): String? {
        return when {
            item.ratingImdb > 0.0 -> String.format(
                Locale.getDefault(),
                "IMDb %.1f",
                item.ratingImdb
            )

            item.ratingKp > 0.0 -> String.format(
                Locale.getDefault(),
                "KP %.1f",
                item.ratingKp
            )

            else -> null
        }
    }

    class HighlightMovieViewHolder(val binding: IHomeHighlightMovieBinding) :
        RecyclerView.ViewHolder(binding.root)
}
