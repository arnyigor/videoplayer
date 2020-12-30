package com.arny.homecinema.presentation.models

import android.view.View
import com.arny.homecinema.R
import com.arny.homecinema.databinding.IHomeVideoBinding
import com.arny.homecinema.di.models.Movie
import com.bumptech.glide.Glide

import com.xwray.groupie.viewbinding.BindableItem

class VideoItem(val movie: Movie) : BindableItem<IHomeVideoBinding>() {

    override fun bind(binding: IHomeVideoBinding, position: Int) {
        with(binding) {
            tvVideoTitle.text = movie.title
            var img = movie.img
            if (img.isNullOrBlank()) {
                img = "https://yt3.ggpht.com/a/AATXAJwQSv9J0nimhTCQgcwQmdE_ePrril6TZg1_nGSf=s900-c-k-c0xffffffff-no-rj-mo"
            }
            Glide.with(ivVideoIcon)
                .load(img)
                .into(ivVideoIcon)
        }
    }

    override fun getLayout(): Int = R.layout.i_home_video

    override fun initializeViewBinding(view: View): IHomeVideoBinding = IHomeVideoBinding.bind(view)
}
