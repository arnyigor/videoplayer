package com.arny.mobilecinema.presentation.listeners

interface OnSearchListener {
    fun isSearchComplete(): Boolean
    fun collapseSearch()
}