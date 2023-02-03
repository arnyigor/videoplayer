package com.arny.mobilecinema.presentation.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHomeVideoBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide

class HomeItemsAdapter(
    private val onItemClick: (item: ViewMovie) -> Unit
) : PagingDataAdapter<ViewMovie, HomeItemsAdapter.VideosViewHolder>(
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

    inner class VideosViewHolder(val binding: IHomeVideoBinding) :
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
                    .load(item.img.getWithDomain())
                    .into(ivVideoIcon)
                val type = getType(item, context)
                tvTypeYear.text = String.format("%d %s", item.year, type)
                tvInfo.text =
                    String.format("%d\uD83D\uDC4D %d\uD83D\uDC4E", item.likes, item.dislikes)
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
