package com.arny.videoplayer.presentation.models

import android.view.View
import com.arny.videoplayer.R
import com.arny.videoplayer.databinding.IHomeVideoBinding
import com.arny.videoplayer.di.models.Video
import com.bumptech.glide.Glide

import com.xwray.groupie.viewbinding.BindableItem

class VideoItem(private val video: Video) : BindableItem<IHomeVideoBinding>() {

    override fun bind(binding: IHomeVideoBinding, position: Int) {
        with(binding) {
            tvVideoTitle.text = video.name
            Glide.with(ivVideoIcon)
                .load(video.img)
                .into(ivVideoIcon)
        }
    }

    override fun getLayout(): Int = R.layout.i_home_video

    override fun initializeViewBinding(view: View): IHomeVideoBinding = IHomeVideoBinding.bind(view)
}
