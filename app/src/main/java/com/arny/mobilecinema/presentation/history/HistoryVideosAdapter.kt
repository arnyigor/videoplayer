package com.arny.mobilecinema.presentation.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.IHistoryVideoBinding
import com.arny.mobilecinema.domain.models.AnwapMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide

class HistoryVideosAdapter(
    private val onItemClick: (item: AnwapMovie) -> Unit,
    private val onItemClearClick: (item: AnwapMovie) -> Unit
) : ListAdapter<AnwapMovie, HistoryVideosAdapter.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { item1, item2 ->
            item1.movieId == item2.movieId
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
        fun bind(item: AnwapMovie) {
            with(binding) {
                root.setOnClickListener { onItemClick(item) }
                ivClear.setOnClickListener { onItemClearClick(item) }
                tvVideoTitle.text = item.title
                Glide.with(ivVideoIcon)
                    .load(item.img.getWithDomain())
                    .into(ivVideoIcon)
            }
        }
    }
}
