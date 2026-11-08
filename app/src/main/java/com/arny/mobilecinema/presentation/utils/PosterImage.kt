package com.arny.mobilecinema.presentation.utils

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.arny.mobilecinema.R

/** Shared by phone and TV: use the same image pipeline as the existing XML/Leanback screens. */
@Composable
fun PosterImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(R.drawable.placeholder_movie), contentDescription, modifier, contentScale = contentScale)
        return
    }
    AndroidView(
        factory = { context -> ImageView(context).apply {
            id = R.id.poster_image
            isFocusable = false
            isClickable = false
        } },
        modifier = modifier,
        onReset = { it.clearPoster() },
        onRelease = { it.clearPoster() },
        update = { view ->
            view.contentDescription = contentDescription
            view.scaleType = if (contentScale == ContentScale.Crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            view.loadPoster(model, crop = contentScale == ContentScale.Crop)
        },
    )
}
