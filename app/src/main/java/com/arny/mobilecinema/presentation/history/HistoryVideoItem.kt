package com.arny.mobilecinema.presentation.history

import android.view.View
import androidx.core.view.isVisible
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHistoryVideoBinding
import com.arny.mobilecinema.di.models.Movie
import com.bumptech.glide.Glide

import com.xwray.groupie.viewbinding.BindableItem

class HistoryVideoItem constructor(
    val movie: Movie,
    private val onClearClick: (movie: Movie) -> Unit
) :
    BindableItem<IHistoryVideoBinding>() {

    override fun bind(binding: IHistoryVideoBinding, position: Int) {
        with(binding) {
            tvVideoTitle.text = movie.title
            var img = movie.img
            val noImg = img.isNullOrBlank()
            tvVideoTitle.isVisible = noImg
            if (noImg) {
                img =
                    "https://yt3.ggpht.com/a/AATXAJwQSv9J0nimhTCQgcwQmdE_ePrril6TZg1_nGSf=s900-c-k-c0xffffffff-no-rj-mo"
            }
            ivVideoIcon.setOnClickListener { onClearClick(movie) }
            Glide.with(ivVideoIcon)
                .load(img)
                .into(ivVideoIcon)
        }
    }

    override fun getLayout(): Int = R.layout.i_history_video

    override fun initializeViewBinding(view: View): IHistoryVideoBinding =
        IHistoryVideoBinding.bind(view)
}
