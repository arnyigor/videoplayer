package com.arny.mobilecinema.presentation.details

import android.content.Context
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.utils.AbstractArrayAdapter

internal class TrackSelectorSpinnerAdapter(context: Context) :
    AbstractArrayAdapter<String>(context, R.layout.i_track_select_text) {
    override fun getItemTitle(item: String): String = item
}