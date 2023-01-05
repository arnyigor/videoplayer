package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ITypeBinding
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.presentation.utils.diffItemCallback

class VideoTypesAdapter(
    private val onItemClick: (position: Int, item: VideoMenuLink) -> Unit
) : ListAdapter<VideoMenuLink, VideoTypesAdapter.VideoTypesViewHolder>(
    diffItemCallback(
        itemsTheSame = { item1, item2 ->
            item1.title == item2.title
        }
    )
) {

    val items: List<VideoMenuLink>
        get() = currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoTypesViewHolder {
        return VideoTypesViewHolder(
            ITypeBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: VideoTypesViewHolder, position: Int) {
        holder.bind(getItem(holder.absoluteAdapterPosition))
    }

    inner class VideoTypesViewHolder(private val itemBinding: ITypeBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(item: VideoMenuLink) {
            with(itemBinding) {
                btnType.text = item.title
                if (item.selected) {
                    btnType.setBackgroundColor(
                        ContextCompat.getColor(
                            itemBinding.root.context,
                            R.color.btn_state_selected
                        )
                    )
                }
                btnType.setOnClickListener {
                    onItemClick(absoluteAdapterPosition, item)
                }
            }
        }
    }
}
