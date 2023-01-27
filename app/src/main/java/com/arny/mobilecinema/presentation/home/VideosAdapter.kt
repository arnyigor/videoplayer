package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.IHomeVideoBinding
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.bumptech.glide.Glide

class VideosAdapter(
    private val onItemClick: (item: AnwapMovie) -> Unit
) : ListAdapter<AnwapMovie, VideosAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { item1, item2 ->
            item1.pageUrl == item2.pageUrl
        },
        contentsTheSame = { item1, item2 ->
            item1 == item2
        }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosViewHolder =
        VideosViewHolder(
            IHomeVideoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: VideosViewHolder, position: Int) {
        holder.bind(getItem(holder.absoluteAdapterPosition))
    }

    inner class VideosViewHolder(private val binding: IHomeVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AnwapMovie) {
            with(binding) {
                root.setOnClickListener {
                    onItemClick(item)
                }
                tvVideoTitle.text = item.title
                val img = item.img
                Glide.with(ivVideoIcon)
                    .load(img)
                    .into(ivVideoIcon)
            }
        }
    }
}
