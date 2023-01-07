package com.arny.mobilecinema.presentation.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.IHistoryVideoBinding
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.bumptech.glide.Glide

class HistoryVideosAdapter(
    private val onItemClick: (item: Movie) -> Unit,
    private val onItemClearClick: (item: Movie) -> Unit
) : ListAdapter<Movie, HistoryVideosAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { item1, item2 ->
            item1.uuid == item2.uuid
        },
        contentsTheSame = { item1, item2 ->
            item1 == item2
        }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosViewHolder =
        VideosViewHolder(
            IHistoryVideoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: VideosViewHolder, position: Int) {
        holder.bind(getItem(holder.absoluteAdapterPosition))
    }

    inner class VideosViewHolder(private val binding: IHistoryVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Movie) {
            with(binding) {
                root.setOnClickListener { onItemClick(item) }
                ivClear.setOnClickListener { onItemClearClick(item) }
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
