package com.arny.mobilecinema.presentation.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHomeVideoBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import java.util.Locale

class VideoItemsAdapter(
    private val baseUrl: String,
    private val showFreshness: Boolean = false,
    private val onItemClick: (item: ViewMovie) -> Unit
) : PagingDataAdapter<ViewMovie, VideoItemsAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.dbId == m2.dbId },
        contentsTheSame = { m1, m2 -> m1 == m2 }
    )
) {
    private companion object {
        const val NEW_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosViewHolder =
        VideosViewHolder(
            IHomeVideoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    class VideosViewHolder(val binding: IHomeVideoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: VideosViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            val context = holder.binding.root.context
            holder.binding.apply {
                root.setOnClickListener {
                    onItemClick(item)
                }
                tvVideoTitle.text = item.title
                Glide.with(ivVideoIcon)
                    .load(item.img.getWithDomain(baseUrl))
                    .placeholder(R.drawable.placeholder_movie)
                    .error(R.drawable.placeholder_movie)
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .centerCrop()
                    .into(ivVideoIcon)
                val type = getType(item, context)
                val isFresh = showFreshness &&
                    item.updated > 0L &&
                    System.currentTimeMillis() - item.updated in 0..NEW_WINDOW_MS

                tvTypeYear.text = buildList {
                    if (item.year > 0) {
                        add(item.year.toString())
                    }
                    if (type.isNotBlank()) {
                        add(type)
                    }
                    if (isFresh) {
                        add("NEW")
                    }
                }.joinToString(" • ")

                tvInfo.text = buildList {
                    when {
                        item.ratingImdb > 0.0 -> {
                            add(
                                String.format(
                                    Locale.getDefault(),
                                    "IMDb %.1f",
                                    item.ratingImdb
                                )
                            )
                        }

                        item.ratingKp > 0.0 -> {
                            add(
                                String.format(
                                    Locale.getDefault(),
                                    "KP %.1f",
                                    item.ratingKp
                                )
                            )
                        }
                    }

                    add("${item.likes}\uD83D\uDC4D")
                    add("${item.dislikes}\uD83D\uDC4E")
                }.joinToString("  ")
                ivFavorite.isVisible = item.isFavorite
            }
        }
    }

    private fun getType(item: ViewMovie, context: Context): String {
        return when (item.type) {
            MovieType.CINEMA.value -> context.getString(R.string.cinema)
            MovieType.SERIAL.value -> context.getString(R.string.serial)
            else -> ""
        }
    }
}
