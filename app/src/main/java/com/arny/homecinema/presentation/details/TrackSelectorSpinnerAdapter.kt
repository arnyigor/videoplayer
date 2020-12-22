package com.arny.homecinema.presentation.details

import android.content.Context
import com.arny.homecinema.R
import com.arny.homecinema.presentation.utils.AbstractArrayAdapter

internal class TrackSelectorSpinnerAdapter(context: Context) :
    AbstractArrayAdapter<String>(context, R.layout.i_track_select_text) {
    override fun getItemTitle(item: String): String = item
}