package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.IHomeVideoBinding
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.bumptech.glide.Glide

class VideosAdapter(
    private val onItemClick: (item: Movie) -> Unit
) : ListAdapter<Movie, VideosAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { item1, item2 ->
            item1.uuid == item2.uuid
        },
        contentsTheSame = { item1, item2 ->
            item1 == item2
        }
    )
) {
    val items: List<Movie>
        get() = currentList

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
        fun bind(item: Movie) {
            with(binding) {
                root.setOnClickListener {
                    onItemClick(item)
                }
                tvVideoTitle.text = item.title
                var img = item.img
                if (img.isNullOrBlank()) {
                    img =
                        "https://yt3.ggpht.com/a/AATXAJwQSv9J0nimhTCQgcwQmdE_ePrril6TZg1_nGSf=s900-c-k-c0xffffffff-no-rj-mo"
                }
                Glide.with(ivVideoIcon)
                    .load(img)
                    .into(ivVideoIcon)
            }
        }
    }
}
