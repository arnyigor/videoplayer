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
            Glide.with(ivVideoIcon)
                .load(movie.img)
                .into(ivVideoIcon)
        }
    }

    override fun getLayout(): Int = R.layout.i_home_video

    override fun initializeViewBinding(view: View): IHomeVideoBinding = IHomeVideoBinding.bind(view)
}
