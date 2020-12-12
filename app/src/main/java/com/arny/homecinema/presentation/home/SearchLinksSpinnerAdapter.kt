package com.arny.homecinema.presentation.home

import android.content.Context
import com.arny.homecinema.di.models.VideoSearchLink
import com.arny.homecinema.presentation.utils.AbstractArrayAdapter

internal class SearchLinksSpinnerAdapter(context: Context) :
    AbstractArrayAdapter<VideoSearchLink>(context, android.R.layout.simple_list_item_1) {
    override fun getItemTitle(item: VideoSearchLink): String {
        return item.title
    }
}