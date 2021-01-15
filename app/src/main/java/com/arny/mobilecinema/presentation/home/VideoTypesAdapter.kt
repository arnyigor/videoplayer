package com.arny.mobilecinema.presentation.home

import android.widget.Button
import androidx.core.content.ContextCompat
import com.arny.mobilecinema.R
import com.arny.mobilecinema.di.models.VideoMenuLink
import com.arny.mobilecinema.presentation.utils.SimpleAbstractAdapter

class VideoTypesAdapter : SimpleAbstractAdapter<VideoMenuLink>() {
    override fun getDiffCallback(): DiffCallback<VideoMenuLink> {
        return object : DiffCallback<VideoMenuLink>() {
            override fun areItemsTheSame(
                oldItem: VideoMenuLink,
                newItem: VideoMenuLink
            ): Boolean = oldItem == newItem

            override fun areContentsTheSame(
                oldItem: VideoMenuLink,
                newItem: VideoMenuLink
            ): Boolean = oldItem == newItem
        }
    }

    override fun getLayout(viewType: Int): Int = R.layout.i_type

    override fun bindView(item: VideoMenuLink, viewHolder: VH) {
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
