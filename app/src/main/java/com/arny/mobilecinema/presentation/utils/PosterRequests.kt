package com.arny.mobilecinema.presentation.utils

import android.widget.ImageView
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.posterUrlCandidates
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import android.graphics.drawable.Drawable

/** All poster surfaces share Glide's existing image client, headers, cache and mirror retry. */
fun ImageView.loadPoster(
    path: String?,
    baseUrl: String = Prefs.getInstance(context).get<String>(PrefsConstants.BASE_URL).orEmpty(),
    widthPx: Int = 0,
    heightPx: Int = 0,
    crop: Boolean = true,
) {
    val manager = Glide.with(this)
    val urls = posterUrlCandidates(path, baseUrl)
    if (urls.isEmpty()) {
        clearPoster()
        setImageResource(R.drawable.placeholder_movie)
        return
    }
    fun request(url: String): RequestBuilder<Drawable> = manager.load(url)
        .placeholder(R.drawable.placeholder_movie)
        .error(R.drawable.placeholder_movie)
        .transition(DrawableTransitionOptions.withCrossFade(200))
        .apply {
            if (crop) centerCrop() else fitCenter()
            if (widthPx > 0 && heightPx > 0) override(widthPx, heightPx)
        }
    val primary = request(urls.first())
    urls.getOrNull(1)?.let { primary.error(request(it)) }
    primary.into(this)
}

fun ImageView.clearPoster() {
    // Release can happen after the Activity has been destroyed; clearing through the app manager is safe.
    Glide.with(context.applicationContext).clear(this)
}
