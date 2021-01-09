package com.arny.homecinema.presentation.home

import android.widget.Button
import androidx.core.content.ContextCompat
import com.arny.homecinema.R
import com.arny.homecinema.di.models.VideoSearchLink
import com.arny.homecinema.presentation.utils.SimpleAbstractAdapter

class VideoTypesAdapter : SimpleAbstractAdapter<VideoSearchLink>() {
    override fun getDiffCallback(): DiffCallback<VideoSearchLink> {
        return object : DiffCallback<VideoSearchLink>() {
            override fun areItemsTheSame(
                oldItem: VideoSearchLink,
                newItem: VideoSearchLink
            ): Boolean = oldItem == newItem

            override fun areContentsTheSame(
                oldItem: VideoSearchLink,
                newItem: VideoSearchLink
            ): Boolean = oldItem == newItem
        }
    }

    override fun getLayout(viewType: Int): Int = R.layout.i_type

    override fun bindView(item: VideoSearchLink, viewHolder: VH) {
        val adapterPosition = viewHolder.adapterPosition
        viewHolder.itemView.apply {
            val btn = findViewById<Button>(R.id.btnType)
            btn.text = item.title
            if (item.selected) {
                btn.setBackgroundColor(ContextCompat.getColor(btn.context,R.color.btn_state_selected))
            }
            btn.setOnClickListener {
                listener?.onItemClick(adapterPosition, item)
            }
        }
    }
}
