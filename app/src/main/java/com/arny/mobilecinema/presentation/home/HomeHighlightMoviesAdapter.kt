package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHomeHighlightMovieBinding
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback

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
                item.ratingLabel()?.let(::add)
            }.joinToString(" \u2022 ")
            ivPoster.loadMoviePoster(item.img, baseUrl)
        }
    }

    class HighlightMovieViewHolder(val binding: IHomeHighlightMovieBinding) :
        RecyclerView.ViewHolder(binding.root)
}
