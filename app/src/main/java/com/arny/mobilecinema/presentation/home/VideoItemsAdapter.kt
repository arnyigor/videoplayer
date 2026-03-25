package com.arny.mobilecinema.presentation.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
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
import java.util.Locale

class VideoItemsAdapter(
    private val baseUrl: String,
    private val onItemClick: (item: ViewMovie, sharedView: View) -> Unit
) : PagingDataAdapter<ViewMovie, VideoItemsAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.dbId == m2.dbId },
        contentsTheSame = { m1, m2 -> m1 == m2 }
    )
) {

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
                ivVideoIcon.transitionName = "poster_${item.dbId}"
                root.setOnClickListener {
                    onItemClick(item, ivVideoIcon)
                }
                tvVideoTitle.text = item.title
                Glide.with(ivVideoIcon)
                    .load(item.img.getWithDomain(baseUrl))
                    .placeholder(R.drawable.play_circle_outline)
                    .dontAnimate() // Раскомментируй, если транзиция будет мерцать
                    .into(ivVideoIcon)
                val type = getType(item, context)
                val year = if (item.year > 0) "${item.year} " else ""
                tvTypeYear.text = String.format("%s%s", year, type)
                tvInfo.text =
                    String.format(
                        Locale.getDefault(),
                        "%d\uD83D\uDC4D %d\uD83D\uDC4E",
                        item.likes,
                        item.dislikes
                    )
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
